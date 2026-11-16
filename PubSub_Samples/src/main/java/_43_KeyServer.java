// MIT License
// Copyright (c) Indi.An GmbH

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.plccom.opc.ua.core.StatusCodes;
import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.core.UserTokenType;
import com.plccom.opc.ua.pubsub.sdk.UaSksKeysRetrievedEvent;
import com.plccom.opc.ua.pubsub.sdk.UaSksRoles;
import com.plccom.opc.ua.pubsub.sdk.UaSksSecurityGroup;
import com.plccom.opc.ua.pubsub.sdk.UaSksServer;
import com.plccom.opc.ua.server.application.UaBindAddressMode;
import com.plccom.opc.ua.server.application.UaEndpointHostMode;
import com.plccom.opc.ua.server.application.UaServer;
import com.plccom.opc.ua.server.application.UaServerCertificate;
import com.plccom.opc.ua.server.application.UaServerCertificateStore;
import com.plccom.opc.ua.server.application.UaServerConfiguration;
import com.plccom.opc.ua.transport.security.SecurityMode;
import com.plccom.opc.ua.transport.security.SecurityPolicy;

/**
 * Workshop 43: Key Server (Security Key Service, SKS).
 *
 * <p>In Workshop 17 and 18 both sides use the same key from the source code. That works for two programs. It does not
 * work for fifty machines, and it never changes the key. A Security Key Service (SKS) solves both problems (OPC UA
 * Part 14, 5.4.5 and 8.2 to 8.8):</p>
 * <ul>
 *   <li>The SKS is an OPC UA server. It keeps the keys of one or more SecurityGroups. Publishers and Subscribers log in
 *       and call the Method GetSecurityKeys (8.3.2) to get the keys of THEIR group.</li>
 *   <li>The keys change by themselves every KeyLifetime. The SecurityTokenId counts up by one each time. Everybody who
 *       asks gets the current key and the next ones, so nobody has to ask at the moment of the change.</li>
 *   <li>Who may get which keys is a question of ROLES: the role SecurityKeyServerAccess may fetch keys,
 *       SecurityKeyServerAdmin manages the groups (8.8). The server checks the role on every call.</li>
 * </ul>
 *
 * <p>SAFE BY DEFAULT - what this program does and why:</p>
 * <ul>
 *   <li>Only ENCRYPTED endpoints (SignAndEncrypt). GetSecurityKeys is refused on any other channel with
 *       Bad_SecurityModeInsufficient.</li>
 *   <li>NO anonymous access. There are two users, 'reader' (role Access) and 'admin' (role Admin). A user with no key
 *       service role gets no key and does not even learn which groups exist.</li>
 *   <li>NO password and NO key in the source code. The passwords come from the environment variables
 *       PLCCOM_SKS_READER_PASSWORD and PLCCOM_SKS_ADMIN_PASSWORD, or you type them (at least 10 characters). The private
 *       key of the server certificate is protected by PLCCOM_PKI_PASSWORD, or you type that too.</li>
 *   <li>Keys never appear on the screen or in a log. This program shows WHO fetched WHAT (user, group, token number,
 *       result), never the key.</li>
 *   <li>The server listens on this computer only (127.0.0.1). To open it to the network, change the base address on
 *       purpose, and put a firewall rule on it.</li>
 * </ul>
 *
 * <p>TRUST: a client certificate is accepted only after YOU move it into the trusted folder. The first time a client
 * connects, its certificate is filed in pki/rejected/certs and the client is refused. Compare the thumbprint, then move
 * the file to pki/trusted/certs and let the client connect again.</p>
 *
 * <p>KEYS ARE KEPT IN MEMORY ONLY. When this program ends, the keys are gone, and after a restart the numbering starts
 * again. The SDK can give you the state of a group to keep it in an encrypted store of your own
 * ({@code UaSksSecurityGroup.exportState()}); this workshop leaves that out.</p>
 *
 * <p>Run Workshop 44 (Publisher) and 45 (Subscriber); they fetch their keys here.</p>
 */
public final class _43_KeyServer {

    // The address of the key server and the name of the group the Workshops 44 and 45 use.
    private static final String ENDPOINT = "opc.tcp://127.0.0.1:48440";
    private static final String GROUP_ID = "WorkshopGroup";

    // A short lifetime so you can watch the keys change: every 30 seconds the token goes up by one.
    // In real life this is hours or days.
    private static final Duration KEY_LIFETIME = Duration.ofSeconds(30);

    private _43_KeyServer() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 43 - Key Server");

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 43                      ║");
            System.out.println("║  Key Server (Security Key Service)                           ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Hands out the keys of the SecurityGroup 'WorkshopGroup'.    ║");
            System.out.println("║  Encrypted channel only, no anonymous access.                ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Address: opc.tcp://127.0.0.1:48440  (this computer only)    ║");
            System.out.println("║  Start Workshop 44 (Publisher) and 45 (Subscriber).          ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // Important !!!!!!!!!!!!!!!!!!
            // Enter your Username + Serial here! Please note: with blank fields the library runs
            // for 15 minutes during a debug session. Both values can also come
            // from configuration or an environment variable.
            // Free trial license (14 days, uninterrupted): https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/
            String licenseUser = "";
            String licenseSerial = "";
            // Leave both fields empty to take the values from the environment
            // variables PLCCOM_OPCUA_LICENSE_USER and PLCCOM_OPCUA_LICENSE_SERIAL.
            // A value entered here is used first.
            if (licenseUser.isEmpty()) {
                licenseUser = System.getenv().getOrDefault("PLCCOM_OPCUA_LICENSE_USER", "");
            }
            if (licenseSerial.isEmpty()) {
                licenseSerial = System.getenv().getOrDefault("PLCCOM_OPCUA_LICENSE_SERIAL", "");
            }

            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

            // -- Step 1: The secrets - from the environment or from you -------------
            String readerPassword = secretFrom(input, "PLCCOM_SKS_READER_PASSWORD", "Password for the user 'reader' (fetches keys)");
            String adminPassword = secretFrom(input, "PLCCOM_SKS_ADMIN_PASSWORD", "Password for the user 'admin' (manages groups)");
            String pkiPassword = secretFrom(input, "PLCCOM_PKI_PASSWORD", "Password that protects the private key of the server certificate");
            System.out.println();

            // -- Step 2: Configure the OPC UA server --------------------------------
            // A Security Key Service lives on an ordinary OPC UA server of this SDK.
            UaServerConfiguration config = new UaServerConfiguration();
            config.setApplicationName("PLCcom Workshop 43 - Key Server");
            config.setApplicationUri("urn:localhost:PLCcom:Workshop:43");
            config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
            config.setNamespaceUri("http://indi-an.com/opcua/workshop/key-server");

            // The server listens on 127.0.0.1 only: UaBindAddressMode.AsConfigured binds the endpoint to the IP
            // address of the base address. Without it the server listens on ALL network cards ("localhost" as
            // host name would do the same, whatever the mode).
            config.setBaseAddresses(Arrays.asList(ENDPOINT));
            config.setEndpointHostMode(UaEndpointHostMode.AsConfigured);
            config.setBindAddressMode(UaBindAddressMode.AsConfigured);

            // ONLY encrypted endpoints. A key must never travel over a channel that is
            // not encrypted. The service would refuse the call anyway; offering no weak
            // endpoint at all means a client cannot even try.
            config.setSecurityModes(Arrays.asList(
                    SecurityMode.AES256_SIGN_ENCRYPT,
                    SecurityMode.AES128_SIGN_ENCRYPT,
                    SecurityMode.BASIC256SHA256_SIGN_ENCRYPT));

            // NO Anonymous. Only a user with name and password can log in. The password
            // itself travels inside the encrypted channel; the policy names the
            // algorithm that protects it on top.
            config.setUserTokenPolicies(Arrays.asList(new UserTokenPolicy("username_basic256sha256",
                    UserTokenType.UserName, null, null, SecurityPolicy.BASIC256SHA256.getPolicyUri())));

            // A client certificate that is not in pki/trusted/certs is refused.
            config.setAutoAcceptUntrustedCertificates(false);

            // The private key of the server certificate is protected with a password.
            // UaServerCertificate makes the certificate on the first start, in ./pki.
            List<UaServerCertificate> certificates = new ArrayList<UaServerCertificate>();
            certificates.add(new UaServerCertificate("./pki", pkiPassword, "KeyServer", config.getApplicationUri(), 365,
                    "Indi.An GmbH", UaServerCertificate.CertificateRole.APPLICATION));
            UaServerCertificateStore store = UaServerCertificateStore.load("./pki", certificates);
            for (UaServerCertificate missing : store.getMissingOrExpired()) {
                missing.build(true);
            }
            config.setCertificateStore(store);

            // -- Step 3: Create the server and its two users ------------------------
            // The role decides what a user may do with the key service (Part 14, 8.8):
            //   UaSksRoles.ACCESS  may fetch keys (GetSecurityKeys)
            //   UaSksRoles.ADMIN   may manage SecurityGroups, but gets no keys by that
            UaServer server = new UaServer(licenseUser, licenseSerial);
            server.addUser("reader", readerPassword, UaSksRoles.ACCESS);
            server.addUser("admin", adminPassword, UaSksRoles.ADMIN);

            System.out.print("  Starting the server ... ");
            server.start(config);
            System.out.println("OK");

            // -- Step 4: Put the key service on the server --------------------------
            // UaSksServer takes the standard object Server/PublishSubscribe of the server
            // (the well-known NodeIds of Part 14), so every client that follows the
            // standard finds GetSecurityKeys where it expects it.
            try (UaSksServer sks = new UaSksServer(licenseUser, licenseSerial, server, null)) {

                // The listener is called for EVERY call of GetSecurityKeys, also for the
                // refused ones. The event carries the user, the group, the token numbers and
                // the result - never a key.
                final DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
                sks.addKeysRetrievedListener((UaSksKeysRetrievedEvent e) -> {
                    String result = e.getResult().isGood() ? "OK" : describe(e.getResult().getValue());
                    System.out.println("  [" + clock.format(e.getTime()) + "] fetch  user="
                            + (e.getUserName() != null ? e.getUserName() : "-") + "  group=" + e.getSecurityGroupId()
                            + "  token=" + e.getFirstTokenId() + " (+" + Math.max(0, e.getKeyCount() - 1) + " future)  -> " + result);
                });

                // One SecurityGroup. Parameters: name, SecurityPolicyUri (null = the default,
                // Aes256-CTR), key lifetime, MaxFutureKeyCount (how many future keys one answer
                // carries at most), MaxPastKeyCount (how many old keys are kept for late
                // subscribers).
                sks.addSecurityGroup(GROUP_ID, null, KEY_LIFETIME, 3, 3);

                sks.start();

                System.out.println("  Key service started.");
                System.out.println("  Group:    " + GROUP_ID + "  (key lifetime " + KEY_LIFETIME.getSeconds() + " s)");
                System.out.println("  Address:  " + ENDPOINT);
                System.out.println("  Users:    reader (fetches keys), admin (manages groups)");
                System.out.println();
                System.out.println("  A client certificate must be trusted first: move it from");
                System.out.println("  pki/rejected/certs to pki/trusted/certs after you compared its thumbprint.");
                System.out.println();
                System.out.println("  Press ENTER to stop.");
                System.out.println();

                // -- Step 5: Show the numbering of the keys -----------------------------
                // The group counts the token up each KeyLifetime, whether somebody asks or not.
                UaSksSecurityGroup group = sks.getSecurityGroup(GROUP_ID);
                long shown = 0;
                while (!PubSubWorkshopHelper.isEnterPressed(input)) {
                    long token = group.getCurrentTokenId();
                    if (token != shown) {
                        System.out.println("  [" + clock.format(java.time.Instant.now()) + "] current token of '" + GROUP_ID
                                + "' is now " + token);
                        shown = token;
                    }
                    Thread.sleep(500L);
                }
                input.readLine();

                // -- Step 6: Stop -------------------------------------------------------
                sks.stop();
            }
            server.stop();
            System.out.println("  Key service stopped. The keys are gone with it.");
        } catch (Exception ex) {
            reportError(ex);
            System.out.println();
            System.out.println("Press ENTER to exit.");
            PubSubWorkshopHelper.waitForEnterAndCloseConsole();
        } finally {
            PubSubWorkshopHelper.closeConsole();
        }
    }

    // The reason of a refused call, by name.
    private static String describe(com.plccom.opc.ua.builtintypes.UnsignedInteger code) {
        if (StatusCodes.Bad_UserAccessDenied.equals(code)) {
            return "Bad_UserAccessDenied";
        }
        if (StatusCodes.Bad_SecurityModeInsufficient.equals(code)) {
            return "Bad_SecurityModeInsufficient";
        }
        if (StatusCodes.Bad_NotFound.equals(code)) {
            return "Bad_NotFound";
        }
        return "0x" + Long.toHexString(code.longValue());
    }

    // A secret comes from the environment variable, or you type it (the characters are not shown when the console allows it).
    // At least 10 characters. Nothing is ever written to the screen or to a file.
    private static String secretFrom(BufferedReader input, String variable, String prompt) throws Exception {
        String value = System.getenv(variable);
        if (value != null && !value.isEmpty()) {
            if (value.length() < 10) {
                throw new IllegalStateException(variable + " is shorter than 10 characters.");
            }
            return value;
        }
        while (true) {
            System.out.print("  " + prompt + " (min. 10 characters): ");
            String typed;
            if (System.console() != null) {
                char[] chars = System.console().readPassword();
                typed = chars == null ? "" : new String(chars);
            } else {
                typed = input.readLine();   // no console (IDE): the characters are shown
                if (typed == null) {
                    throw new IllegalStateException("No input for " + variable + ".");
                }
            }
            if (typed.length() >= 10) {
                return typed;
            }
            System.out.println("  Too short.");
        }
    }

    private static void reportError(Exception ex) {
        System.out.println("Error: " + ex);
        Throwable reason = null;
        Throwable current = ex.getCause();
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().trim().length() > 0) {
                reason = current;
            }
            current = current.getCause();
        }
        if (reason != null && !String.valueOf(ex.getMessage()).contains(reason.getMessage())) {
            System.out.println("Reason: " + reason.getMessage());
        }
        String verboseProperty = System.getProperty("plccom.pubsub.workshop.verboseErrors");
        String verboseEnvironment = System.getenv("PLCCOM_PUBSUB_WORKSHOP_VERBOSE_ERRORS");
        boolean verboseErrors = "true".equalsIgnoreCase(verboseProperty) || "1".equals(verboseProperty)
                || "yes".equalsIgnoreCase(verboseProperty) || "true".equalsIgnoreCase(verboseEnvironment)
                || "1".equals(verboseEnvironment) || "yes".equalsIgnoreCase(verboseEnvironment);
        if (verboseErrors) {
            ex.printStackTrace(System.out);
        } else {
            System.out.println("Enable verbose workshop errors with -Dplccom.pubsub.workshop.verboseErrors=true "
                    + "or PLCCOM_PUBSUB_WORKSHOP_VERBOSE_ERRORS=true.");
        }
    }
}

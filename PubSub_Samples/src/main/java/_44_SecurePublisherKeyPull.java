// MIT License
// Copyright (c) Indi.An GmbH

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.UaClientCertificate;
import com.plccom.opc.ua.client.application.UserIdentity;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.pubsub.encoding.uadp.UadpSecurityKey;
import com.plccom.opc.ua.pubsub.encoding.uadp.UadpSecurityKeyUpdate;
import com.plccom.opc.ua.pubsub.sdk.NetworkInterfaces;
import com.plccom.opc.ua.pubsub.sdk.PubSubTransportMode;
import com.plccom.opc.ua.pubsub.sdk.UaClientSksMethodCaller;
import com.plccom.opc.ua.pubsub.sdk.UaPublisher;
import com.plccom.opc.ua.pubsub.sdk.UaPublisherConfiguration;
import com.plccom.opc.ua.pubsub.sdk.UaSksClient;
import com.plccom.opc.ua.pubsub.sdk.UaSksGetSecurityKeysRequest;
import com.plccom.opc.ua.pubsub.sdk.UaSksKeyRefreshScheduler;
import com.plccom.opc.ua.pubsub.sdk.UaUadpSecurityConfiguration;
import com.plccom.opc.ua.pubsub.sdk.UaUadpSecurityKeyRuntime;
import com.plccom.opc.ua.pubsub.sdk.UaUadpSecurityMode;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;

/**
 * Workshop 44: Secure Publisher, keys from the Key Server.
 *
 * <p>The publisher of Workshop 17, but without a key in the source code: it logs in at the Key Server of Workshop 43
 * and fetches the key of its SecurityGroup with the Method GetSecurityKeys (OPC UA Part 14, 8.3.2; the publisher side is
 * described in 5.4.5).</p>
 *
 * <p>HOW IT WORKS:</p>
 * <ol>
 *   <li>The publisher connects to the key server over an ENCRYPTED channel (SignAndEncrypt) with the user 'reader'. The
 *       key server refuses every other kind of channel.</li>
 *   <li>It calls GetSecurityKeys for the group 'WorkshopGroup' with the StartingTokenId 0 ("the current key") and asks
 *       for two future keys.</li>
 *   <li>UaUadpSecurityKeyRuntime holds the keys and knows which one is current.</li>
 *   <li>UaSksKeyRefreshScheduler fetches again before the keys run out (Part 14 recommends half the KeyLifetime). When
 *       the key server changes the key, the publisher switches to the new one by itself - you can watch the token number
 *       go up in the output.</li>
 * </ol>
 *
 * <p>NOTHING SECRET IN THE CODE: The password of the user 'reader' comes from PLCCOM_SKS_READER_PASSWORD, the password of
 * the private key of this program's certificate from PLCCOM_PKI_PASSWORD; or you type them. The output shows the token
 * number of the key in use, never the key.</p>
 *
 * <p>TRUST: the first time, the program shows you the certificate of the key server and asks whether to trust it. Compare
 * the thumbprint with the one the key server's folder pki/own/certs holds before you answer 'y'. The key server in turn
 * needs your certificate in its pki/trusted/certs (see Workshop 43).</p>
 *
 * <p>TRY THIS: Let it run for a minute: the token changes every 30 seconds. Start with a wrong password: the key server
 * refuses the login. Stop Workshop 43 for a short moment: the publisher keeps sending with the keys it has (it fetched
 * future keys), and the refresh error shows in the output.</p>
 *
 * <p>Run Workshop 45 (Secure Subscriber, keys from the Key Server) to receive the data.</p>
 */
public final class _44_SecurePublisherKeyPull {

    private static final String KEY_SERVER_URL = "opc.tcp://127.0.0.1:48440";
    private static final String GROUP_ID = "WorkshopGroup";

    private _44_SecurePublisherKeyPull() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 44 - Secure Publisher, keys from the Key Server");

        UaClient keyClient = null;
        ScheduledExecutorService refresher = null;
        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 44                      ║");
            System.out.println("║  Secure Publisher, keys from the Key Server                  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Fetches its key from Workshop 43 and follows the changes.   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Key server: opc.tcp://127.0.0.1:48440                       ║");
            System.out.println("║  Target:     opc.udp://localhost:4840                        ║");
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

            // -- Step 1: Log in at the key server (encrypted channel, user 'reader') --
            String readerPassword = secretFrom(input, "PLCCOM_SKS_READER_PASSWORD", "Password of the user 'reader'");
            String pkiPassword = secretFrom(input, "PLCCOM_PKI_PASSWORD", "Password for the private key of this program's certificate");
            System.out.println();
            keyClient = connectToKeyServer(input, licenseUser, licenseSerial, readerPassword, pkiPassword);
            System.out.println("  Connected to the key server (SignAndEncrypt).");

            // -- Step 2: Fetch the first keys ------------------------------------------
            // UaClientSksMethodCaller carries the Method call over the session.
            // UaSksClient turns the answer into a UadpSecurityKeyUpdate.
            // current(group, 2): the current key, and two future keys.
            UaSksClient sks = UaSksClient.publishSubscribe(new UaClientSksMethodCaller(keyClient));
            UaSksGetSecurityKeysRequest request = UaSksGetSecurityKeysRequest.current(GROUP_ID, 2);
            UadpSecurityKeyUpdate first = sks.getSecurityKeys(request);
            System.out.println("  Keys received for '" + GROUP_ID + "': first token " + first.getFirstTokenId() + ", "
                    + first.getFutureKeys().size() + " future key(s),");
            System.out.printf("  key lifetime %.0f s.%n", first.getKeyLifetimeMillis() / 1000.0);
            System.out.println();

            // -- Step 3: Keep the keys fresh -------------------------------------------
            final UaUadpSecurityKeyRuntime runtime = UaUadpSecurityKeyRuntime.fromUpdate(first);
            UaUadpSecurityConfiguration security = UaUadpSecurityConfiguration
                    .withKeySequence(first.getSequence())
                    .securityMode(UaUadpSecurityMode.SIGN_AND_ENCRYPT)
                    .build();
            final UaSksKeyRefreshScheduler scheduler = new UaSksKeyRefreshScheduler(sks, runtime, request);
            // Every second we look whether a fetch is due. A failed fetch is reported here
            // and tried again; the keys you have keep working until they end.
            refresher = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Key refresh");
                t.setDaemon(true);
                return t;
            });
            refresher.scheduleWithFixedDelay(() -> {
                try {
                    scheduler.refreshIfDue();
                } catch (Exception ex) {
                    System.out.println("  Key refresh failed: " + ex.getMessage());
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS);

            // -- Step 4: The publisher, as in Workshop 17, with the runtime ------------
            UaPublisherConfiguration config = UaPublisherConfiguration.Build("SecureTemperaturePublisher", "opcua:Workshop44")
                    .WithNetworkInterface(NetworkInterfaces.All)
                    .WithTransport(PubSubTransportMode.DirectUnicast, "opc.udp://localhost:4840")
                    .uadpSecurity(security, runtime)
                    .WithPublishingInterval(1000)
                    .AddDataSet("SecureTemperatures", ds -> ds
                            .AddField("Sensor1", new NodeId(2, 1001))
                            .AddField("Sensor2", new NodeId(2, 1002))
                            .AddField("Sensor3", new NodeId(2, 1003))
                            .WithKeyFrameCount(10)
                            .WithInterval(1000))
                    .build();

            try (UaPublisher publisher = new UaPublisher(licenseUser, licenseSerial, config)) {
                publisher.addErrorListener(PubSubWorkshopHelper::printPubSubError);
                publisher.Start();
                System.out.println("  License: " + publisher.getLicenceMessage());
                System.out.println("  Secure publisher started. Press ENTER to stop.");
                System.out.println();

                Random random = new Random();
                int counter = 0;
                long lastToken = -1;
                while (!PubSubWorkshopHelper.isEnterPressed(input)) {
                    counter++;
                    double temp1 = 20.0 + random.nextDouble() * 10.0;
                    double temp2 = 30.0 + random.nextDouble() * 5.0;
                    double temp3 = 40.0 + random.nextDouble() * 5.0;
                    publisher.WriteValue("SecureTemperatures", "Sensor1", temp1);
                    publisher.WriteValue("SecureTemperatures", "Sensor2", temp2);
                    publisher.WriteValue("SecureTemperatures", "Sensor3", temp3);

                    // The number of the key in use - the key itself is never shown.
                    long token = currentToken(runtime);
                    String change = token != lastToken && lastToken >= 0 ? "   <-- new key" : "";
                    lastToken = token;
                    System.out.printf("  [%05d] token %d  Sensor1=%.2f°C  Sensor2=%.2f°C  Sensor3=%.2f°C%s%n",
                            counter, token, temp1, temp2, temp3, change);
                    Thread.sleep(1000L);
                }
                input.readLine();
                publisher.Stop();
                System.out.println("  Publisher stopped.");
            }
        } catch (Exception ex) {
            reportError(ex);
            // Typical reasons: wrong password (the login is refused), a certificate that the
            // key server has not trusted yet (look in its pki/rejected/certs), a user without
            // the role SecurityKeyServerAccess (Bad_UserAccessDenied), a group that does not
            // exist (Bad_NotFound).
            System.out.println();
            System.out.println("Press ENTER to exit.");
            PubSubWorkshopHelper.waitForEnterAndCloseConsole();
        } finally {
            if (refresher != null) {
                refresher.shutdownNow();
            }
            if (keyClient != null) {
                try {
                    keyClient.close();
                } catch (Exception ignored) {
                    // the key server may be gone already
                }
            }
            PubSubWorkshopHelper.closeConsole();
        }
    }

    private static long currentToken(UaUadpSecurityKeyRuntime runtime) {
        try {
            UadpSecurityKey key = runtime.getCurrentPublisherKeyAtMillis(System.currentTimeMillis());
            return key != null ? key.getSecurityTokenId() : -1;
        } catch (RuntimeException ex) {
            return -1;   // no usable key right now
        }
    }

    // Connects to the key server over a SignAndEncrypt endpoint, as the user 'reader'.
    private static UaClient connectToKeyServer(BufferedReader input, String licenseUser, String licenseSerial,
                                               String readerPassword, String pkiPassword) throws Exception {
        CertificateValidator ask = askForServerCertificate(input);
        EndpointDescription[] endpoints = UaClient.discoverEndpoints(new URI(KEY_SERVER_URL), ask);
        EndpointDescription endpoint = null;
        if (endpoints != null) {
            for (EndpointDescription candidate : endpoints) {
                if (candidate.getSecurityMode() == MessageSecurityMode.SignAndEncrypt
                        && (endpoint == null || candidate.getSecurityLevel().intValue() > endpoint.getSecurityLevel().intValue())) {
                    endpoint = candidate;
                }
            }
        }
        if (endpoint == null) {
            throw new IllegalStateException("The key server offers no SignAndEncrypt endpoint. Is Workshop 43 running?");
        }

        String alias = "Workshop44";
        UaClientCertificate application = UaClientCertificate.load("./pki", alias, pkiPassword);
        if (application == null || !application.isReady()) {
            application = new UaClientCertificate("./pki", pkiPassword, alias, 365, "Indi.An GmbH").build(true);
        }
        ClientConfiguration session = new ClientConfiguration(new LocalizedText("Workshop 44", "en"), endpoint,
                new UserIdentity("reader", readerPassword));
        session.setInstanceCertificate(application);
        session.setCertificateValidator(ask);
        session.setAutoConnectEnabled(false);

        UaClient client = new UaClient(licenseUser, licenseSerial, session);
        client.connect();
        return client;
    }

    // The key server's certificate: shown once, accepted only if you say so.
    private static CertificateValidator askForServerCertificate(final BufferedReader input) {
        return new CertificateValidator() {
            private String trustedThumbprint;

            @Override
            public StatusCode validateCertificate(Cert cert) {
                String thumbprint = hex(cert.encodedCertificateThumbprint);
                if (thumbprint.equals(trustedThumbprint)) {
                    return StatusCode.GOOD;
                }
                System.out.println();
                System.out.println("  The key server presents this certificate:");
                System.out.println("    Subject:    " + cert.certificate.getSubjectX500Principal().getName());
                System.out.println("    Thumbprint: " + thumbprint);
                System.out.print("  Trust it? (y/N): ");
                try {
                    String answer = input.readLine();
                    if (answer != null && answer.trim().equalsIgnoreCase("y")) {
                        trustedThumbprint = thumbprint;
                        return StatusCode.GOOD;
                    }
                } catch (java.io.IOException ex) {
                    // no answer: not trusted
                }
                return new StatusCode(com.plccom.opc.ua.core.StatusCodes.Bad_CertificateUntrusted);
            }

            @Override
            public StatusCode validateCertificate(ApplicationDescription application, Cert cert) {
                return validateCertificate(cert);
            }
        };
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) {
            text.append(String.format("%02X", b & 0xff));
        }
        return text.toString();
    }

    // A secret comes from the environment variable, or you type it (the characters are not shown when the console allows it).
    private static String secretFrom(BufferedReader input, String variable, String prompt) throws Exception {
        String value = System.getenv(variable);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        System.out.print("  " + prompt + ": ");
        if (System.console() != null) {
            char[] chars = System.console().readPassword();
            return chars == null ? "" : new String(chars);
        }
        String typed = input.readLine();   // no console (IDE): the characters are shown
        return typed == null ? "" : typed;
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

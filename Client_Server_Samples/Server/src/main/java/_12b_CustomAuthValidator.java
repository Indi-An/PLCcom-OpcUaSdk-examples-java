// MIT License
// Copyright (c) Indi.An GmbH
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 12b: Custom Auth Validator
//
// Workshop 12a used the built-in user database (addUser) and OPC UA RolePermissions
// to control access. This workshop demonstrates the alternative approach:
//
//   IUaCredentialValidator  — replaces username/password validation entirely.
//                             No addUser() calls needed.
//
//   IUaPermissionValidator  — replaces the built-in RolePermissions enforcement.
//                             No setRolePermissions() on nodes needed.
//                             Nodes are created with WITHOUT_RESTRICTIONS.
//
// The same three users and the same access rules as Workshop 12a are implemented,
// but entirely in custom validator classes — no OPC UA role concepts involved.
//
// Users:
//   admin    / admin123    -> full access  (read, write, call)
//   operator / operator123 -> read + write + call
//   viewer   / viewer123   -> read only   (browse, read, subscribe)
//
// What you will learn:
//   * How to replace built-in authentication with a custom credential validator
//   * How to replace built-in RolePermissions with a custom permission validator
//   * How every access check is logged to the console
//   * That nodes must use WITHOUT_RESTRICTIONS when a permission validator is active
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.Variant;
import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.IUaCredentialValidator;
import com.plccom.opc.ua.server.application.IUaPermissionValidator;
import com.plccom.opc.ua.server.application.UaNodeContext;
import com.plccom.opc.ua.server.application.UaPermissionCheck;
import com.plccom.opc.ua.server.application.UaRolePermissions;
import com.plccom.opc.ua.server.application.UaServer;
import com.plccom.opc.ua.server.application.UaServerCertificate;
import com.plccom.opc.ua.server.application.UaServerCertificateStore;
import com.plccom.opc.ua.server.application.UaServerConfiguration;
import com.plccom.opc.ua.server.application.UaEndpointHostMode;
import com.plccom.opc.ua.server.application.UaServerNodes.UaFolder;
import com.plccom.opc.ua.server.application.UaServerNodes.UaVariable;
import com.plccom.opc.ua.server.application.UaSessionContext;

import java.util.HashMap;
import java.util.Map;

public class _12b_CustomAuthValidator {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 12b - Custom Auth Validator", 1000);

        // TODO: Replace with your license credentials from your license e-mail
        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 12b: Custom Validator   ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Same users and access rules as Workshop 12a, but using      ║");
        System.out.println("║  IUaCredentialValidator and IUaPermissionValidator instead   ║");
        System.out.println("║  of addUser() and setRolePermissions().                      ║");
        System.out.println("║                                                              ║");
        System.out.println("║    admin    / admin123    -> full access                     ║");
        System.out.println("║    operator / operator123 -> read + write + call             ║");
        System.out.println("║    viewer   / viewer123   -> read only                       ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Anonymous access is disabled - you MUST log in.             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // =============================================================================
        // Step 1: Configure the server
        // =============================================================================
        UaServerConfiguration config = createConfig();

        printConfig(config);

        // =============================================================================
        // Step 2: Create server and register custom validators
        // =============================================================================
        // IUaCredentialValidator replaces addUser() — no user database needed.
        // IUaPermissionValidator replaces setRolePermissions() — no OPC UA roles needed.
        // The two validators are independent: you can use either or both.
        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            // Register the custom credential validator.
            // Called during ActivateSession to verify username/password.
            server.setCredentialValidator(new MyCredentialValidator());

            // Register the custom permission validator.
            // Called for every Browse, Read, Write, Call, Subscribe etc.
            // When set, the built-in RolePermissions on nodes are bypassed entirely.
            server.setPermissionValidator(new MyPermissionValidator());

            System.out.println("── Validators ──────────────────────────────────────────────");
            System.out.println("  CredentialValidator : MyCredentialValidator (custom auth)");
            System.out.println("  PermissionValidator : MyPermissionValidator (custom access)");
            System.out.println();

            // =============================================================================
            // Step 3: Handle certificate validation and session events
            // =============================================================================
            // Accepts all client certificates automatically - suitable for development.
            // Remove this listener to activate PKI-based validation via the store above.
            server.addCertificateValidationListener(e -> e.setAccept(true));

            server.addSessionListener(new UaServer.UaSessionListener() {
                @Override
                public void onSessionCreated(UaServer.UaSessionInfo s) {
                    String name = s.getSessionName() != null ? s.getSessionName() : "(unnamed)";
                    System.out.println("  >> Client connected:    \"" + name + "\"");
                }
                @Override
                public void onSessionClosed(UaServer.UaSessionInfo s) {
                    String name = s.getSessionName() != null ? s.getSessionName() : "(unnamed)";
                    System.out.println("  << Client disconnected: \"" + name + "\"");
                }
            });

            server.addValuesWrittenListener(items -> {
                for (UaServer.UaWrittenItem item : items)
                    System.out.println("  << OPC Write: " + item.getPath()
                            + " (" + item.getNodeId() + ") = " + item.getValueAsString());
            });

            // WriteValidation — called BEFORE any client write is committed to the address space.
            // All internal checks (AccessLevel, DataType, Permissions) have already passed.
            // The handler receives ALL items of the write request as a batch.
            // Set item.setStatusCode() to any Bad_* value to reject that specific item.
            // If not handled or StatusCode remains Good, the write proceeds normally.
            //
            // You can also MODIFY the value before it is written by calling item.setValue().
            // The modified value is then stored in the address space instead of the original.
            //
            // !! IMPORTANT — PERFORMANCE WARNING !!
            // This handler runs synchronously on the server's write thread.
            // Any blocking operation (device I/O, database, slow network) will stall
            // the entire write request and can block other clients as well.
            //
            // If you need to forward the value to a device, prefer one of these patterns:
            //   a) Accept immediately (Good) and forward asynchronously via CompletableFuture or a queue.
            //      The OPC UA client gets a fast response; the device update happens in the background.
            //   b) If you must wait for the device, always use a short timeout (e.g. 500 ms)
            //      and return BadTimeout or BadNoCommunication if the device does not respond in time.
            //
            // Never block indefinitely inside this handler.
            server.addWriteValidationListener(items -> {
                for (UaServer.UaWriteValidationItem item : items) {
                    item.setStatusCode(com.plccom.opc.ua.builtintypes.StatusCode.GOOD);
                    System.out.println("  >> WriteValidation: " + item.getPath() + " = " + item.getValue());
                }
            });

            // =============================================================================
            // Step 4: Start server
            // =============================================================================
            System.out.print("  Starting server ... ");
            try {
                server.start(config);
            } catch (Exception ex) {
                System.out.println("FAILED: " + ex.getMessage());
                System.out.println("  Press ENTER to exit.");
                System.in.read();
                PLCcomConsole.close();
                return;
            }
            System.out.println("OK");
            for (String addr : config.getBaseAddresses())
                System.out.println("  Endpoint: " + addr);
            System.out.println();

            // =============================================================================
            // Step 5: Build address space
            // =============================================================================
            // IMPORTANT: When using IUaPermissionValidator, nodes must be created with
            // WITHOUT_RESTRICTIONS. The permission validator takes full control of access
            // decisions — the stack must not pre-filter via RolePermissions.
            UaFolder plant = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaVariable<Double>  temp = server.createVariable(plant, "Temperature",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class,  22.0,  false);
            UaVariable<Integer> rpm  = server.createVariable(plant, "RPM",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Integer.class, 1500,  false);

            server.createMethod(plant, "Reset",
                    inputs -> {
                        temp.setValue(22.0);
                        rpm.setValue(1500);
                        System.out.println("  << Reset called");
                        return new Variant[0];
                    },
                    UaRolePermissions.WITHOUT_RESTRICTIONS);

            System.out.println("── Address space ────────────────────────────────────────────");
            System.out.printf("  Double  %-12s %s  = 22.0%n", temp.getName(), temp.getNodeId());
            System.out.printf("  Int32   %-12s %s  = 1500%n", rpm.getName(),  rpm.getNodeId());
            System.out.println("  Method  Reset");
            System.out.println("  (access controlled by MyPermissionValidator)");
            System.out.println();

            // =============================================================================
            // Step 6: Wait
            // =============================================================================
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running - authentication required.                ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Test with Client Workshop 13:                               ║");
            System.out.println("║  * viewer/viewer123   -> read OK, write/call rejected        ║");
            System.out.println("║  * operator/operator123 -> read + write + call OK            ║");
            System.out.println("║  * admin/admin123     -> full access                         ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Watch [AUTH] and [PERM] log lines in this console.          ║");
            System.out.println("║  Press ENTER to exit.                                        ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Server stopped.");
        }
        PLCcomConsole.close();
    }

    // =============================================================================
    // MyCredentialValidator
    // =============================================================================
    // Replaces the built-in addUser() database. Called during ActivateSession.
    // Returns true to accept the credentials, false to reject them.
    static class MyCredentialValidator implements IUaCredentialValidator {

        private static final Map<String, String> USERS = new HashMap<String, String>();
        static {
            USERS.put("admin",    "admin123");
            USERS.put("operator", "operator123");
            USERS.put("viewer",   "viewer123");
        }

        @Override
        public boolean validateCredentials(String userName, String password) {
            String expected = USERS.get(userName);
            boolean ok = expected != null && expected.equals(password);
            System.out.println("  [AUTH] " + userName + " -> " + (ok ? "accepted" : "rejected"));
            return ok;
        }

        @Override
        public boolean validateCertificate(byte[] certificateData) {
            System.out.println("  [AUTH CERT] certificate presented -> accepted");
            return true;
        }
    }

    // =============================================================================
    // MyPermissionValidator
    // =============================================================================
    // Replaces the built-in RolePermissions enforcement. Called for every access check.
    // Returns true to allow the operation, false to deny it.
    //
    // Access rules (same as Workshop 12a):
    //   admin    -> full access
    //   operator -> all except HistoryWrite
    //   viewer   -> Browse + Read + Subscribe + ReadRolePermissions only
    static class MyPermissionValidator implements IUaPermissionValidator {

        @Override
        public boolean validatePermission(UaSessionContext session, UaNodeContext node,
                                          UaPermissionCheck check) {
            String user = session.getUserName();
            boolean allowed;

            if ("admin".equals(user)) {
                allowed = true;
            } else if ("operator".equals(user)) {
                allowed = check != UaPermissionCheck.HISTORY_WRITE;
            } else if ("viewer".equals(user)) {
                allowed = check == UaPermissionCheck.BROWSE
                       || check == UaPermissionCheck.READ
                       || check == UaPermissionCheck.SUBSCRIBE
                       || check == UaPermissionCheck.READ_ROLE_PERMISSIONS;
            } else {
                allowed = false; // unknown or anonymous user
            }

            String nodeName = node.getPath() != null ? node.getPath() : node.getNodeId().toString();
            System.out.printf("  [PERM] %-10s %-25s %-35s -> %s%n",
                    user != null ? user : "(anon)", check, nodeName,
                    allowed ? "ALLOW" : "DENY");
            return allowed;
        }
    }

    // =============================================================================
    // Helper: createConfig
    // =============================================================================
    // Helper: createConfig
    // =============================================================================
    // Returns the server configuration. All available options are listed here
    // with a description and the default value. Adjust to your needs.
    private static UaServerConfiguration createConfig() throws Exception {
        UaServerConfiguration config = new UaServerConfiguration();

        // ── Application Identity ──────────────────────────────────────────────
        // ApplicationName: human-readable name shown to connecting clients
        //   and embedded in the server certificate.
        config.setApplicationName("PLCcom Workshop 12b - Custom Auth Validator");

        // ApplicationUri: globally unique identifier for this server instance.
        //   Must match the URI in the server certificate.
        //   Recommended format: urn:<host>:<company>:<product>
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:12b");

        // ProductUri: URI identifying the software product (not the instance).
        //   Typically a URL pointing to the product page.
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");

        // NamespaceUri: URI for this server's application address space (ns=2).
        //   Use a stable URI based on your company domain.
        //   Default: null (auto-generated as ApplicationUri + "/nodes").
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/custom-auth-validator");

        // ── ServerStatus/BuildInfo ────────────────────────────────────────────
        // These values appear under Server/ServerStatus/BuildInfo in the OPC UA
        // address space and identify the software to connecting clients.
        // Default: empty string.
        config.setManufacturerName("My Company GmbH");
        config.setProductName("My OPC UA Server");
        config.setSoftwareVersion("1.0.0");
        config.setBuildNumber("42");

        // ── Endpoints ─────────────────────────────────────────────────────────
        // The URLs clients connect to. Multiple endpoints are supported.
        //   opc.tcp   — binary protocol, best performance, recommended
        //   opc.https — SOAP/XML over HTTPS, for firewall-friendly scenarios
        // Default: empty (binds to all local interfaces on port 4840).
        config.setBaseAddresses(java.util.Arrays.asList(
                "opc.tcp://localhost:48410",
                "opc.https://localhost:48411"));

        // ── Security Policies ─────────────────────────────────────────────────
        // Which encryption algorithms to offer on the endpoints.
        // getRecommendedSecurityModes() returns:
        //   None (no encryption, for development only)
        //   Basic256Sha256, Aes128_Sha256_RsaOaep, Aes256_Sha256_RsaPss
        //   each with Sign + SignAndEncrypt
        config.setSecurityModes(UaServer.getRecommendedSecurityModes());

        // ── User Authentication ───────────────────────────────────────────────
        // Which authentication methods to accept from connecting clients.
        //   Anonymous   — no credentials required
        //   UserName    — username + password (see server.getUserManager())
        //   Certificate — X.509 client certificate (see server.getUserManager())
        // Default: Anonymous + SecureUsernamePassword.
        config.setUserTokenPolicies(java.util.Arrays.asList(
                UserTokenPolicy.SECURE_USERNAME_PASSWORD, UserTokenPolicy.SECURE_CERTIFICATE));

        // AutoAcceptUntrustedCertificates: skip client certificate validation.
        // WARNING: only for development/testing — never use in production!
        // Default: false.
        config.setAutoAcceptUntrustedCertificates(false);

        // ── Session & Connection ──────────────────────────────────────────────
        // MaxSessionCount: maximum number of concurrent client sessions.
        // Default: 100. 0 = unlimited.
        config.setMaxSessionCount(100);

        // ShutdownDelay: seconds the server waits for clients to disconnect
        // gracefully when stop() is called. Default: 5.
        config.setShutdownDelay(5);

        // HttpsMutualTls: require the client TLS certificate to match the OPC UA
        // application certificate sent in CreateSession. Default: false.
        config.setHttpsMutualTls(false);

        // ── Local Discovery Server (LDS) ──────────────────────────────────────
        // RegisterWithDiscoveryServer: register with a LDS so that clients can
        // discover this server via FindServers without knowing its URL.
        // Default: false.
        config.setRegisterWithDiscoveryServer(false);

        // ── VendorServerInfo ──────────────────────────────────────────────────
        // These values appear under Server/VendorServerInfo in the OPC UA
        // address space and identify your product to connecting clients.
        // null = the corresponding node is not created. Default: null.
        config.setVendorName("My Company GmbH");
        config.setVendorProductName("My OPC UA Server");
        config.setVendorProductVersion("1.0.0");

        // ── HTTPS TLS Policies ────────────────────────────────────────────────
        // Which TLS versions to offer on the opc.https endpoint.
        // IMPORTANT: if null, the opc.https endpoint is NOT activated (CRA compliance).
        // Must be set explicitly to enable HTTPS.
        config.setHttpsSecurityPolicies(java.util.Arrays.asList(
                com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_2_PFS,
                com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_3));

        // ── OperationLimits ───────────────────────────────────────────────────
        // These values appear under Server/ServerCapabilities/OperationLimits.
        // Clients read these to size their request batches correctly.
        // 0 = no limit imposed by this server (not recommended for production).
        config.setMaxNodesPerRead(1000);                          // max nodes per Read request
        config.setMaxNodesPerWrite(1000);                         // max nodes per Write request
        config.setMaxNodesPerBrowse(1000);                        // max nodes per Browse/BrowseNext
        config.setMaxNodesPerHistoryReadData(100);                // max nodes per HistoryRead (data)
        config.setMaxNodesPerHistoryReadEvents(100);              // max nodes per HistoryRead (events)
        config.setMaxNodesPerHistoryUpdateData(100);              // max nodes per HistoryUpdate (data)
        config.setMaxNodesPerHistoryUpdateEvents(100);            // max nodes per HistoryUpdate (events)
        config.setMaxNodesPerMethodCall(200);                     // max nodes per Method Call
        config.setMaxNodesPerRegisterNodes(1000);                 // max nodes per RegisterNodes
        config.setMaxNodesPerTranslateBrowsePathsToNodeIds(1000); // max nodes per TranslateBrowsePaths
        config.setMaxNodesPerNodeManagement(1000);                // max nodes per AddNodes/DeleteNodes
        config.setMaxMonitoredItemsPerCall(1000);                 // max items per CreateMonitoredItems
        // AsConfigured (default) = endpoints use exactly the host from BaseAddresses
        // NormalizeToHostname    = replace localhost/127.0.0.1 with the machine name
        config.setEndpointHostMode(UaEndpointHostMode.AsConfigured);

        // ── Certificate Store ─────────────────────────────────────────────────
        // Build the certificate store: one APPLICATION cert for the OPC UA secure channel,
        // plus one default HTTPS certificate presented at every opc.https TLS handshake.
        // load() tries to load all certs from disk; getMissingOrExpired() returns any
        // that are missing or expired so they can be rebuilt individually.
        java.util.List<UaServerCertificate> certs = new java.util.ArrayList<>();
        certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_12b",
                config.getApplicationUri(), 720, "Indi.An GmbH",
                UaServerCertificate.CertificateRole.APPLICATION));
        // One default HTTPS certificate for all opc.https ports. The SDK presents it at the
        // TLS handshake for any opc.https port that has no specifically assigned certificate.
        // To serve an official domain certificate on a port, create another HTTPS certificate
        // and assign it: config.assignHttpsCertificateToPort(port, cert).
        UaServerCertificate httpsDefault = new UaServerCertificate("./pki", "secretpassword", "https-default", "urn:https-default:https", 720, "Indi.An GmbH", UaServerCertificate.CertificateRole.HTTPS);
        certs.add(httpsDefault);
        config.setDefaultHttpsCertificate(httpsDefault);

        // Try to load all certificates from disk into the store.
        // Certificates that are missing or cannot be read remain in the store
        // but are marked as not ready (isReady() = false).
        UaServerCertificateStore store = UaServerCertificateStore.load("./pki", certs);

        // getMissingOrExpired() returns all certificates that are either:
        //   - not present on disk (first run)
        //   - expired (NotAfter < now)
        //   - could not be loaded (wrong password, corrupt file)
        // Each of these is rebuilt as a new self-signed certificate.
        // build(true) overwrites any existing file - safe because we only
        // reach this for certs that are missing or no longer valid.
        for (UaServerCertificate missing : store.getMissingOrExpired())
            missing.build(true);

        // Hand the fully populated store to the configuration.
        // UaServer.start() will use it to set up the secure channel and
        // create the PKI directory structure (trusted/, rejected/, issuers/).

        config.setCertificateStore(store);
        return config;
    }

    private static void printConfig(UaServerConfiguration config) {
        System.out.println("── Active Server Configuration ──────────────────────────────");
        System.out.println("  ApplicationName  : " + config.getApplicationName());
        System.out.println("  ApplicationUri   : " + config.getApplicationUri());
        System.out.println("  NamespaceUri     : " + config.getNamespaceUri());
        System.out.println();
        System.out.println("  Endpoints:");
        for (String addr : config.getBaseAddresses())
            System.out.println("    " + addr);
        System.out.println();
        System.out.println("  Authentication: UserName + Certificate (Anonymous disabled)");
        System.out.println();
        System.out.println("  Certificate Store:");
        if (config.getCertificateStore() != null)
            System.out.println("    " + config.getCertificateStore());
        else
            System.out.println("    (not set)");
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println();
    }

}
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
// PLCcom OPC UA Server SDK - Workshop 12a: User Authentication
//
// Workshop 11 allowed anonymous access - anyone could connect and write values.
// In production, you need to control who can connect and what they can do.
//
// OPC UA supports three authentication methods:
//   Anonymous   - no login required (disabled in this example)
//   UserName    - classic username + password
//   Certificate - X.509 client certificate (machine-to-machine)
//
// Each authenticated user is assigned one or more roles that control access:
//   Engineer  - full access (read, write, browse, call methods)
//   Operator  - read + write + method calls
//   Observer  - read-only (writes and calls are rejected with BadUserAccessDenied)
//
// OPC UA well-known roles (Part 18):
//   Observer  - browse, read, subscribe (no write, no call)
//   Operator  - read + write + method calls
//   Engineer  - full access including configuration
//
// IMPORTANT: Roles are labels only. The SDK does NOT enforce permissions
// automatically unless setRolePermissions() is called on each node.
// Without setRolePermissions(), all authenticated users have identical access
// regardless of their assigned role.
//
// This workshop demonstrates:
//   * How to require user authentication (no anonymous access)
//   * How to add users with different roles
//   * How to enforce role-based access via setRolePermissions()
//   * How to create a Reset method
//   * How to handle X.509 user certificate validation
//   * How to track session lifecycle (connect/disconnect)
//
// Test scenario:
//   1. Try connecting without credentials -> rejected
//   2. Connect as viewer/viewer123  -> read OK, write rejected, call rejected
//   3. Connect as operator/operator123 -> read + write + call OK
//   4. Connect as admin/admin123    -> full access
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.builtintypes.Variant;
import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.UaRolePermissions;
import com.plccom.opc.ua.server.application.UaServer;
import com.plccom.opc.ua.server.application.UaServerCertificate;
import com.plccom.opc.ua.server.application.UaServerCertificateStore;
import com.plccom.opc.ua.server.application.UaServerConfiguration;
import com.plccom.opc.ua.server.application.UaEndpointHostMode;
import com.plccom.opc.ua.server.application.UaServerNodes.UaFolder;
import com.plccom.opc.ua.server.application.UaServerNodes.UaVariable;
import com.plccom.opc.ua.server.application.UaUserManager.Role;

public class _12a_UserAuthentication {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 12a - User Authentication", 1000);

        // Important !!!!!!!!!!!!!!!!!!
        // Enter your Username + Serial here! Please note: with blank fields the library runs
        // for 15 minutes during a debug session. Both values can also come
        // from configuration or an environment variable.
        // Free trial license (14 days, uninterrupted): https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/
        String licenseUser   = "";
        String licenseSerial = "";

        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 12a: User Authentication ║");
        System.out.println("║                                                               ║");
        System.out.println("║  Workshop 11 allowed anonymous access - anyone could write.   ║");
        System.out.println("║  This example requires authentication and assigns roles:      ║");
        System.out.println("║                                                               ║");
        System.out.println("║    admin    / admin123    -> Engineer  (full access)          ║");
        System.out.println("║    operator / operator123 -> Operator  (read + write + call)  ║");
        System.out.println("║    viewer   / viewer123   -> Observer  (read-only)            ║");
        System.out.println("║                                                               ║");
        System.out.println("║  Anonymous access is disabled - you MUST log in.              ║");
        System.out.println("║  Try writing Temperature as viewer -> BadUserAccessDenied.    ║");
        System.out.println("║  Try calling Reset as viewer       -> BadUserAccessDenied.    ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Roles are labels only - setRolePermissions() activates       ║");
        System.out.println("║  enforcement. Without it all users have identical access.     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // =============================================================================
        // Step 1: Configure the server
        // =============================================================================
        UaServerConfiguration config = createConfig();
        printConfig(config);

        // =============================================================================
        // Step 2: Create server and add users with roles
        // =============================================================================
        // Each user is registered with a username, password and one or more roles.
        // Passwords are stored in memory only and never persisted to disk.
        // The role assigned here is the key that setRolePermissions() uses later
        // to decide what each user is allowed to do on each node.
        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            server.addUser("admin",    "admin123",    Role.Engineer);
            server.addUser("operator", "operator123", Role.Operator);
            server.addUser("viewer",   "viewer123",   Role.Observer);

            System.out.println("── Users ───────────────────────────────────────────────────");
            System.out.println("  admin    / admin123    -> Engineer  (full access)");
            System.out.println("  operator / operator123 -> Operator  (read + write + call)");
            System.out.println("  viewer   / viewer123   -> Observer  (read-only)");
            System.out.println();

            // =============================================================================
            // Step 3: Handle certificate validation and session events
            // =============================================================================
            // Accept all client application certificates automatically.
            // WARNING: Do NOT use this in production! Use the PKI trust store instead.
            // In production, remove this listener and set AutoAcceptUntrustedCertificates
            // Accepts all client certificates automatically - suitable for development.
            // Remove this listener to activate PKI-based validation via the store above.
            server.addCertificateValidationListener(e -> e.setAccept(true));

            // Accept all X.509 user certificates for certificate-based login.
            // This listener is only called when a client authenticates with a certificate
            // instead of username/password (UserTokenType.Certificate).
            // In production, validate the certificate against a trusted CA or thumbprint list.
            server.getUserManager().addCertificateValidationListener(e -> {
                System.out.println("  [USER CERT] certificate presented -> Accepted");
                e.setAccept(true);
            });

            // Session events: fired when a client creates or closes a session.
            // onSessionCreated is called after CreateSession — the client is connected
            // but not yet authenticated. onSessionClosed is called when the session ends.
            server.addSessionListener(new UaServer.UaSessionListener() {
                @Override
                public void onSessionCreated(UaServer.UaSessionInfo s) {
                    String name = s.getSessionName() != null ? s.getSessionName() : "(unnamed)";
                    String uri  = s.getClientUri()   != null ? "  [" + s.getClientUri() + "]" : "";
                    System.out.println("  >> Client connected:    \"" + name + "\"" + uri);
                }
                @Override
                public void onSessionClosed(UaServer.UaSessionInfo s) {
                    String name = s.getSessionName() != null ? s.getSessionName() : "(unnamed)";
                    System.out.println("  << Client disconnected: \"" + name + "\"");
                }
            });

            // ValuesWritten fires whenever an OPC UA client successfully writes a value.
            // Note: if the client is an Observer, the write is rejected before this fires
            // because the role permission check happens first in the server stack.
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
                    item.setStatusCode(StatusCode.GOOD);
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
            // Step 5 + 6: Build address space with role-based permissions
            // =============================================================================
            // permissions is passed directly to createVariable/createMethod — no separate
            // setRolePermissions() call needed. If permissions is omitted (null), all
            // authenticated users have full access to the node regardless of their role.
            //
            // allowRead()      -> Browse + Read + ReadRolePermissions + ReceiveEvents
            //                     The client can see and read the node, but not write or call.
            // allowReadWrite() -> Browse + Read + ReadRolePermissions + Write + ReceiveEvents + Call
            //                     The client can read, write and call methods.
            // allowAll()       -> all permission bits (0x1FFFF)
            //                     Full access including attribute writes and history.
            UaFolder plant = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);

            // Both variables share the same permissions object — Observer reads,
            // Operator reads and writes, Engineer has full access.
            UaRolePermissions varPerms = new UaRolePermissions()
                    .allowRead(Role.Observer)
                    .allowReadWrite(Role.Operator)
                    .allowAll(Role.Engineer);

            UaVariable<Double>  temp = server.createVariable(plant, "Temperature", varPerms, Double.class,  22.0,  false);
            UaVariable<Integer> rpm  = server.createVariable(plant, "RPM",         varPerms, Integer.class, 1500,  false);

            // Observer gets allowRead on Reset: they can browse and see the method node
            // but the Call permission bit is not set, so calling it returns BadUserAccessDenied.
            // Operator and Engineer get allowReadWrite which includes the Call bit.
            NodeId resetId = server.createMethod(plant, "Reset",
                    inputs -> {
                        temp.setValue(22.0);
                        rpm.setValue(1500);
                        System.out.println("  << Reset called");
                        return new Variant[0];
                    },
                    new UaRolePermissions()
                            .allowRead(Role.Observer)
                            .allowReadWrite(Role.Operator)
                            .allowAll(Role.Engineer));

            System.out.println("── Address space ────────────────────────────────────────────────");
            System.out.printf("  Double  %-12s %s  = 22.0  [Observer:R  Operator:RW  Engineer:ALL]%n", temp.getName(), temp.getNodeId());
            System.out.printf("  Int32   %-12s %s  = 1500  [Observer:R  Operator:RW  Engineer:ALL]%n", rpm.getName(),  rpm.getNodeId());
            System.out.printf("  Method  %-12s %s         [Observer:R  Operator:RW  Engineer:ALL]%n", "Reset",         resetId);
            System.out.println();

            // =============================================================================
            // Step 6: Wait
            // =============================================================================
            System.out.println("╔═══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running - authentication required.                 ║");
            System.out.println("║                                                               ║");
            System.out.println("║  Test role-based access with Client Workshop 13:              ║");
            System.out.println("║  * viewer/viewer123   -> read OK, write/call rejected         ║");
            System.out.println("║  * operator/operator123 -> read + write + call OK             ║");
            System.out.println("║  * admin/admin123     -> full access                          ║");
            System.out.println("║                                                               ║");
            System.out.println("║  Press ENTER to exit.                                         ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Server stopped.");
        }
        PLCcomConsole.close();
    }

    // =============================================================================
    // Helper: createConfig
    // =============================================================================
    private static UaServerConfiguration createConfig() throws Exception {
        UaServerConfiguration config = new UaServerConfiguration();

        config.setApplicationName("PLCcom Workshop 12 - User Authentication");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:12");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/user-authentication");

        config.setManufacturerName("My Company GmbH");
        config.setProductName("My OPC UA Server");
        config.setSoftwareVersion("1.0.0");
        config.setBuildNumber("42");

        config.setBaseAddresses(java.util.Arrays.asList(
                "opc.tcp://localhost:48410",
                "opc.https://localhost:48411"));

        config.setSecurityModes(UaServer.getRecommendedSecurityModes());

        // Anonymous is intentionally NOT listed — clients must authenticate.
        config.setUserTokenPolicies(java.util.Arrays.asList(
                UserTokenPolicy.SECURE_USERNAME_PASSWORD,
                UserTokenPolicy.SECURE_CERTIFICATE));

                config.setAutoAcceptUntrustedCertificates(false);

        config.setMaxSessionCount(100);
        config.setShutdownDelay(5);

        config.setVendorName("My Company GmbH");
        config.setVendorProductName("My OPC UA Server");
        config.setVendorProductVersion("1.0.0");

        config.setHttpsSecurityPolicies(java.util.Arrays.asList(
                com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_2_PFS,
                com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_3));

        config.setMaxNodesPerRead(1000);
        config.setMaxNodesPerWrite(1000);
        config.setMaxNodesPerBrowse(1000);
        config.setMaxNodesPerHistoryReadData(100);
        config.setMaxNodesPerHistoryReadEvents(100);
        config.setMaxNodesPerHistoryUpdateData(100);
        config.setMaxNodesPerHistoryUpdateEvents(100);
        config.setMaxNodesPerMethodCall(200);
        config.setMaxNodesPerRegisterNodes(1000);
        config.setMaxNodesPerTranslateBrowsePathsToNodeIds(1000);
        config.setMaxNodesPerNodeManagement(1000);
        config.setMaxMonitoredItemsPerCall(1000);
        // AsConfigured (default) = endpoints use exactly the host from BaseAddresses
        // NormalizeToHostname    = replace localhost/127.0.0.1 with the machine name
        config.setEndpointHostMode(UaEndpointHostMode.AsConfigured);


        // Build the certificate store: one APPLICATION cert for the OPC UA secure channel,
        // plus one default HTTPS certificate presented at every opc.https TLS handshake.
        // load() tries to load all certs from disk; getMissingOrExpired() returns any
        // that are missing or expired so they can be rebuilt individually.
        java.util.List<UaServerCertificate> certs = new java.util.ArrayList<>();
        certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_12a",
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
        // create the PKI directory structure (trusted/, rejected/, issuer/).

        config.setCertificateStore(store);
        return config;
    }

    // =============================================================================
    // Helper: printConfig
    // =============================================================================
    private static void printConfig(UaServerConfiguration config) {
        System.out.println("── Active Server Configuration ────────────────────────────────");
        System.out.println("  ApplicationName  : " + config.getApplicationName());
        System.out.println("  ApplicationUri   : " + config.getApplicationUri());
        System.out.println("  NamespaceUri     : " + (config.getNamespaceUri() != null ? config.getNamespaceUri() : "(default)"));
        System.out.println("  ManufacturerName : " + (config.getManufacturerName().isEmpty() ? "(not set)" : config.getManufacturerName()));
        System.out.println("  ProductName      : " + (config.getProductName().isEmpty() ? "(not set)" : config.getProductName()));
        System.out.println("  SoftwareVersion  : " + (config.getSoftwareVersion().isEmpty() ? "(not set)" : config.getSoftwareVersion()));
        System.out.println("  BuildNumber      : " + (config.getBuildNumber().isEmpty() ? "(not set)" : config.getBuildNumber()));
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
        System.out.println();
        System.out.println("  VendorServerInfo (Server/VendorServerInfo):");
        System.out.println("    VendorName           = " + (config.getVendorName() != null ? config.getVendorName() : "(not set)"));
        System.out.println("    VendorProductName    = " + (config.getVendorProductName() != null ? config.getVendorProductName() : "(not set)"));
        System.out.println("    VendorProductVersion = " + (config.getVendorProductVersion() != null ? config.getVendorProductVersion() : "(not set)"));
        System.out.println();
        System.out.println("  OperationLimits (Server/ServerCapabilities/OperationLimits):");
        System.out.printf("    MaxNodesPerRead                          = %d%n", config.getMaxNodesPerRead());
        System.out.printf("    MaxNodesPerWrite                         = %d%n", config.getMaxNodesPerWrite());
        System.out.printf("    MaxNodesPerBrowse                        = %d%n", config.getMaxNodesPerBrowse());
        System.out.printf("    MaxNodesPerHistoryReadData               = %d%n", config.getMaxNodesPerHistoryReadData());
        System.out.printf("    MaxNodesPerHistoryReadEvents             = %d%n", config.getMaxNodesPerHistoryReadEvents());
        System.out.printf("    MaxNodesPerHistoryUpdateData             = %d%n", config.getMaxNodesPerHistoryUpdateData());
        System.out.printf("    MaxNodesPerHistoryUpdateEvents           = %d%n", config.getMaxNodesPerHistoryUpdateEvents());
        System.out.printf("    MaxNodesPerMethodCall                    = %d%n", config.getMaxNodesPerMethodCall());
        System.out.printf("    MaxNodesPerRegisterNodes                 = %d%n", config.getMaxNodesPerRegisterNodes());
        System.out.printf("    MaxNodesPerTranslateBrowsePathsToNodeIds = %d%n", config.getMaxNodesPerTranslateBrowsePathsToNodeIds());
        System.out.printf("    MaxNodesPerNodeManagement                = %d%n", config.getMaxNodesPerNodeManagement());
        System.out.printf("    MaxMonitoredItemsPerCall                 = %d%n", config.getMaxMonitoredItemsPerCall());
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println();
    }

}
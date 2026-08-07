// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 71: Reverse Connect
//
// In standard OPC UA, the CLIENT connects to the SERVER.
// With Reverse Connect, the SERVER connects to the CLIENT.
//
// Why use Reverse Connect?
//   * The server is behind a firewall that blocks incoming connections
//   * The server is in a protected network (OT/ICS) and the client is in IT/cloud
//   * The server has a dynamic IP address
//
// How it works:
//   1. The client opens a listening port (e.g. 48500)
//   2. The server periodically sends a ReverseHello message to the client
//   3. The client uses that connection to establish a normal OPC UA session
//   4. From the application's perspective, the session works exactly the same
//
// This server also keeps its normal endpoint (48410) for direct connections.
//
// What you will learn:
//   * How to add a reverse connection target to the server
//   * How the server periodically attempts to connect to the client
//   * How to use both normal and reverse connect simultaneously
//
// Normal endpoint:  opc.tcp://localhost:48410
// Reverse Connect:  -> opc.tcp://localhost:48500 (server connects to client)
// ==============================================================================

import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.util.Random;

public class _71_Server_ReverseConnect {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 71 - Reverse Connect", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 71: Reverse Connect     ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║  * Server initiates connection to client (firewall-safe)     ║");
        System.out.println("║  * ReverseHello message flow                                 ║");
        System.out.println("║  * Normal endpoint still available for direct connections    ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Use case: Server behind firewall, client in DMZ/cloud       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        UaServerConfiguration config = createConfig();

        printConfig(config);

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            // Accepts all client certificates automatically - suitable for development.
            // Remove this listener to activate PKI-based validation via the store above.
            server.addCertificateValidationListener(e -> {
                System.out.println("  [CERT] Certificate received -> Accepted");
                e.setAccept(true);
            });

            // Log session events to see when the reverse connection is established
            server.addSessionListener(new UaServer.UaSessionListener() {
                @Override
                public void onSessionCreated(UaServer.UaSessionInfo session) {
                    System.out.println("\n  [SESSION+] " + session.getSessionName()
                            + " from " + session.getClientUri());
                }
                @Override
                public void onSessionClosed(UaServer.UaSessionInfo session) {
                    System.out.println("\n  [SESSION-] " + session.getSessionName());
                }
            });

            // Enable logging to see reverse connect activity
            server.addLogListener(e -> System.out.printf("  [%-7s] %s%n",
                    e.getLevel(), e.getMessage()));
            server.setLogLevel(UaLogLevel.Info);

            System.out.print("  Starting server ... ");
            try {
                server.start(config);
            } catch (Exception ex) {
                System.out.println("FAILED: " + ex.getMessage());
                System.in.read();
                PLCcomConsole.close();
                return;
            }
            System.out.println("OK");
            for (String addr : config.getBaseAddresses())
                System.out.println("  Endpoint: " + addr);
            System.out.println();

            // Create a variable to give the client something to read
            UaFolder plant = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaVariable<Double> temp = server.createVariable(plant, "Temperature",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 22.5, false);
            temp.setEURange(0, 100);
            temp.setEngineeringUnits("C");

            // -- Add Reverse Connection --------------------------------------------
            // addReverseConnection() tells the server to periodically connect to
            // this URL and send a ReverseHello message.
            // The client must be listening on this port for incoming connections.
            // Per OPC UA Part 6 §7.1.3, the connection is automatically
            // re-established when closed.
            String clientUrl = "opc.tcp://localhost:48500";
            server.addReverseConnection(clientUrl);

            System.out.println("  Normal endpoint:    opc.tcp://localhost:48410");
            System.out.println("  Reverse Connect to: " + clientUrl);
            System.out.println();
            System.out.println("  The server will attempt to connect to the client periodically.");
            System.out.println("  Start a reverse-connect-capable client on port 48500 to test.");
            System.out.println("  (See Workshop ReverseConnect_Client for a matching client)");
            System.out.println();

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running with Reverse Connect enabled.             ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Normal endpoint (direct):                                   ║");
            System.out.println("║    opc.tcp://localhost:48410                                 ║");
            System.out.println("║    -> connect as usual, server is listening                  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Reverse Connect endpoint:                                   ║");
            System.out.println("║    opc.tcp://localhost:48500                                 ║");
            System.out.println("║    -> the CLIENT must listen on this port                    ║");
            System.out.println("║    -> the SERVER connects to the client (not the other way)  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  How to test with an OPC UA client that supports             ║");
            System.out.println("║  Reverse Connect:                                            ║");
            System.out.println("║    1. In the client, open 'Add Server' with Reverse Connect  ║");
            System.out.println("║       mode and enter: opc.tcp://localhost:48500              ║");
            System.out.println("║       The client will now LISTEN on port 48500               ║");
            System.out.println("║    2. This server sends a ReverseHello to port 48500         ║");
            System.out.println("║    3. The client receives the ReverseHello and establishes   ║");
            System.out.println("║       a normal OPC UA session over that connection           ║");
            System.out.println("║    4. Watch the [SESSION+] message appear here               ║");
            System.out.println("║    5. Browse and read Plant/Temperature as usual             ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to start value loop, CTRL+C to exit.            ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Pushing values every second...");
            Random rng = new Random();
            long cycle = 0;

            while (true) {
                cycle++;
                double t = Math.round((20.0 + rng.nextDouble() * 10.0) * 10.0) / 10.0;
                temp.setValue(t);
                System.out.printf("  Cycle=%-5d  %s = %.1fC  (sessions: %d)%n",
                        cycle, temp.getPath(), t, server.getActiveSessionCount());
                Thread.sleep(1000);
            }
        }
    }

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
        config.setApplicationName("PLCcom Workshop 71 - Reverse Connect");

        // ApplicationUri: globally unique identifier for this server instance.
        //   Must match the URI in the server certificate.
        //   Recommended format: urn:<host>:<company>:<product>
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:71");

        // ProductUri: URI identifying the software product (not the instance).
        //   Typically a URL pointing to the product page.
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");

        // NamespaceUri: URI for this server's application address space (ns=2).
        //   Use a stable URI based on your company domain.
        //   Default: null (auto-generated as ApplicationUri + "/nodes").
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/reverse-connect");

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
                UserTokenPolicy.ANONYMOUS));

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
        certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_71",
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

    private static void printConfig(UaServerConfiguration config) {
        System.out.println("── Active Server Configuration ──────────────────────────────────────────────");
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
        System.out.println("─────────────────────────────────────────────────────────────────────────────");
        System.out.println();
    }

}
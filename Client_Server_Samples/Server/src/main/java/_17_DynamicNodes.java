// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 17: Dynamic Nodes
//
// In most OPC UA servers the address space is static - built once at startup.
// This SDK supports dynamic changes at runtime using the same API as at startup:
//   * createFolder / createVariable / createObject work before AND after start()
//   * removeNode removes a node and all its children at any time
//   * Connected clients see changes immediately on their next browse
//
// This workshop demonstrates step by step — pause between each step to inspect
// the address space with your OPC UA client:
//
//   Step 1 — Initial address space (Plant/Line1/Temperature)
//   Step 2 — Add nodes at runtime (DynamicNodes folder with Counter + Message)
//   Step 3 — Remove a single node (Counter removed, Message stays)
//   Step 4 — Remove an entire subtree (DynamicNodes folder + all children)
//   Step 5 — Path-based lookup (getNodeId, getValue, setValue)
//   Step 6 — Timer-based device discovery (new Device_N every 5 seconds)
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.*;
import com.plccom.opc.ua.core.*;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.util.*;

public class _17_DynamicNodes {

    @SuppressWarnings("unused")
	public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 17 - Dynamic Nodes", 1000);

        // Important !!!!!!!!!!!!!!!!!!
        // Enter your Username + Serial here! Please note: with blank fields the library runs
        // for 15 minutes during a debug session. Both values can also come
        // from configuration or an environment variable.
        // Free trial license (14 days, uninterrupted): https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/
        String licenseUser   = "";
        String licenseSerial = "";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 17: Dynamic Nodes       ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Demonstrates live address space changes at runtime.         ║");
        System.out.println("║  Use an OPC UA client to inspect the address space between   ║");
        System.out.println("║  each step.                                                  ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Endpoint: opc.tcp://localhost:48410                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        UaServerConfiguration config = createConfig();
        printConfig(config);


        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            // Accepts all client certificates automatically - suitable for development.
            // Remove this listener to activate PKI-based validation via the store above.
            server.addCertificateValidationListener(e -> e.setAccept(true));

            // ValuesWritten fires AFTER a successful write — the client already received Good.
            // If WriteValidation rejects, this does NOT fire.
            server.addValuesWrittenListener(items -> {
                for (UaServer.UaWrittenItem item : items)
                    System.out.println("  << Written: " + item.getPath() + " = " + item.getValueAsString());
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
                    // Example: forward to device, reject on failure
                    // boolean ok = plc.writeValue(item.getPath(), item.getValue());
                    // if (!ok) item.setStatusCode(new StatusCode(StatusCodes.Bad_NoCommunication));
                    item.setStatusCode(StatusCode.GOOD);
                    System.out.println("  >> WriteValidation: " + item.getPath() + " = " + item.getValue());
                }
            });

            server.addSessionListener(new UaServer.UaSessionListener() {
                @Override
                public void onSessionCreated(UaServer.UaSessionInfo s) {
                    System.out.println("  [SESSION+] " + (s.getSessionName() != null ? s.getSessionName() : "unknown")
                            + " from " + (s.getClientUri() != null ? s.getClientUri() : "unknown"));
                }
                @Override
                public void onSessionClosed(UaServer.UaSessionInfo s) {
                    System.out.println("  [SESSION-] " + (s.getSessionName() != null ? s.getSessionName() : "unknown"));
                }
            });

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

            // =============================================================================
            // Step 1: Initial address space
            // =============================================================================
            UaFolder plant = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaFolder line1 = server.createFolder(plant, "Line1", UaRolePermissions.WITHOUT_RESTRICTIONS);
			UaVariable<Double> temperature = server.createVariable(line1, "Temperature", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 22.0, false);

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Step 1/5 — Initial address space                            ║");
            System.out.println("║                                                              ║");
            System.out.println("║  The server is running with a static address space:          ║");
            System.out.println("║    Objects                                                   ║");
            System.out.println("║      └── Plant                                               ║");
            System.out.println("║            └── Line1                                         ║");
            System.out.println("║                  └── Temperature = 22.0                      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Connect your OPC UA client and browse the address space.    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to add new nodes at runtime.                    ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 2: Add nodes at runtime
            // =============================================================================
            // createFolder and createVariable work exactly the same after start() as before.
            // Connected clients see the new nodes immediately on their next browse.
            UaFolder dynFolder  = server.createFolder(plant, "DynamicNodes", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaVariable<Integer> dynCounter = server.createVariable(dynFolder, "Counter", UaRolePermissions.WITHOUT_RESTRICTIONS, Integer.class, 42, false);
            UaVariable<String>  dynMessage = server.createVariable(dynFolder, "Message", UaRolePermissions.WITHOUT_RESTRICTIONS, String.class, "Hello", false);

            System.out.println("  + Created: Objects.Plant.DynamicNodes.Counter = " + dynCounter.getValue());
            System.out.println("  + Created: Objects.Plant.DynamicNodes.Message = " + dynMessage.getValue());
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Step 2/5 — Nodes added at runtime                           ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Two new nodes were added while the server was running:      ║");
            System.out.println("║    Objects                                                   ║");
            System.out.println("║      └── Plant                                               ║");
            System.out.println("║            ├── Line1/Temperature                             ║");
            System.out.println("║            └── DynamicNodes          ← NEW                   ║");
            System.out.println("║                  ├── Counter = 42    ← NEW                   ║");
            System.out.println("║                  └── Message = Hello ← NEW                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Refresh your client browser — the new nodes are visible.    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to remove the Counter node.                     ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 3: Remove a single node
            // =============================================================================
            // removeNode removes exactly the specified node. Sibling nodes are unaffected.
            boolean removed = server.removeNode(dynCounter.getNodeId());
            System.out.println("  - Removed: Objects.Plant.DynamicNodes.Counter  →  " + (removed ? "OK" : "FAILED"));
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Step 3/5 — Single node removed                              ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Counter was removed. Message is still there:                ║");
            System.out.println("║    Objects                                                   ║");
            System.out.println("║      └── Plant                                               ║");
            System.out.println("║            ├── Line1/Temperature                             ║");
            System.out.println("║            └── DynamicNodes                                  ║");
            System.out.println("║                  └── Message = Hello  (Counter is gone)      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Refresh your client — Counter has disappeared.              ║");
            System.out.println("║  Subscriptions on Counter now receive BadNodeIdUnknown.      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to remove the entire DynamicNodes subtree.      ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 4: Remove an entire subtree
            // =============================================================================
            // removeNode on a folder removes the folder AND all its children recursively.
            removed = server.removeNode(dynFolder.getNodeId());
            System.out.println("  - Removed: Objects.Plant.DynamicNodes (including all children)  →  " + (removed ? "OK" : "FAILED"));
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Step 4/5 — Entire subtree removed                           ║");
            System.out.println("║                                                              ║");
            System.out.println("║  DynamicNodes folder and all remaining children are gone:    ║");
            System.out.println("║    Objects                                                   ║");
            System.out.println("║      └── Plant                                               ║");
            System.out.println("║            └── Line1                                         ║");
            System.out.println("║                  └── Temperature = 22.0                      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  The address space is back to its initial state.             ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to demonstrate path-based lookup.               ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 5: Path-based lookup
            // =============================================================================
            // getNodeId / getValue / setValue let you access nodes by dot-separated path
            // without storing the NodeId or UaVariable wrapper from creation time.
            System.out.println("-- Step 5/5: Path-based lookup -----------------------------------");

            NodeId nodeId = server.getNodeId("Objects.Plant.Line1.Temperature");
            System.out.println("  getNodeId(\"Objects.Plant.Line1.Temperature\") = " + nodeId);

            Object currentVal = server.getValue("Objects.Plant.Line1.Temperature");
            System.out.println("  getValue  = " + currentVal);

            server.setValue("Objects.Plant.Line1.Temperature", 99.9);
            System.out.println("  setValue  → 99.9");
            System.out.println("  getValue  = " + server.getValue("Objects.Plant.Line1.Temperature"));
            System.out.println();

            // =============================================================================
            // Step 6: Timer-based device discovery
            // =============================================================================
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Step 5/5 — Timer-based device discovery                     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Every 5 seconds a new Device_N folder appears under Plant.  ║");
            System.out.println("║  After 5 devices the oldest is removed (sliding window).     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Watch your OPC UA client — devices appear and disappear     ║");
            System.out.println("║  in real time without restarting the server.                 ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to start the simulation (CTRL+C to exit).       ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("Simulating device discovery...");
            System.out.println();

            Random rng = new Random();
            int deviceNumber = 0;
            Queue<NodeId> activeDevices = new LinkedList<>();
            final int MAX_DEVICES = 5;

            while (true) {
                deviceNumber++;
                String deviceName = "Device_" + deviceNumber;

                UaFolder deviceFolder = server.createFolder(plant, deviceName, UaRolePermissions.WITHOUT_RESTRICTIONS);
                UaVariable<Double> devTemp   = server.createVariable(deviceFolder, "Temperature", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, Math.round((20.0 + rng.nextDouble() * 15.0) * 10.0) / 10.0, false);
                UaVariable<String> devStatus = server.createVariable(deviceFolder, "Status",      UaRolePermissions.WITHOUT_RESTRICTIONS, String.class, "Online", false);

                activeDevices.add(deviceFolder.getNodeId());
                System.out.printf("  + %s: Temp=%.1f  Status=%s%n", deviceName, devTemp.getValue(), devStatus.getValue());

                if (activeDevices.size() > MAX_DEVICES) {
                    NodeId oldest = activeDevices.poll();
                    server.removeNode(oldest);
                    System.out.println("  - Removed Device_" + (deviceNumber - MAX_DEVICES) + " (sliding window, max=" + MAX_DEVICES + ")");
                }

                System.out.println("    Active: " + activeDevices.size() + "/" + MAX_DEVICES);
                Thread.sleep(5000);
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
        config.setApplicationName("PLCcom Workshop 17 - Dynamic Nodes");

        // ApplicationUri: globally unique identifier for this server instance.
        //   Must match the URI in the server certificate.
        //   Recommended format: urn:<host>:<company>:<product>
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:17");

        // ProductUri: URI identifying the software product (not the instance).
        //   Typically a URL pointing to the product page.
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");

        // NamespaceUri: URI for this server's application address space (ns=2).
        //   Use a stable URI based on your company domain.
        //   Default: null (auto-generated as ApplicationUri + "/nodes").
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/dynamic-nodes");

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
        certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_17",
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
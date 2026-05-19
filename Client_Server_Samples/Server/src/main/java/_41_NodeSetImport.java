// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 41: NodeSet Import
//
// OPC UA NodeSet2 XML is the standard format for sharing address space
// definitions. It is used by:
//   * OPC UA Companion Specifications (PackML, Euromap, DI, Machinery, etc.)
//   * Vendor-specific type libraries
//   * Pre-defined address space templates
//
// A NodeSet XML file contains:
//   * Type definitions (ObjectTypes, VariableTypes, DataTypes)
//   * Namespace URIs
//   * Optionally: pre-built instances
//
// After importing, the types appear in the server's type hierarchy and
// can be used to create typed instances with createObject().
//
// This workshop includes a ready-to-use sample NodeSet:
//   PLCcom_Workshop_NodeSet.xml  (in src/main/resources)
// It defines MotorType and SensorType with two instances each.
//
// What you will learn:
//   * How to import a NodeSet2.xml file into the server
//   * How namespaces from the NodeSet are registered automatically
//   * How to verify the imported nodes in the address space
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.*;

import java.io.InputStream;

public class _41_NodeSetImport {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 41 - NodeSet Import", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 41: NodeSet Import      ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║  * Importing NodeSet2.xml files into the address space       ║");
        System.out.println("║  * Automatic namespace registration                          ║");
        System.out.println("║  * Types and instances from companion specifications         ║");
        System.out.println("║  * Verifying imported nodes                                  ║");
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

            // -- Import NodeSet XML ------------------------------------------------
            // importNodeSet() reads the XML and adds all nodes to the address space.
            // Namespaces defined in the NodeSet are automatically registered.
            // The method returns the number of nodes imported.
            //
            // The NodeSet2 XML format is defined by the OPC Foundation in:
            //   OPC UA Specification Part 6 - Mappings, Annex F (UANodeSet XML Schema)
            //
            // ── Namespace rules for NodeSet XML files ─────────────────────────────
            //
            //   Server namespace layout:
            //     ns=0  OPC UA Standard (http://opcfoundation.org/UA/)
            //     ns=1  SDK internal – not available for user nodes
            //     ns=2  Server Application Namespace (from config.NamespaceUri)
            //     ns=3+ Additional namespaces (registered via <NamespaceUris> in the XML)
            //
            //   Rules for authoring NodeSet XML files:
            //     1. Namespace indices in the XML are ABSOLUTE server indices.
            //        ns=2 in the file means ns=2 on the server. ns=3 means ns=3.
            //     2. Every namespace used in the file (except ns=0) MUST be declared
            //        in <NamespaceUris>. The server application namespace (ns=2) must
            //        also be listed if nodes use it.
            //     3. If a namespace referenced by a node is NOT declared in
            //        <NamespaceUris>, the node falls back to ns=2.
            //     4. AccessLevel="3" (CurrentRead | CurrentWrite) must be set explicitly
            //        on writable UAVariable nodes. Without it, the OPC UA spec default
            //        is ReadOnly (Part 6, Table F.8).
            //     5. Properties (HasProperty) like SerialNumber or Unit are typically
            //        left without AccessLevel → ReadOnly by design.
            //
            // ── Sample NodeSet ─────────────────────────────────────────────────────
            //
            // PLCcom_Workshop_NodeSet.xml is included as a resource in this project.
            // It defines two namespaces:
            //   ns=2  Server App Namespace → SensorType, TempSensor1, PressureSensor1
            //   ns=3  urn:plccom:workshop:nodeset → MotorType, Motor1, Motor2
            //
            // Types:
            //   SensorType (ns=2) - Value (Double), Unit (String), InAlarm (Boolean)
            //   MotorType  (ns=3) - Speed (Double), Running (Boolean), SerialNumber (String)
            // Instances:
            //   Sensors/ (ns=2) - TempSensor1, PressureSensor1
            //   Motors/  (ns=3) - Motor1, Motor2
            String resourceName = "PLCcom_Workshop_NodeSet.xml";
            InputStream stream = _41_NodeSetImport.class.getClassLoader()
                    .getResourceAsStream(resourceName);

            if (stream != null) {
                System.out.println("  Importing: " + resourceName);
                int count = server.importNodeSet(stream);
                System.out.println("  Imported " + count + " nodes successfully");
                System.out.println();
                System.out.println("  Nodes imported:");
                System.out.println("    Types    -> Types/ObjectTypes/SensorType  (ns=2)");
                System.out.println("    Types    -> Types/ObjectTypes/MotorType   (ns=3)");
                System.out.println("    Instance -> Objects/Sensors/TempSensor1, PressureSensor1  (ns=2)");
                System.out.println("    Instance -> Objects/Motors/Motor1, Motor2                (ns=3)");
            } else {
                System.out.println("  ERROR: '" + resourceName + "' not found in resources.");
                System.out.println("  Make sure PLCcom_Workshop_NodeSet.xml is in src/main/resources.");
            }
            System.out.println();

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Try:                                                        ║");
            System.out.println("║  * Browse Objects -> Motors -> Motor1 -> Speed, Running      ║");
            System.out.println("║  * Browse Objects -> Sensors -> TempSensor1 -> Value, Unit   ║");
            System.out.println("║  * Browse Types -> ObjectTypes -> MotorType, SensorType      ║");
            System.out.println("║  * Check Server -> NamespaceArray for the imported namespace ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to exit.                                        ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();
        }

        PLCcomConsole.close();
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
        config.setApplicationName("PLCcom Workshop 41 - NodeSet Import");

        // ApplicationUri: globally unique identifier for this server instance.
        //   Must match the URI in the server certificate.
        //   Recommended format: urn:<host>:<company>:<product>
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:41");

        // ProductUri: URI identifying the software product (not the instance).
        //   Typically a URL pointing to the product page.
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");

        // NamespaceUri: URI for this server's application address space (ns=2).
        //   Use a stable URI based on your company domain.
        //   Default: null (auto-generated as ApplicationUri + "/nodes").
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/nodeset-import");

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
        // plus one HTTPS cert per opc.https:// hostname derived from the base addresses.
        // load() tries to load all certs from disk; getMissingOrExpired() returns any
        // that are missing or expired so they can be rebuilt individually.
        java.util.List<UaServerCertificate> certs = new java.util.ArrayList<>();
        certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_41",
                config.getApplicationUri(), 720, "Indi.An GmbH",
                UaServerCertificate.CertificateRole.APPLICATION));
        for (String host : UaServerCertificateStore.extractHttpsHostnames(config.getBaseAddresses()))
            certs.add(new UaServerCertificate("./pki", "secretpassword", host,
                    "urn:" + host + ":https", 720, "Indi.An GmbH",
                    UaServerCertificate.CertificateRole.HTTPS));

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
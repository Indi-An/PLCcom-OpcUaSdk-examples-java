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
import java.util.Arrays;

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

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 41 - NodeSet Import");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:41");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/nodeset-import");
        config.setManufacturerName("My Company GmbH");
        config.setProductName("My OPC UA Server");
        config.setSoftwareVersion("1.0.0");
        config.setBuildNumber("42");
        config.setBaseAddresses(Arrays.asList(
                "opc.tcp://localhost:48410", "opc.https://localhost:48411"));
        config.setSecurityModes(UaServer.getRecommendedSecurityModes());
        config.setUserTokenPolicies(Arrays.asList(UserTokenPolicy.ANONYMOUS));
        config.setCertificateStorePath("./pki");
        config.setCertificateLifetimeInMonths(60);
        config.setAutoAcceptUntrustedCertificates(false);
        config.setMaxSessionCount(100); config.setShutdownDelay(5);
        config.setVendorName("My Company GmbH");
        config.setVendorProductName("My OPC UA Server");
        config.setVendorProductVersion("1.0.0");
        config.setHttpsSecurityPolicies(Arrays.asList(
                com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_2_PFS,
                com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_3));
        config.setMaxNodesPerRead(1000); config.setMaxNodesPerWrite(1000);
        config.setMaxNodesPerBrowse(1000); config.setMaxNodesPerHistoryReadData(100);
        config.setMaxNodesPerHistoryReadEvents(100); config.setMaxNodesPerHistoryUpdateData(100);
        config.setMaxNodesPerHistoryUpdateEvents(100); config.setMaxNodesPerMethodCall(200);
        config.setMaxNodesPerRegisterNodes(1000);
        config.setMaxNodesPerTranslateBrowsePathsToNodeIds(1000);
        config.setMaxNodesPerNodeManagement(1000); config.setMaxMonitoredItemsPerCall(1000);
        // AsConfigured (default) = endpoints use exactly the host from BaseAddresses
        // NormalizeToHostname    = replace localhost/127.0.0.1 with the machine name
        config.setEndpointHostMode(UaEndpointHostMode.AsConfigured);
        return config;
    }

    private static void printConfig(UaServerConfiguration config) {
        System.out.println("── Active Server Configuration ──────────────────────────────");
        System.out.println("  ApplicationName : " + config.getApplicationName());
        System.out.println("  ApplicationUri  : " + config.getApplicationUri());
        System.out.println("  NamespaceUri    : " + config.getNamespaceUri());
        System.out.println("  Endpoints:");
        for (String addr : config.getBaseAddresses()) System.out.println("    " + addr);
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println();
    }
}

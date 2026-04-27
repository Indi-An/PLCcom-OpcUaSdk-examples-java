// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 16: Multiple Namespaces
//
// Every node in OPC UA has a NodeId and a BrowseName, both of which belong
// to a namespace. Namespaces prevent naming collisions when multiple vendors,
// standards or subsystems share the same server.
//
// This workshop demonstrates:
//   ns=0  OPC UA standard        - standard nodes (always present)
//   ns=1  Local server            - server's own nodes (always present)
//   ns=2  Company namespace       - company-wide type definitions
//   ns=3  Plant A namespace       - nodes for Plant A
//   ns=4  Plant B namespace       - nodes for Plant B
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.*;
import com.plccom.opc.ua.core.*;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

public class _16_MultipleNamespaces {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 16 - Multiple Namespaces", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 16: Multiple Namespaces ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║    * Registering additional namespaces                       ║");
        System.out.println("║    * Creating nodes in specific namespaces                   ║");
        System.out.println("║    * Sharing ObjectTypes across namespaces                   ║");
        System.out.println("║    * Two plants with identical structure but separate nodes  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        UaServerConfiguration config = createConfig();

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            server.addCertificateValidationListener(e -> e.setAccept(true));

            server.addValuesWrittenListener(items -> {
                for (UaServer.UaWrittenItem item : items)
                    System.out.println("  << OPC Write: " + item.getPath()
                            + " (" + item.getNodeId() + ") = " + item.getValueAsString());
            });

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
            // Step 2: Register additional namespaces
            // =============================================================================
            System.out.println("── Registering namespaces ───────────────────────────────────");

            int nsCompany = server.addNamespace("urn:mycompany:types");
            int nsPlantA  = server.addNamespace("urn:mycompany:plant-a");
            int nsPlantB  = server.addNamespace("urn:mycompany:plant-b");

            System.out.printf("  ns=%d  urn:mycompany:types     (company-wide types)%n", nsCompany);
            System.out.printf("  ns=%d  urn:mycompany:plant-a   (Plant A instances)%n", nsPlantA);
            System.out.printf("  ns=%d  urn:mycompany:plant-b   (Plant B instances)%n", nsPlantB);
            System.out.println();

            int check = server.getNamespaceIndex("urn:mycompany:plant-a");
            System.out.println("  GetNamespaceIndex(\"urn:mycompany:plant-a\") = " + check);
            System.out.println();

            // Default namespace nodes (ns=2) for comparison
            System.out.println("── Default namespace nodes (ns=2) ───────────────────────────");
            UaFolder defaultFolder = server.createFolder("DefaultNS", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaVariable<Double> testValue1 = server.createVariable(defaultFolder, "TestValue1", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 42.0, false);
            UaVariable<String> testValue2 = server.createVariable(defaultFolder, "TestValue2", UaRolePermissions.WITHOUT_RESTRICTIONS, String.class, "hello", false);
            System.out.printf("  %-40s NodeId=%s%n", "DefaultNS",   defaultFolder.getNodeId());
            System.out.printf("  %-40s NodeId=%s  = %.1f%n", "  TestValue1", testValue1.getNodeId(), testValue1.getValue());
            System.out.printf("  %-40s NodeId=%s  = %s%n",   "  TestValue2", testValue2.getNodeId(), testValue2.getValue());
            System.out.println();

            // =============================================================================
            // Step 3: Company-wide ObjectTypes in the company namespace
            // =============================================================================
            System.out.printf("── Company-wide ObjectTypes (ns=%d) ──────────────────────────%n", nsCompany);

            NodeId reactorTypeId = server.createObjectType("ReactorType", nsCompany);
            NodeId mixerTypeId   = server.createObjectType("MixerType", nsCompany);

            System.out.println("  ReactorType  " + reactorTypeId);
            System.out.println("  MixerType    " + mixerTypeId);
            System.out.println();

            // =============================================================================
            // Step 4: Build Plant A in its own namespace
            // =============================================================================
            System.out.printf("── Plant A (ns=%d) ──────────────────────────────────────────%n", nsPlantA);

            UaFolder plantA = server.createFolder("PlantA", UaRolePermissions.WITHOUT_RESTRICTIONS, nsPlantA);

            UaObject reactorA = server.createObject(plantA, "Reactor", UaRolePermissions.WITHOUT_RESTRICTIONS, reactorTypeId);
            UaVariable<Double> tempA  = server.createVariable(reactorA, "Temperature", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 85.0, false);
            UaVariable<Double> pressA = server.createVariable(reactorA, "Pressure",    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 2.5,  false);

            UaObject mixerA = server.createObject(plantA, "Mixer", UaRolePermissions.WITHOUT_RESTRICTIONS, mixerTypeId);
            UaVariable<Double> speedA = server.createVariable(mixerA, "Speed", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 120.0, false);

            System.out.printf("  %-40s NodeId=%s%n", "PlantA",                plantA.getNodeId());
            System.out.printf("  %-40s NodeId=%s  = %.1f%n", "  Reactor/Temperature", tempA.getNodeId(),  tempA.getValue());
            System.out.printf("  %-40s NodeId=%s  = %.1f%n", "  Reactor/Pressure",    pressA.getNodeId(), pressA.getValue());
            System.out.printf("  %-40s NodeId=%s  = %.1f%n", "  Mixer/Speed",         speedA.getNodeId(), speedA.getValue());
            System.out.println();

            // =============================================================================
            // Step 5: Build Plant B in its own namespace
            // =============================================================================
            System.out.printf("── Plant B (ns=%d) ──────────────────────────────────────────%n", nsPlantB);

            UaFolder plantB = server.createFolder("PlantB", UaRolePermissions.WITHOUT_RESTRICTIONS, nsPlantB);

            UaObject reactorB = server.createObject(plantB, "Reactor", UaRolePermissions.WITHOUT_RESTRICTIONS, reactorTypeId);
            UaVariable<Double> tempB  = server.createVariable(reactorB, "Temperature", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 92.0, false);
            UaVariable<Double> pressB = server.createVariable(reactorB, "Pressure",    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 3.1,  false);

            UaObject mixerB = server.createObject(plantB, "Mixer", UaRolePermissions.WITHOUT_RESTRICTIONS, mixerTypeId);
            UaVariable<Double> speedB = server.createVariable(mixerB, "Speed", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 80.0, false);

            System.out.printf("  %-40s NodeId=%s%n", "PlantB",                plantB.getNodeId());
            System.out.printf("  %-40s NodeId=%s  = %.1f%n", "  Reactor/Temperature", tempB.getNodeId(),  tempB.getValue());
            System.out.printf("  %-40s NodeId=%s  = %.1f%n", "  Reactor/Pressure",    pressB.getNodeId(), pressB.getValue());
            System.out.printf("  %-40s NodeId=%s  = %.1f%n", "  Mixer/Speed",         speedB.getNodeId(), speedB.getValue());
            System.out.println();

            // =============================================================================
            // Step 6: Cross-namespace reading
            // =============================================================================
            System.out.println("── Cross-namespace GetValue ─────────────────────────────────");

            Object tA = server.getValue("Objects.PlantA.Reactor.Temperature");
            Object tB = server.getValue("Objects.PlantB.Reactor.Temperature");
            System.out.println("  PlantA Reactor Temperature = " + tA);
            System.out.println("  PlantB Reactor Temperature = " + tB);
            System.out.println();

            // =============================================================================
            // Step 7: Run the server
            // =============================================================================
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Try:                                                        ║");
            System.out.println("║  * Browse Objects -> PlantA -> Reactor -> Temperature        ║");
            System.out.println("║  * Browse Objects -> PlantB -> Reactor -> Temperature        ║");
            System.out.println("║  * Compare NodeIds: both have numeric IDs but different ns   ║");
            System.out.println("║  * Compare BrowseNames: same name, different namespace index ║");
            System.out.println("║  * Browse Types -> ObjectTypes -> ReactorType, MixerType     ║");
            System.out.println("║  * Write PlantA/Reactor/Temperature and PlantB independently ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to exit.                                        ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Server stopped.");
        }
        PLCcomConsole.close();
    }

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 16 - Multiple Namespaces");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:16");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/multiple-namespaces");
        config.setManufacturerName("My Company GmbH");
        config.setProductName("My OPC UA Server");
        config.setSoftwareVersion("1.0.0");
        config.setBuildNumber("42");
        config.setBaseAddresses(java.util.Arrays.asList(
                "opc.tcp://localhost:48410",
                "opc.https://localhost:48411"));
        config.setSecurityModes(UaServer.getRecommendedSecurityModes());
        config.setUserTokenPolicies(java.util.Arrays.asList(
                UserTokenPolicy.ANONYMOUS));
        config.setCertificateStorePath("./pki");
        config.setCertificateLifetimeInMonths(60);
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
        return config;
    }
}

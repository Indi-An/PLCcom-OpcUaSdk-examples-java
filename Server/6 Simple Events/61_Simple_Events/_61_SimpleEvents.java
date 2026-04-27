// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 61: Simple Events
//
// OPC UA Events are notifications that something happened - not a value change,
// but a discrete occurrence like a state transition, a warning, or an action.
//
// Events are different from DataChange notifications:
//   DataChange: a variable's value changed (subscription-based polling)
//   Event:      something happened at a source node (event subscription)
//
// To use events:
//   1. Call enableEvents() on the source node (folder or object)
//   2. Call fireEvent() to send an event to all subscribed clients
//   3. Clients subscribe to the source node's EventNotifier attribute
//
// Events propagate upward automatically:
//   Machine1 -> Plant -> Objects -> Server
//   A client subscribed to the Server node receives ALL events from all sources.
//   A client subscribed to Machine1 only receives events from Machine1.
//
// Events have a severity level (1-1000):
//   Low    (  1-333): informational, normal operation
//   Medium (334-666): warning, attention needed
//   High   (667-1000): critical, immediate action required
//
// What you will learn:
//   * How to enable event notifications on a node
//   * How to fire events with different severity levels
//   * How clients subscribe to events in the Event View
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.util.Random;

public class _61_SimpleEvents {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 61 - Simple Events", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 61: Simple Events       ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║  * Enabling event notifications on nodes                     ║");
        System.out.println("║  * Firing events with message and severity                   ║");
        System.out.println("║  * Event severity levels (Low, Medium, High)                 ║");
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

            UaFolder plant   = server.createFolder("Plant",    UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaFolder machine = server.createFolder(plant, "Machine1", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaVariable<Double> temp = server.createVariable(machine, "Temperature",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 22.0, false);

            // -- Enable events on the source node ----------------------------------
            // enableEvents() sets the EventNotifier attribute on the node so that
            // clients can subscribe to events from it.
            // Without this call, FireEvent() has no effect for subscribed clients.
            // Events fired on Machine1 automatically propagate up to Plant -> Server,
            // so clients subscribed to the Server node receive them as well.
            server.enableEvents(machine);

            // Fire an initial event to confirm the server started successfully.
            // This event is delivered to any client that is already subscribed.
            server.fireEvent(machine, "Machine1 started successfully", EventSeverity.Low);

            System.out.println("  Machine1: Events enabled");
            System.out.println("  Initial event fired: 'Machine1 started successfully'");
            System.out.println();

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  To see events in the client:                                ║");
            System.out.println("║  1. Open Document -> Add -> Event View                       ║");
            System.out.println("║  2. In the Event View, click the '+' button and select       ║");
            System.out.println("║     Objects -> Server (to receive all events)                ║");
            System.out.println("║  3. Press ENTER here to start firing events                  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to start firing events every 5 seconds.         ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Firing events every 5 seconds... (CTRL+C to exit)");
            System.out.println("    Temperature > 30 -> High severity event");
            System.out.println("    Temperature > 25 -> Medium severity event");
            System.out.println("    Temperature <= 25 -> Low severity event");
            System.out.println();

            Random rng = new Random();

            while (true) {
                double t = 20.0 + rng.nextDouble() * 15.0;
                temp.setValue(Math.round(t * 10.0) / 10.0);

                // Fire events with different severity based on the temperature value.
                // The severity level is visible in the client's Event View as a color
                // or numeric value in the Severity column.
                if (t > 30.0) {
                    server.fireEvent(machine, String.format("Temperature HIGH: %.1fC", t), EventSeverity.High);
                    System.out.printf("  [EVENT HIGH] Temperature = %.1fC%n", t);
                } else if (t > 25.0) {
                    server.fireEvent(machine, String.format("Temperature warning: %.1fC", t), EventSeverity.Medium);
                    System.out.printf("  [EVENT MED]  Temperature = %.1fC%n", t);
                } else {
                    server.fireEvent(machine, String.format("Temperature normal: %.1fC", t), EventSeverity.Low);
                    System.out.printf("  [EVENT LOW]  Temperature = %.1fC%n", t);
                }

                Thread.sleep(5000);
            }
        }
    }

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 61 - Simple Events");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:61");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/simple-events");
        config.setManufacturerName("My Company GmbH");
        config.setProductName("My OPC UA Server");
        config.setSoftwareVersion("1.0.0");
        config.setBuildNumber("42");
        config.setBaseAddresses(java.util.Arrays.asList(
                "opc.tcp://localhost:48410", "opc.https://localhost:48411"));
        config.setSecurityModes(UaServer.getRecommendedSecurityModes());
        config.setUserTokenPolicies(java.util.Arrays.asList(UserTokenPolicy.ANONYMOUS));
        config.setCertificateStorePath("./pki");
        config.setCertificateLifetimeInMonths(60);
        config.setAutoAcceptUntrustedCertificates(false);
        config.setMaxSessionCount(100); config.setShutdownDelay(5);
        config.setVendorName("My Company GmbH");
        config.setVendorProductName("My OPC UA Server");
        config.setVendorProductVersion("1.0.0");
        config.setHttpsSecurityPolicies(java.util.Arrays.asList(
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

// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 33: Historical Events
//
// OPC UA servers can store events in a history that clients can query later.
// This is useful for audit trails, alarm history and post-mortem analysis.
//
// This workshop demonstrates:
//   1. enableEvents() on a source node (required for live events)
//   2. enableHistoryEvents() on the same node (enables HistoryRead for events)
//   3. fireEvent() to send live events to subscribed clients
//   4. recordHistoryEvent() to store the event in the history
//   5. Clients use HistoryRead with ReadEventDetails to query past events
//
// The event history is stored in memory with a configurable maximum size.
// For production use, you would store events in a database.
//
// What you will learn:
//   * How to enable event history on a source node
//   * How to record events in the history store
//   * How clients read historical events via HistoryRead
//   * The difference between live events and historical events
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.*;
import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.alarm.UaAlarm;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.util.Random;

public class _33_HistoricalEvents {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 33 - Historical Events", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 33: Historical Events   ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║  * Enabling event history on source nodes                    ║");
        System.out.println("║  * Recording events in the history store                     ║");
        System.out.println("║  * Clients can query past events via HistoryRead             ║");
        System.out.println("║  * Live events AND historical events from the same source    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        UaServerConfiguration config = createConfig();
        printConfig(config);

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            server.addCertificateValidationListener(e -> e.setAccept(true));

            // Log all history update operations from clients (e.g. DeleteEvent from Client WS43)
            server.addHistoryUpdateListener(item -> {
                String detail = item.getValue() instanceof Integer
                        ? "deleted " + item.getValue() + " event(s)"
                        : (item.getValue() != null ? item.getValue().toString() : "");
                System.out.printf("  << History %-15s  %s  path=%s%n",
                        item.getOperation(), detail,
                        item.getPath() != null ? item.getPath() : item.getNodeId());
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

            UaFolder plant   = server.createFolder("Plant",   UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaFolder reactor = server.createFolder(plant, "Reactor", UaRolePermissions.WITHOUT_RESTRICTIONS);

            UaVariable<Double> temperature = server.createVariable(reactor, "Temperature",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 25.0, false);
            temperature.setEURange(0, 200);
            temperature.setEngineeringUnits("C", "Degrees Celsius");

            // Step 1: Enable live events on the reactor node.
            server.enableEvents(reactor);

            // Step 2: Enable event history on the same node.
            // maxEntries limits the in-memory buffer (oldest events are discarded).
            server.enableHistoryEvents(reactor, 500);

            System.out.println("  Reactor:");
            System.out.println("    Temperature (0-200 C)");
            System.out.println("    Events: live + history enabled (max 500 entries)");
            System.out.println();

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  To see live events:                                         ║");
            System.out.println("║  1. Open Document -> Add -> Event View                       ║");
            System.out.println("║  2. Click '+' and select Objects -> Server                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  To read historical events:                                  ║");
            System.out.println("║  Use HistoryRead with ReadEventDetails in any OPC UA client  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to start the simulation.                        ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Simulating... events fire every 5 seconds (CTRL+C to exit)");
            System.out.println("    Temperature > 80C -> High severity event");
            System.out.println("    Temperature > 60C -> Medium severity event");
            System.out.println("    Temperature <= 60C -> Low severity event");
            System.out.println();

            Random rng = new Random();
            long cycle = 0;

            while (true) {
                cycle++;

                double t = 50.0 + Math.sin(cycle * 0.15) * 40.0 + rng.nextDouble() * 5.0;
                temperature.setValue(Math.round(t * 10.0) / 10.0);

                EventSeverity severity;
                String message;
                String label;
                if (t > 80.0) {
                    severity = EventSeverity.High;
                    message  = String.format("Temperature HIGH: %.1fC", t);
                    label    = "HIGH";
                } else if (t > 60.0) {
                    severity = EventSeverity.Medium;
                    message  = String.format("Temperature warning: %.1fC", t);
                    label    = "MED ";
                } else {
                    severity = EventSeverity.Low;
                    message  = String.format("Temperature normal: %.1fC", t);
                    label    = "LOW ";
                }

                // Step 3: Fire a live event to subscribed clients.
                server.fireEvent(reactor, message, severity);

                // Step 4: Record the same event in the history store.
                Variant[] eventFields = buildEventFields(reactor, message, severity.getValue());
                server.recordHistoryEvent(reactor.getNodeId(), eventFields);

                int histCount = server.getEventHistory(reactor.getNodeId()).size();
                System.out.printf("  [%s] %s  (history: %d entries)%n", label, message, histCount);

                Thread.sleep(5000);
            }
        }
    }

    private static Variant[] buildEventFields(UaFolder reactor, String message, int severity) {
        Variant[] fields = new Variant[UaAlarm.FIELD_SUPPRESSED + 1];
        for (int i = 0; i < fields.length; i++) fields[i] = new Variant(null);
        fields[UaAlarm.FIELD_EVENT_ID]    = new Variant(ByteString.valueOf(UaAlarm.newEventId()));
        fields[UaAlarm.FIELD_EVENT_TYPE]  = new Variant(com.plccom.opc.ua.core.Identifiers.BaseEventType);
        fields[UaAlarm.FIELD_SOURCE_NODE] = new Variant(reactor.getNodeId());
        fields[UaAlarm.FIELD_SOURCE_NAME] = new Variant(reactor.getName());
        fields[UaAlarm.FIELD_TIME]        = new Variant(DateTime.currentTime());
        fields[UaAlarm.FIELD_MESSAGE]     = new Variant(
                new LocalizedText(message, LocalizedText.NO_LOCALE));
        fields[UaAlarm.FIELD_SEVERITY]    = new Variant(UnsignedShort.valueOf(severity));
        fields[UaAlarm.FIELD_RETAIN]      = new Variant(Boolean.FALSE);
        return fields;
    }

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 33 - Historical Events");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:33");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/historical-events");
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

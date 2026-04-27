// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 21: Alarm Conditions
//
// OPC UA Alarms & Conditions (Part 9) extends the event model with stateful
// alarms that clients can acknowledge and confirm.
//
// This workshop demonstrates all alarm types that OPC UA supports:
//
//   AlarmConditionType      - General alarm (active/inactive, ack/confirm)
//   ExclusiveLimitAlarmType - Limit alarm with levels: Low / High / HighHigh
//   DiscreteAlarmType       - Alarm triggered by a discrete (boolean) state
//   DialogConditionType     - Dialog asking the operator to choose a response
//
// Each type maps to a filter option in the client workshops (31/32/33):
//   Client filter "3 - Alarms"       -> AlarmConditionType
//   Client filter "4 - Limit alarms" -> ExclusiveLimitAlarmType
//   Client filter "5 - Discrete"     -> DiscreteAlarmType
//   Client filter "2 - Dialogs"      -> DialogConditionType
//   Client filter "1 - All"          -> all of the above
//
// Address space:
//   Objects
//     +-- Plant
//           +-- Reactor                          (events enabled)
//                 +-- Temperature    (Double)    [0..200 C]
//                 +-- Pressure       (Double)    [0..10 bar]
//                 +-- PumpRunning    (Boolean)
//                 +-- TemperatureHighAlarm        [AlarmCondition]
//                 +-- PressureLimitAlarm          [ExclusiveLimitAlarm]
//                 +-- PumpFailureAlarm            [DiscreteAlarm]
//                 +-- MaintenanceDialog           [DialogCondition]
//
// Simulation behaviour:
//   * Temperature follows a sine wave (10..90 C) with random noise
//   * Pressure is derived from temperature with random noise
//   * Pump stops briefly every 20 seconds (simulates intermittent failure)
//   * Maintenance dialog appears every 30 seconds, auto-confirmed after 5s
//
// Alarm thresholds:
//   * TemperatureHighAlarm:  ON when T > 80C, OFF when T < 70C (hysteresis)
//   * PressureLimitAlarm:    High when P > 6 bar, HighHigh when P > 8 bar
//   * PumpFailureAlarm:      ON when pump stops, OFF when pump restarts
//   * MaintenanceDialog:     Activated every 30s, operator responds after 5s
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.core.*;
import com.plccom.opc.ua.server.alarm.*;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.util.*;

public class _21_AlarmConditions {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 21 - Alarm Conditions", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 21: Alarm Conditions    ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Demonstrates all OPC UA alarm types:                        ║");
        System.out.println("║  * AlarmConditionType     - general alarm (ack/confirm)      ║");
        System.out.println("║  * ExclusiveLimitAlarmType - limit levels Low/High/HighHigh  ║");
        System.out.println("║  * DiscreteAlarmType      - boolean state alarm              ║");
        System.out.println("║  * DialogConditionType    - operator response dialog         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // =============================================================================
        // Step 1: Configure and start the server
        // =============================================================================
        UaServerConfiguration config = createConfig();

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            // Accept all client certificates for development
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

            // =============================================================================
            // Step 2: Build the address space
            // =============================================================================
            // Create a Plant/Reactor folder structure. EnableEvents on the Reactor
            // folder so clients can subscribe to alarm events from it.
            UaFolder plant   = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaFolder reactor = server.createFolder(plant, "Reactor", UaRolePermissions.WITHOUT_RESTRICTIONS);
            server.enableEvents(reactor);

            // Process variables that drive the alarm simulation.
            // Temperature and Pressure have EURange and EngineeringUnits properties
            // so clients can display proper units and gauge ranges.
            UaVariable<Double>  temperature = server.createVariable(reactor, "Temperature",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 25.0, false);
            UaVariable<Double>  pressure    = server.createVariable(reactor, "Pressure",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 1.0,  false);
            UaVariable<Boolean> pumpRunning = server.createVariable(reactor, "PumpRunning",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Boolean.class, true, false);

            temperature.setEURange(0, 200);
            temperature.setEngineeringUnits("C");
            pressure.setEURange(0, 10);
            pressure.setEngineeringUnits("bar");

            // =============================================================================
            // Step 3: Create alarms
            // =============================================================================
            UaAlarm tempAlarm = server.createAlarm(reactor, "TemperatureHighAlarm");
            UaLimitAlarm pressLimitAlarm = server.createLimitAlarm(reactor, "PressureLimitAlarm");
            UaDiscreteAlarm pumpAlarm = server.createDiscreteAlarm(reactor, "PumpFailureAlarm");
            UaDialog maintenanceDialog = server.createDialog(reactor, "MaintenanceDialog",
                    "Scheduled maintenance check required. Confirm to proceed.",
                    new String[]{ "Confirm", "Postpone 1h", "Postpone 4h" });

            System.out.println("── Reactor Alarms ──────────────────────────────────────────");
            System.out.println("  [AlarmCondition]      TemperatureHighAlarm");
            System.out.println("                        → active when T > 80C, off when T < 70C");
            System.out.println("  [ExclusiveLimitAlarm] PressureLimitAlarm");
            System.out.println("                        → High > 6 bar, HighHigh > 8 bar, off < 5 bar");
            System.out.println("  [DiscreteAlarm]       PumpFailureAlarm");
            System.out.println("                        → active when pump stops (every 20s)");
            System.out.println("  [DialogCondition]     MaintenanceDialog");
            System.out.println("                        → prompt every 30s, auto-confirm after 5s");
            System.out.println("────────────────────────────────────────────────────────────");
            System.out.println();

            // =============================================================================
            // Step 4: Wait for client connection
            // =============================================================================
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Subscribe to events on the Reactor node to see alarms.      ║");
            System.out.println("║  Use client filter options to filter by alarm type.          ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to start simulation.                            ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 5: Simulation loop
            // =============================================================================
            System.out.println("── Simulation running (CTRL+C to exit) ─────────────────────");
            System.out.println();

            Random rng = new Random();
            boolean tempActive    = false;
            boolean pressHigh     = false;
            boolean pressHH       = false;
            boolean pumpActive    = false;
            boolean dialogActive  = false;
            int tick = 0;

            while (true) {
                tick++;

                // --- Generate process values ---
                double t = 50.0 + Math.sin(System.nanoTime() * 0.0000000001) * 40.0
                         + rng.nextDouble() * 5.0;
                double p = Math.max(0.1, 1.0 + (t - 50.0) / 5.0 + rng.nextDouble() * 0.5);
                boolean pump = (tick % 20 != 0);

                temperature.setValue(Math.round(t * 10.0) / 10.0);
                pressure.setValue(Math.round(p * 100.0) / 100.0);
                pumpRunning.setValue(pump);

                // --- AlarmConditionType: temperature high alarm ---
                if (t > 80.0 && !tempActive) {
                    tempAlarm.activate(
                            String.format("Temperature HIGH: %.1fC", t),
                            EventSeverity.High.getValue());
                    tempActive = true;
                    printAlarmEvent("AlarmCondition ", "ON ", String.format("T=%.1fC (threshold 80C)", t));
                } else if (t < 70.0 && tempActive) {
                    tempAlarm.deactivate(
                            String.format("Temperature normal: %.1fC", t));
                    tempActive = false;
                    printAlarmEvent("AlarmCondition ", "OFF", String.format("T=%.1fC (threshold 70C)", t));
                }

                // --- ExclusiveLimitAlarmType: pressure with escalating levels ---
                if (p > 8.0 && !pressHH) {
                    pressLimitAlarm.activate(LimitAlarmStates.HIGH_HIGH,
                            String.format("Pressure HIGHHIGH: %.2f bar", p),
                            EventSeverity.High.getValue());
                    pressHH = true; pressHigh = true;
                    printAlarmEvent("LimitAlarm  HH ", "ON ", String.format("P=%.2f bar (threshold 8.0)", p));
                } else if (p > 6.0 && !pressHigh && !pressHH) {
                    pressLimitAlarm.activate(LimitAlarmStates.HIGH,
                            String.format("Pressure HIGH: %.2f bar", p),
                            EventSeverity.MediumHigh.getValue());
                    pressHigh = true;
                    printAlarmEvent("LimitAlarm  H  ", "ON ", String.format("P=%.2f bar (threshold 6.0)", p));
                } else if (p < 5.0 && (pressHigh || pressHH)) {
                    pressLimitAlarm.deactivate(
                            String.format("Pressure normal: %.2f bar", p));
                    pressHigh = false; pressHH = false;
                    printAlarmEvent("LimitAlarm     ", "OFF", String.format("P=%.2f bar (threshold 5.0)", p));
                }

                // --- DiscreteAlarmType: pump failure ---
                if (!pump && !pumpActive) {
                    pumpAlarm.activate("Pump stopped unexpectedly",
                            EventSeverity.High.getValue());
                    pumpActive = true;
                    printAlarmEvent("DiscreteAlarm  ", "ON ", "Pump stopped unexpectedly");
                } else if (pump && pumpActive) {
                    pumpAlarm.deactivate("Pump running normally");
                    pumpActive = false;
                    printAlarmEvent("DiscreteAlarm  ", "OFF", "Pump running normally");
                }

                // --- DialogConditionType: maintenance prompt ---
                if (tick % 30 == 0 && !dialogActive) {
                    maintenanceDialog.activate(
                            "Scheduled maintenance check required. Confirm to proceed.",
                            EventSeverity.Medium.getValue());
                    dialogActive = true;
                    printAlarmEvent("Dialog         ", "ON ", "Maintenance check requested");
                } else if (dialogActive && tick % 30 == 5) {
                    maintenanceDialog.respond(0);
                    dialogActive = false;
                    printAlarmEvent("Dialog         ", "OFF", "Operator selected: Confirm");
                }

                if (tick % 5 == 0) {
                    System.out.printf("  [%4d] T=%6.1fC %-8s  P=%5.2f bar %-6s  Pump=%-3s%n",
                            tick,
                            temperature.getValue(),
                            tempActive ? "ALARM!" : "",
                            pressure.getValue(),
                            pressHH ? "HH!" : pressHigh ? "H!" : "",
                            pumpRunning.getValue() ? "ON" : "OFF");
                }

                Thread.sleep(1000);
            }
        }
    }

    /** Prints a formatted alarm state change event to the console. */
    private static void printAlarmEvent(String alarmType, String state, String detail) {
        System.out.printf("         >>> %-15s [%3s] %s%n", alarmType, state, detail);
    }

    // =============================================================================
    // Helper: CreateConfig
    // =============================================================================
    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 21 - Alarm Conditions");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:21");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/alarm-conditions");
        config.setManufacturerName("My Company GmbH");
        config.setProductName("My OPC UA Server");
        config.setSoftwareVersion("1.0.0");
        config.setBuildNumber("42");
        config.setBaseAddresses(java.util.Arrays.asList(
                "opc.tcp://localhost:48410",
                "opc.https://localhost:48411"));
        config.setSecurityModes(UaServer.getRecommendedSecurityModes());
        config.setUserTokenPolicies(java.util.Arrays.asList(UserTokenPolicy.ANONYMOUS));
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

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
    // Helper: createConfig
    // =============================================================================
    // Returns the server configuration. All available options are listed here
    // with a description and the default value. Adjust to your needs.
    private static UaServerConfiguration createConfig() throws Exception {
        UaServerConfiguration config = new UaServerConfiguration();

        // ── Application Identity ──────────────────────────────────────────────
        // ApplicationName: human-readable name shown to connecting clients
        //   and embedded in the server certificate.
        config.setApplicationName("PLCcom Workshop 21 - Alarm Conditions");

        // ApplicationUri: globally unique identifier for this server instance.
        //   Must match the URI in the server certificate.
        //   Recommended format: urn:<host>:<company>:<product>
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:21");

        // ProductUri: URI identifying the software product (not the instance).
        //   Typically a URL pointing to the product page.
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");

        // NamespaceUri: URI for this server's application address space (ns=2).
        //   Use a stable URI based on your company domain.
        //   Default: null (auto-generated as ApplicationUri + "/nodes").
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/alarm-conditions");

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
        certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_21",
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
// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 31: Historical Access
//
// OPC UA Historical Access (Part 11) allows clients to read past values
// of variables using the HistoryRead service.
//
// This is useful for:
//   * Trend displays in HMI/SCADA systems
//   * Data analysis and reporting
//   * Audit trails and compliance logging
//
// How it works:
//   1. Call enableHistory() to start recording values for a variable
//   2. Call recordHistoryValue() each time you want to store a value
//   3. Clients use HistoryRead to retrieve values for a time range
//
// The SDK stores history in memory (in-process).
// For production use, you would typically store history in a database.
//
// What you will learn:
//   * How to enable history recording on variables
//   * How to record values with timestamps
//   * How clients read historical data
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.util.Random;

public class _31_HistoricalAccess {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 31 - Historical Access", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 31: Historical Access   ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║  * Enabling history on variables (Historizing = true)        ║");
        System.out.println("║  * Recording values every second                             ║");
        System.out.println("║  * Clients can read history via HistoryRead service          ║");
        System.out.println("║  * In-memory store with max 500 entries per variable         ║");
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

            UaFolder plant  = server.createFolder("Plant",  UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaFolder sensor = server.createFolder(plant, "Sensor", UaRolePermissions.WITHOUT_RESTRICTIONS);

            UaVariable<Double> temperature = server.createVariable(sensor, "Temperature",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 20.0, false);
            UaVariable<Double> humidity    = server.createVariable(sensor, "Humidity",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 50.0, false);

            temperature.setEURange(-40, 120);
            temperature.setEngineeringUnits("C");
            humidity.setEURange(0, 100);
            humidity.setEngineeringUnits("%RH");

            // -- Enable history recording ------------------------------------------
            // enableHistory() sets the Historizing attribute to true on the variable.
            // maxEntries limits the in-memory buffer — oldest entries are discarded
            // when the buffer is full (circular buffer behaviour).
            server.enableHistory(temperature, 500);
            server.enableHistory(humidity,    500);

            System.out.println("  Variables with history enabled:");
            System.out.println("    Temperature: Historizing=true, max 500 entries");
            System.out.println("    Humidity:    Historizing=true, max 500 entries");
            System.out.println();

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  To view history:                                            ║");
            System.out.println("║  1. Press ENTER to start recording values                    ║");
            System.out.println("║  2. Wait 30+ seconds to accumulate some history              ║");
            System.out.println("║  3. In the client, right-click Temperature -> History        ║");
            System.out.println("║     or add it to a History Trend View                        ║");
            System.out.println("║  4. Set a time range and read the historical values          ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to start recording.                             ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Recording history every second... (CTRL+C to exit)");
            Random rng = new Random();
            long cycle = 0;

            while (true) {
                cycle++;
                java.util.Date now = new java.util.Date();

                // Simulate sinusoidal sensor values with some noise
                double t = 20.0 + Math.sin(cycle * 0.1) * 10.0 + rng.nextDouble() * 2.0;
                double h = 50.0 + Math.cos(cycle * 0.08) * 20.0 + rng.nextDouble() * 3.0;
                temperature.setValue(Math.round(t * 10.0) / 10.0);
                humidity.setValue(Math.round(h * 10.0) / 10.0);

                // recordHistoryValue() stores the current value with the given timestamp.
                // Always use the same timestamp for the variable update and the history
                // record to ensure consistency between current value and history.
                server.recordHistoryValue(temperature, now);
                server.recordHistoryValue(humidity,    now);

                int histCount = server.getHistory(temperature.getNodeId()).size();
                System.out.printf("  Cycle=%d  T=%.1fC  H=%.1f%%RH  History=%d entries%n",
                        cycle, temperature.getValue(), humidity.getValue(), histCount);

                Thread.sleep(1000);
            }
        }
    }

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 31 - Historical Access");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:31");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/historical-access");
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
        config.setMaxSessionCount(100);
        config.setShutdownDelay(5);
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

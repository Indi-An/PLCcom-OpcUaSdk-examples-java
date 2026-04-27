// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 32: Historical Update
//
// Workshop 31 demonstrated reading historical data. This workshop extends
// the server to also accept HistoryUpdate requests from clients:
//   Insert       - add a new value at a specific timestamp
//   Update       - insert or replace (upsert)
//   Replace      - replace an existing value (fails if not exists)
//   Remove       - remove a value by timestamp
//   DeleteRaw    - delete all values in a time range
//   DeleteAtTime - delete values at specific timestamps
//
// The server uses the same in-memory history store as Workshop 31.
// Clients can use the PLCcom Client SDK methods or any OPC UA compliant client.
//
// What you will learn:
//   * How enableHistory() automatically enables HistoryWrite access
//   * How clients can insert, update, replace and delete history values
//   * How the server validates operations (BadEntryExists, BadNoEntryExists)
//   * How to verify history changes by reading back
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.util.Random;

public class _32_HistoricalUpdate {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 32 - Historical Update", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 32: Historical Update   ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║  * History recording with read AND write access              ║");
        System.out.println("║  * Clients can Insert, Update, Replace, Remove values        ║");
        System.out.println("║  * Clients can DeleteRaw (by range) and DeleteAtTime         ║");
        System.out.println("║  * Server validates each operation and returns StatusCodes   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        UaServerConfiguration config = createConfig();
        printConfig(config);

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            server.addCertificateValidationListener(e -> e.setAccept(true));

            // Log all history update operations from clients
            server.addHistoryUpdateListener(item -> {
                String detail;
                if (item.getOperation() == UaServer.UaHistoryUpdatedItem.Operation.DELETE_AT_TIME
                        && item.getValue() instanceof Integer) {
                    detail = "deleted " + item.getValue() + " entries";
                } else {
                    String val = item.getValue() != null ? item.getValue().toString() : "(deleted)";
                    String ts  = item.getTimestamp() != null
                            ? new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(item.getTimestamp()) : "";
                    detail = ts + "  value=" + val;
                }
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

            UaFolder plant  = server.createFolder("Plant",  UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaFolder sensor = server.createFolder(plant, "Sensor", UaRolePermissions.WITHOUT_RESTRICTIONS);

            UaVariable<Double> temperature = server.createVariable(sensor, "Temperature",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 20.0, false);
            temperature.setEURange(-40, 120);
            temperature.setEngineeringUnits("C", "Degrees Celsius");

            UaVariable<Double> pressure = server.createVariable(sensor, "Pressure",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 1.0, false);
            pressure.setEURange(0, 10);
            pressure.setEngineeringUnits("bar", "Bar");

            // EnableHistory sets Historizing=true AND AccessLevel includes
            // HistoryRead + HistoryWrite. Clients can both read AND modify history.
            server.enableHistory(temperature, 500);
            server.enableHistory(pressure,    500);

            System.out.println("  Variables with history enabled (read + write):");
            System.out.println("    Temperature: Historizing=true, HistoryRead + HistoryWrite");
            System.out.println("    Pressure:    Historizing=true, HistoryRead + HistoryWrite");
            System.out.println();

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  The server records values every second. Clients can:        ║");
            System.out.println("║  * Read history (HistoryRead / ReadRaw)                      ║");
            System.out.println("║  * Insert new values at specific timestamps                  ║");
            System.out.println("║  * Update (upsert) existing values                           ║");
            System.out.println("║  * Replace existing values                                   ║");
            System.out.println("║  * Remove values by timestamp                                ║");
            System.out.println("║  * Delete all values in a time range (DeleteRaw)             ║");
            System.out.println("║  * Delete values at specific timestamps (DeleteAtTime)       ║");
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

                double t = 20.0 + Math.sin(cycle * 0.1) * 10.0 + rng.nextDouble() * 2.0;
                double p = 1.0  + Math.cos(cycle * 0.08) * 0.5  + rng.nextDouble() * 0.2;
                temperature.setValue(Math.round(t * 10.0) / 10.0);
                pressure.setValue(Math.round(p * 100.0) / 100.0);

                server.recordHistoryValue(temperature, now);
                server.recordHistoryValue(pressure,    now);

                int histCount = server.getHistory(temperature.getNodeId()).size();
                System.out.printf("  Cycle=%d  T=%.1fC  P=%.2fbar  History=%d entries%n",
                        cycle, temperature.getValue(), pressure.getValue(), histCount);

                Thread.sleep(1000);
            }
        }
    }

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 32 - Historical Update");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:32");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/historical-update");
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

// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 34: Custom History Store
//
// By default the SDK keeps all historical values in RAM (in-memory store).
// This works well for testing but is not suitable for production:
//   * History is lost when the server restarts
//   * Memory grows with every recorded value
//   * No integration with existing data infrastructure
//
// The SDK solves this with the UaHistoryStore interface.
// UaHistoryStore is the extension point that lets YOU decide where history
// data is stored. You implement the interface once and pass it to
// enableHistory() — the SDK calls it automatically whenever values are
// recorded or clients request history.
//
// Typical back-ends you can connect via UaHistoryStore:
//   * Relational databases  (SQL Server, PostgreSQL, SQLite, MySQL, ...)
//   * Time-series databases (InfluxDB, TimescaleDB, Prometheus, ...)
//   * Cloud storage         (Azure Blob, AWS S3, ...)
//   * Message brokers       (Kafka, MQTT, ...)
//   * Custom binary files, CSV, Parquet, or any proprietary format
//
// This workshop demonstrates the pattern using CSV files as the back-end.
// CSV is chosen because it requires no external dependencies and is easy
// to inspect - NOT because it is recommended for production.
// Replace CsvHistoryStore with your own implementation for real use.
//
// What you will learn:
//   * How to implement UaHistoryStore for any storage back-end
//   * How to pass a custom store to enableHistory()
//   * How history survives a server restart
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.core.PerformUpdateType;
import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class _34_CustomHistoryStore {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 34 - Custom History Store", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 34:                     ║");
        System.out.println("║                              Custom History Store            ║");
        System.out.println("║                                                              ║");
        System.out.println("║  UaHistoryStore lets you connect ANY storage back-end:       ║");
        System.out.println("║    SQL Server, PostgreSQL, SQLite, InfluxDB, TimescaleDB,    ║");
        System.out.println("║    Azure Blob, AWS S3, Kafka, custom files, and more.        ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This workshop uses CSV files to demonstrate the pattern.    ║");
        System.out.println("║  Replace CsvHistoryStore with your own implementation.       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        UaServerConfiguration config = createConfig();
        printConfig(config);

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            server.addCertificateValidationListener(e -> e.setAccept(true));

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
            UaVariable<Double> humidity    = server.createVariable(sensor, "Humidity",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 50.0, false);

            temperature.setEURange(-40, 120);
            temperature.setEngineeringUnits("C");
            humidity.setEURange(0, 100);
            humidity.setEngineeringUnits("%RH");

            // -- Register the custom history store ---------------------------------
            // Pass a CsvHistoryStore instance to enableHistory().
            // The SDK calls store.append() on every recordHistoryValue() call
            // and store.read() when a client requests historical data.
            CsvHistoryStore store = new CsvHistoryStore("./history");
            server.enableHistory(temperature, 500, store);
            server.enableHistory(humidity,    500, store);

            System.out.println("  History store: CsvHistoryStore -> ./history/");
            System.out.println("  Variables with history enabled:");
            System.out.println("    Temperature: CSV file, max 500 entries");
            System.out.println("    Humidity:    CSV file, max 500 entries");
            System.out.println();

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  History is written to CSV files in ./history/               ║");
            System.out.println("║  Restart the server - history will still be available!       ║");
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
                double h = 50.0 + Math.cos(cycle * 0.08) * 20.0 + rng.nextDouble() * 3.0;
                temperature.setValue(Math.round(t * 10.0) / 10.0);
                humidity.setValue(Math.round(h * 10.0) / 10.0);

                server.recordHistoryValue(temperature, now);
                server.recordHistoryValue(humidity,    now);

                int histCount = server.getHistory(temperature.getNodeId()).size();
                System.out.printf("  Cycle=%d  T=%.1fC  H=%.1f%%RH  History=%d entries%n",
                        cycle, temperature.getValue(), humidity.getValue(), histCount);

                Thread.sleep(1000);
            }
        }
    }

    // ==========================================================================
    // CsvHistoryStore — example UaHistoryStore implementation using CSV files
    //
    // This class exists solely to demonstrate HOW to implement UaHistoryStore.
    // CSV is not recommended for production — use a database or time-series store.
    //
    // To connect your own back-end, create a class that implements UaHistoryStore
    // and replace "new CsvHistoryStore(...)" with "new YourStore(...)" above.
    //
    // One CSV file per variable, named by NodeId (e.g. "ns=2_i=3.csv").
    // Format: timestamp (ISO 8601), value, statusCode
    // ==========================================================================

    static class CsvHistoryStore implements UaServer.UaHistoryStore {

        private final String directory;
        private final Object lock = new Object();
        private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        static { ISO.setTimeZone(TimeZone.getTimeZone("UTC")); }

        CsvHistoryStore(String directory) {
            this.directory = directory;
            new File(directory).mkdirs();
        }

        @Override
        public void initialize(NodeId nodeId, int maxEntries) { }

        @Override
        public void append(NodeId nodeId, UaHistoryEntry entry) {
            synchronized (lock) {
                try (PrintWriter pw = new PrintWriter(new FileWriter(filePath(nodeId), true))) {
                    pw.printf("%s,%s,%s%n",
                            ISO.format(entry.getTimestamp()),
                            entry.getValue(),
                            entry.getStatusCode());
                } catch (IOException ignored) {}
            }
        }

        @Override
        public List<UaHistoryEntry> read(NodeId nodeId, Date start, Date end, int maxValues) {
            synchronized (lock) {
                File f = new File(filePath(nodeId));
                if (!f.exists()) return Collections.emptyList();
                List<UaHistoryEntry> result = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        UaHistoryEntry entry = parseLine(line);
                        if (entry == null) continue;
                        if (start != null && entry.getTimestamp().before(start)) continue;
                        if (end   != null && entry.getTimestamp().after(end))    continue;
                        result.add(entry);
                        if (maxValues > 0 && result.size() >= maxValues) break;
                    }
                } catch (IOException ignored) {}
                return result;
            }
        }

        @Override
        public StatusCode insertOrReplace(NodeId nodeId, UaHistoryEntry entry, PerformUpdateType mode) {
            synchronized (lock) {
                List<UaHistoryEntry> all = loadAll(nodeId);
                int idx = -1;
                for (int i = 0; i < all.size(); i++) {
                    if (all.get(i).getTimestamp().equals(entry.getTimestamp())) { idx = i; break; }
                }
                switch (mode) {
                    case Insert:
                        if (idx >= 0) return new StatusCode(com.plccom.opc.ua.core.StatusCodes.Bad_EntryExists);
                        all.add(entry);
                        all.sort(Comparator.comparing(UaHistoryEntry::getTimestamp));
                        saveAll(nodeId, all);
                        return StatusCode.GOOD;
                    case Replace:
                        if (idx < 0) return new StatusCode(com.plccom.opc.ua.core.StatusCodes.Bad_NoEntryExists);
                        all.set(idx, entry); saveAll(nodeId, all); return StatusCode.GOOD;
                    case Update:
                        if (idx >= 0) all.set(idx, entry);
                        else { all.add(entry); all.sort(Comparator.comparing(UaHistoryEntry::getTimestamp)); }
                        saveAll(nodeId, all); return StatusCode.GOOD;
                    case Remove:
                        if (idx < 0) return new StatusCode(com.plccom.opc.ua.core.StatusCodes.Bad_NoEntryExists);
                        all.remove(idx); saveAll(nodeId, all); return StatusCode.GOOD;
                    default:
                        return new StatusCode(com.plccom.opc.ua.core.StatusCodes.Bad_HistoryOperationUnsupported);
                }
            }
        }

        @Override
        public void delete(NodeId nodeId, Date start, Date end) {
            synchronized (lock) {
                List<UaHistoryEntry> all = loadAll(nodeId);
                all.removeIf(e -> !e.getTimestamp().before(start) && !e.getTimestamp().after(end));
                saveAll(nodeId, all);
            }
        }

        @Override
        public List<StatusCode> deleteAt(NodeId nodeId, List<Date> timestamps) {
            List<StatusCode> results = new ArrayList<>();
            synchronized (lock) {
                List<UaHistoryEntry> all = loadAll(nodeId);
                for (Date ts : timestamps) {
                    boolean removed = all.removeIf(e -> e.getTimestamp().equals(ts));
                    results.add(removed ? StatusCode.GOOD
                            : new StatusCode(com.plccom.opc.ua.core.StatusCodes.Bad_NoEntryExists));
                }
                saveAll(nodeId, all);
            }
            return results;
        }

        private String filePath(NodeId nodeId) {
            return directory + File.separator
                    + nodeId.toString().replace(":", "_").replace(";", "_") + ".csv";
        }

        private List<UaHistoryEntry> loadAll(NodeId nodeId) {
            File f = new File(filePath(nodeId));
            if (!f.exists()) return new ArrayList<>();
            List<UaHistoryEntry> result = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    UaHistoryEntry e = parseLine(line);
                    if (e != null) result.add(e);
                }
            } catch (IOException ignored) {}
            return result;
        }

        private void saveAll(NodeId nodeId, List<UaHistoryEntry> entries) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(filePath(nodeId), false))) {
                for (UaHistoryEntry e : entries)
                    pw.printf("%s,%s,%s%n", ISO.format(e.getTimestamp()), e.getValue(), e.getStatusCode());
            } catch (IOException ignored) {}
        }

        private UaHistoryEntry parseLine(String line) {
            if (line == null || line.trim().isEmpty()) return null;
            String[] parts = line.split(",", 3);
            if (parts.length < 2) return null;
            try {
                Date ts = ISO.parse(parts[0]);
                return new UaHistoryEntry(ts, parts[1], StatusCode.GOOD);
            } catch (Exception ignored) { return null; }
        }
    }

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 34 - Custom History Store");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:34");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/custom-history-store");
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

// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 35: Custom Event History Store
//
// Workshop 33 showed how to record historical events using the default
// in-memory store. This workshop shows how to replace that store with
// your own implementation using the UaServer.UaEventHistoryStore interface.
//
// UaEventHistoryStore is the extension point that lets YOU decide where
// event history is stored. You implement the interface once and pass it
// to enableHistoryEvents() — the SDK calls it automatically whenever
// events are recorded or clients request event history via HistoryRead.
//
// Typical back-ends you can connect via UaEventHistoryStore:
//   * Relational databases  (SQL Server, PostgreSQL, SQLite, MySQL, ...)
//   * Time-series databases (InfluxDB, TimescaleDB, ...)
//   * Cloud storage         (Azure Blob, AWS S3, ...)
//   * Message brokers       (Kafka, MQTT, ...)
//   * Custom binary files, CSV, Parquet, or any proprietary format
//
// The interface is intentionally minimal — only three methods:
//   * initialize() - called once when enableHistoryEvents() is invoked
//   * append()     - called by recordHistoryEvent()
//   * read()       - called when a client requests historical events
//
// This workshop demonstrates the pattern using CSV files as the back-end.
// Replace CsvEventHistoryStore with your own implementation for real use.
//
// What you will learn:
//   * How to implement UaEventHistoryStore for any storage back-end
//   * How to pass a custom store to enableHistoryEvents()
//   * How event history survives a server restart
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.*;
import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.alarm.UaAlarm;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class _35_CustomEventHistoryStore {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 35 - Custom Event History Store", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 35:                     ║");
        System.out.println("║                      Custom Event History Store              ║");
        System.out.println("║                                                              ║");
        System.out.println("║  UaEventHistoryStore lets you connect ANY storage back-end:  ║");
        System.out.println("║    SQL Server, PostgreSQL, SQLite, InfluxDB, TimescaleDB,    ║");
        System.out.println("║    Azure Blob, AWS S3, Kafka, custom files, and more.        ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This workshop uses CSV files to demonstrate the pattern.    ║");
        System.out.println("║  Replace CsvEventHistoryStore with your own implementation.  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        UaServerConfiguration config = createConfig();
        printConfig(config);

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            server.addCertificateValidationListener(e -> e.setAccept(true));

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

            server.enableEvents(reactor);

            // -- Register the custom event history store ---------------------------
            // Pass a CsvEventHistoryStore instance to enableHistoryEvents().
            // The SDK calls store.append() on every recordHistoryEvent() call
            // and store.read() when a client requests historical event data.
            CsvEventHistoryStore eventStore = new CsvEventHistoryStore("./event_history");
            server.enableHistoryEvents(reactor, 500, eventStore);

            System.out.println("  Event history store: CsvEventHistoryStore -> ./event_history/");
            System.out.println("  Reactor:");
            System.out.println("    Temperature (0-200 C)");
            System.out.println("    Events: live + history enabled (max 500 entries)");
            System.out.println();

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Event history is written to CSV files in ./event_history/   ║");
            System.out.println("║  Restart the server - event history will still be available! ║");
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

                server.fireEvent(reactor, message, severity);

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
        fields[UaAlarm.FIELD_MESSAGE]     = new Variant(new LocalizedText(message, LocalizedText.NO_LOCALE));
        fields[UaAlarm.FIELD_SEVERITY]    = new Variant(UnsignedShort.valueOf(severity));
        fields[UaAlarm.FIELD_RETAIN]      = new Variant(Boolean.FALSE);
        return fields;
    }

    // ==========================================================================
    // CsvEventHistoryStore — example UaEventHistoryStore using CSV files
    //
    // One CSV file per source node, named by NodeId.
    // Format: time (ISO 8601), sourceName, message, severity
    // ==========================================================================

    static class CsvEventHistoryStore implements UaServer.UaEventHistoryStore {

        private final String directory;
        private final Object lock = new Object();
        private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        static { ISO.setTimeZone(TimeZone.getTimeZone("UTC")); }
        // CSV format: eventIdBase64;timestamp;sourceName;message;severity
        // Semicolon delimiter avoids conflicts with decimal comma in message text (e.g. "56,9C")

        CsvEventHistoryStore(String directory) {
            this.directory = directory;
            new File(directory).mkdirs();
        }

        @Override
        public void initialize(NodeId sourceNodeId, int maxEntries) { }

        @Override
        public void append(NodeId sourceNodeId, Variant[] eventFields) {
            synchronized (lock) {
                Object eidObj = eventFields.length > UaAlarm.FIELD_EVENT_ID
                        && eventFields[UaAlarm.FIELD_EVENT_ID] != null
                        ? eventFields[UaAlarm.FIELD_EVENT_ID].getValue() : null;
                String eventIdB64 = (eidObj instanceof ByteString)
                        ? java.util.Base64.getEncoder().encodeToString(((ByteString) eidObj).getValue())
                        : java.util.Base64.getEncoder().encodeToString(UaAlarm.newEventId());
                String sourceName = eventFields.length > UaAlarm.FIELD_SOURCE_NAME
                        && eventFields[UaAlarm.FIELD_SOURCE_NAME] != null
                        ? String.valueOf(eventFields[UaAlarm.FIELD_SOURCE_NAME].getValue()) : "";
                String message = eventFields.length > UaAlarm.FIELD_MESSAGE
                        && eventFields[UaAlarm.FIELD_MESSAGE] != null
                        && eventFields[UaAlarm.FIELD_MESSAGE].getValue() instanceof LocalizedText
                        ? ((LocalizedText) eventFields[UaAlarm.FIELD_MESSAGE].getValue()).getText() : "";
                Object sev = eventFields.length > UaAlarm.FIELD_SEVERITY
                        && eventFields[UaAlarm.FIELD_SEVERITY] != null
                        ? eventFields[UaAlarm.FIELD_SEVERITY].getValue() : 0;
                try (PrintWriter pw = new PrintWriter(new FileWriter(filePath(sourceNodeId), true))) {
                    pw.printf("%s;%s;%s;%s;%s%n", eventIdB64, ISO.format(new Date()), sourceName, message, sev);
                } catch (IOException ignored) {}
            }
        }

        @Override
        public List<Variant[]> read(NodeId sourceNodeId, Date start, Date end, int maxValues) {
            synchronized (lock) {
                File f = new File(filePath(sourceNodeId));
                if (!f.exists()) return Collections.emptyList();
                List<Variant[]> result = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        Variant[] fields = parseLine(line);
                        if (fields == null) continue;
                        result.add(fields);
                        if (maxValues > 0 && result.size() >= maxValues) break;
                    }
                } catch (IOException ignored) {}
                return result;
            }
        }

        @Override
        public int deleteEvents(NodeId sourceNodeId, List<ByteString> eventIds) {
            synchronized (lock) {
                File f = new File(filePath(sourceNodeId));
                if (!f.exists()) return 0;
                List<String> kept = new ArrayList<>();
                int deleted = 0;
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        Variant[] fields = parseLine(line);
                        if (fields == null) { kept.add(line); continue; }
                        Object stored = fields[UaAlarm.FIELD_EVENT_ID] != null
                                ? fields[UaAlarm.FIELD_EVENT_ID].getValue() : null;
                        boolean match = false;
                        if (stored instanceof ByteString) {
                            for (ByteString eid : eventIds) {
                                if (eid != null && java.util.Arrays.equals(
                                        ((ByteString) stored).getValue(), eid.getValue())) {
                                    match = true; break;
                                }
                            }
                        }
                        if (match) deleted++; else kept.add(line);
                    }
                } catch (IOException ignored) {}
                if (deleted > 0) {
                    try (PrintWriter pw = new PrintWriter(new FileWriter(f, false))) {
                        for (String l : kept) pw.println(l);
                    } catch (IOException ignored) {}
                }
                return deleted;
            }
        }

        private String filePath(NodeId sourceNodeId) {
            return directory + File.separator
                    + sourceNodeId.toString().replace(":", "_").replace(";", "_") + ".csv";
        }

        private Variant[] parseLine(String line) {
            if (line == null || line.trim().isEmpty()) return null;
            String[] parts = line.split(";", 5);
            if (parts.length < 5) return null;
            try {
                byte[] eventId = java.util.Base64.getDecoder().decode(parts[0].trim());
                Date ts = ISO.parse(parts[1].trim());
                Variant[] fields = new Variant[UaAlarm.FIELD_SUPPRESSED + 1];
                for (int i = 0; i < fields.length; i++) fields[i] = new Variant(null);
                fields[UaAlarm.FIELD_EVENT_ID]    = new Variant(ByteString.valueOf(eventId));
                fields[UaAlarm.FIELD_SOURCE_NAME] = new Variant(parts[2]);
                fields[UaAlarm.FIELD_TIME]        = new Variant(DateTime.fromMillis(ts.getTime()));
                fields[UaAlarm.FIELD_MESSAGE]     = new Variant(
                        new LocalizedText(parts[3], LocalizedText.NO_LOCALE));
                fields[UaAlarm.FIELD_SEVERITY]    = new Variant(
                        UnsignedShort.valueOf(Integer.parseInt(parts[4].trim())));
                return fields;
            } catch (Exception ignored) { return null; }
        }
    }

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 35 - Custom Event History Store");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:35");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/custom-event-history");
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

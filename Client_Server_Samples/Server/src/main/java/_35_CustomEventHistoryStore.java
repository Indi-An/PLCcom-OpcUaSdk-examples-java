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

            // Accepts all client certificates automatically - suitable for development.
            // Remove this listener to activate PKI-based validation via the store above.
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
        config.setApplicationName("PLCcom Workshop 35 - Custom Event History Store");

        // ApplicationUri: globally unique identifier for this server instance.
        //   Must match the URI in the server certificate.
        //   Recommended format: urn:<host>:<company>:<product>
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:35");

        // ProductUri: URI identifying the software product (not the instance).
        //   Typically a URL pointing to the product page.
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");

        // NamespaceUri: URI for this server's application address space (ns=2).
        //   Use a stable URI based on your company domain.
        //   Default: null (auto-generated as ApplicationUri + "/nodes").
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/custom-event-history");

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
        // plus one HTTPS cert per opc.https:// hostname derived from the base addresses.
        // load() tries to load all certs from disk; getMissingOrExpired() returns any
        // that are missing or expired so they can be rebuilt individually.
        java.util.List<UaServerCertificate> certs = new java.util.ArrayList<>();
        certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_35",
                config.getApplicationUri(), 720, "Indi.An GmbH",
                UaServerCertificate.CertificateRole.APPLICATION));
        for (String host : UaServerCertificateStore.extractHttpsHostnames(config.getBaseAddresses()))
            certs.add(new UaServerCertificate("./pki", "secretpassword", host,
                    "urn:" + host + ":https", 720, "Indi.An GmbH",
                    UaServerCertificate.CertificateRole.HTTPS));

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
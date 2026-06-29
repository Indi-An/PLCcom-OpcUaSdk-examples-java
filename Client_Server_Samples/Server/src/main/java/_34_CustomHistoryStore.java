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

            // Accepts all client certificates automatically - suitable for development.
            // Remove this listener to activate PKI-based validation via the store above.
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
        config.setApplicationName("PLCcom Workshop 34 - Custom History Store");

        // ApplicationUri: globally unique identifier for this server instance.
        //   Must match the URI in the server certificate.
        //   Recommended format: urn:<host>:<company>:<product>
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:34");

        // ProductUri: URI identifying the software product (not the instance).
        //   Typically a URL pointing to the product page.
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");

        // NamespaceUri: URI for this server's application address space (ns=2).
        //   Use a stable URI based on your company domain.
        //   Default: null (auto-generated as ApplicationUri + "/nodes").
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/custom-history-store");

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
        certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_34",
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
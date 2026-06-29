// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 15: Custom Types
//
// This workshop demonstrates the full range of struct features:
//   Part A — Object Hierarchy (the simple alternative to structs)
//   Part B — Flat Struct (MotorDataType with 3 scalar fields)
//   Part C — Nested Struct (PlantDataType containing MotorDataType)
//   Part D — Struct with Array fields (double[], string[])
//   Part E — Array of Structs (3 motors as MotorDataType[3])
//   Part F — Struct containing an Array-of-Structs field
//   Part G — Struct with a 2D Matrix field (multidimensional array)
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.*;
import com.plccom.opc.ua.core.*;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

public class _15_CustomTypes {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 15 - Custom Types", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 15: Custom Types        ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║    * Object hierarchy (Objects with child Variables)         ║");
        System.out.println("║    * Flat structs (MotorDataType, MachineDataType)           ║");
        System.out.println("║    * Nested structs (PlantDataType contains MotorDataType)   ║");
        System.out.println("║    * Struct with array fields (double[], string[])           ║");
        System.out.println("║    * Array of structs (MotorDataType[3])                     ║");
        System.out.println("║    * Struct with array-of-structs field                      ║");
        System.out.println("║    * Struct with 2D matrix field                             ║");
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

            // ValuesWritten fires AFTER a successful write — the client already received Good.
            // If WriteValidation rejects, this does NOT fire.
            server.addValuesWrittenListener(items -> {
                for (UaServer.UaWrittenItem item : items)
                    System.out.println("  << Written: " + item.getPath() + " = " + item.getValueAsString());
            });

            // WriteValidation — called BEFORE any client write is committed to the address space.
            // All internal checks (AccessLevel, DataType, Permissions) have already passed.
            // The handler receives ALL items of the write request as a batch.
            // Set item.setStatusCode() to any Bad_* value to reject that specific item.
            // If not handled or StatusCode remains Good, the write proceeds normally.
            //
            // You can also MODIFY the value before it is written by calling item.setValue().
            // The modified value is then stored in the address space instead of the original.
            //
            // !! IMPORTANT — PERFORMANCE WARNING !!
            // This handler runs synchronously on the server's write thread.
            // Any blocking operation (device I/O, database, slow network) will stall
            // the entire write request and can block other clients as well.
            //
            // If you need to forward the value to a device, prefer one of these patterns:
            //   a) Accept immediately (Good) and forward asynchronously via CompletableFuture or a queue.
            //      The OPC UA client gets a fast response; the device update happens in the background.
            //   b) If you must wait for the device, always use a short timeout (e.g. 500 ms)
            //      and return BadTimeout or BadNoCommunication if the device does not respond in time.
            //
            // Never block indefinitely inside this handler.
            server.addWriteValidationListener(items -> {
                for (UaServer.UaWriteValidationItem item : items) {
                    // Example: forward to device, reject on failure
                    // boolean ok = plc.writeValue(item.getPath(), item.getValue());
                    // if (!ok) item.setStatusCode(new StatusCode(StatusCodes.Bad_NoCommunication));
                    item.setStatusCode(StatusCode.GOOD);
                    System.out.println("  >> WriteValidation: " + item.getPath() + " = " + item.getValue());
                }
            });

            server.addSessionListener(new UaServer.UaSessionListener() {
                @Override
                public void onSessionCreated(UaServer.UaSessionInfo s) {
                    String name = s.getSessionName() != null ? s.getSessionName() : "(unnamed)";
                    System.out.println("  >> Client connected:    \"" + name + "\"");
                }
                @Override
                public void onSessionClosed(UaServer.UaSessionInfo s) {
                    String name = s.getSessionName() != null ? s.getSessionName() : "(unnamed)";
                    System.out.println("  << Client disconnected: \"" + name + "\"");
                }
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

            // =============================================================================
            // Part A: Object Hierarchy
            // =============================================================================
            System.out.println("── Part A: Object Hierarchy ─────────────────────────────────");

            UaFolder hierarchy = server.createFolder("Hierarchy", UaRolePermissions.WITHOUT_RESTRICTIONS);

            NodeId motorTypeId   = server.createObjectType("MotorType");
            NodeId bearingTypeId = server.createObjectType("BearingType");
            NodeId machineTypeId = server.createObjectType("MachineType");

            UaObject machine = server.createObject(hierarchy, "CNC_Machine_01", UaRolePermissions.WITHOUT_RESTRICTIONS, machineTypeId);

            UaObject motor = server.createObject(machine.getNodeId(), "MainMotor", UaRolePermissions.WITHOUT_RESTRICTIONS, motorTypeId);
            server.createVariable(motor, "Speed",       UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class,  1500.0, false);
            server.createVariable(motor, "Temperature", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class,  45.0,   false);
            server.createVariable(motor, "Running",     UaRolePermissions.WITHOUT_RESTRICTIONS, Boolean.class, true,   false);

            UaObject bearing = server.createObject(machine.getNodeId(), "MainBearing", UaRolePermissions.WITHOUT_RESTRICTIONS, bearingTypeId);
            server.createVariable(bearing, "Temperature", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 38.0, false);
            server.createVariable(bearing, "Vibration",   UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 0.5,  false);

            server.createVariable(machine, "State",      UaRolePermissions.WITHOUT_RESTRICTIONS, String.class, "Running", false);
            server.createVariable(machine, "CycleCount", UaRolePermissions.WITHOUT_RESTRICTIONS, Long.class,   0L,       false);

            System.out.println("  CNC_Machine_01");
            System.out.println("    MainMotor    (MotorType):   Speed=1500, Temp=45, Running=true");
            System.out.println("    MainBearing  (BearingType): Temp=38, Vibration=0.5");
            System.out.println("    State=Running, CycleCount=0");
            System.out.println();

            // =============================================================================
            // Part B: Flat Structs
            // =============================================================================
            System.out.println("── Part B: Flat Structs ─────────────────────────────────────");

            UaFolder structFolder = server.createFolder("StructData", UaRolePermissions.WITHOUT_RESTRICTIONS);

            NodeId motorDataTypeId = server.createStructDataType("MotorDataType", new StructureField[]{
                    UaServer.structField("Speed",       Identifiers.Double),
                    UaServer.structField("Temperature", Identifiers.Double),
                    UaServer.structField("Running",     Identifiers.Boolean)
            });

            NodeId machineDataTypeId = server.createStructDataType("MachineDataType", new StructureField[]{
                    UaServer.structField("State",      Identifiers.String),
                    UaServer.structField("CycleCount", Identifiers.Int64),
                    UaServer.structField("MotorSpeed", Identifiers.Double)
            });

            UaStructVariable motorStruct = server.createStructVariable(structFolder, "Motor_Struct", motorDataTypeId);
            motorStruct.setField("Speed",       1500.0);
            motorStruct.setField("Temperature", 45.0);
            motorStruct.setField("Running",     true);

            UaStructVariable machineStruct = server.createStructVariable(structFolder, "Machine_Struct", machineDataTypeId);
            machineStruct.setField("State",      "Running");
            machineStruct.setField("CycleCount", 0L);
            machineStruct.setField("MotorSpeed", 1500.0);

            System.out.println("  MotorDataType     " + motorDataTypeId);
            System.out.println("  MachineDataType   " + machineDataTypeId);
            System.out.println("  Motor_Struct      " + motorStruct.getPath());
            System.out.println("  Machine_Struct    " + machineStruct.getPath());
            System.out.println();

            // =============================================================================
            // Part C: Nested Struct
            // =============================================================================
            System.out.println("── Part C: Nested Struct ────────────────────────────────────");

            NodeId plantDataTypeId = server.createStructDataType("PlantDataType", new StructureField[]{
                    UaServer.structField("PlantName",       Identifiers.String),
                    UaServer.structField("ProductionCount", Identifiers.Int32),
                    UaServer.structField("Motor",           motorDataTypeId),
                    UaServer.structField("Machine",         machineDataTypeId)
            });

            UaStructVariable plantStruct = server.createStructVariable(structFolder, "Plant_Struct", plantDataTypeId);
            plantStruct.setField("PlantName",       "Factory_01");
            plantStruct.setField("ProductionCount", 42);

            plantStruct.setField("Motor.Speed",       2200.0);
            plantStruct.setField("Motor.Temperature", 55.5);
            plantStruct.setField("Motor.Running",     true);

            plantStruct.setField("Machine.State",      "Producing");
            plantStruct.setField("Machine.CycleCount", 12345L);
            plantStruct.setField("Machine.MotorSpeed", 2200.0);

            System.out.println("  PlantDataType     " + plantDataTypeId);
            System.out.println("  Plant_Struct      " + plantStruct.getPath());
            System.out.println("    PlantName       = Factory_01");
            System.out.println("    Motor.Speed     = 2200");
            System.out.println("    Machine.State   = Producing");
            System.out.println();

            // =============================================================================
            // Part D: Struct with Array fields
            // =============================================================================
            System.out.println("── Part D: Struct with Array fields ─────────────────────────");

            NodeId sensorDataTypeId = server.createStructDataType("SensorDataType", new StructureField[]{
                    UaServer.structField("Name",       Identifiers.String),
                    UaServer.structField("Readings",   Identifiers.Double, new int[]{4}),
                    UaServer.structField("Thresholds", Identifiers.Double, new int[]{2})
            });

            UaStructVariable sensorStruct = server.createStructVariable(structFolder, "Sensor_Struct", sensorDataTypeId);
            sensorStruct.setField("Name",       "TempSensor_01");
            sensorStruct.setField("Readings",   new Double[]{23.5, 24.1, 22.8, 25.0});
            sensorStruct.setField("Thresholds", new Double[]{50.0, 75.0});

            System.out.println("  SensorDataType    " + sensorDataTypeId);
            System.out.println("  Sensor_Struct     " + sensorStruct.getPath());
            System.out.println("    Readings   = [23.5, 24.1, 22.8, 25.0]");
            System.out.println("    Thresholds = [50.0, 75.0]");
            System.out.println();

            // =============================================================================
            // Part E: Array of Structs
            // =============================================================================
            System.out.println("── Part E: Array of Structs ─────────────────────────────────");

            UaStructArrayVariable motorArray = server.createStructArrayVariable(
                    structFolder, "Motor_Array", motorDataTypeId, 3);

            motorArray.get(0).setField("Speed",       1000.0);
            motorArray.get(0).setField("Temperature", 40.0);
            motorArray.get(0).setField("Running",     true);

            motorArray.get(1).setField("Speed",       1500.0);
            motorArray.get(1).setField("Temperature", 55.0);
            motorArray.get(1).setField("Running",     true);

            motorArray.get(2).setField("Speed",       0.0);
            motorArray.get(2).setField("Temperature", 22.0);
            motorArray.get(2).setField("Running",     false);

            System.out.println("  Motor_Array       " + motorArray.getPath());
            System.out.println("    [0]: Speed=1000, Temp=40,  Running=true");
            System.out.println("    [1]: Speed=1500, Temp=55,  Running=true");
            System.out.println("    [2]: Speed=0,    Temp=22,  Running=false");
            System.out.println();

            // =============================================================================
            // Part F: Struct with Array-of-Structs field
            // =============================================================================
            System.out.println("── Part F: Struct with Array-of-Structs field ───────────────");

            NodeId factoryDataTypeId = server.createStructDataType("FactoryDataType", new StructureField[]{
                    UaServer.structField("FactoryName", Identifiers.String),
                    UaServer.structField("Motors",      motorDataTypeId, new int[]{2})
            });

            UaStructVariable factoryStruct = server.createStructVariable(structFolder, "Factory_Struct", factoryDataTypeId);
            factoryStruct.setField("FactoryName", "MainFactory");

            factoryStruct.setField("Motors.[0].Speed",       1000.0);
            factoryStruct.setField("Motors.[0].Temperature", 40.0);
            factoryStruct.setField("Motors.[0].Running",     true);

            factoryStruct.setField("Motors.[1].Speed",       2000.0);
            factoryStruct.setField("Motors.[1].Temperature", 60.0);
            factoryStruct.setField("Motors.[1].Running",     false);

            System.out.println("  FactoryDataType   " + factoryDataTypeId);
            System.out.println("  Factory_Struct    " + factoryStruct.getPath());
            System.out.println("    Motors[0]: Speed=1000, Temp=40, Running=true");
            System.out.println("    Motors[1]: Speed=2000, Temp=60, Running=false");
            System.out.println();

            // =============================================================================
            // Part G: Struct with 2D Matrix field
            // =============================================================================
            System.out.println("── Part G: Struct with 2D Matrix field ──────────────────────");

            NodeId gridDataTypeId = server.createStructDataType("GridDataType", new StructureField[]{
                    UaServer.structField("Label",  Identifiers.String),
                    UaServer.structField("Matrix", Identifiers.Double, new int[]{2, 3})
            });

            UaStructVariable gridStruct = server.createStructVariable(structFolder, "Grid_Struct", gridDataTypeId);
            gridStruct.setField("Label", "HeatMap_01");

            // Set individual matrix cells
            gridStruct.setField("Matrix.[0][0]", 1.0);
            gridStruct.setField("Matrix.[0][1]", 2.0);
            gridStruct.setField("Matrix.[0][2]", 3.0);
            gridStruct.setField("Matrix.[1][0]", 4.0);
            gridStruct.setField("Matrix.[1][1]", 5.0);
            gridStruct.setField("Matrix.[1][2]", 6.0);

            System.out.println("  GridDataType      " + gridDataTypeId);
            System.out.println("  Grid_Struct       " + gridStruct.getPath());
            System.out.println("    Matrix (2x3): [[1,2,3],[4,5,6]]");
            System.out.println();

            // =============================================================================
            // Step 2: Run the server
            // =============================================================================
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Try:                                                        ║");
            System.out.println("║  * Browse Hierarchy -> CNC_Machine_01 (Object hierarchy)     ║");
            System.out.println("║  * Browse StructData -> Motor_Struct (flat struct)           ║");
            System.out.println("║  * Browse StructData -> Plant_Struct (nested struct)         ║");
            System.out.println("║  * Browse StructData -> Sensor_Struct (array fields)         ║");
            System.out.println("║  * Browse StructData -> Motor_Array (array of structs)       ║");
            System.out.println("║  * Browse StructData -> Factory_Struct (array-of-structs)    ║");
            System.out.println("║  * Browse StructData -> Grid_Struct (2D matrix)              ║");
            System.out.println("║  * Write Motor_Struct/Speed = 2000 and check the value       ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to exit.                                        ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Server stopped.");
        }
        PLCcomConsole.close();
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
        config.setApplicationName("PLCcom Workshop 15 - Custom Types");

        // ApplicationUri: globally unique identifier for this server instance.
        //   Must match the URI in the server certificate.
        //   Recommended format: urn:<host>:<company>:<product>
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:15");

        // ProductUri: URI identifying the software product (not the instance).
        //   Typically a URL pointing to the product page.
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");

        // NamespaceUri: URI for this server's application address space (ns=2).
        //   Use a stable URI based on your company domain.
        //   Default: null (auto-generated as ApplicationUri + "/nodes").
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/custom-types");

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
        certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_15",
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
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

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            server.addCertificateValidationListener(e -> e.setAccept(true));

            server.addValuesWrittenListener(items -> {
                for (UaServer.UaWrittenItem item : items)
                    System.out.println("  << OPC Write: " + item.getPath()
                            + " (" + item.getNodeId() + ") = " + item.getValueAsString());
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

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 15 - Custom Types");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:15");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/custom-types");
        config.setManufacturerName("My Company GmbH");
        config.setProductName("My OPC UA Server");
        config.setSoftwareVersion("1.0.0");
        config.setBuildNumber("42");
        config.setBaseAddresses(java.util.Arrays.asList(
                "opc.tcp://localhost:48410",
                "opc.https://localhost:48411"));
        config.setSecurityModes(UaServer.getRecommendedSecurityModes());
        config.setUserTokenPolicies(java.util.Arrays.asList(
                UserTokenPolicy.ANONYMOUS));
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

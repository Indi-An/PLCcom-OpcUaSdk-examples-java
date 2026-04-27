// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 19: Advanced Server
//
// A realistic OPC UA server that combines every Data Access feature
// demonstrated in Workshops 11-17 into a single, production-grade application.
//
// This server models a small factory with two CNC machines. It demonstrates
// how all the individual features work together in a real-world scenario.
//
// Architecture:
//   * Company namespace (ns=3) for reusable ObjectTypes and StructTypes
//   * Application namespace (ns=2) for all instance nodes
//   * Anonymous access with full read/write permissions
//   * Certificate validation with auto-accept for development
//   * Session tracking with console output
//   * Continuous value push simulating live process data
//
// Address space:
//   Objects
//     +-- Factory
//     |     +-- CNC_Machine_01  (MachineType)
//     |     |     +-- MainMotor  (MotorType)
//     |     |     |     +-- Speed        (Double)  [0..6000 rpm]  ReadOnly
//     |     |     |     +-- Temperature  (Double)  [0..150 degC]  ReadOnly
//     |     |     |     +-- Running      (Boolean)                ReadOnly
//     |     |     +-- State        (String)                       ReadOnly
//     |     |     +-- CycleCount   (Int64)                        ReadOnly
//     |     |     +-- SerialNumber (String)                       ReadOnly
//     |     |     +-- Setpoints    (Double[4])  exposeElements    Writable
//     |     |     +-- Reset        (Method)
//     |     |
//     |     +-- CNC_Machine_02  (MachineType)
//     |     |     +-- (same structure as Machine_01)
//     |     |
//     |     +-- FactoryStatus  (FactoryStatusType - Struct)
//     |     |     +-- PlantName       (String)
//     |     |     +-- MachinesOnline  (Int32)
//     |     |     +-- TotalCycles     (Int64)
//     |     |
//     |     +-- EnvironmentData
//     |           +-- AmbientTemp     (Double)  [0..50 degC]
//     |           +-- Humidity        (Double)  [0..100 %]
//     |           +-- Readings        (Double[6])  exposeElements  ReadOnly
//     |
//     +-- Parameters
//           +-- MaxSpeed       (Double)  OnWrite validates 0..6000
//           +-- EmergencyStop  (Boolean) OnWrite logs to console
//           +-- BatchSize      (Int32)   OnWrite validates 1..1000
//           +-- MaxLinearSpeed (Double)  OnRead computes from MaxSpeed
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.*;
import com.plccom.opc.ua.core.*;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.util.*;

public class _19_AdvancedServer {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 19 - Advanced Server", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 19: Advanced Server     ║");
        System.out.println("║                                                              ║");
        System.out.println("║  A production-grade OPC UA server combining:                 ║");
        System.out.println("║    * Multiple namespaces (Company types + Application)       ║");
        System.out.println("║    * ObjectTypes with typed instances                        ║");
        System.out.println("║    * Scalar variables, arrays, exposeElements                ║");
        System.out.println("║    * Properties (EURange, EngineeringUnits)                  ║");
        System.out.println("║    * Structured DataTypes (Structs)                          ║");
        System.out.println("║    * Methods with input/output arguments                     ║");
        System.out.println("║    * OnRead/OnWrite callbacks with validation                ║");
        System.out.println("║    * Session tracking and certificate validation             ║");
        System.out.println("║    * Continuous value push (simulated process data)          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        UaServerConfiguration config = createConfig();

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            server.addCertificateValidationListener(e -> e.setAccept(true));

            server.addSessionListener(new UaServer.UaSessionListener() {
                @Override
                public void onSessionCreated(UaServer.UaSessionInfo s) {
                    System.out.println("  >> Session opened: " + s.getSessionName() + " (" + s.getClientUri() + ")");
                }
                @Override
                public void onSessionClosed(UaServer.UaSessionInfo s) {
                    System.out.println("  >> Session closed: " + s.getSessionName());
                }
            });

            server.addValuesWrittenListener(items -> {
                for (UaServer.UaWrittenItem item : items)
                    System.out.println("  << OPC Write: " + item.getPath()
                            + " (" + item.getNodeId() + ") = " + item.getValueAsString());
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
            // Step 3: Register company namespace
            // =============================================================================
            int nsCompany = server.addNamespace("urn:mycompany:cnc:types");

            System.out.println("  Namespace table:");
            System.out.println("    ns=2  " + config.getNamespaceUri() + " (application)");
            System.out.printf ("    ns=%d  urn:mycompany:cnc:types (company types)%n", nsCompany);
            System.out.println();

            // =============================================================================
            // Step 4: Define company-wide ObjectTypes
            // =============================================================================
            System.out.println("-- Defining ObjectTypes ------------------------------------------");

            NodeId motorTypeId   = server.createObjectType("MotorType",   nsCompany);
            NodeId machineTypeId = server.createObjectType("MachineType", nsCompany);

            System.out.println("  MotorType    " + motorTypeId);
            System.out.println("  MachineType  " + machineTypeId);

            // =============================================================================
            // Step 5: Define StructType for factory status
            // =============================================================================
            NodeId factoryStatusTypeId = server.createStructDataType("FactoryStatusType",
                    new StructureField[]{
                        UaServer.structField("PlantName",      Identifiers.String),
                        UaServer.structField("MachinesOnline", Identifiers.Int32),
                        UaServer.structField("TotalCycles",    Identifiers.Int64)
                    });

            System.out.println("  FactoryStatusType  " + factoryStatusTypeId);
            System.out.println();

            // =============================================================================
            // Step 6: Build the address space
            // =============================================================================
            System.out.println("-- Building address space ----------------------------------------");

            UaFolder factory = server.createFolder("Factory", UaRolePermissions.WITHOUT_RESTRICTIONS);

            // --- CNC Machine 01 ---
            UaVariable<Double>[] m1 = createMachine(server, factory, "CNC_Machine_01", "SN-2025-001",
                    2400.0, 52.0, machineTypeId, motorTypeId);

            // --- CNC Machine 02 ---
            UaVariable<Double>[] m2 = createMachine(server, factory, "CNC_Machine_02", "SN-2025-002",
                    1800.0, 45.0, machineTypeId, motorTypeId);

            System.out.println();

            // --- Factory status struct ---
            UaStructVariable factoryStatus = server.createStructVariable(
                    factory, "FactoryStatus", factoryStatusTypeId);
            factoryStatus.setField("PlantName",      "MainFactory");
            factoryStatus.setField("MachinesOnline", 2);
            factoryStatus.setField("TotalCycles",    0L);

            System.out.println("  Objects.Factory.FactoryStatus");
            System.out.println("    PlantName=MainFactory, MachinesOnline=2");

            // --- Environment data ---
            UaFolder envFolder = server.createFolder(factory, "EnvironmentData", UaRolePermissions.WITHOUT_RESTRICTIONS);

            UaVariable<Double> ambientTemp = server.createVariable(envFolder, "AmbientTemp",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 21.5, false);
            ambientTemp.setEURange(0, 50);
            ambientTemp.setEngineeringUnits("degC", "Degrees Celsius");

            UaVariable<Double> humidity = server.createVariable(envFolder, "Humidity",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 45.0, false);
            humidity.setEURange(0, 100);
            humidity.setEngineeringUnits("%", "Percent relative humidity");

            UaVariable<Double[]> readings = server.createArrayVariable(envFolder, "Readings",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class,
                    new Double[]{ 21.5, 21.3, 21.7, 21.4, 21.6, 21.5 }, true, true);

            System.out.println("  Objects.Factory.EnvironmentData");
            System.out.println("    AmbientTemp=21.5 degC, Humidity=45.0 %");
            System.out.println();

            // =============================================================================
            // Step 7: Writable parameters with validation
            // =============================================================================
            System.out.println("-- Writable parameters with validation ---------------------------");

            UaFolder paramFolder = server.createFolder("Parameters", UaRolePermissions.WITHOUT_RESTRICTIONS);

            UaVariable<Double> maxSpeed = server.createVariable(paramFolder, "MaxSpeed",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 3000.0, false);
            maxSpeed.setEURange(0, 6000);
            maxSpeed.setEngineeringUnits("rpm", "Revolutions per minute");
            maxSpeed.setOnWrite(newValue -> {
                if (newValue < 0 || newValue > 6000) {
                    System.out.println("  !! MaxSpeed rejected: " + newValue + " (must be 0..6000)");
                    return false;
                }
                System.out.println("  >> MaxSpeed accepted: " + newValue);
                return true;
            });

            UaVariable<Boolean> emergencyStop = server.createVariable(paramFolder, "EmergencyStop",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Boolean.class, false, false);
            emergencyStop.setOnWrite(newValue -> {
                if (newValue)
                    System.out.println("  !! EMERGENCY STOP ACTIVATED by client");
                else
                    System.out.println("  >> Emergency stop released");
                return true;
            });

            UaVariable<Integer> batchSize = server.createVariable(paramFolder, "BatchSize",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Integer.class, 100, false);
            batchSize.setOnWrite(newValue -> {
                if (newValue < 1 || newValue > 1000) {
                    System.out.println("  !! BatchSize rejected: " + newValue + " (must be 1..1000)");
                    return false;
                }
                return true;
            });

            UaVariable<Double> maxLinearSpeed = server.createVariable(paramFolder, "MaxLinearSpeed",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 0.0, true);
            maxLinearSpeed.setEngineeringUnits("m/s", "Meters per second");
            maxLinearSpeed.setOnRead(current ->
                    Math.round(maxSpeed.getValue() * 2.0 * Math.PI * 0.1 / 60.0 * 1000.0) / 1000.0);

            System.out.printf("  %-45s OnWrite validates 0..6000%n", "Objects.Parameters.MaxSpeed");
            System.out.printf("  %-45s OnWrite logs to console%n",   "Objects.Parameters.EmergencyStop");
            System.out.printf("  %-45s OnWrite validates 1..1000%n", "Objects.Parameters.BatchSize");
            System.out.printf("  %-45s OnRead computes from MaxSpeed%n", "Objects.Parameters.MaxLinearSpeed");
            System.out.println();

            // =============================================================================
            // Step 8: Run the server
            // =============================================================================
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Connect anonymously - full read/write access                ║");
            System.out.println("║  Try:                                                        ║");
            System.out.println("║  * Browse Factory -> CNC_Machine_01 -> MainMotor             ║");
            System.out.println("║  * Check EURange and EngineeringUnits on Speed               ║");
            System.out.println("║  * Write Setpoints[2] = 999 (writable)                       ║");
            System.out.println("║  * Call CNC_Machine_01/Reset method                          ║");
            System.out.println("║  * Write Parameters/MaxSpeed = 5000 (accepted)               ║");
            System.out.println("║  * Write Parameters/MaxSpeed = 9999 (rejected)               ║");
            System.out.println("║  * Read Parameters/MaxLinearSpeed (computed)                 ║");
            System.out.println("║  * Write Parameters/EmergencyStop = true                     ║");
            System.out.println("║  * Browse FactoryStatus struct fields                        ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to start the simulation loop.                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 9: Simulation loop
            // =============================================================================
            System.out.println("Simulating process data... (CTRL+C to exit)");
            System.out.println();

            Random rng = new Random();

            while (true) {
                boolean eStop = emergencyStop.getValue();

                // Machine 1
                if (!eStop) {
                    m1[0].setValue(Math.round((2200.0 + rng.nextDouble() * 400.0) * 10.0) / 10.0);
                    m1[1].setValue(Math.round((48.0   + rng.nextDouble() * 10.0)  * 10.0) / 10.0);
                }

                // Machine 2
                if (!eStop) {
                    m2[0].setValue(Math.round((1600.0 + rng.nextDouble() * 400.0) * 10.0) / 10.0);
                    m2[1].setValue(Math.round((42.0   + rng.nextDouble() * 8.0)   * 10.0) / 10.0);
                }

                // Cycle counts
                if (!eStop) {
                    Long c1 = (Long) server.getValue("Objects.Factory.CNC_Machine_01.CycleCount");
                    Long c2 = (Long) server.getValue("Objects.Factory.CNC_Machine_02.CycleCount");
                    server.setValue("Objects.Factory.CNC_Machine_01.CycleCount", (c1 != null ? c1 : 0L) + rng.nextInt(4) + 1);
                    server.setValue("Objects.Factory.CNC_Machine_02.CycleCount", (c2 != null ? c2 : 0L) + rng.nextInt(3) + 1);
                }

                // Environment
                ambientTemp.setValue(Math.round((20.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0);
                humidity.setValue(   Math.round((40.0 + rng.nextDouble() * 20.0) * 10.0) / 10.0);
                readings.setValue(new Double[]{
                        Math.round((20.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0,
                        Math.round((20.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0,
                        Math.round((20.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0,
                        Math.round((20.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0,
                        Math.round((20.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0,
                        Math.round((20.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0
                });

                // Factory status struct
                Long c1 = (Long) server.getValue("Objects.Factory.CNC_Machine_01.CycleCount");
                Long c2 = (Long) server.getValue("Objects.Factory.CNC_Machine_02.CycleCount");
                long totalCycles = (c1 != null ? c1 : 0L) + (c2 != null ? c2 : 0L);
                factoryStatus.setField("MachinesOnline", eStop ? 0 : 2);
                factoryStatus.setField("TotalCycles",    totalCycles);

                System.out.printf("\r  M1: %7.1frpm %5.1fC  M2: %7.1frpm %5.1fC  Cycles=%-8d %s",
                        m1[0].getValue(), m1[1].getValue(),
                        m2[0].getValue(), m2[1].getValue(),
                        totalCycles,
                        eStop ? "E-STOP!" : "       ");

                Thread.sleep(1000);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static UaVariable<Double>[] createMachine(
            UaServer server, UaFolder parent, String name, String serial,
            double initialSpeed, double initialTemp,
            NodeId machineTypeId, NodeId motorTypeId) throws Exception {

        UaObject machine = server.createObject(parent, name,
                UaRolePermissions.WITHOUT_RESTRICTIONS, machineTypeId);

        UaObject motor = server.createObject(machine.getNodeId(), "MainMotor",
                UaRolePermissions.WITHOUT_RESTRICTIONS, motorTypeId);

        UaVariable<Double> speed = server.createVariable(motor, "Speed",
                UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, initialSpeed, true);
        speed.setEURange(0, 6000);
        speed.setEngineeringUnits("rpm", "Revolutions per minute");

        UaVariable<Double> temp = server.createVariable(motor, "Temperature",
                UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, initialTemp, true);
        temp.setEURange(0, 150);
        temp.setEngineeringUnits("degC", "Degrees Celsius");

        server.createVariable(motor, "Running",
                UaRolePermissions.WITHOUT_RESTRICTIONS, Boolean.class, true, true);

        UaVariable<String> state = server.createVariable(machine, "State",
                UaRolePermissions.WITHOUT_RESTRICTIONS, String.class, "Running", true);
        server.createVariable(machine, "CycleCount",
                UaRolePermissions.WITHOUT_RESTRICTIONS, Long.class, 0L, true);
        server.createVariable(machine, "SerialNumber",
                UaRolePermissions.WITHOUT_RESTRICTIONS, String.class, serial, true);

        server.createArrayVariable(machine.getNodeId(), "Setpoints",
                UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class,
                new Double[]{ 100.0, 200.0, 300.0, 400.0 }, false, true);

        String machineName = name;
        server.createMethod(machine.getNodeId(), "Reset",
                inputs -> {
                    server.setValue("Objects.Factory." + machineName + ".CycleCount", 0L);
                    state.setValue("Idle");
                    System.out.println("  !! " + machineName + " RESET by client");
                    return new Variant[0];
                }, UaRolePermissions.WITHOUT_RESTRICTIONS);

        System.out.println("  Objects.Factory." + name);
        System.out.printf ("    Motor: Speed=%.1f rpm, Temp=%.1f degC%n", initialSpeed, initialTemp);
        System.out.println("    Serial: " + serial + ", Setpoints: [100, 200, 300, 400]");

        return new UaVariable[]{ speed, temp };
    }

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 19 - Advanced Server");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:19");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/advanced-server");
        config.setManufacturerName("My Company GmbH");
        config.setProductName("CNC Factory Server");
        config.setSoftwareVersion("2.0.0");
        config.setBuildNumber("100");
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
        config.setVendorProductName("CNC Factory Server");
        config.setVendorProductVersion("2.0.0");
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

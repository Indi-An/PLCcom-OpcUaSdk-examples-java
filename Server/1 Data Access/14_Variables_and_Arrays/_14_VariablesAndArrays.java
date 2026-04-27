// MIT License
// Copyright (c) Indi.An GmbH
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 14: Variables and Arrays
//
// This workshop demonstrates the full range of variable features available
// in the PLCcom Server SDK. While Workshop 11 introduced basic variables,
// this example goes deeper into:
//
//   1. All scalar data types supported by OPC UA
//   2. Properties — EURange and EngineeringUnits (metadata for HMI/SCADA)
//   3. OnRead / OnWrite callbacks for custom validation and computed values
//   4. Arrays with exposeElements — each array element as a browsable child
//   5. Read-only variables and write rejection via OnWrite
//
// The address space built here:
//   Objects
//     +-- Scalars
//     |     +-- MyBool       (Boolean)    = true
//     |     +-- MyByte       (Byte)       = 42
//     |     +-- MySByte      (SByte)      = -7
//     |     +-- MyInt16      (Int16)      = -1000
//     |     +-- MyUInt16     (UInt16)     = 5000
//     |     +-- MyInt32      (Int32)      = 100000
//     |     +-- MyUInt32     (UInt32)     = 200000
//     |     +-- MyInt64      (Int64)      = 9876543210
//     |     +-- MyUInt64     (UInt64)     = 1234567890
//     |     +-- MyFloat      (Float)      = 3.14
//     |     +-- MyDouble     (Double)     = 2.71828
//     |     +-- MyString     (String)     = "Hello OPC UA"
//     |     +-- MyDateTime   (DateTime)   = now
//     |     +-- MyGuid       (Guid)       = random
//     |     +-- MyByteString (ByteString) = [0xDE, 0xAD, 0xBE, 0xEF]
//     |
//     +-- Properties
//     |     +-- Temperature  (Double)     = 22.5
//     |     |     +-- EURange            [0 .. 100]
//     |     |     +-- EngineeringUnits   "degC"
//     |     +-- Pressure     (Double)     = 1.013
//     |     |     +-- EURange            [0 .. 10]
//     |     |     +-- EngineeringUnits   "bar"
//     |     +-- Speed        (Double)     = 1500
//     |           +-- EURange            [0 .. 3000]
//     |           +-- EngineeringUnits   "rpm"
//     |
//     +-- Callbacks
//     |     +-- Computed     (Double)     OnRead returns Temperature * 1.8 + 32
//     |     +-- Validated    (Int32)      OnWrite rejects values outside 0..100
//     |     +-- Counter      (Int32)      [ReadOnly] incremented by server
//     |
//     +-- Arrays
//           +-- Temperatures (Double[5])  plain array
//           +-- Setpoints    (Double[4])  exposeElements -> V[0]..V[3]
//           +-- Flags        (Boolean[3]) exposeElements -> V[0]..V[2]
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.*;
import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.UaFolder;
import com.plccom.opc.ua.server.application.UaServerNodes.UaVariable;

import java.util.Random;
import java.util.UUID;

public class _14_VariablesAndArrays {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 14 - Variables and Arrays", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 14: Variables & Arrays  ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║    * All OPC UA scalar data types                            ║");
        System.out.println("║    * EURange and EngineeringUnits properties                 ║");
        System.out.println("║    * OnRead / OnWrite callbacks                              ║");
        System.out.println("║    * Arrays with exposeElements (browsable child nodes)      ║");
        System.out.println("║    * Read-only variables and write validation                ║");
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
                System.out.println("  Press ENTER to exit.");
                System.in.read();
                PLCcomConsole.close();
                return;
            }
            System.out.println("OK");
            for (String addr : config.getBaseAddresses())
                System.out.println("  Endpoint: " + addr);
            System.out.println();

            // =============================================================================
            // Step 2: Scalar data types
            // =============================================================================
            System.out.println("── Part A: Scalar data types ────────────────────────────────");

            UaFolder scalars = server.createFolder("Scalars", UaRolePermissions.WITHOUT_RESTRICTIONS);

            UaVariable<Boolean>         vBool       = server.createVariable(scalars, "MyBool",       UaRolePermissions.WITHOUT_RESTRICTIONS, Boolean.class,         true,                          false);
            UaVariable<UnsignedByte>    vByte       = server.createVariable(scalars, "MyByte",       UaRolePermissions.WITHOUT_RESTRICTIONS, UnsignedByte.class,    UnsignedByte.valueOf(42),      false);
            UaVariable<Byte>            vSByte      = server.createVariable(scalars, "MySByte",      UaRolePermissions.WITHOUT_RESTRICTIONS, Byte.class,            (byte) -7,                     false);
            UaVariable<Short>           vInt16      = server.createVariable(scalars, "MyInt16",      UaRolePermissions.WITHOUT_RESTRICTIONS, Short.class,           (short) -1000,                 false);
            UaVariable<UnsignedShort>   vUInt16     = server.createVariable(scalars, "MyUInt16",     UaRolePermissions.WITHOUT_RESTRICTIONS, UnsignedShort.class,   UnsignedShort.valueOf(5000),   false);
            UaVariable<Integer>         vInt32      = server.createVariable(scalars, "MyInt32",      UaRolePermissions.WITHOUT_RESTRICTIONS, Integer.class,         100000,                        false);
            UaVariable<UnsignedInteger> vUInt32     = server.createVariable(scalars, "MyUInt32",     UaRolePermissions.WITHOUT_RESTRICTIONS, UnsignedInteger.class, UnsignedInteger.valueOf(200000), false);
            UaVariable<Long>            vInt64      = server.createVariable(scalars, "MyInt64",      UaRolePermissions.WITHOUT_RESTRICTIONS, Long.class,            9876543210L,                   false);
            UaVariable<UnsignedLong>    vUInt64     = server.createVariable(scalars, "MyUInt64",     UaRolePermissions.WITHOUT_RESTRICTIONS, UnsignedLong.class,    UnsignedLong.valueOf(1234567890L), false);
            UaVariable<Float>           vFloat      = server.createVariable(scalars, "MyFloat",      UaRolePermissions.WITHOUT_RESTRICTIONS, Float.class,           3.14f,                         false);
            UaVariable<Double>          vDouble     = server.createVariable(scalars, "MyDouble",     UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class,          2.71828,                       false);
            UaVariable<String>          vString     = server.createVariable(scalars, "MyString",     UaRolePermissions.WITHOUT_RESTRICTIONS, String.class,          "Hello OPC UA",                false);
            UaVariable<DateTime>        vDateTime   = server.createVariable(scalars, "MyDateTime",   UaRolePermissions.WITHOUT_RESTRICTIONS, DateTime.class,        DateTime.currentTime(),        false);
            UaVariable<UUID>            vGuid       = server.createVariable(scalars, "MyGuid",       UaRolePermissions.WITHOUT_RESTRICTIONS, UUID.class,            UUID.randomUUID(),             false);
            UaVariable<ByteString>      vByteString = server.createVariable(scalars, "MyByteString", UaRolePermissions.WITHOUT_RESTRICTIONS, ByteString.class,      ByteString.valueOf(new byte[]{(byte)0xDE,(byte)0xAD,(byte)0xBE,(byte)0xEF}), false);

            System.out.printf("  Boolean     %-35s = %s%n",  vBool.getName(),       vBool.getValue());
            System.out.printf("  Byte        %-35s = %s%n",  vByte.getName(),       vByte.getValue());
            System.out.printf("  SByte       %-35s = %s%n",  vSByte.getName(),      vSByte.getValue());
            System.out.printf("  Int16       %-35s = %s%n",  vInt16.getName(),      vInt16.getValue());
            System.out.printf("  UInt16      %-35s = %s%n",  vUInt16.getName(),     vUInt16.getValue());
            System.out.printf("  Int32       %-35s = %s%n",  vInt32.getName(),      vInt32.getValue());
            System.out.printf("  UInt32      %-35s = %s%n",  vUInt32.getName(),     vUInt32.getValue());
            System.out.printf("  Int64       %-35s = %s%n",  vInt64.getName(),      vInt64.getValue());
            System.out.printf("  UInt64      %-35s = %s%n",  vUInt64.getName(),     vUInt64.getValue());
            System.out.printf("  Float       %-35s = %s%n",  vFloat.getName(),      vFloat.getValue());
            System.out.printf("  Double      %-35s = %s%n",  vDouble.getName(),     vDouble.getValue());
            System.out.printf("  String      %-35s = %s%n",  vString.getName(),     vString.getValue());
            System.out.printf("  DateTime    %-35s = %s%n",  vDateTime.getName(),   vDateTime.getValue());
            System.out.printf("  Guid        %-35s = %s%n",  vGuid.getName(),       vGuid.getValue());
            System.out.printf("  ByteString  %-35s = %s%n",  vByteString.getName(), vByteString.getValue());
            System.out.println();

            // =============================================================================
            // Step 3: Properties — EURange and EngineeringUnits
            // =============================================================================
            System.out.println("── Part B: Properties (EURange, EngineeringUnits) ──────────");

            UaFolder props = server.createFolder("Properties", UaRolePermissions.WITHOUT_RESTRICTIONS);

            UaVariable<Double> temperature = server.createVariable(props, "Temperature", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 22.5, false);
            temperature.setEURange(0, 100);
            temperature.setEngineeringUnits("degC", "Degrees Celsius");

            UaVariable<Double> pressure = server.createVariable(props, "Pressure", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 1.013, false);
            pressure.setEURange(0, 10);
            pressure.setEngineeringUnits("bar", "Bar");

            UaVariable<Double> speed = server.createVariable(props, "Speed", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 1500.0, false);
            speed.setEURange(0, 3000);
            speed.setEngineeringUnits("rpm", "Revolutions per minute");

            System.out.printf("  %-40s = %.1f  [0..100 degC]%n", temperature.getName(), temperature.getValue());
            System.out.printf("  %-40s = %.3f  [0..10 bar]%n",   pressure.getName(),    pressure.getValue());
            System.out.printf("  %-40s = %.0f  [0..3000 rpm]%n", speed.getName(),       speed.getValue());
            System.out.println();

            // =============================================================================
            // Step 4: OnRead / OnWrite callbacks
            // =============================================================================
            System.out.println("── Part C: OnRead / OnWrite callbacks ──────────────────────");

            UaFolder callbacks = server.createFolder("Callbacks", UaRolePermissions.WITHOUT_RESTRICTIONS);

            UaVariable<Double> computed = server.createVariable(callbacks, "Computed", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 0.0, true);
            computed.setOnRead(currentValue ->
                    Math.round((temperature.getValue() * 1.8 + 32.0) * 100.0) / 100.0);

            UaVariable<Integer> validated = server.createVariable(callbacks, "Validated", UaRolePermissions.WITHOUT_RESTRICTIONS, Integer.class, 50, false);
            validated.setOnWrite(newValue -> {
                if (newValue < 0 || newValue > 100) {
                    System.out.println("  !! Rejected write: " + newValue + " (must be 0..100)");
                    return false;
                }
                return true;
            });

            UaVariable<Integer> counter = server.createVariable(callbacks, "Counter", UaRolePermissions.WITHOUT_RESTRICTIONS, Integer.class, 0, true);

            System.out.printf("  %-40s OnRead -> Fahrenheit%n",        computed.getName());
            System.out.printf("  %-40s OnWrite -> reject if not 0..100%n", validated.getName());
            System.out.printf("  %-40s [ReadOnly] server-incremented%n",   counter.getName());
            System.out.println();

            // =============================================================================
            // Step 5: Arrays and exposeElements
            // =============================================================================
            System.out.println("── Part D: Arrays and exposeElements ───────────────────────");

            UaFolder arrays = server.createFolder("Arrays", UaRolePermissions.WITHOUT_RESTRICTIONS);

            UaVariable<Double[]> temps = server.createArrayVariable(
                    arrays, "Temperatures", UaRolePermissions.WITHOUT_RESTRICTIONS,
                    Double.class, new Double[]{20.0, 21.5, 22.0, 23.5, 24.0}, false);

            UaVariable<Double[]> setpoints = server.createArrayVariable(
                    arrays, "Setpoints", UaRolePermissions.WITHOUT_RESTRICTIONS,
                    Double.class, new Double[]{100.0, 200.0, 300.0, 400.0}, false, true);

            UaVariable<Boolean[]> flags = server.createArrayVariable(
                    arrays, "Flags", UaRolePermissions.WITHOUT_RESTRICTIONS,
                    Boolean.class, new Boolean[]{true, false, true}, false, true);

            System.out.printf("  %-40s Double[5]  (plain array)%n",    temps.getName());
            System.out.printf("  %-40s Double[4]  (exposeElements)%n", setpoints.getName());
            System.out.printf("  %-40s Bool[3]    (exposeElements)%n", flags.getName());
            System.out.println();

            // =============================================================================
            // Step 6: Run the server
            // =============================================================================
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Try:                                                        ║");
            System.out.println("║  * Browse Scalars - all 15 OPC UA data types                 ║");
            System.out.println("║  * Browse Properties - check EURange and EngineeringUnits    ║");
            System.out.println("║  * Read Callbacks/Computed - shows Fahrenheit conversion     ║");
            System.out.println("║  * Write Callbacks/Validated - try 50 (OK) and 200 (reject)  ║");
            System.out.println("║  * Write Callbacks/Counter - should fail (ReadOnly)          ║");
            System.out.println("║  * Browse Arrays/Setpoints - see V[0]..V[3] child nodes      ║");
            System.out.println("║  * Subscribe to V[1] only - get changes for one element      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to start the value push loop.                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 7: Push value changes
            // =============================================================================
            System.out.println("  Pushing values every second... (CTRL+C or ENTER to exit)");
            System.out.println();

            Random rng = new Random();
            long cycle = 0;
            final boolean[] stop = {false};

            Thread exitThread = new Thread(() -> {
                try { System.in.read(); } catch (Exception ignored) {}
                stop[0] = true;
            });
            exitThread.setDaemon(true);
            exitThread.start();

            while (!stop[0]) {
                cycle++;

                temperature.setValue(Math.round((18.0 + rng.nextDouble() * 12.0) * 100.0) / 100.0);
                pressure.setValue(Math.round((0.8 + rng.nextDouble() * 0.5) * 1000.0) / 1000.0);
                speed.setValue(1200.0 + rng.nextInt(600));
                counter.setValue((int) cycle);

                temps.setValue(new Double[]{
                        Math.round((19.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0,
                        Math.round((20.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0,
                        Math.round((21.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0,
                        Math.round((22.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0,
                        Math.round((23.0 + rng.nextDouble() * 3.0) * 10.0) / 10.0
                });

                setpoints.setValue(new Double[]{
                        100.0 + rng.nextInt(50),
                        200.0 + rng.nextInt(50),
                        300.0 + rng.nextInt(50),
                        400.0 + rng.nextInt(50)
                });

                PLCcomConsole.replaceLastLine(String.format(
                        "  Cycle=%d  Temp=%.1f\u00b0C (%.1f\u00b0F)  P=%.3fbar  Counter=%d",
                        cycle, temperature.getValue(), computed.getValue(),
                        pressure.getValue(), counter.getValue()));

                Thread.sleep(1000);
            }
            System.out.println();
            System.out.println("  Server stopped.");
        }
        PLCcomConsole.close();
    }

    // =============================================================================
    // Helper: createConfig
    // =============================================================================
    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 14 - Variables and Arrays");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:14");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/variables-and-arrays");
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
        config.setHttpsMutualTls(false);
        config.setRegisterWithDiscoveryServer(false);
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

    // =============================================================================
    // Helper: printConfig
    // =============================================================================
    private static void printConfig(UaServerConfiguration config) {
        System.out.println("── Active Server Configuration ──────────────────────────────");
        System.out.println("  ApplicationName  : " + config.getApplicationName());
        System.out.println("  ApplicationUri   : " + config.getApplicationUri());
        System.out.println("  NamespaceUri     : " + (config.getNamespaceUri() != null ? config.getNamespaceUri() : "(default)"));
        System.out.println("  ManufacturerName : " + config.getManufacturerName());
        System.out.println("  ProductName      : " + config.getProductName());
        System.out.println("  SoftwareVersion  : " + config.getSoftwareVersion());
        System.out.println("  BuildNumber      : " + config.getBuildNumber());
        System.out.println();
        System.out.println("  Endpoints:");
        for (String addr : config.getBaseAddresses())
            System.out.println("    " + addr);
        System.out.println();
        System.out.println("  VendorServerInfo:");
        System.out.println("    VendorName           = " + config.getVendorName());
        System.out.println("    VendorProductName    = " + config.getVendorProductName());
        System.out.println("    VendorProductVersion = " + config.getVendorProductVersion());
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println();
    }
}

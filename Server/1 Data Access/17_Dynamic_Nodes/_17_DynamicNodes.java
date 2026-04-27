// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 17: Dynamic Nodes
//
// In most OPC UA servers the address space is static - built once at startup.
// This SDK supports dynamic changes at runtime using the same API as at startup:
//   * createFolder / createVariable / createObject work before AND after start()
//   * removeNode removes a node and all its children at any time
//   * Connected clients see changes immediately on their next browse
//
// This workshop demonstrates step by step — pause between each step to inspect
// the address space with your OPC UA client:
//
//   Step 1 — Initial address space (Plant/Line1/Temperature)
//   Step 2 — Add nodes at runtime (DynamicNodes folder with Counter + Message)
//   Step 3 — Remove a single node (Counter removed, Message stays)
//   Step 4 — Remove an entire subtree (DynamicNodes folder + all children)
//   Step 5 — Path-based lookup (getNodeId, getValue, setValue)
//   Step 6 — Timer-based device discovery (new Device_N every 5 seconds)
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.*;
import com.plccom.opc.ua.core.*;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.util.*;

public class _17_DynamicNodes {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 17 - Dynamic Nodes", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 17: Dynamic Nodes       ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Demonstrates live address space changes at runtime.         ║");
        System.out.println("║  Use an OPC UA client to inspect the address space between   ║");
        System.out.println("║  each step.                                                  ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Endpoint: opc.tcp://localhost:48410                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

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
                    System.out.println("  [SESSION+] " + (s.getSessionName() != null ? s.getSessionName() : "unknown")
                            + " from " + (s.getClientUri() != null ? s.getClientUri() : "unknown"));
                }
                @Override
                public void onSessionClosed(UaServer.UaSessionInfo s) {
                    System.out.println("  [SESSION-] " + (s.getSessionName() != null ? s.getSessionName() : "unknown"));
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
            // Step 1: Initial address space
            // =============================================================================
            UaFolder plant = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaFolder line1 = server.createFolder(plant, "Line1", UaRolePermissions.WITHOUT_RESTRICTIONS);
            server.createVariable(line1, "Temperature", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 22.0, false);

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Step 1/5 — Initial address space                            ║");
            System.out.println("║                                                              ║");
            System.out.println("║  The server is running with a static address space:          ║");
            System.out.println("║    Objects                                                   ║");
            System.out.println("║      └── Plant                                               ║");
            System.out.println("║            └── Line1                                         ║");
            System.out.println("║                  └── Temperature = 22.0                      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Connect your OPC UA client and browse the address space.    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to add new nodes at runtime.                    ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 2: Add nodes at runtime
            // =============================================================================
            // createFolder and createVariable work exactly the same after start() as before.
            // Connected clients see the new nodes immediately on their next browse.
            UaFolder dynFolder  = server.createFolder(plant, "DynamicNodes", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaVariable<Integer> dynCounter = server.createVariable(dynFolder, "Counter", UaRolePermissions.WITHOUT_RESTRICTIONS, Integer.class, 42, false);
            UaVariable<String>  dynMessage = server.createVariable(dynFolder, "Message", UaRolePermissions.WITHOUT_RESTRICTIONS, String.class, "Hello", false);

            System.out.println("  + Created: Objects.Plant.DynamicNodes.Counter = " + dynCounter.getValue());
            System.out.println("  + Created: Objects.Plant.DynamicNodes.Message = " + dynMessage.getValue());
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Step 2/5 — Nodes added at runtime                           ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Two new nodes were added while the server was running:      ║");
            System.out.println("║    Objects                                                   ║");
            System.out.println("║      └── Plant                                               ║");
            System.out.println("║            ├── Line1/Temperature                             ║");
            System.out.println("║            └── DynamicNodes          ← NEW                   ║");
            System.out.println("║                  ├── Counter = 42    ← NEW                   ║");
            System.out.println("║                  └── Message = Hello ← NEW                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Refresh your client browser — the new nodes are visible.    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to remove the Counter node.                     ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 3: Remove a single node
            // =============================================================================
            // removeNode removes exactly the specified node. Sibling nodes are unaffected.
            boolean removed = server.removeNode(dynCounter.getNodeId());
            System.out.println("  - Removed: Objects.Plant.DynamicNodes.Counter  →  " + (removed ? "OK" : "FAILED"));
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Step 3/5 — Single node removed                              ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Counter was removed. Message is still there:                ║");
            System.out.println("║    Objects                                                   ║");
            System.out.println("║      └── Plant                                               ║");
            System.out.println("║            ├── Line1/Temperature                             ║");
            System.out.println("║            └── DynamicNodes                                  ║");
            System.out.println("║                  └── Message = Hello  (Counter is gone)      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Refresh your client — Counter has disappeared.              ║");
            System.out.println("║  Subscriptions on Counter now receive BadNodeIdUnknown.      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to remove the entire DynamicNodes subtree.      ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 4: Remove an entire subtree
            // =============================================================================
            // removeNode on a folder removes the folder AND all its children recursively.
            removed = server.removeNode(dynFolder.getNodeId());
            System.out.println("  - Removed: Objects.Plant.DynamicNodes (including all children)  →  " + (removed ? "OK" : "FAILED"));
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Step 4/5 — Entire subtree removed                           ║");
            System.out.println("║                                                              ║");
            System.out.println("║  DynamicNodes folder and all remaining children are gone:    ║");
            System.out.println("║    Objects                                                   ║");
            System.out.println("║      └── Plant                                               ║");
            System.out.println("║            └── Line1                                         ║");
            System.out.println("║                  └── Temperature = 22.0                      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  The address space is back to its initial state.             ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to demonstrate path-based lookup.               ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            // =============================================================================
            // Step 5: Path-based lookup
            // =============================================================================
            // getNodeId / getValue / setValue let you access nodes by dot-separated path
            // without storing the NodeId or UaVariable wrapper from creation time.
            System.out.println("-- Step 5/5: Path-based lookup -----------------------------------");

            NodeId nodeId = server.getNodeId("Objects.Plant.Line1.Temperature");
            System.out.println("  getNodeId(\"Objects.Plant.Line1.Temperature\") = " + nodeId);

            Object currentVal = server.getValue("Objects.Plant.Line1.Temperature");
            System.out.println("  getValue  = " + currentVal);

            server.setValue("Objects.Plant.Line1.Temperature", 99.9);
            System.out.println("  setValue  → 99.9");
            System.out.println("  getValue  = " + server.getValue("Objects.Plant.Line1.Temperature"));
            System.out.println();

            // =============================================================================
            // Step 6: Timer-based device discovery
            // =============================================================================
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Step 5/5 — Timer-based device discovery                     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Every 5 seconds a new Device_N folder appears under Plant.  ║");
            System.out.println("║  After 5 devices the oldest is removed (sliding window).     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Watch your OPC UA client — devices appear and disappear     ║");
            System.out.println("║  in real time without restarting the server.                 ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to start the simulation (CTRL+C to exit).       ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("Simulating device discovery...");
            System.out.println();

            Random rng = new Random();
            int deviceNumber = 0;
            Queue<NodeId> activeDevices = new LinkedList<>();
            final int MAX_DEVICES = 5;

            while (true) {
                deviceNumber++;
                String deviceName = "Device_" + deviceNumber;

                UaFolder deviceFolder = server.createFolder(plant, deviceName, UaRolePermissions.WITHOUT_RESTRICTIONS);
                UaVariable<Double> devTemp   = server.createVariable(deviceFolder, "Temperature", UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, Math.round((20.0 + rng.nextDouble() * 15.0) * 10.0) / 10.0, false);
                UaVariable<String> devStatus = server.createVariable(deviceFolder, "Status",      UaRolePermissions.WITHOUT_RESTRICTIONS, String.class, "Online", false);

                activeDevices.add(deviceFolder.getNodeId());
                System.out.printf("  + %s: Temp=%.1f  Status=%s%n", deviceName, devTemp.getValue(), devStatus.getValue());

                if (activeDevices.size() > MAX_DEVICES) {
                    NodeId oldest = activeDevices.poll();
                    server.removeNode(oldest);
                    System.out.println("  - Removed Device_" + (deviceNumber - MAX_DEVICES) + " (sliding window, max=" + MAX_DEVICES + ")");
                }

                System.out.println("    Active: " + activeDevices.size() + "/" + MAX_DEVICES);
                Thread.sleep(5000);
            }
        }
    }

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 17 - Dynamic Nodes");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:17");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/dynamic-nodes");
        config.setManufacturerName("My Company GmbH");
        config.setProductName("My OPC UA Server");
        config.setSoftwareVersion("1.0.0");
        config.setBuildNumber("42");
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

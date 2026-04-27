// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 71: Reverse Connect
//
// In standard OPC UA, the CLIENT connects to the SERVER.
// With Reverse Connect, the SERVER connects to the CLIENT.
//
// Why use Reverse Connect?
//   * The server is behind a firewall that blocks incoming connections
//   * The server is in a protected network (OT/ICS) and the client is in IT/cloud
//   * The server has a dynamic IP address
//
// How it works:
//   1. The client opens a listening port (e.g. 48500)
//   2. The server periodically sends a ReverseHello message to the client
//   3. The client uses that connection to establish a normal OPC UA session
//   4. From the application's perspective, the session works exactly the same
//
// This server also keeps its normal endpoint (48410) for direct connections.
//
// What you will learn:
//   * How to add a reverse connection target to the server
//   * How the server periodically attempts to connect to the client
//   * How to use both normal and reverse connect simultaneously
//
// Normal endpoint:  opc.tcp://localhost:48410
// Reverse Connect:  -> opc.tcp://localhost:48500 (server connects to client)
// ==============================================================================

import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.*;
import com.plccom.opc.ua.server.application.UaServerNodes.*;

import java.util.Random;

public class _71_ReverseConnect {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 71 - Reverse Connect", 1000);

        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 71: Reverse Connect     ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║  * Server initiates connection to client (firewall-safe)     ║");
        System.out.println("║  * ReverseHello message flow                                 ║");
        System.out.println("║  * Normal endpoint still available for direct connections    ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Use case: Server behind firewall, client in DMZ/cloud       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        UaServerConfiguration config = createConfig();
        printConfig(config);

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            server.addCertificateValidationListener(e -> {
                System.out.println("  [CERT] Certificate received -> Accepted");
                e.setAccept(true);
            });

            // Log session events to see when the reverse connection is established
            server.addSessionListener(new UaServer.UaSessionListener() {
                @Override
                public void onSessionCreated(UaServer.UaSessionInfo session) {
                    System.out.println("\n  [SESSION+] " + session.getSessionName()
                            + " from " + session.getClientUri());
                }
                @Override
                public void onSessionClosed(UaServer.UaSessionInfo session) {
                    System.out.println("\n  [SESSION-] " + session.getSessionName());
                }
            });

            // Enable logging to see reverse connect activity
            server.addLogListener(e -> System.out.printf("  [%-7s] %s%n",
                    e.getLevel(), e.getMessage()));
            server.setLogLevel(UaLogLevel.Info);

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

            // Create a variable to give the client something to read
            UaFolder plant = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaVariable<Double> temp = server.createVariable(plant, "Temperature",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 22.5, false);
            temp.setEURange(0, 100);
            temp.setEngineeringUnits("C");

            // -- Add Reverse Connection --------------------------------------------
            // addReverseConnection() tells the server to periodically connect to
            // this URL and send a ReverseHello message.
            // The client must be listening on this port for incoming connections.
            // Per OPC UA Part 6 §7.1.3, the connection is automatically
            // re-established when closed.
            String clientUrl = "opc.tcp://localhost:48500";
            server.addReverseConnection(clientUrl);

            System.out.println("  Normal endpoint:    opc.tcp://localhost:48410");
            System.out.println("  Reverse Connect to: " + clientUrl);
            System.out.println();
            System.out.println("  The server will attempt to connect to the client periodically.");
            System.out.println("  Start a reverse-connect-capable client on port 48500 to test.");
            System.out.println("  (See Workshop ReverseConnect_Client for a matching client)");
            System.out.println();

            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running with Reverse Connect enabled.             ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Normal endpoint (direct):                                   ║");
            System.out.println("║    opc.tcp://localhost:48410                                 ║");
            System.out.println("║    -> connect as usual, server is listening                  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Reverse Connect endpoint:                                   ║");
            System.out.println("║    opc.tcp://localhost:48500                                 ║");
            System.out.println("║    -> the CLIENT must listen on this port                    ║");
            System.out.println("║    -> the SERVER connects to the client (not the other way)  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  How to test with an OPC UA client that supports             ║");
            System.out.println("║  Reverse Connect:                                            ║");
            System.out.println("║    1. In the client, open 'Add Server' with Reverse Connect  ║");
            System.out.println("║       mode and enter: opc.tcp://localhost:48500              ║");
            System.out.println("║       The client will now LISTEN on port 48500               ║");
            System.out.println("║    2. This server sends a ReverseHello to port 48500         ║");
            System.out.println("║    3. The client receives the ReverseHello and establishes   ║");
            System.out.println("║       a normal OPC UA session over that connection           ║");
            System.out.println("║    4. Watch the [SESSION+] message appear here               ║");
            System.out.println("║    5. Browse and read Plant/Temperature as usual             ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to start value loop, CTRL+C to exit.            ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Pushing values every second...");
            Random rng = new Random();
            long cycle = 0;

            while (true) {
                cycle++;
                double t = Math.round((20.0 + rng.nextDouble() * 10.0) * 10.0) / 10.0;
                temp.setValue(t);
                System.out.printf("  Cycle=%-5d  %s = %.1fC  (sessions: %d)%n",
                        cycle, temp.getPath(), t, server.getActiveSessionCount());
                Thread.sleep(1000);
            }
        }
    }

    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();
        config.setApplicationName("PLCcom Workshop 71 - Reverse Connect");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:71");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/reverse-connect");
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

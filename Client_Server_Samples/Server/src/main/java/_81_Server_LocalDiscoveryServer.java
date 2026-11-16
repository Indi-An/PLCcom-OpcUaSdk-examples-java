// MIT License
// Copyright (c) Indi.An GmbH

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 81: Local Discovery Server (LDS)
//
// A Local Discovery Server is the telephone book of a computer. Servers on the
// computer register with it, clients ask it which servers there are and where
// to connect to them (FindServers, GetEndpoints). The client no longer needs
// to know the address of every server.
//
// This program is a Local Discovery Server. It has no address space and no
// sessions, only the discovery services.
//
// What you will learn:
//   * How to start a Local Discovery Server (UaDiscoveryServer)
//   * How a server registers with it (two lines in the server's configuration)
//   * Why nothing is trusted by itself: you confirm each certificate
//   * How a client finds the registered servers
//
// Steps to try it:
//   1. Start this program. It listens on opc.tcp://localhost:4840/UADiscovery,
//      on this computer only (the loopback address 127.0.0.1).
//      Another Local Discovery Server may already use port 4840 on your
//      computer; then start this program with the argument:  --port 4850
//   2. Start a server that registers with it, for example Workshop 11
//      (Simple Server) with these two lines in its UaServerConfiguration:
//
//          config.setRegisterWithDiscoveryServer(true);
//          config.setDiscoveryServerUrl("opc.tcp://localhost:4840");   // your port
//
//      The server must trust the certificate of the Local Discovery Server: copy the
//      file in .\lds\pki\own\certs to the pki\trusted\certs folder of the server
//      before you start it. A server checks the certificate of the Local Discovery
//      Server with its PKI folder, like the certificate of a client; the listener
//      that Workshop 11 uses to accept clients does not apply to it.
//   3. This program reports the certificate of the server and asks whether you
//      trust it. Answer y. The server registers within a few seconds.
//   4. Run Client Workshop 11 (Discover Server) with the address of this
//      program as serverUrl: its last step (FindServers) lists the Local
//      Discovery Server and your server.
//
// Registrations only come from servers on this computer, through a secured
// channel (SignAndEncrypt), and only from a server whose certificate names the
// application URI it registers. A server ends its registration when it stops.
// ==============================================================================

import com.plccom.opc.ua.server.application.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class _81_Server_LocalDiscoveryServer {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 81 - Local Discovery Server", 1000);

        // Important !!!!!!!!!!!!!!!!!!
        // Enter your Username + Serial here! Please note: with blank fields the library runs
        // for 15 minutes during a debug session. Both values can also come
        // from configuration or an environment variable.
        // Free trial license (14 days, uninterrupted): https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/
        String licenseUser   = "";
        String licenseSerial = "";
        // Leave both fields empty to take the values from the environment
        // variables PLCCOM_OPCUA_LICENSE_USER and PLCCOM_OPCUA_LICENSE_SERIAL.
        // A value entered here is used first.
        if (licenseUser.isEmpty()) {
            licenseUser = System.getenv().getOrDefault("PLCCOM_OPCUA_LICENSE_USER", "");
        }
        if (licenseSerial.isEmpty()) {
            licenseSerial = System.getenv().getOrDefault("PLCCOM_OPCUA_LICENSE_SERIAL", "");
        }

        // -- Port --------------------------------------------------------------------
        // 4840 is the well-known port of a Local Discovery Server. --port <number> uses another one.
        int port = 4840;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                try {
                    int p = Integer.parseInt(args[i + 1]);
                    if (p > 0 && p < 65536) {
                        port = p;
                    }
                } catch (NumberFormatException ignored) {
                    // keep the default
                }
            }
        }

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 81: Local Discovery     ║");
        System.out.println("║  Server                                                      ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example demonstrates:                                  ║");
        System.out.println("║  * A Local Discovery Server (FindServers, GetEndpoints)      ║");
        System.out.println("║  * Servers that register with it (RegisterServer2)           ║");
        System.out.println("║  * Trust confirmed by you, certificate by certificate        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // -- Configuration -----------------------------------------------------------
        // The defaults are the ones the specification names: the well-known address,
        // secured registration, registrations from this computer only, an application
        // certificate that the Local Discovery Server creates under the storage path.
        UaDiscoveryServerConfiguration config = new UaDiscoveryServerConfiguration();
        config.setApplicationName("PLCcom Workshop 81 - Local Discovery Server");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:81");
        config.setBaseAddresses(Arrays.asList("opc.tcp://localhost:" + port + "/UADiscovery"));

        // Where the Local Discovery Server keeps its files: the certificate folders
        // (pki/own, trusted, issuer, rejected) and registrations that are held by a semaphore file.
        config.setStoragePath("./lds");

        // Registrations from other computers are refused (default), and the server then
        // listens on the loopback address 127.0.0.1 only: nothing on the network reaches
        // it. Set true only if you want servers on other computers to register here; the
        // server then listens on every network adapter.
        config.setAcceptRegistrationsFromOtherHosts(false);

        // A server registers again at least every 10 minutes; the Local Discovery Server
        // waits a minute longer (660 s) before it removes a registration.
        config.setRegistrationTimeoutSeconds(660);
        config.setMaxRegistrations(256);

        try (UaDiscoveryServer lds = new UaDiscoveryServer(licenseUser, licenseSerial)) {

            // Report what the Local Discovery Server does.
            lds.addLogListener(e -> System.out.printf("  [%-7s] %s%n", e.getLevel(), e.getMessage()));
            lds.addRegistrationListener(e -> {
                UaDiscoveryRegistration r = e.getRegistration();
                System.out.println("  [REGISTRATION] " + e.getChange() + ": " + r.getServerUri() + " "
                        + String.join(", ", r.getServerNames()) + " " + String.join(", ", r.getDiscoveryUrls())
                        + (e.getReason() != null ? "  (" + e.getReason() + ")" : ""));
            });

            // A server whose certificate is not trusted yet is refused and reported here. The listener
            // only notes it; the question is asked from the main loop below, because this event comes
            // from a thread of the OPC UA stack.
            ConcurrentLinkedQueue<UaDiscoveryCertificateEvent> unknown = new ConcurrentLinkedQueue<>();
            Set<String> asked = new HashSet<>();
            lds.addCertificateListener(unknown::add);

            System.out.print("Starting Local Discovery Server ... ");
            try {
                lds.start(config);
            } catch (UaDiscoveryPortInUseException ex) {
                System.out.println("FAILED");
                System.out.println(ex.getMessage());
                System.out.println("Start this program with another port, for example:  --port " + (port + 10));
                PLCcomConsole.close();
                return;
            } catch (Exception ex) {
                System.out.println("FAILED");
                System.out.println(ex.getMessage());
                PLCcomConsole.close();
                return;
            }
            System.out.println("OK");
            System.out.println();
            System.out.println("  Address            : " + String.join(", ", lds.getEndpointUrls()));
            System.out.println("  Certificate folders: " + lds.getPkiPath());
            System.out.println();
            System.out.println("  A server registers with these two lines in its UaServerConfiguration:");
            System.out.println("      config.setRegisterWithDiscoveryServer(true);");
            System.out.println("      config.setDiscoveryServerUrl(\"opc.tcp://localhost:" + port + "\");");
            System.out.println();
            System.out.println("  The server has to trust this Local Discovery Server. Before you start it, copy the");
            System.out.println("  certificate file in");
            System.out.println("      " + new java.io.File(new java.io.File(lds.getPkiPath(), "own"), "certs"));
            System.out.println("  to the pki" + java.io.File.separator + "trusted" + java.io.File.separator
                    + "certs folder of the server.");
            System.out.println();
            System.out.println("  Type q and press Enter to quit.");
            System.out.println();

            // -- Main loop -----------------------------------------------------------
            // Confirm certificates, quit on q.
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            boolean quit = false;
            while (!quit) {
                UaDiscoveryCertificateEvent cert = unknown.poll();
                if (cert != null) {
                    // The server tries again and again until it is trusted: ask once per certificate.
                    if (!asked.add(cert.getThumbprint())) {
                        continue;
                    }
                    System.out.println();
                    System.out.println("  A server wants to register, but its certificate is not trusted:");
                    System.out.println("    Application URI: " + (cert.getApplicationUri() != null ? cert.getApplicationUri() : "(none)"));
                    System.out.println("    Subject        : " + cert.getSubject());
                    System.out.println("    Thumbprint     : " + cert.getThumbprint());
                    System.out.print("  Do you know this server and trust its certificate? [y/N] ");
                    String answer = in.readLine();
                    if (answer == null || !answer.trim().equalsIgnoreCase("y")) {
                        System.out.println("  Not trusted. The registration stays refused.");
                        continue;
                    }

                    // The refused certificate is filed on a background task of the stack; allow it a moment.
                    boolean trusted = false;
                    for (int attempt = 0; attempt < 50 && !trusted; attempt++) {
                        trusted = lds.trustCertificate(cert.getThumbprint());
                        if (!trusted) {
                            Thread.sleep(100);
                        }
                    }
                    System.out.println(trusted
                            ? "  Trusted. The server registers with its next attempt (within a few seconds)."
                            : "  The certificate was not found among the refused ones. Start the server again.");
                    asked.remove(cert.getThumbprint());   // if it comes back, ask again
                    continue;
                }

                if (System.in.available() > 0) {
                    String line = in.readLine();
                    if (line == null || line.trim().equalsIgnoreCase("q")) {
                        quit = true;
                    }
                    continue;
                }
                Thread.sleep(200);
            }

            System.out.println();
            System.out.print("Stopping ... ");
        }
        System.out.println("OK");
        PLCcomConsole.close();
    }
}

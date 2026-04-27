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

import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaCertificateManager;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.ReadResponse;
import com.plccom.opc.ua.core.RegisterNodesResponse;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import com.plccom.opc.ua.transport.security.KeyPair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Workshop 27 - Registered Read and Write
 *
 * RegisterNodes is an OPC UA optimization for high-frequency access. The
 * server assigns a session-local alias NodeId that is faster to look up
 * than the original. Always unregister nodes when done to free server
 * resources.
 *
 * What you will learn:
 *   - How to register nodes for optimized access (RegisterNodes service)
 *   - How to read and write using registered alias NodeIds
 *   - How to unregister nodes when done (UnregisterNodes service)
 *   - When RegisterNodes is beneficial (high-frequency polling loops)
 *
 * Target server: opc.tcp://localhost:48410
 * (Start any Server SDK workshop first, e.g. Server Workshop 11)
 */
public class _27_RegisteredReadWrite
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _27_RegisteredReadWrite().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 27 - Registered Read and Write", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 27: Registered R/W      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  RegisterNodes is an OPC UA optimization for high-frequency  ║");
            System.out.println("║  polling. The server returns session-local alias NodeIds     ║");
            System.out.println("║  that are faster to resolve than the original NodeIds.       ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Register nodes for optimized access                     ║");
            System.out.println("║    * Read and write using registered NodeIds                 ║");
            System.out.println("║    * Unregister nodes when done                              ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 11 (Simple Server)         ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // TODO: Replace with your license credentials from your license e-mail
            String licenseUser   = "<Enter your UserName here>";
            String licenseSerial = "<Enter your Serial here>";

            // -- Step 1: Discover and select endpoint -----------------------------
            String serverUrl = "opc.tcp://localhost:48410";

            System.out.println("  Server URL: " + serverUrl);
            System.out.println("  Discovering endpoints...");
            System.out.println();

            EndpointDescription[] endpoints = UaClient.discoverEndpoints(new URI(serverUrl), this);

            if (endpoints == null || endpoints.length == 0) {
                System.err.println("  No endpoints found. Is the server running?");
                System.out.println("  Press ENTER to exit.");
                try { System.in.read(); } catch (Exception ignored) { }
                return;
            }

            endpoints = UaClient.sortBySecurityLevel(endpoints, SortDirection.Asc);

            // -- Step 2: Display endpoints and let user choose --------------------
            System.out.println("  " + endpoints.length + " endpoint(s) found:");
            System.out.println();
            for (int i = 0; i < endpoints.length; i++) {
                EndpointDescription ep = endpoints[i];
                System.out.println("  [" + i + "] " + OpcUaDisplayUtils.toDisplayString(ep));
            }
            System.out.println();
            System.out.print("  Please enter index of desired endpoint: ");

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String input = reader.readLine();
            int index = -1;
            try { index = Integer.parseInt(input.trim()); } catch (NumberFormatException ignored) { }

            if (index < 0 || index >= endpoints.length) {
                System.err.println("  Invalid endpoint index.");
                System.out.println("  Press ENTER to exit.");
                try { System.in.read(); } catch (Exception ignored) { }
                return;
            }

            EndpointDescription endpoint = endpoints[index];
            System.out.println();
            System.out.println("  Selected: " + OpcUaDisplayUtils.toDisplayString(endpoint));
            System.out.println();

            // -- Step 3: Build configuration and connect --------------------------
            // ClientConfiguration wraps the endpoint together with the application
            // name and optional certificate. Secured endpoints (SignAndEncrypt)
            // require an application instance certificate.
            ClientConfiguration config = new ClientConfiguration(
                    new LocalizedText("PLCcom_Workshop_27", "en"), endpoint);

            if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
                KeyPair cert = loadOrCreateCertificate(
                        "CertificateStores/PLCcom_Workshop_27.der", "secretpassword", "PLCcom_Workshop_27");
                config.setInstanceCertificate(cert);
            }

            // Accept all server certificates for development.
            // In production, verify against a trusted certificate store.
            config.setCertificateValidator(this);

            UaClient client = new UaClient(licenseUser, licenseSerial, config);
            System.out.println("  License: " + client.getLicenceMessage());
            System.out.println();

            client.addSessionKeepAliveListener(this);
            client.addSessionConnectionStateChangeListener(this);

            System.out.print("  Connecting ... ");
            client.connect();
            System.out.println("OK");
            System.out.println();

            // -- Step 4: Register nodes ----------------------------------------
            // RegisterNodes is an optimization for high-frequency polling loops.
            // The server assigns session-local alias NodeIds that bypass the normal
            // address space lookup on every Read/Write - reducing server-side overhead.
            // Note: registered NodeIds are only valid within the current session.
            String path1 = "Objects.Plant.Line1.Machine1.RPM";
            String path2 = "Objects.Plant.Line1.Machine1.Temperature";
            NodeId[] nodesToRegister = {
                client.getNodeIdByPath(path1),
                client.getNodeIdByPath(path2)
            };
            System.out.println("  Registering nodes:");
            System.out.println("    " + path1 + "  ->  " + nodesToRegister[0]);
            System.out.println("    " + path2 + "  ->  " + nodesToRegister[1]);
            System.out.println();

            // registerNodes() sends a single RegisterNodes service call.
            // The response contains one alias NodeId per requested NodeId, in the same order.
            // If the server does not support RegisterNodes it returns the original NodeIds unchanged.
            RegisterNodesResponse regResponse = client.registerNodes(nodesToRegister);
            NodeId[] registeredIds = regResponse.getRegisteredNodeIds();

            System.out.println("  Registered " + registeredIds.length + " node(s).");
            System.out.println();

            // -- Step 5: Read and write using registered NodeIds ---------------
            System.out.println("  Reading and writing 5 times with registered NodeIds:");
            for (int i = 0; i < 5; i++) {
                // Use the registered alias NodeId for every read/write - faster than the original.
                ReadResponse res = client.readValue(registeredIds[0], registeredIds[1]);
                int    rpmVal  = ((Number) res.getResults()[0].getValue().getValue()).intValue();
                double tempVal = ((Number) res.getResults()[1].getValue().getValue()).doubleValue();
                System.out.println("  [" + (i + 1) + "] RPM=" + rpmVal + "  Temperature=" + tempVal);

                // Write incremented values back using the registered alias NodeIds
                client.writeValue(registeredIds[0], rpmVal + 10);
                client.writeValue(registeredIds[1], tempVal + 0.5);
                Thread.sleep(1000);
            }

            System.out.println();

            // -- Step 6: Unregister nodes --------------------------------------
            // Always unregister when done - the server holds resources for each
            // registered node for the lifetime of the session if not released.
            client.unregisterNodes(registeredIds);
            System.out.println("  Nodes unregistered.");
            System.out.println();

            // -- Step 7: Disconnect -----------------------------------------------
            System.out.println("  Press ENTER to disconnect and exit.");
            reader.readLine();

            if (client.isConnected()) {
                client.close();
            }
            System.out.println("  Disconnected.");

        } catch (Exception e) {
            System.err.println("  Error: " + e.getMessage());
            e.printStackTrace();
            System.out.println("  Press ENTER to exit.");
            try { System.in.read(); } catch (Exception ignored) { }
        } finally {
            PLCcomConsole.close();
        }
    }

    // ── Event handlers ──────────────────────────────────────────────────────

    // Called periodically by the server to confirm the session is still alive.
    @Override
    public void onSessionKeepAlive(ServerStatusDataType status, ServerState state) {
    }

    // Called whenever the session connects or disconnects (e.g. network
    // interruption). The SDK attempts automatic reconnection in the background.
    @Override
    public void onSessionConnectionStateChanged(boolean isConnected) {
        if (isConnected) {
            System.out.println("  [Connected] Session established");
        } else {
            System.out.println("  [ConnectionLost] Connection lost");
        }
    }

    // Accept all server certificates for development.
    // In production, verify against a trusted certificate store.
    @Override
    public StatusCode validateCertificate(Cert cert) {
        return StatusCode.GOOD;
    }

    // Overload called when the server also provides its ApplicationDescription.
    @Override
    public StatusCode validateCertificate(ApplicationDescription app, Cert cert) {
        return StatusCode.GOOD;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Loads an existing application instance certificate from disk, or creates
     * a new self-signed certificate if none exists yet.
     *
     * @param certFile path to the .der certificate file
     * @param password password used to protect the private key
     * @param alias    common name (CN) used when creating a new certificate
     * @return the loaded or newly created key pair
     * @throws Exception if certificate creation or loading fails
     */
    static KeyPair loadOrCreateCertificate(String certFile, String password, String alias) throws Exception {
        java.io.File f = new java.io.File(certFile);
        f.getParentFile().mkdirs();
        if (!f.isFile())
            return UaCertificateManager.createSelfSignedCertificate(certFile, alias, password, 720, "Indi.An GmbH");
        else
            return UaCertificateManager.getCertificate(certFile, password);
    }
}

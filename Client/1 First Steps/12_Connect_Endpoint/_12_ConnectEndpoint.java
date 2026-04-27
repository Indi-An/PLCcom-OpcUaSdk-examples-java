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
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import com.plccom.opc.ua.transport.security.KeyPair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Workshop 12 - Connect to Endpoint
 *
 * Demonstrates the full connect/disconnect lifecycle of a UaClient session.
 * All available endpoints are discovered, displayed and the user selects one
 * interactively. For secured endpoints an application instance certificate is
 * created automatically on first run and reused on subsequent runs.
 *
 * What you will learn:
 *   - How to discover and sort endpoints by security level
 *   - How to select an endpoint interactively
 *   - How to create a ClientConfiguration from an endpoint
 *   - How to register KeepAlive and ConnectionState event listeners
 *   - How to handle server certificate validation
 *   - How to connect and disconnect cleanly
 *
 * The class implements the three listener/validator interfaces directly so that
 * the event handler methods appear cleanly at the bottom of the file - a pattern
 * used throughout all workshops.
 *
 * Target server: opc.tcp://localhost:48410
 * (Start any Server SDK workshop first, e.g. Server Workshop 11)
 */
public class _12_ConnectEndpoint
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _12_ConnectEndpoint().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 12 - Connect to Endpoint", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 12: Connect Endpoint    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Connecting to an OPC UA server requires discovering its     ║");
            System.out.println("║  endpoints first and selecting the right one. This workshop  ║");
            System.out.println("║  shows the full connect/disconnect lifecycle.                ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Discover and sort endpoints by security level           ║");
            System.out.println("║    * Create a ClientConfiguration from an endpoint           ║");
            System.out.println("║    * Register KeepAlive and ConnectionState events           ║");
            System.out.println("║    * Handle server certificate validation                    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 11 (Simple Server)         ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // TODO: Replace with your license credentials from your license e-mail
            String licenseUser   = "<Enter your UserName here>";
            String licenseSerial = "<Enter your Serial here>";

            // -- Step 1: Discover and sort endpoints ------------------------------
            // discoverEndpoints() queries the server for all available endpoints.
            // sortBySecurityLevel() with Asc puts the least secure (None) first,
            // making index 0 the easiest to connect to for testing.
            String serverUrl = "opc.tcp://localhost:48410";
            
            System.out.println("  Server URL: " + serverUrl);
            System.out.println("  Discovering endpoints...");
            System.out.println();

            EndpointDescription[] endpoints = UaClient.discoverEndpoints(new URI(serverUrl), this);

            if (endpoints == null || endpoints.length == 0) {
                System.err.println("  No endpoints found. Is the server running?");
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
                return;
            }

            EndpointDescription endpoint = endpoints[index];
            System.out.println();
            System.out.println("  Selected: " + OpcUaDisplayUtils.toDisplayString(endpoint));
            System.out.println();

            // -- Step 3: Build client configuration -------------------------------
            // ClientConfiguration wraps the selected endpoint together with the
            // application name and optional certificate. Always pass the full
            // EndpointDescription - never just a URL string, because the endpoint
            // carries the security policy and mode needed for the session.
            ClientConfiguration config = new ClientConfiguration(
                    new LocalizedText("PLCcom_Workshop_12", "en"), endpoint);

            // Secured endpoints require an application instance certificate.
            // loadOrCreateCertificate() reuses an existing certificate from disk
            // or creates a new self-signed one on first run.
            if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
                KeyPair cert = loadOrCreateCertificate(
                        "CertificateStores/PLCcom_Workshop_12.der", "secretpassword", "PLCcom_Workshop_12");
                config.setInstanceCertificate(cert);
                System.out.println("  Certificate: ready");
                System.out.println();
            }

            // Register this class as the certificate validator for the server
            // certificate. The validateCertificate() method below accepts all
            // certificates. In production, verify against a trusted certificate
            // store (see Workshop 14 for proper chain validation).
            config.setCertificateValidator(this);

            // -- Step 4: Create client and register event listeners ---------------
            // The UaClient manages the OPC UA session. Pass your license
            // credentials and the client configuration.
            UaClient client = new UaClient(licenseUser, licenseSerial, config);
            System.out.println("  License: " + client.getLicenceMessage());
            System.out.println();

            // onSessionKeepAlive() fires periodically to confirm the server is alive.
            client.addSessionKeepAliveListener(this);

            // onSessionConnectionStateChanged() fires when the session connects,
            // disconnects or reconnects after a network interruption.
            client.addSessionConnectionStateChangeListener(this);

            // -- Step 5: Connect --------------------------------------------------
            System.out.print("  Connecting ... ");
            client.connect();
            System.out.println("OK");
            System.out.println("  Session connected: " + client.isConnected());
            System.out.println();

            // -- Step 6: Disconnect -----------------------------------------------
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
    // The ServerStatusDataType contains server diagnostics (uptime, build info);
    // the ServerState reflects the server's current run state (Running, etc.).
    @Override
    public void onSessionKeepAlive(ServerStatusDataType status, ServerState state) {
        System.out.println("  [KeepAlive] Server state: " + state);
    }

    // Called whenever the session connects or disconnects (e.g. network
    // interruption). The SDK attempts automatic reconnection in the background;
    // isConnected=true signals a successful (re)connect.
    @Override
    public void onSessionConnectionStateChanged(boolean isConnected) {
        if (isConnected) {
            System.out.println("  [Connected] Session established");
        } else {
            System.out.println("  [ConnectionLost] Connection lost");
        }
    }

    // Called when the server presents its certificate during the secure channel
    // handshake. Return StatusCode.GOOD to accept, or StatusCode.BAD to reject.
    // For development we accept all certificates here. In production, verify
    // the certificate against a trusted certificate store.
    @Override
    public StatusCode validateCertificate(Cert cert) {
        System.out.println("  [Certificate] Accepted: "
                + (cert != null ? cert.certificate.getSubjectDN() : "null"));
        return StatusCode.GOOD;
    }

    // Overload called when the server presents both an ApplicationDescription
    // and a certificate. The ApplicationDescription can be used to cross-check
    // the certificate's common name against the server's application URI.
    @Override
    public StatusCode validateCertificate(ApplicationDescription app, Cert cert) {
        return validateCertificate(cert);
    }

    // -- Helpers ---------------------------------------------------------------

    /**
     * Loads an existing application instance certificate from disk, or creates
     * a new self-signed certificate if none exists yet. The certificate is
     * stored as a .der file alongside a matching private key file.
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

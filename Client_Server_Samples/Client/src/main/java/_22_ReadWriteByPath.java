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
import com.plccom.opc.ua.client.application.UaClientCertificate;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.ReadResponse;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Workshop 22 - Read and Write by Path
 *
 * Instead of using numeric NodeIds, you can address nodes by their
 * dot-separated browse path (e.g. "Objects.Plant.Line1.Temperature").
 * getNodeIdByPath() resolves the path to a NodeId, then you read/write
 * as usual. This is more readable and maintainable than raw NodeIds.
 *
 * Compare these two approaches:
 *   NodeId approach:  client.readValue(new NodeId("ns=2;i=4"))
 *   Path approach:    client.readValue(client.getNodeIdByPath("Objects.Plant.Line1.Machine1.Temperature"))
 *
 * The path approach is:
 *   - Self-documenting: the path tells you exactly what you are reading
 *   - Portable: works even if the server assigns different NodeIds after a restart
 *   - Maintainable: changing a variable name only requires updating the path string
 *
 * What you will learn:
 *   - How to resolve a browse path to a NodeId (getNodeIdByPath)
 *   - How to read and write values using path-resolved NodeIds
 *   - How the SDK resolves and caches paths automatically
 *
 * Target server: opc.tcp://localhost:48410
 * (Start any Server SDK workshop first, e.g. Server Workshop 11)
 */
public class _22_ReadWriteByPath
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _22_ReadWriteByPath().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 22 - Read and Write by Path", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 22: Read/Write by Path  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Instead of numeric NodeIds, address nodes by their          ║");
            System.out.println("║  dot-separated browse path. getNodeIdByPath() resolves       ║");
            System.out.println("║  the path to a NodeId, then you read/write as usual.         ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Resolve browse paths to NodeIds (getNodeIdByPath)       ║");
            System.out.println("║    * Read and write using path-resolved NodeIds              ║");
            System.out.println("║    * SDK resolves and caches paths automatically             ║");
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

            // -- Step 3: Build client configuration -------------------------------
            // createConfig() builds the ClientConfiguration for the selected endpoint.
            // It handles certificate creation/loading automatically based on the
            // endpoint security mode and transport protocol.
            ClientConfiguration config = createConfig(endpoint);
            printConfig(config);

            // Registers this class as the certificate validator for the server
            // certificate. The validateCertificate() method below accepts all
            // certificates - suitable for development and testing.
            // Remove this call to activate PKI-based validation via the store above.
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

            // -- Step 4: Resolve paths to NodeIds ---------------------------------
            // getNodeIdByPath() walks the address space once and caches the result.
            // All subsequent calls with the same path return the cached NodeId instantly.
            // This is more readable and maintainable than hardcoding numeric NodeIds.
            NodeId temperatureId = client.getNodeIdByPath("Objects.Plant.Line1.Machine1.Temperature");
            NodeId rpmId         = client.getNodeIdByPath("Objects.Plant.Line1.Machine1.RPM");
            NodeId pressureId    = client.getNodeIdByPath("Objects.Plant.Line1.Machine1.Pressure");

            System.out.println("  Temperature -> " + temperatureId + "  (Objects.Plant.Line1.Machine1.Temperature)");
            System.out.println("  RPM         -> " + rpmId + "  (Objects.Plant.Line1.Machine1.RPM)");
            System.out.println("  Pressure    -> " + pressureId + "  (Objects.Plant.Line1.Machine1.Pressure)");
            System.out.println();

            // -- Step 5: Read values by path-resolved NodeIds --------------------
            System.out.println("  -- Read values --");
            ReadResponse readRes = client.readValue(temperatureId, rpmId, pressureId);
            System.out.println("  Temperature: " + readRes.getResults()[0].getValue().getValue()
                    + "  (" + readRes.getResults()[0].getStatusCode() + ")");
            System.out.println("  RPM:         " + readRes.getResults()[1].getValue().getValue()
                    + "  (" + readRes.getResults()[1].getStatusCode() + ")");
            System.out.println("  Pressure:    " + readRes.getResults()[2].getValue().getValue()
                    + "  (" + readRes.getResults()[2].getStatusCode() + ")");
            System.out.println();

            // -- Step 6: Write values by path-resolved NodeIds -------------------
            // writeValue() accepts the NodeId we already resolved.
            // The path is only resolved once - the NodeId is reused from the cache.
            System.out.println("  -- Write values --");
            StatusCode sc1 = client.writeValue(temperatureId, 25.5);
            StatusCode sc2 = client.writeValue(rpmId, 1750);
            StatusCode sc3 = client.writeValue(pressureId, 1.05f);
            System.out.println("  Temperature write: " + sc1);
            System.out.println("  RPM         write: " + sc2);
            System.out.println("  Pressure    write: " + sc3);
            System.out.println();

            // Read back to verify
            System.out.println("  -- Read back after write --");
            ReadResponse verify = client.readValue(temperatureId, rpmId, pressureId);
            System.out.println("  Temperature: " + verify.getResults()[0].getValue().getValue());
            System.out.println("  RPM:         " + verify.getResults()[1].getValue().getValue());
            System.out.println("  Pressure:    " + verify.getResults()[2].getValue().getValue());
            System.out.println();

            // -- Step 7: Disconnect -----------------------------------------------
            System.out.println("  Press ENTER to disconnect and exit.");
            new BufferedReader(new InputStreamReader(System.in)).readLine();

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
    // =============================================================================
    // Helper: createConfig
    // =============================================================================
    // Builds the ClientConfiguration for the selected endpoint.
    //
    // Certificate handling:
    //   Application certificate — required for Sign / SignAndEncrypt endpoints.
    //   HTTPS certificate       — required for opc.https:// endpoints (any SecurityMode).
    //
    // UaClientCertificate derives file paths automatically from the PKI base directory:
    //   pki/own/certs/<alias>.der    <- certificate
    //   pki/own/private/<alias>.pem  <- private key
    //
    // load() returns null if the certificate does not exist yet or cannot be read.
    // build(true) creates a new self-signed certificate, overwriting any existing file.
    static ClientConfiguration createConfig(EndpointDescription endpoint) throws Exception {
        ClientConfiguration config = new ClientConfiguration(
                new LocalizedText("PLCcom_Workshop_22", "en"), endpoint);

        // HTTPS Certificate — required for opc.https:// endpoints, independent of SecurityMode.
        // The hostname is extracted from the endpoint URL and used as the certificate alias.
        UaClientCertificate httpsCert = null;
        if (endpoint.getEndpointUrl() != null &&
                endpoint.getEndpointUrl().toLowerCase().startsWith("opc.https://")) {
            String host = new java.net.URI(endpoint.getEndpointUrl()).getHost();
            httpsCert = UaClientCertificate.load("./pki", host, "secretpassword");
            if (httpsCert == null || !httpsCert.checkValidity())
                httpsCert = new UaClientCertificate("./pki", "secretpassword", host, 720, "Indi.An GmbH")
                        .build(true);
        }

        // Application Certificate — required for secured endpoints (Sign or SignAndEncrypt).
        // Not needed for SecurityMode.None (unencrypted connections).
        UaClientCertificate appCert = null;
        if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
            appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_22", "secretpassword");
            if (appCert == null || !appCert.checkValidity())
                appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_22", 720, "Indi.An GmbH")
                        .build(true);
        }

        // Apply certificates to the configuration.
        // setInstanceCertificate() also sets the PKI store path and reads the
        // ApplicationUri from the certificate automatically.
        if (appCert != null && httpsCert != null)
            config.setInstanceCertificate(appCert, httpsCert);
        else if (appCert != null)
            config.setInstanceCertificate(appCert);

        return config;
    }

    // =============================================================================
    // Helper: printConfig
    // =============================================================================
    // Prints the active client configuration to the console so you can verify
    // all settings at a glance before connecting.
    private static void printConfig(ClientConfiguration config) {
        System.out.println("── Active Client Configuration ──────────────────────────────────────────────");
        if (config.getEndpoint() != null) {
            System.out.println("  Endpoint  : " + config.getEndpoint().getEndpointUrl());
            System.out.println("  Security  : " + OpcUaDisplayUtils.toDisplayString(config.getEndpoint()));
        }
        System.out.println("  PKI Store : " + (config.getCertificateStorePath() != null
                ? config.getCertificateStorePath() : "(not set)"));
        System.out.println("─────────────────────────────────────────────────────────────────────────────");
        System.out.println();
    }

}

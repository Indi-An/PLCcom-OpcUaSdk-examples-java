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
import com.plccom.opc.ua.client.application.UaClientCertificate;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.BrowseDescription;
import com.plccom.opc.ua.core.BrowseDirection;
import com.plccom.opc.ua.core.BrowseResponse;
import com.plccom.opc.ua.core.BrowseResult;
import com.plccom.opc.ua.core.BrowseResultMask;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.Identifiers;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.NodeClass;
import com.plccom.opc.ua.core.ReferenceDescription;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Workshop 15 - Browse by NodeId
 *
 * Browsing is how you explore the OPC UA address space. Starting from a
 * known NodeId (e.g. the ObjectsFolder i=85), you request all child
 * references and discover what the server exposes.
 *
 * This workshop browses from the ObjectsFolder using two reference types:
 *   Aggregates - finds HasComponent, HasProperty and similar references
 *   Organizes  - finds folder-like organizational references
 *
 * What you will learn:
 *   - How to construct a BrowseDescription with filters
 *   - How to browse from a known NodeId (ObjectsFolder = i=85)
 *   - How to read NodeId, NodeClass, BrowseName and DisplayName
 *   - How browseFull handles continuation points automatically
 *
 * Target server: opc.tcp://localhost:48410
 * (Start any Server SDK workshop first, e.g. Server Workshop 11)
 */
public class _15_BrowseByNodeId
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _15_BrowseByNodeId().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 15 - Browse by NodeId", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 15: Browse by NodeId    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Browsing is how you explore the OPC UA address space.       ║");
            System.out.println("║  Starting from a known NodeId, you request all child         ║");
            System.out.println("║  references to discover what the server exposes.             ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Construct a BrowseDescription with filters              ║");
            System.out.println("║    * Browse from ObjectsFolder (i=85)                        ║");
            System.out.println("║    * Read NodeId, NodeClass, BrowseName, DisplayName         ║");
            System.out.println("║    * browseFull handles continuation points automatically    ║");
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

            // -- Step 4: Build browse request ------------------------------------
            // Start from the ObjectsFolder (NodeId i=85, namespace 0).
            // This is the root of all application-defined nodes.

            // BrowseDescription 1: find all components (HasComponent, HasProperty)
            // Aggregates is the abstract super-type that covers both.
            BrowseDescription nodeToBrowse1 = new BrowseDescription();
            nodeToBrowse1.setNodeId(Identifiers.ObjectsFolder);
            nodeToBrowse1.setBrowseDirection(BrowseDirection.Forward);
            nodeToBrowse1.setReferenceTypeId(Identifiers.Aggregates);
            nodeToBrowse1.setIncludeSubtypes(true);
            nodeToBrowse1.setNodeClassMask(NodeClass.Object, NodeClass.Variable);
            nodeToBrowse1.setResultMask(BrowseResultMask.All);

            // BrowseDescription 2: find all organized children (Organizes)
            // Organizes references represent folder-like grouping.
            BrowseDescription nodeToBrowse2 = new BrowseDescription();
            nodeToBrowse2.setNodeId(Identifiers.ObjectsFolder);
            nodeToBrowse2.setBrowseDirection(BrowseDirection.Forward);
            nodeToBrowse2.setReferenceTypeId(Identifiers.Organizes);
            nodeToBrowse2.setIncludeSubtypes(true);
            nodeToBrowse2.setNodeClassMask(NodeClass.Object, NodeClass.Variable);
            nodeToBrowse2.setResultMask(BrowseResultMask.All);

            // -- Step 5: Execute browse and display results -----------------------
            // browseFull() handles continuation points automatically.
            // If the server returns more results than fit in one response,
            // browseFull sends BrowseNext requests until all results are collected.
            System.out.println("  Browsing ObjectsFolder (i=85)...");
            System.out.println();

            BrowseResponse response = client.browseFull(nodeToBrowse1, nodeToBrowse2);

            int totalCount = 0;
            for (BrowseResult result : response.getResults()) {
                if (result.getReferences() == null) continue;
                for (ReferenceDescription ref : result.getReferences()) {
                    System.out.printf("  %-30s NodeId=%-20s Class=%-10s BrowseName=%s%n",
                            ref.getDisplayName().getText(),
                            ref.getNodeId(),
                            ref.getNodeClass(),
                            ref.getBrowseName());
                    totalCount++;
                }
            }

            System.out.println();
            if (totalCount > 0) {
                System.out.println("  " + totalCount + " child node(s) found.");
            } else {
                System.out.println("  No child nodes found.");
            }

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
                new LocalizedText("PLCcom_Workshop_15", "en"), endpoint);

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
            appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_15", "secretpassword");
            if (appCert == null || !appCert.checkValidity())
                appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_15", 720, "Indi.An GmbH")
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

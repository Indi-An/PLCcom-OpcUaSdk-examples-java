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
 * Workshop 19 - Enable Debug Tracing
 *
 * When troubleshooting OPC UA communication issues, the built-in trace
 * system is invaluable. The PLCcom Java SDK uses SLF4J for all internal
 * logging. By default, only warnings and errors are shown.
 *
 * This workshop shows how to enable verbose debug logging before connecting.
 * All OPC UA stack activity (service calls, security handshakes, errors)
 * becomes visible in the console or a log file.
 *
 * What you will learn:
 *   - How to enable debug-level logging via SLF4J system properties
 *   - How to redirect log output to a file
 *   - How to control log verbosity (error, warn, info, debug, trace)
 *   - How to use logging to diagnose connection issues
 *
 * Tip: For production use, replace slf4j-simple with Logback or Log4j2
 * for full control over log rotation, file size limits and formatting.
 *
 * Target server: opc.tcp://localhost:48410
 * (Start any Server SDK workshop first, e.g. Server Workshop 11)
 */
public class _19_EnableDebugTracing
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _19_EnableDebugTracing().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 19 - Enable Debug Tracing", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 19: Debug Tracing       ║");
            System.out.println("║                                                              ║");
            System.out.println("║  The built-in trace system logs all OPC UA stack activity    ║");
            System.out.println("║  to a file: service calls, security handshakes, errors.      ║");
            System.out.println("║  Essential for troubleshooting communication issues.         ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Enable debug-level logging via SLF4J properties         ║");
            System.out.println("║    * Redirect log output to a file                           ║");
            System.out.println("║    * Control log verbosity                                   ║");
            System.out.println("║    * Use logging to diagnose connection issues               ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 11 (Simple Server)         ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // TODO: Replace with your license credentials from your license e-mail
            String licenseUser   = "<Enter your UserName here>";
            String licenseSerial = "<Enter your Serial here>";

            // -- Step 1: Configure SLF4J tracing BEFORE connecting ----------------
            // The PLCcom SDK uses SLF4J (Simple Logging Facade for Java) for all
            // internal logging. The default implementation is slf4j-simple, which
            // is configured entirely via system properties.
            //
            // IMPORTANT: These properties must be set BEFORE the first Logger is
            // created. That means before any SDK class is instantiated.
            //
            // Available log levels (from least to most verbose):
            //   error   - only critical errors
            //   warn    - errors + warnings (default)
            //   info    - general information about connections and services
            //   debug   - detailed OPC UA stack activity (recommended for troubleshooting)
            //   trace   - maximum verbosity (very noisy, includes raw byte buffers)

            // Set the log level to "debug" - this makes all OPC UA service calls,
            // security negotiations and internal state changes visible.
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");

            // Include timestamps in the log output so you can correlate events
            // with what you see in the OPC UA client or server.
            System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
            System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");

            // Show the short class name in each log line so you know which
            // component produced the message (e.g. UaClient, UaSessionChannel).
            System.setProperty("org.slf4j.simpleLogger.showLogName", "true");
            System.setProperty("org.slf4j.simpleLogger.showShortLogName", "true");

            // Redirect all log output to a file instead of the console.
            // Comment out the next two lines to see output directly in the console.
            String logFile = "Logs/opcua-debug.log";
            new java.io.File(logFile).getParentFile().mkdirs();
            System.setProperty("org.slf4j.simpleLogger.logFile", logFile);

            System.out.println("  Tracing configuration:");
            System.out.println("    Log level:  debug");
            System.out.println("    Log file:   " + new java.io.File(logFile).getAbsolutePath());
            System.out.println();
            System.out.println("  Tip: Change 'debug' to 'trace' for maximum verbosity.");
            System.out.println("  Tip: Remove the logFile property to see output in the console.");
            System.out.println("  Tip: For production, replace slf4j-simple with Logback or Log4j2");
            System.out.println("       for log rotation, file size limits and custom formatting.");
            System.out.println();

            // -- Step 2: Discover and select endpoint -----------------------------
            String serverUrl = "opc.tcp://localhost:48410";

            System.out.println("  Server URL: " + serverUrl);
            System.out.println("  Discovering endpoints...");
            System.out.println();

            // This discovery call already generates trace output in the log file:
            // you will see the GetEndpoints service call and the server's response.
            EndpointDescription[] endpoints = UaClient.discoverEndpoints(new URI(serverUrl), this);

            if (endpoints == null || endpoints.length == 0) {
                System.err.println("  No endpoints found. Is the server running?");
                System.out.println("  Check the log file for details: " + logFile);
                System.out.println("  Press ENTER to exit.");
                try { System.in.read(); } catch (Exception ignored) { }
                return;
            }

            // Sort ascending by security level so index 0 is always the least secure (None)
            endpoints = UaClient.sortBySecurityLevel(endpoints, SortDirection.Asc);

            // -- Step 3: Display endpoints and let user choose --------------------
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

            // KeepAlive events are also logged - you will see periodic Read calls
            // to the Server_ServerStatus node in the trace output.
            client.addSessionKeepAliveListener(this);
            client.addSessionConnectionStateChangeListener(this);

            System.out.print("  Connecting ... ");
            client.connect();
            System.out.println("OK");
            System.out.println();

            // -- Step 5: Browse to generate more trace output ------------------
            // Browsing the Objects folder generates Browse service calls that
            // appear in the debug log. Open the log file to see the full
            // request/response cycle.
            System.out.println("  Browsing Objects folder to generate trace output...");
            System.out.println();

            BrowseDescription browse = new BrowseDescription();
            browse.setNodeId(Identifiers.ObjectsFolder);
            // Forward: follow references from parent to children.
            browse.setBrowseDirection(BrowseDirection.Forward);
            // includeSubtypes=true: also follow derived reference types.
            browse.setIncludeSubtypes(true);
            // Filter to only Object and Variable nodes.
            browse.setNodeClassMask(NodeClass.Object, NodeClass.Variable);
            // Return all available fields in the result.
            browse.setResultMask(BrowseResultMask.All);

            BrowseResponse response = client.browse(browse);

            for (BrowseResult result : response.getResults()) {
                if (result.getReferences() == null) continue;
                System.out.println("  " + result.getReferences().length + " child node(s) found:");
                for (ReferenceDescription ref : result.getReferences()) {
                    System.out.println("    [" + ref.getNodeClass() + "] "
                            + ref.getBrowseName().getName()
                            + "  NodeId=" + ref.getNodeId());
                }
            }

            System.out.println();
            System.out.println("  ── Check the log file for detailed OPC UA stack activity: ──");
            System.out.println("  " + new java.io.File(logFile).getAbsolutePath());
            System.out.println();
            System.out.println("  You will see:");
            System.out.println("    * OpenSecureChannel request/response");
            System.out.println("    * CreateSession / ActivateSession handshake");
            System.out.println("    * Browse service call with full request parameters");
            System.out.println("    * KeepAlive Read calls to Server_ServerStatus");
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
                new LocalizedText("PLCcom_Workshop_19", "en"), endpoint);

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
            appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_19", "secretpassword");
            if (appCert == null || !appCert.checkValidity())
                appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_19", 720, "Indi.An GmbH")
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

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
import com.plccom.opc.ua.builtintypes.Variant;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaClientCertificate;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.CallMethodRequest;
import com.plccom.opc.ua.core.CallMethodResult;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Workshop 24 - Simple Method Calls
 *
 * OPC UA Methods are callable functions exposed by the server as nodes in
 * the address space. A client invokes a method by sending a Call request
 * with input arguments and receives output arguments in the response.
 *
 * What you will learn:
 *   - How to build a CallMethodRequest with object and method NodeIds
 *   - How to pass input arguments as Variant arrays
 *   - How to call a method and evaluate the result
 *   - How to read output arguments from the CallMethodResult
 *
 * Required server: Server Workshop 13 (Methods) on port 48412
 * Target server:   opc.tcp://localhost:48412
 */
public class _24_SimpleMethodCalls
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _24_SimpleMethodCalls().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 24 - Simple Method Calls", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 24: Simple Method Calls ║");
            System.out.println("║                                                              ║");
            System.out.println("║  OPC UA methods are callable functions exposed by the server ║");
            System.out.println("║  as nodes in the address space. They accept typed input      ║");
            System.out.println("║  arguments and return typed output arguments.                ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Build a CallMethodRequest with object and method NodeIds║");
            System.out.println("║    * Pass input arguments as Variant arrays                  ║");
            System.out.println("║    * Call a method and evaluate the result                   ║");
            System.out.println("║    * Read output arguments from the CallMethodResult         ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 13 (Methods)               ║");
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
                System.err.println("  No endpoints found. Is Server Workshop 13 running?");
                System.out.println("  Press ENTER to exit.");
                try { System.in.read(); } catch (Exception ignored) { }
                return;
            }

            endpoints = UaClient.sortBySecurityLevel(endpoints, SortDirection.Asc);

            // -- Step 2: Display endpoints and let user choose --------------------
            System.out.println("  " + endpoints.length + " endpoint(s) found:");
            System.out.println();
            for (int i = 0; i < endpoints.length; i++)
                System.out.println("  [" + i + "] " + OpcUaDisplayUtils.toDisplayString(endpoints[i]));
            System.out.println();
            System.out.print("  Please enter index of desired endpoint: ");

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            int index = -1;
            try { index = Integer.parseInt(reader.readLine().trim()); } catch (NumberFormatException ignored) { }

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

            // -- Step 4: Resolve object and method NodeIds ------------------------
            // A method node lives as a child of an object node in the address space.
            // The call requires both: the NodeId of the parent object and the NodeId
            // of the method itself. getNodeIdByPath() resolves both by browse path.
            NodeId machineNode = client.getNodeIdByPath("Objects.Plant.Machine1");
            if (machineNode == null) {
                System.err.println("  Objects.Plant.Machine1 not found - is Server Workshop 13 running?");
                client.close();
                return;
            }

            // -- Step 5: Call Reset() - no input, no output -----------------------
            // Reset() sets the CycleCount variable back to 0.
            NodeId resetMethod = client.getNodeIdByPath("Objects.Plant.Machine1.Reset");

            CallMethodRequest resetRequest = new CallMethodRequest();
            resetRequest.setObjectId(machineNode);
            resetRequest.setMethodId(resetMethod);
            resetRequest.setInputArguments(new Variant[0]);

            System.out.println("  Calling: Objects.Plant.Machine1.Reset()");
            CallMethodResult[] resetResults = client.call(resetRequest);
            System.out.println("  Status:  " + resetResults[0].getStatusCode());
            System.out.println();

            // -- Step 6: Call Add(A, B) - two inputs, one output ------------------
            // Add() returns the sum of two Double values.
            // Input arguments are passed as Variant[] - each Variant wraps a typed value.
            // The server validates argument types against the method's InputArguments node.
            NodeId addMethod = client.getNodeIdByPath("Objects.Plant.Machine1.Add");

            double a = 12.5, b = 7.3;
            CallMethodRequest addRequest = new CallMethodRequest();
            addRequest.setObjectId(machineNode);
            addRequest.setMethodId(addMethod);
            addRequest.setInputArguments(new Variant[] {
                new Variant(a),
                new Variant(b)
            });

            System.out.println("  Calling: Objects.Plant.Machine1.Add(" + a + ", " + b + ")");
            CallMethodResult[] addResults = client.call(addRequest);
            if (addResults[0].getStatusCode().isGood()) {
                // Output arguments are returned as Variant[] in the order defined
                // by the method's OutputArguments node on the server.
                for (Variant out : addResults[0].getOutputArguments())
                    System.out.println("  Sum:     " + out.getValue());
            } else {
                System.err.println("  Method call failed: " + addResults[0].getStatusCode());
            }
            System.out.println();

            // -- Step 7: Call SetTemperature(Value) - one input, no output --------
            NodeId setTempMethod = client.getNodeIdByPath("Objects.Plant.Machine1.SetTemperature");

            double newTemp = 37.5;
            CallMethodRequest setTempRequest = new CallMethodRequest();
            setTempRequest.setObjectId(machineNode);
            setTempRequest.setMethodId(setTempMethod);
            setTempRequest.setInputArguments(new Variant[] { new Variant(newTemp) });

            System.out.println("  Calling: Objects.Plant.Machine1.SetTemperature(" + newTemp + ")");
            CallMethodResult[] setTempResults = client.call(setTempRequest);
            System.out.println("  Status:  " + setTempResults[0].getStatusCode());
            System.out.println();

            // -- Step 8: Disconnect -----------------------------------------------
            System.out.println("  Press ENTER to disconnect and exit.");
            reader.readLine();

            if (client.isConnected())
                client.close();
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
                new LocalizedText("PLCcom_Workshop_24", "en"), endpoint);

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
            appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_24", "secretpassword");
            if (appCert == null || !appCert.checkValidity())
                appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_24", 720, "Indi.An GmbH")
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

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

import com.plccom.opc.ua.builtintypes.ExpandedNodeId;
import com.plccom.opc.ua.builtintypes.ExtensionObject;
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
import com.plccom.opc.ua.encoding.binary.BinaryEncoder;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Workshop 25 - Advanced Method Calls with Structures
 *
 * Extends Workshop 24 by showing how to pass complex structured data types
 * as method arguments. Structures are encoded using BinaryEncoder into a
 * byte array, then wrapped in an ExtensionObject with the structure's TypeId.
 *
 * The structure fields must be encoded in the exact order defined by the
 * server's type system - there is no field name tagging in UA Binary encoding.
 *
 * What you will learn:
 *   - How to encode a structure with BinaryEncoder
 *   - How to wrap it in an ExtensionObject with TypeId
 *   - How to pass structured arguments to a method call
 *   - How to evaluate the method result
 *
 * Required server: Server Workshop 13 (Methods) on port 48410
 * Target server: opc.tcp://localhost:48410
 */
public class _25_AdvancedCallsWithStructs
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _25_AdvancedCallsWithStructs().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 25 - Advanced Method Calls with Structures", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 25: Advanced Calls      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Complex method arguments use OPC UA structures encoded as   ║");
            System.out.println("║  ExtensionObjects. BinaryEncoder serializes the struct       ║");
            System.out.println("║  fields in the order defined by the server's type system.    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * How to encode a structure with BinaryEncoder            ║");
            System.out.println("║    * How to wrap it in an ExtensionObject with TypeId        ║");
            System.out.println("║    * How to pass nested structures and structure arrays      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 13 (Methods)               ║");
            System.out.println("║  Requires: Server Workshop 13 (Methods)                      ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // Important !!!!!!!!!!!!!!!!!!
            // Enter your Username + Serial here! Please note: with blank fields the library runs
            // for 15 minutes during a debug session. Both values can also come
            // from configuration or an environment variable.
            // Free trial license (14 days, uninterrupted): https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/
            String licenseUser   = "";
            String licenseSerial = "";

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

            // -- Step 4: Encode nested structure and call method ---------------
            /*
             * Server Workshop 13 exposes myObjectNode_Advanced under Objects.Plant
             * with myMethodNode that expects DataStructure_One:
             *
             *   DataStructure_One = {
             *     int    myIntValue1
             *     string myStringValue2
             *     ExtensionObject(DataStructure_Two)   embedded struct
             *     int    myIntValue3
             *     ExtensionObject[](DataStructure_Two) array of structs
             *     int    trailing int
             *   }
             *
             *   DataStructure_Two = { int, string, int }
             *
             * Fields are encoded in this exact order using UA Binary encoding.
             * The namespace index (3) and type names must match the server.
             */

            // -- Encode DataStructure_Two (embedded) --------------------------
            ByteBuffer buf2 = ByteBuffer.allocate(256);
            buf2.order(ByteOrder.LITTLE_ENDIAN);
            BinaryEncoder enc2 = new BinaryEncoder(buf2);
            enc2.setEncoderContext(client.getEncoderContext());
            enc2.putInt32("", 222);
            enc2.putString("", "test_string11");
            enc2.putInt32("", 1212);
            byte[] bytes2 = new byte[buf2.position()];
            System.arraycopy(buf2.array(), 0, bytes2, 0, buf2.position());

            ExtensionObject embeddedStruct = new ExtensionObject(
                    new ExpandedNodeId(new NodeId(3, "DataStructure_Two")),
                    com.plccom.opc.ua.builtintypes.ByteString.valueOf(bytes2));

            // -- Encode DataStructure_Two array (3 items) ---------------------
            ExtensionObject[] structArray = new ExtensionObject[3];
            for (int i = 0; i < 3; i++) {
                ByteBuffer bufArr = ByteBuffer.allocate(256);
                bufArr.order(ByteOrder.LITTLE_ENDIAN);
                BinaryEncoder encArr = new BinaryEncoder(bufArr);
                encArr.setEncoderContext(client.getEncoderContext());
                encArr.putInt32("", 555);
                encArr.putString("", "test_stringArray365");
                encArr.putInt32("", 1212);
                byte[] bytesArr = new byte[bufArr.position()];
                System.arraycopy(bufArr.array(), 0, bytesArr, 0, bufArr.position());
                structArray[i] = new ExtensionObject(
                        new ExpandedNodeId(new NodeId(3, "DataStructure_Two")),
                        com.plccom.opc.ua.builtintypes.ByteString.valueOf(bytesArr));
            }

            // -- Encode DataStructure_One (outer struct) -----------------------
            ByteBuffer buf1 = ByteBuffer.allocate(2048);
            buf1.order(ByteOrder.LITTLE_ENDIAN);
            BinaryEncoder enc1 = new BinaryEncoder(buf1);
            enc1.setEncoderContext(client.getEncoderContext());
            enc1.putInt32("", 1);                    // myIntValue1
            enc1.putString("", "test_string");        // myStringValue2
            enc1.putExtensionObject("", embeddedStruct); // DataStructure_Two
            enc1.putInt32("", 3333);                  // myIntValue3
            enc1.putExtensionObjectArray("", structArray); // DataStructure_Two[]
            enc1.putInt32("", 3333);                  // trailing int

            byte[] bytes1 = new byte[buf1.position()];
            System.arraycopy(buf1.array(), 0, bytes1, 0, buf1.position());

            ExtensionObject outerStruct = new ExtensionObject(
                    new ExpandedNodeId(new NodeId(3, "DataStructure_One")),
                    com.plccom.opc.ua.builtintypes.ByteString.valueOf(bytes1));

            // -- Resolve object and method by path ----------------------------
            NodeId objectNode = client.getNodeIdByPath("Objects.Plant.myObjectNode_Advanced");
            if (objectNode == null) {
                System.err.println("  myObjectNode_Advanced not found - is Server Workshop 13 running?");
                client.close();
                return;
            }
            NodeId methodNode = client.getNodeIdByPath("Objects.Plant.myObjectNode_Advanced.myMethodNode");

            CallMethodRequest request = new CallMethodRequest();
            request.setObjectId(objectNode);
            request.setMethodId(methodNode);
            request.setInputArguments(new Variant[] { new Variant(outerStruct) });

            System.out.println("  Calling: Objects.Plant.myObjectNode_Advanced.myMethodNode(DataStructure_One)");
            CallMethodResult[] results = client.call(request);

            for (CallMethodResult result : results) {
                if (result.getStatusCode().isGood()) {
                    System.out.println("  Status: " + result.getStatusCode());
                    for (Variant out : result.getOutputArguments())
                        if (out != Variant.NULL)
                            System.out.println("  Output: " + out.getValue());
                } else {
                    System.err.println("  Method call failed: " + result.getStatusCode());
                }
            }

            System.out.println();

            // -- Step 5: Disconnect -----------------------------------------------
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
                new LocalizedText("PLCcom_Workshop_25", "en"), endpoint);

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
            appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_25", "secretpassword");
            if (appCert == null || !appCert.checkValidity())
                appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_25", 720, "Indi.An GmbH")
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

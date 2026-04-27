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

import com.plccom.opc.ua.builtintypes.DataValue;
import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.builtintypes.UnsignedInteger;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaCertificateManager;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.client.core.attributes.UaAttributes;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.Attributes;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.ReadResponse;
import com.plccom.opc.ua.core.ReadValueId;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.core.StatusCodes;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import com.plccom.opc.ua.transport.security.KeyPair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Workshop 26 - Read Attributes
 *
 * Every OPC UA node has a set of attributes beyond just its Value - such as
 * NodeClass, BrowseName, DisplayName, DataType, AccessLevel and more.
 * This workshop reads all standard attributes of a node in a single call.
 *
 * Not all attributes are valid for every node class - e.g. Value only exists
 * on Variable nodes, Executable only on Method nodes. The server returns
 * Bad_AttributeIdInvalid for unsupported attributes.
 *
 * What you will learn:
 *   - How to read all attributes of a node (NodeClass through UserExecutable)
 *   - How to interpret attribute values and data types
 *   - How to handle Bad_AttributeIdInvalid for unsupported attributes
 *
 * Target server: opc.tcp://localhost:48410
 * (Start any Server SDK workshop first, e.g. Server Workshop 11)
 */
public class _26_ReadAttributes
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _26_ReadAttributes().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 26 - Read Attributes", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 26: Read Attributes     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Every OPC UA node has attributes beyond just its Value:     ║");
            System.out.println("║  NodeClass, BrowseName, DisplayName, DataType, AccessLevel.  ║");
            System.out.println("║  This workshop reads all attributes of a node in one call.   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Read all attributes (NodeClass through UserExecutable)  ║");
            System.out.println("║    * Interpret attribute values and data types               ║");
            System.out.println("║    * Handle Bad_AttributeIdInvalid for unsupported attrs     ║");
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
                    new LocalizedText("PLCcom_Workshop_26", "en"), endpoint);

            if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
                KeyPair cert = loadOrCreateCertificate(
                        "CertificateStores/PLCcom_Workshop_26.der", "secretpassword", "PLCcom_Workshop_26");
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

            // -- Step 4: Read all attributes of a node ----------------------------
            // Resolve the target node by browse path - readable and portable.
            // getNodeIdByPath() walks the address space once and caches the result.
            String attrPath = "Objects.Plant.Line1.Machine1.Temperature";
            NodeId nodeId = client.getNodeIdByPath(attrPath);
            System.out.println("  Reading all attributes of: '" + attrPath + "'");
            System.out.println("  Resolved NodeId: " + nodeId);
            System.out.println();

            // OPC UA defines attribute IDs 1 (NodeId) through 28 (UserExecutable).
            // Not all attributes are valid for every node class - e.g. Value only
            // exists on Variable nodes, Executable only on Method nodes.
            // We request all of them in one Read service call and filter afterwards.
            List<ReadValueId> nodesToReadList = new ArrayList<>();
            for (int attrId = UaAttributes.NodeClass.getValue().intValue();
                    attrId <= Attributes.UserExecutable.intValue(); attrId++) {
                ReadValueId nodeToRead = new ReadValueId();
                nodeToRead.setNodeId(nodeId);
                nodeToRead.setAttributeId(new UnsignedInteger(attrId));
                nodesToReadList.add(nodeToRead);
            }

            // client.read() sends all ReadValueIds in a single Read service call.
            // The server returns one DataValue per request, in the same order.
            ReadValueId[] nodesToRead = nodesToReadList.toArray(new ReadValueId[0]);
            ReadResponse response = client.read(nodesToRead);
            DataValue[] results = response.getResults();

            for (int i = 0; i < results.length; i++) {
                // Bad_AttributeIdInvalid means this attribute does not exist on
                // this node class (e.g. Value on an Object node) - skip silently.
                if (results[i].getStatusCode().equals(new StatusCode(StatusCodes.Bad_AttributeIdInvalid)))
                    continue;
                String attrName = UaAttributes.valueOf(nodesToRead[i].getAttributeId()).name();
                // For bad status codes other than AttributeIdInvalid, show the
                // status code itself so the user knows why the value is missing.
                String value = results[i].getStatusCode().isBad()
                        ? results[i].getStatusCode().toString()
                        : results[i].getValue().toString(true);
                System.out.printf("  %-20s = %s%n", attrName, value);
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
    static KeyPair loadOrCreateCertificate(String certFile, String password, String alias) throws Exception {
        java.io.File f = new java.io.File(certFile);
        f.getParentFile().mkdirs();
        if (!f.isFile())
            return UaCertificateManager.createSelfSignedCertificate(certFile, alias, password, 720, "Indi.An GmbH");
        else
            return UaCertificateManager.getCertificate(certFile, password);
    }
}

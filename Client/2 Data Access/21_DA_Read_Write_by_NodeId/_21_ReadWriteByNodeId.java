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
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import com.plccom.opc.ua.transport.security.KeyPair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Workshop 21 - Read and Write by NodeId
 *
 * A NodeId is the unique address of a node in the OPC UA address space.
 * It consists of a namespace index and an identifier (numeric, string,
 * GUID or opaque). This is the low-level approach to reading and writing.
 * See Workshop 22 for the more readable path-based approach.
 *
 * NodeId formats:
 *   Numeric:  new NodeId(namespaceIndex, numericId)   e.g. new NodeId(3, 1001)
 *   String:   new NodeId(namespaceIndex, "MyVar")     e.g. new NodeId(2, "Motor.Speed")
 *   GUID:     new NodeId(namespaceIndex, uuid)
 *   Parsed:   new NodeId("ns=3;i=1001")               from OPC UA string notation
 *
 * What you will learn:
 *   - How to construct NodeIds from string notation (ns=2;i=X)
 *   - How to read single and multiple values by NodeId
 *   - How to write values and check the StatusCode
 *   - How to verify written values by reading them back
 *
 * Target server: opc.tcp://localhost:48410
 * (Start any Server SDK workshop first, e.g. Server Workshop 11)
 */
public class _21_ReadWriteByNodeId
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _21_ReadWriteByNodeId().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 21 - Read and Write by NodeId", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 21: Read/Write by NodeId║");
            System.out.println("║                                                              ║");
            System.out.println("║  A NodeId is the unique address of a node in the OPC UA      ║");
            System.out.println("║  address space (e.g. ns=2;i=10219). This workshop shows      ║");
            System.out.println("║  how to read and write values using NodeIds directly.        ║");
            System.out.println("║  See Workshop 22 for the more readable path-based approach.  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Construct NodeIds from string notation                  ║");
            System.out.println("║    * Read single and multiple values (sync)                  ║");
            System.out.println("║    * Write values and check the StatusCode                   ║");
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
                    new LocalizedText("PLCcom_Workshop_21", "en"), endpoint);

            if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
                KeyPair cert = loadOrCreateCertificate(
                        "CertificateStores/PLCcom_Workshop_21.der", "secretpassword", "PLCcom_Workshop_21");
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

            // -- Step 4: Construct NodeIds ----------------------------------------
            // NodeIds for Server Workshop 11 - assigned in creation order.
            // ns=2 is the application namespace; i= is the sequential node counter.
            // Internal server nodes occupy i=1..8, app nodes start at i=9.
            // Plant=9, Line1=10, Machine1=11, Temperature=12, Pressure=13, RPM=14
            NodeId temperatureId = new NodeId("ns=2;i=12");  // Double
            NodeId pressureId    = new NodeId("ns=2;i=13");  // Float
            NodeId rpmId         = new NodeId("ns=2;i=14");  // Int32

            // -- Step 4a: Read single value --------------------------------------
            System.out.println("  -- Read single value --");
            ReadResponse res = client.readValue(temperatureId);
            DataValue dv = res.getResults()[0];
            System.out.println("  NodeId:     " + temperatureId);
            System.out.println("  Value:      " + dv.getValue().getValue());
            System.out.println("  StatusCode: " + dv.getStatusCode());
            System.out.println();

            // -- Step 5: Read multiple values in one call -----------------------
            // Reading multiple nodes in a single request is more efficient
            // than sending one request per node.
            System.out.println("  -- Read multiple values in one call --");
            ReadResponse multiRes = client.readValue(temperatureId, rpmId, pressureId);
            System.out.println("  " + temperatureId + ": " + multiRes.getResults()[0].getValue().getValue()
                    + "  (" + multiRes.getResults()[0].getStatusCode() + ")");
            System.out.println("  " + rpmId + ": " + multiRes.getResults()[1].getValue().getValue()
                    + "  (" + multiRes.getResults()[1].getStatusCode() + ")");
            System.out.println("  " + pressureId + ": " + multiRes.getResults()[2].getValue().getValue()
                    + "  (" + multiRes.getResults()[2].getStatusCode() + ")");
            System.out.println();

            // -- Step 6: Write a value by NodeId ---------------------------------
            // writeValue() takes the NodeId and the new value.
            // The SDK converts the Java object to the correct OPC UA type.
            System.out.println("  -- Write value --");
            System.out.println("  Writing 25.5 to " + temperatureId + " ...");
            StatusCode writeStatus1 = client.writeValue(temperatureId, 25.5);
            System.out.println("  StatusCode: " + writeStatus1);

            System.out.println("  Writing 1750 to " + rpmId + " ...");
            StatusCode writeStatus2 = client.writeValue(rpmId, 1750);
            System.out.println("  StatusCode: " + writeStatus2);

            System.out.println("  Writing 1.05 to " + pressureId + " ...");
            StatusCode writeStatus3 = client.writeValue(pressureId, 1.05f);
            System.out.println("  StatusCode: " + writeStatus3);

            // Read back to verify
            ReadResponse verify = client.readValue(temperatureId, rpmId, pressureId);
            System.out.println("  " + temperatureId + " after write: " + verify.getResults()[0].getValue().getValue());
            System.out.println("  " + rpmId + " after write: " + verify.getResults()[1].getValue().getValue());
            System.out.println("  " + pressureId + " after write: " + verify.getResults()[2].getValue().getValue());
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

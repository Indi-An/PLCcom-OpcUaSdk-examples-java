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
import com.plccom.opc.ua.builtintypes.DateTime;
import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.UnsignedInteger;
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.UaSubscription;
import com.plccom.opc.ua.client.application.listener.MonitoredItemNotificationListener;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.client.application.listener.SubscriptionListener;
import com.plccom.opc.ua.core.Attributes;
import com.plccom.opc.ua.common.ServiceResultException;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.EventFieldList;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.client.application.MonitoredItem;
import com.plccom.opc.ua.core.MonitoredItemCreateRequest;
import com.plccom.opc.ua.core.MonitoringMode;
import com.plccom.opc.ua.core.MonitoringParameters;
import com.plccom.opc.ua.core.ReadValueId;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import com.plccom.opc.ua.transport.security.KeyPair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.plccom.opc.ua.client.application.UaCertificateManager;

/**
 * Workshop 71 - Reverse Connect
 *
 * In a standard OPC UA connection the CLIENT connects to the SERVER.
 * Reverse Connect turns this around: the SERVER connects to the CLIENT.
 *
 * This is useful when the server is behind a firewall or NAT and cannot
 * accept incoming TCP connections, but is allowed to make outgoing ones.
 *
 * How it works:
 *   1. The client opens a listening port (listenUrl).
 *   2. The server periodically sends a ReverseHello message to that port.
 *   3. The client receives the ReverseHello and establishes the OPC UA session
 *      over the server-initiated TCP connection.
 *
 * From the application perspective the API is almost identical to a normal
 * connect() - just two extra calls:
 *   - startReverseConnectListener(listenUrl)  ... open the listening port
 *   - connectReverse(timeout)                 ... wait for ReverseHello + open session
 *
 * What you will learn:
 *   - How Reverse Connect differs from standard connections
 *   - How to configure the client for Reverse Connect
 *   - How to wait for the server to connect back
 *   - How the session works identically after connect
 *
 * Listen URL: opc.tcp://localhost:48500
 * Target server: opc.tcp://localhost:48410
 */
public class _71_ReverseConnect
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener,
                   CertificateValidator, MonitoredItemNotificationListener, SubscriptionListener {

    public static void main(String[] args) {
        new _71_ReverseConnect().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 71 - Reverse Connect", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 71: Reverse Connect     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  With Reverse Connect, the server initiates the TCP          ║");
            System.out.println("║  connection to the client. Useful when the server is         ║");
            System.out.println("║  behind a firewall and cannot accept connections.            ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * How Reverse Connect differs from standard mode          ║");
            System.out.println("║    * Configure the client for Reverse Connect                ║");
            System.out.println("║    * Wait for the server to connect back                     ║");
            System.out.println("║                                                              ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 71 (Reverse Connect)       ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // TODO: Replace with your license credentials from your license e-mail
            String licenseUser   = "<Enter your UserName here>";
            String licenseSerial = "<Enter your Serial here>";

            // -- Step 1: Configure the reverse connect listen URL ----------------
            // The client opens this port and waits for the server to connect.
            // The server must be configured to send ReverseHello to exactly this URL.
            String listenUrl = "opc.tcp://localhost:48500";

            // The server's own endpoint URL - used to configure the OPC UA session.
            // This is the server's normal endpoint, NOT the reverse-connect listen URL.
            String serverEndpointUrl = "opc.tcp://localhost:48410";

            System.out.println("  Listen URL:  " + listenUrl);
            System.out.println("  Server URL:  " + serverEndpointUrl);
            System.out.println();

            // -- Step 2: Build the endpoint description manually ---------------
            // For reverse connect we cannot discover endpoints first (the server
            // is not reachable from the client). We build the endpoint manually.
            // Set SecurityMode and SecurityPolicyUri to match the desired endpoint
            // on the server - must match exactly what the server offers.
            // UserIdentityTokens must include Anonymous so the stack can build
            // the correct token for ActivateSession.
            EndpointDescription endpoint = new EndpointDescription();
            endpoint.setEndpointUrl(serverEndpointUrl);
            endpoint.setSecurityMode(MessageSecurityMode.None);
            com.plccom.opc.ua.core.UserTokenPolicy anonPolicy = new com.plccom.opc.ua.core.UserTokenPolicy();
            anonPolicy.setTokenType(com.plccom.opc.ua.core.UserTokenType.Anonymous);
            endpoint.setUserIdentityTokens(new com.plccom.opc.ua.core.UserTokenPolicy[]{ anonPolicy });

            // -- Step 3: Build configuration -------------------------------------
            // For Reverse Connect we build the endpoint manually (no discovery).
            // ClientConfiguration wraps the endpoint together with the application
            // name and certificate.
            ClientConfiguration config = new ClientConfiguration(
                    new LocalizedText("PLCcom_Workshop_71", "en"), endpoint);

            // Reverse Connect requires an application instance certificate even for
            // None security - the SDK needs it to build the SecureChannel internally.
            KeyPair cert = loadOrCreateCertificate(
                    "CertificateStores/PLCcom_Workshop_71.der", "secretpassword", "PLCcom_Workshop_71");
            config.setInstanceCertificate(cert);
            // Accept all server certificates for development.
            config.setCertificateValidator(this);

            UaClient client = new UaClient(licenseUser, licenseSerial, config);
            client.addSessionKeepAliveListener(this);
            client.addSessionConnectionStateChangeListener(this);
            client.getSubscriptionManager().addSubscriptionListener(this);

            // -- Step 4: Start listening and wait for ReverseHello ----------------
            System.out.println("  Opening listen port: " + listenUrl);
            client.startReverseConnectListener(listenUrl);
            System.out.println("  Waiting for server to connect (timeout 60s)...");
            System.out.println();

            // connectReverse() blocks until the server sends a ReverseHello
            // or the timeout expires. Internally it mirrors connect() - same
            // certificate and security logic applies.
            client.connectReverse(60000);

            System.out.println("  Connected!");
            System.out.println("  License: " + client.getLicenceMessage());
            System.out.println();

            // -- Step 5: Use the session normally --------------------------------
            // After connectReverse() the session works exactly like after connect().
            // We monitor the Temperature variable from the Server Workshop 71.
            UaSubscription subscription = client.getSubscriptionManager().createSubscription(1000.0);

            NodeId tempNodeId = client.getNodeIdByPath("Objects.Plant.Temperature");
            System.out.println("  Monitoring: Objects.Plant.Temperature  ->  " + tempNodeId);
            System.out.println();

            MonitoringParameters params = new MonitoringParameters(
                    UnsignedInteger.valueOf(1), // clientHandle
                    500.0,                      // samplingInterval ms
                    null,                       // no filter
                    UnsignedInteger.MAX_VALUE,  // queueSize
                    true);                      // discardOldest

            subscription.createMonitoredItems(
                    new MonitoredItemCreateRequest(
                            new ReadValueId(tempNodeId, Attributes.Value, null, null),
                            MonitoringMode.Reporting, params),
                    this);

            System.out.println("  Monitoring Temperature - values appear below.");

            // -- Step 6: Disconnect -----------------------------------------------
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("  Press ENTER to disconnect and exit.");
            reader.readLine();

            client.getSubscriptionManager().closeAndClearAllSubscriptions();
            if (client.isConnected()) {
                client.close();
            }
            System.out.println("  Disconnected.");

        } catch (java.util.concurrent.TimeoutException ex) {
            System.err.println("  Timeout: " + ex.getMessage());
            System.err.println("  Is the server running and configured for Reverse Connect?");
            System.out.println("  Press ENTER to exit.");
            try { System.in.read(); } catch (Exception ignored) { }
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

    // Called whenever the session connects or disconnects.
    // For Reverse Connect, isConnected=true fires after connectReverse() succeeds.
    @Override
    public void onSessionConnectionStateChanged(boolean isConnected) {
        if (isConnected) {
            System.out.println("  [Connected] Session established");
        } else {
            System.out.println("  [ConnectionLost] Connection lost");
        }
    }

    // Called for each value change notification on the monitored Temperature node.
    @Override
    public void onValueNotification(MonitoredItem item, DataValue value) {
        String time = "?";
        if (value.getSourceTimestamp() != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss.SSS 'UTC'");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            time = sdf.format(value.getSourceTimestamp().getCalendar(
                    java.util.TimeZone.getTimeZone("UTC")).getTime());
        }
        System.out.printf("  [VALUE] %s  Objects.Plant.Temperature = %s  (%s)%n",
                time,
                value.getValue() != null ? value.getValue().getValue() : "null",
                value.getStatusCode());
    }

    // Not used - we monitor a data variable, not an event source.
    @Override public void onEventNotification(MonitoredItem item, EventFieldList e) { }

    // Called when the server reports a status change on the subscription.
    @Override public void onStatusChangeNotification(UaSubscription s, StatusCode sc) { }

    // Called when a Publish request to the server fails.
    @Override public void onPublishFailure(ServiceResultException e) { }

    // Called when notification messages were lost (sequence number gap).
    @Override public void onNotificationDataLost(UaSubscription s) { }

    // Called when the server sends a keep-alive for the subscription.
    @Override public void onSubscriptionKeepAlive(UaSubscription s, DateTime t) { }

    // Accept all server certificates for development.
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
     * Loads an existing certificate or creates a new self-signed one.
     *
     * @param certFile path to the .der certificate file
     * @param password password for the private key
     * @param alias    common name (CN) for a new certificate
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

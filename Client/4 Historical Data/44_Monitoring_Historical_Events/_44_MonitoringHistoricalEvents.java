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

import com.plccom.opc.ua.builtintypes.ByteString;
import com.plccom.opc.ua.builtintypes.DataValue;
import com.plccom.opc.ua.builtintypes.DateTime;
import com.plccom.opc.ua.builtintypes.ExtensionObject;
import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.QualifiedName;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.builtintypes.UnsignedInteger;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.MonitoredItem;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaCertificateManager;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.UaSubscription;
import com.plccom.opc.ua.client.application.listener.MonitoredItemNotificationListener;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.client.application.listener.SubscriptionListener;
import com.plccom.opc.ua.common.ServiceResultException;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.Attributes;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.EventFieldList;
import com.plccom.opc.ua.core.EventFilter;
import com.plccom.opc.ua.core.Identifiers;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.MonitoredItemCreateRequest;
import com.plccom.opc.ua.core.MonitoringMode;
import com.plccom.opc.ua.core.MonitoringParameters;
import com.plccom.opc.ua.core.ReadValueId;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.core.SimpleAttributeOperand;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import com.plccom.opc.ua.transport.security.KeyPair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.time.LocalTime;

/**
 * Workshop 44 - Monitor Historical Events
 *
 * This workshop subscribes to live events from a node that also has
 * event history enabled. New events arrive in real-time via subscription
 * and are also stored in the server's event history for later retrieval.
 *
 * What you will learn:
 *   - How to subscribe to live events from a history-enabled source node
 *   - How to receive and display event notifications in real-time
 *   - The difference between live events (subscription) and
 *     historical events (HistoryRead) - see Workshop 43
 *
 * Target server: opc.tcp://localhost:48410
 */
public class _44_MonitoringHistoricalEvents
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener,
                   CertificateValidator, MonitoredItemNotificationListener, SubscriptionListener {

    // Field names in UaAlarm.FIELD_* order - the server returns fields in this order
    // regardless of the filter select clause order.
    // Order: EventId(0), EventType(1), SourceNode(2), SourceName(3), Time(4), Message(5), Severity(6)
    private static final String[] FIELD_NAMES =
            { "EventId", "EventType", "SourceNode", "SourceName", "Time", "Message", "Severity" };
    private static final int IDX_EVENTID = 0;

    public static void main(String[] args) {
        new _44_MonitoringHistoricalEvents().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 44 - Monitor Historical Events", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 44: Monitor Hist. Events║");
            System.out.println("║                                                              ║");
            System.out.println("║  Subscribes to live events from a node that also has event   ║");
            System.out.println("║  history enabled. New events arrive in real-time and are     ║");
            System.out.println("║  also stored in the server's history for later retrieval.    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Subscribe to live events from a history-enabled node    ║");
            System.out.println("║    * Receive and display event notifications in real-time    ║");
            System.out.println("║    * Difference: live events vs. HistoryRead (WS 43)         ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 33 (Historical Events)     ║");
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
                System.err.println("  No endpoints found. Is Server Workshop 33 running?");
                System.out.println("  Press ENTER to exit.");
                try { System.in.read(); } catch (Exception ignored) { }
                return;
            }

            endpoints = UaClient.sortBySecurityLevel(endpoints, SortDirection.Asc);

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

            // -- Step 2: Build configuration and connect --------------------------
            ClientConfiguration config = new ClientConfiguration(
                    new LocalizedText("PLCcom_Workshop_44", "en"), endpoint);

            if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
                KeyPair cert = loadOrCreateCertificate(
                        "CertificateStores/PLCcom_Workshop_44.der", "secretpassword", "PLCcom_Workshop_44");
                config.setInstanceCertificate(cert);
            }

            config.setCertificateValidator(this);

            UaClient client = new UaClient(licenseUser, licenseSerial, config);
            System.out.println("  License: " + client.getLicenceMessage());
            System.out.println();

            client.addSessionKeepAliveListener(this);
            client.addSessionConnectionStateChangeListener(this);
            client.getSubscriptionManager().addSubscriptionListener(this);

            System.out.print("  Connecting ... ");
            client.connect();
            System.out.println("OK");
            System.out.println();

            // -- Step 3: Resolve the reactor node (event source) ------------------
            // Server Workshop 33 creates: Plant -> Reactor with EnableEvents() + EnableHistoryEvents()
            NodeId nodeId = client.getNodeIdByPath("Objects.Plant.Reactor");
            if (nodeId == null) {
                System.out.println("  Could not find 'Objects.Plant.Reactor'.");
                System.out.println("  Is Server Workshop 33 running?");
                System.out.println("  Press ENTER to exit.");
                reader.readLine();
                client.close();
                return;
            }
            System.out.println("  Reactor NodeId: " + nodeId);
            System.out.println();

            // -- Step 4: Build event filter ---------------------------------------
            // BaseEventType filter: receives all events regardless of type.
            // Field order defines the index in EventFieldList.getEventFields().
            SimpleAttributeOperand[] selectClauses = new SimpleAttributeOperand[FIELD_NAMES.length];
            for (int i = 0; i < FIELD_NAMES.length; i++) {
                selectClauses[i] = new SimpleAttributeOperand(
                        Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, FIELD_NAMES[i]) },
                        Attributes.Value, null);
            }
            EventFilter eventFilter = new EventFilter(selectClauses, null);

            // -- Step 5: Create subscription and monitored item -------------------
            // Subscribe to the reactor node's EventNotifier attribute.
            // The reactor has EnableEvents() so it propagates events to the Server node.
            UaSubscription subscription = client.getSubscriptionManager().createSubscription(1000.0);

            MonitoringParameters params = new MonitoringParameters(
                    UnsignedInteger.valueOf(1), // clientHandle
                    0.0,                        // samplingInterval: 0 = server decides
                    new ExtensionObject(eventFilter),
                    UnsignedInteger.MAX_VALUE,  // queueSize
                    true);                      // discardOldest

            MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                    new ReadValueId(Identifiers.Server, Attributes.EventNotifier, null, null),
                    MonitoringMode.Reporting, params);

            subscription.createMonitoredItems(request, this);

            System.out.println("  Monitoring live events on: 'Objects.Plant.Reactor'");
            System.out.println("  Live event notifications appear below.");
            System.out.println("  Press ENTER to stop and exit.");
            System.out.println();
            reader.readLine();

            // -- Step 6: Disconnect -----------------------------------------------
            client.getSubscriptionManager().closeAndClearAllSubscriptions();
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

    // ── MonitoredItemNotificationListener ───────────────────────────────────

    // Not used - we monitor the EventNotifier attribute, not a data variable.
    @Override
    public void onValueNotification(MonitoredItem item, DataValue value) { }

    // Called for each live event notification.
    @Override
    public void onEventNotification(MonitoredItem item, EventFieldList ef) {
        if (ef.getEventFields() == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(LocalTime.now()).append(" new event notification:\n");
        for (int i = 0; i < Math.min(ef.getEventFields().length, FIELD_NAMES.length); i++) {
            Object val = ef.getEventFields()[i] != null ? ef.getEventFields()[i].getValue() : null;
            if (val == null) continue;
            String display = (i == IDX_EVENTID && val instanceof ByteString)
                    ? byteArrayToHex(((ByteString) val).getValue())
                    : val.toString();
            sb.append("  ").append(FIELD_NAMES[i]).append(" = ").append(display).append("\n");
        }
        System.out.println(sb.toString());
    }

    // ── SubscriptionListener ────────────────────────────────────────────────

    @Override
    public void onStatusChangeNotification(UaSubscription s, StatusCode sc) {
        System.out.println("  Subscription state changed: " + sc);
    }

    @Override public void onPublishFailure(ServiceResultException e) { }
    @Override public void onNotificationDataLost(UaSubscription s) { }
    @Override public void onSubscriptionKeepAlive(UaSubscription s, DateTime t) { }

    // ── SessionKeepAliveListener / SessionConnectionStateChangeListener ──────

    @Override
    public void onSessionKeepAlive(ServerStatusDataType status, ServerState state) { }

    @Override
    public void onSessionConnectionStateChanged(boolean isConnected) {
        if (isConnected)
            System.out.println("  [Connected] Session established");
        else
            System.out.println("  [ConnectionLost] Connection lost");
    }

    // ── CertificateValidator ─────────────────────────────────────────────────

    @Override
    public StatusCode validateCertificate(Cert cert) { return StatusCode.GOOD; }

    @Override
    public StatusCode validateCertificate(ApplicationDescription app, Cert cert) { return StatusCode.GOOD; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String byteArrayToHex(byte[] ba) {
        if (ba == null) return "(null)";
        StringBuilder sb = new StringBuilder(ba.length * 2);
        for (byte b : ba) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static KeyPair loadOrCreateCertificate(String certFile, String password, String alias) throws Exception {
        java.io.File f = new java.io.File(certFile);
        f.getParentFile().mkdirs();
        if (!f.isFile())
            return UaCertificateManager.createSelfSignedCertificate(certFile, alias, password, 720, "Indi.An GmbH");
        else
            return UaCertificateManager.getCertificate(certFile, password);
    }
}

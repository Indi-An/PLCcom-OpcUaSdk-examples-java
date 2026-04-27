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
import com.plccom.opc.ua.builtintypes.QualifiedName;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.builtintypes.Variant;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaCertificateManager;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.UaSubscription;
import com.plccom.opc.ua.client.application.listener.MonitoredItemNotificationListener;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.client.application.listener.SubscriptionListener;
import com.plccom.opc.ua.client.core.attributes.UaAttributes;
import com.plccom.opc.ua.common.ServiceResultException;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.Attributes;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.EventFieldList;
import com.plccom.opc.ua.core.EventFilter;
import com.plccom.opc.ua.builtintypes.ExtensionObject;
import com.plccom.opc.ua.core.Identifiers;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.client.application.MonitoredItem;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workshop 32 - Alarm List
 *
 * Extends Workshop 31 by maintaining a local alarm cache. The Retain flag
 * in the event notification indicates whether an alarm is still active.
 * Active alarms are added to the cache, cleared alarms are removed.
 *
 * What you will learn:
 *   - How to use the Retain flag to manage an alarm list
 *   - How to add/remove alarms from a local ConcurrentHashMap cache
 *   - How to display the current active alarm list on each update
 *
 * Target server: opc.tcp://localhost:48410
 */
public class _32_AlarmList
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener,
                   CertificateValidator, MonitoredItemNotificationListener, SubscriptionListener {

    /** Local cache of active alarms: key = ConditionName, value = formatted alarm info */
    private final ConcurrentHashMap<String, String> activeAlarms = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        new _32_AlarmList().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 32 - Alarm List", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 32: Alarm List          ║");
            System.out.println("║                                                              ║");
            System.out.println("║  A real application needs to maintain a list of currently    ║");
            System.out.println("║  active alarms. The OPC UA Retain flag tells the client      ║");
            System.out.println("║  whether an alarm is still active (true) or cleared (false). ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Use the Retain flag to manage an alarm list             ║");
            System.out.println("║    * Add/remove alarms from a local cache                    ║");
            System.out.println("║    * Display the current active alarm list                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 22 (Alarm Conditions)      ║");
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
            ClientConfiguration config = new ClientConfiguration(
                    new LocalizedText("PLCcom_Workshop_32", "en"), endpoint);
            config.setDefaultPublishingInterval(1000.0);

            if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
                KeyPair cert = loadOrCreateCertificate(
                        "CertificateStores/PLCcom_Workshop_32.der", "secretpassword", "PLCcom_Workshop_32");
                config.setInstanceCertificate(cert);
            }

            // Accept all server certificates for development.
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

            // -- Step 4: Subscribe to alarm events with Retain field -----------
            UaSubscription subscription = client.getSubscriptionManager().createSubscription();

            SimpleAttributeOperand[] selectClauses = {
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "EventId") },       Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "SourceName") },    Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "Message") },       Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "Severity") },      Attributes.Value, null),
                // Retain = true means the alarm is still active
                new SimpleAttributeOperand(Identifiers.ConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "Retain") },        Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.ConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "ConditionName") }, Attributes.Value, null),
            };

            MonitoringParameters params = new MonitoringParameters();
            params.setSamplingInterval(0.0);
            params.setFilter(new ExtensionObject(new EventFilter(selectClauses, null)));

            List<MonitoredItemCreateRequest> requests = new ArrayList<>();
            requests.add(new MonitoredItemCreateRequest(
                    new ReadValueId(Identifiers.Server, UaAttributes.EventNotifier.getValue(), null, null),
                    MonitoringMode.Reporting, params));
            subscription.createMonitoredItems(requests, this);

            System.out.println("  Listening for alarms - active alarm list updates appear below.");

            // -- Step 5: Disconnect -----------------------------------------------
            System.out.println("  Press ENTER to stop and exit.");
            reader.readLine();

            client.getSubscriptionManager().closeAndClearAllSubscriptions();
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

    // Called whenever the session connects or disconnects.
    @Override
    public void onSessionConnectionStateChanged(boolean isConnected) {
        if (isConnected) {
            System.out.println("  [Connected] Session established");
        } else {
            System.out.println("  [ConnectionLost] Connection lost");
        }
    }

    // Not used - we monitor the EventNotifier attribute, not a data variable.
    @Override
    public void onValueNotification(MonitoredItem item, DataValue value) { }

    // Called for each alarm event. Updates the active alarm cache based on the
    // Retain flag: Retain=true means the alarm is still active, false means cleared.
    @Override
    public void onEventNotification(MonitoredItem item, EventFieldList ef) {
        Variant[] f = ef.getEventFields();
        if (f == null || f.length < 6) return;

        String source   = f[1] != null ? String.valueOf(f[1].getValue()) : "?";
        String message  = f[2] != null && f[2].getValue() instanceof LocalizedText
                ? ((LocalizedText) f[2].getValue()).getText() : "?";
        String severity = f[3] != null ? String.valueOf(f[3].getValue()) : "?";
        boolean retain  = f[4] != null && f[4].getValue() instanceof Boolean && (Boolean) f[4].getValue();
        String condName = f[5] != null ? String.valueOf(f[5].getValue()) : source;

        // Log the notification
        String status = retain ? "ALARM ON " : "ALARM OFF";
        System.out.println();
        System.out.println("  [" + status + "] " + java.time.LocalTime.now().toString().substring(0, 8));
        System.out.println("    Source    : " + source);
        System.out.println("    Alarm     : " + condName);
        System.out.println("    Message   : " + message);
        System.out.println("    Severity  : " + severity);
        System.out.println("    Retain    : " + retain);

        // Add or remove from active alarm cache based on Retain flag
        if (retain) {
            activeAlarms.put(condName, "Source=" + source + "  Severity=" + severity + "  " + message);
        } else {
            activeAlarms.remove(condName);
        }

        // Print current alarm list
        System.out.println();
        System.out.println("  ── Active Alarms (" + activeAlarms.size() + ") ──");
        if (activeAlarms.isEmpty()) {
            System.out.println("    (no active alarms)");
        } else {
            activeAlarms.forEach((k, v) -> System.out.println("    [" + k + "] " + v));
        }
        System.out.println();
    }

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

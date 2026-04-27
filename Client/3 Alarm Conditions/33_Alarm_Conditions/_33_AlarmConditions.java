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
import com.plccom.opc.ua.builtintypes.NodeId;
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
import com.plccom.opc.ua.core.Attributes;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.CallMethodRequest;
import com.plccom.opc.ua.core.CallMethodResult;
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
 * Workshop 33 - Alarm Conditions
 *
 * Extends Workshop 32 with the full operator workflow: acknowledge, confirm,
 * add comments and enable/disable conditions. Uses the same alarm cache
 * as Workshop 32 but adds interactive commands.
 *
 * What you will learn:
 *   - How to acknowledge an alarm condition
 *   - How to confirm an alarm condition
 *   - How to add comments to conditions
 *   - How to enable/disable conditions
 *   - How to refresh the alarm list from the server
 *
 * Required server: Server Workshop 22 (Alarm Conditions)
 * Target server: opc.tcp://localhost:48410
 */
public class _33_AlarmConditions
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener,
                   CertificateValidator, MonitoredItemNotificationListener, SubscriptionListener {

    private UaClient client;
    private UaSubscription subscription;

    // f[0]=EventId, f[1]=SourceName, f[2]=Message, f[3]=Severity,
    // f[4]=Retain, f[5]=ConditionName, f[6]=AckedState, f[7]=ConditionId
    private static final int F_EVENT_ID    = 0;
    private static final int F_SOURCE      = 1;
    private static final int F_MESSAGE     = 2;
    private static final int F_SEVERITY    = 3;
    private static final int F_RETAIN      = 4;
    private static final int F_COND_NAME   = 5;
    private static final int F_ACKED_STATE = 6;
    private static final int F_COND_ID     = 7;
    private static final int F_COMMENT     = 8;
    private static final int F_ENABLED     = 9;
    private static final int F_ACTIVE      = 10;
    private static final int F_CONFIRMED   = 11;

    private static class AlarmEntry {
        final NodeId  conditionId;
        final byte[]  eventId;
        final String  source;
        final String  conditionName;
        final String  message;
        final String  severity;
        final String  ackedState;
        final String  comment;
        final String  enabledState;
        final String  activeState;
        final String  confirmedState;
        AlarmEntry(NodeId conditionId, byte[] eventId, String source,
                   String conditionName, String message, String severity, String ackedState,
                   String comment, String enabledState, String activeState, String confirmedState) {
            this.conditionId    = conditionId;
            this.eventId        = eventId;
            this.source         = source;
            this.conditionName  = conditionName;
            this.message        = message;
            this.severity       = severity;
            this.ackedState     = ackedState;
            this.comment        = comment;
            this.enabledState   = enabledState;
            this.activeState    = activeState;
            this.confirmedState = confirmedState;
        }
    }

    private final ConcurrentHashMap<String, AlarmEntry> alarmCache = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        new _33_AlarmConditions().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 33 - Alarm Conditions", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 33: Alarm Conditions    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Extends Workshop 32 with the full operator workflow:        ║");
            System.out.println("║  acknowledge, confirm, add comments, enable/disable.         ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Acknowledge an alarm condition                          ║");
            System.out.println("║    * Confirm an alarm condition                              ║");
            System.out.println("║    * Add comments to conditions                              ║");
            System.out.println("║    * Enable/disable conditions                               ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 22 (Alarm Conditions)      ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

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
                return;
            }

            EndpointDescription endpoint = endpoints[index];
            System.out.println();
            System.out.println("  Selected: " + OpcUaDisplayUtils.toDisplayString(endpoint));
            System.out.println();

            // -- Step 2: Connect --------------------------------------------------
            ClientConfiguration config = new ClientConfiguration(
                    new LocalizedText("PLCcom_Workshop_33", "en"), endpoint);
            config.setDefaultPublishingInterval(1000.0);
            if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
                KeyPair cert = loadOrCreateCertificate(
                        "CertificateStores/PLCcom_Workshop_33.der", "secretpassword", "PLCcom_Workshop_33");
                config.setInstanceCertificate(cert);
            }
            config.setCertificateValidator(this);

            client = new UaClient(licenseUser, licenseSerial, config);
            System.out.println("  License: " + client.getLicenceMessage());
            System.out.println();

            client.addSessionKeepAliveListener(this);
            client.addSessionConnectionStateChangeListener(this);
            client.getSubscriptionManager().addSubscriptionListener(this);

            System.out.print("  Connecting ... ");
            client.connect();
            System.out.println("OK");
            System.out.println();

            // -- Step 3: Filter selection -----------------------------------------
            System.out.println("  Select alarm filter:");
            System.out.println("    1 - All conditions");
            System.out.println("    2 - Dialogs");
            System.out.println("    3 - Alarms");
            System.out.println("    4 - Limit alarms");
            System.out.println("    5 - Discrete alarms");
            System.out.print("  Choice [1]: ");
            String filterChoice = reader.readLine().trim();
            System.out.println();

            NodeId filterTypeId;
            switch (filterChoice) {
                case "2":  filterTypeId = Identifiers.DialogConditionType; break;
                case "3":  filterTypeId = Identifiers.AlarmConditionType; break;
                case "4":  filterTypeId = Identifiers.ExclusiveLimitAlarmType; break;
                case "5":  filterTypeId = Identifiers.DiscreteAlarmType; break;
                default:   filterTypeId = Identifiers.ConditionType; break;
            }

            // -- Step 4: Subscribe ------------------------------------------------
            // Select clauses - same order as F_* constants above
            subscription = client.getSubscriptionManager().createSubscription();

            SimpleAttributeOperand[] selectClauses = {
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "EventId") },       Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "SourceName") },    Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "Message") },       Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "Severity") },      Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.ConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "Retain") },        Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.ConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "ConditionName") }, Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.AcknowledgeableConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "AckedState") },    Attributes.Value, null),
                // ConditionId: read the NodeId attribute of the ConditionType node itself
                new SimpleAttributeOperand(Identifiers.ConditionType,
                        new QualifiedName[0], Attributes.NodeId, null),
                new SimpleAttributeOperand(Identifiers.ConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "Comment") },       Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.ConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "EnabledState") },  Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.AlarmConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "ActiveState") },   Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.AcknowledgeableConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "ConfirmedState") },Attributes.Value, null),
            };

            com.plccom.opc.ua.core.LiteralOperand typeOperand = new com.plccom.opc.ua.core.LiteralOperand();
            typeOperand.setValue(new Variant(filterTypeId));
            com.plccom.opc.ua.core.ContentFilterElement ofTypeElement = new com.plccom.opc.ua.core.ContentFilterElement(
                    com.plccom.opc.ua.core.FilterOperator.OfType,
                    new ExtensionObject[]{ ExtensionObject.binaryEncode(typeOperand, client.getEncoderContext()) });
            com.plccom.opc.ua.core.ContentFilter whereClause = new com.plccom.opc.ua.core.ContentFilter(
                    new com.plccom.opc.ua.core.ContentFilterElement[]{ ofTypeElement });

            MonitoringParameters params = new MonitoringParameters();
            params.setSamplingInterval(0.0);
            params.setFilter(new ExtensionObject(new EventFilter(selectClauses, whereClause)));

            List<MonitoredItemCreateRequest> requests = new ArrayList<>();
            requests.add(new MonitoredItemCreateRequest(
                    new ReadValueId(Identifiers.Server, UaAttributes.EventNotifier.getValue(), null, null),
                    MonitoringMode.Reporting, params));
            subscription.createMonitoredItems(requests, this);

            client.refreshConditions(subscription);

            System.out.println("  Monitoring active. Alarm notifications appear in the background.");
            System.out.println();

            // -- Step 5: Interactive command loop ---------------------------------
            String command;
            do {
                System.out.println("  Commands:");
                System.out.println("    1 - List all alarms");
                System.out.println("    2 - Refresh alarms from server");
                System.out.println("    3 - Enable alarm");
                System.out.println("    4 - Disable alarm");
                System.out.println("    5 - Acknowledge alarm");
                System.out.println("    6 - Add comment");
                System.out.println("    7 - Confirm alarm");
                System.out.println("    0 - Exit");
                System.out.print("  > ");
                command = reader.readLine().trim();

                switch (command) {
                    case "1": listAlarms(); break;
                    case "2":
                        client.refreshConditions(subscription);
                        Thread.sleep(1000);
                        listAlarms();
                        break;
                    case "3": enableDisable(reader, true);  break;
                    case "4": enableDisable(reader, false); break;
                    case "5": acknowledge(reader); break;
                    case "6": addComment(reader);  break;
                    case "7": confirm(reader);     break;
                    case "0": break;
                    default:  System.out.println("  Unknown command."); break;
                }
                System.out.println();
            } while (!"0".equals(command));

            // -- Step 6: Disconnect -----------------------------------------------
            client.getSubscriptionManager().closeAndClearAllSubscriptions();
            if (client.isConnected()) client.close();
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

    // ── Alarm operations ────────────────────────────────────────────────────

    private void listAlarms() {
        System.out.println();
        System.out.println("  ── Active Alarms (" + alarmCache.size() + ") ──");
        if (alarmCache.isEmpty()) {
            System.out.println("    (no active alarms)");
            return;
        }
        int i = 0;
        for (AlarmEntry ae : alarmCache.values()) {
            System.out.printf("    %d  %-18s %-22s Sev=%-5s%n", i, ae.source, ae.conditionName, ae.severity);
            System.out.printf("       Active=%-10s Acked=%-15s Confirmed=%-12s Enabled=%-8s%n",
                    ae.activeState, ae.ackedState, ae.confirmedState, ae.enabledState);
            System.out.printf("       Message: %s%n", ae.message);
            System.out.printf("       Comment: %s%n", ae.comment.isEmpty() ? "(none)" : ae.comment);
            i++;
        }
    }

    private AlarmEntry getAlarmByIndex(BufferedReader reader) throws Exception {
        System.out.print("  Enter alarm number: ");
        int num = Integer.parseInt(reader.readLine().trim());
        AlarmEntry[] entries = alarmCache.values().toArray(new AlarmEntry[0]);
        if (num < 0 || num >= entries.length) {
            System.out.println("  Alarm number out of range.");
            return null;
        }
        return entries[num];
    }

    private void acknowledge(BufferedReader reader) {
        try {
            listAlarms();
            AlarmEntry ae = getAlarmByIndex(reader);
            if (ae == null || ae.conditionId == null) { System.out.println("  No conditionId available."); return; }
            System.out.print("  Comment: ");
            String comment = reader.readLine().trim();
            CallMethodResult[] r = client.call(new CallMethodRequest(ae.conditionId,
                    Identifiers.AcknowledgeableConditionType_Acknowledge,
                    new Variant[]{ new Variant(ByteString.valueOf(ae.eventId)),
                                   new Variant(new LocalizedText(comment, "")) }));
            System.out.println("  Result: " + r[0].getStatusCode());
            Thread.sleep(500);
            listAlarms();
        } catch (Exception e) { System.err.println("  Error: " + e.getMessage()); }
    }

    private void confirm(BufferedReader reader) {
        try {
            listAlarms();
            AlarmEntry ae = getAlarmByIndex(reader);
            if (ae == null || ae.conditionId == null) { System.out.println("  No conditionId available."); return; }
            System.out.print("  Comment: ");
            String comment = reader.readLine().trim();
            CallMethodResult[] r = client.call(new CallMethodRequest(ae.conditionId,
                    Identifiers.AcknowledgeableConditionType_Confirm,
                    new Variant[]{ new Variant(ByteString.valueOf(ae.eventId)),
                                   new Variant(new LocalizedText(comment, "")) }));
            System.out.println("  Result: " + r[0].getStatusCode());
            Thread.sleep(500);
            listAlarms();
        } catch (Exception e) { System.err.println("  Error: " + e.getMessage()); }
    }

    private void addComment(BufferedReader reader) {
        try {
            listAlarms();
            AlarmEntry ae = getAlarmByIndex(reader);
            if (ae == null || ae.conditionId == null) { System.out.println("  No conditionId available."); return; }
            System.out.print("  Comment: ");
            String comment = reader.readLine().trim();
            CallMethodResult[] r = client.call(new CallMethodRequest(ae.conditionId,
                    Identifiers.ConditionType_AddComment,
                    new Variant[]{ new Variant(ByteString.valueOf(ae.eventId)),
                                   new Variant(new LocalizedText(comment, "")) }));
            System.out.println("  Result: " + r[0].getStatusCode());
            Thread.sleep(500);
            listAlarms();
        } catch (Exception e) { System.err.println("  Error: " + e.getMessage()); }
    }

    private void enableDisable(BufferedReader reader, boolean enable) {
        try {
            listAlarms();
            AlarmEntry ae = getAlarmByIndex(reader);
            if (ae == null || ae.conditionId == null) { System.out.println("  No conditionId available."); return; }
            NodeId methodId = enable ? Identifiers.ConditionType_Enable : Identifiers.ConditionType_Disable;
            CallMethodResult[] r = client.call(new CallMethodRequest(ae.conditionId, methodId, new Variant[0]));
            System.out.println("  Result: " + r[0].getStatusCode());
            Thread.sleep(500);
            listAlarms();
        } catch (Exception e) { System.err.println("  Error: " + e.getMessage()); }
    }

    // ── Event handlers ──────────────────────────────────────────────────────

    @Override public void onSessionKeepAlive(ServerStatusDataType status, ServerState state) { }

    @Override
    public void onSessionConnectionStateChanged(boolean isConnected) {
        System.out.println(isConnected ? "  [Connected] Session established" : "  [ConnectionLost] Connection lost");
    }

    @Override public void onValueNotification(MonitoredItem item, DataValue value) { }

    @Override
    public void onEventNotification(MonitoredItem item, EventFieldList ef) {
        Variant[] f = ef.getEventFields();
        if (f == null || f.length <= F_RETAIN) return;

        byte[]  eventId   = f[F_EVENT_ID] != null && f[F_EVENT_ID].getValue() instanceof ByteString
                ? ((ByteString) f[F_EVENT_ID].getValue()).getValue() : null;
        String  source    = f[F_SOURCE] != null ? String.valueOf(f[F_SOURCE].getValue()) : "?";
        String  message   = f[F_MESSAGE] != null && f[F_MESSAGE].getValue() instanceof LocalizedText
                ? ((LocalizedText) f[F_MESSAGE].getValue()).getText() : "?";
        String  severity  = f[F_SEVERITY] != null ? String.valueOf(f[F_SEVERITY].getValue()) : "?";
        boolean retain    = f[F_RETAIN] != null && Boolean.TRUE.equals(f[F_RETAIN].getValue());
        String  condName  = f.length > F_COND_NAME && f[F_COND_NAME] != null
                ? String.valueOf(f[F_COND_NAME].getValue()) : source;
        String  ackedState = f.length > F_ACKED_STATE && f[F_ACKED_STATE] != null
                && f[F_ACKED_STATE].getValue() instanceof LocalizedText
                ? ((LocalizedText) f[F_ACKED_STATE].getValue()).getText() : "?";
        NodeId  condId    = f.length > F_COND_ID && f[F_COND_ID] != null
                && f[F_COND_ID].getValue() instanceof NodeId
                ? (NodeId) f[F_COND_ID].getValue() : null;

        String comment = f.length > F_COMMENT && f[F_COMMENT] != null
                && f[F_COMMENT].getValue() instanceof LocalizedText
                ? ((LocalizedText) f[F_COMMENT].getValue()).getText() : "";
        String enabledState = f.length > F_ENABLED && f[F_ENABLED] != null
                && f[F_ENABLED].getValue() instanceof LocalizedText
                ? ((LocalizedText) f[F_ENABLED].getValue()).getText() : "?";
        String activeState = f.length > F_ACTIVE && f[F_ACTIVE] != null
                && f[F_ACTIVE].getValue() instanceof LocalizedText
                ? ((LocalizedText) f[F_ACTIVE].getValue()).getText() : "?";
        String confirmedState = f.length > F_CONFIRMED && f[F_CONFIRMED] != null
                && f[F_CONFIRMED].getValue() instanceof LocalizedText
                ? ((LocalizedText) f[F_CONFIRMED].getValue()).getText() : "?";

        if (retain && eventId != null) {
            alarmCache.put(condName, new AlarmEntry(condId, eventId, source,
                    condName, message, severity, ackedState,
                    comment, enabledState, activeState, confirmedState));
        } else {
            alarmCache.remove(condName);
        }
    }

    @Override public void onStatusChangeNotification(UaSubscription s, StatusCode sc) { }
    @Override public void onPublishFailure(ServiceResultException e) { }
    @Override public void onNotificationDataLost(UaSubscription s) { }
    @Override public void onSubscriptionKeepAlive(UaSubscription s, DateTime t) { }

    @Override public StatusCode validateCertificate(Cert cert) { return StatusCode.GOOD; }
    @Override public StatusCode validateCertificate(ApplicationDescription app, Cert cert) { return StatusCode.GOOD; }

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

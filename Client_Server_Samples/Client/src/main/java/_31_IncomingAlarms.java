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

import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.DateTime;
import com.plccom.opc.ua.builtintypes.DataValue;
import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.QualifiedName;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.builtintypes.Variant;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaClientCertificate;
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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Workshop 31 - Incoming Alarms
 *
 * OPC UA Alarms & Conditions (Part 9) defines how servers report process
 * alarms. Clients subscribe to an event source node's EventNotifier and
 * receive alarm notifications whenever a condition changes state (active,
 * acknowledged, cleared, etc.).
 *
 * What you will learn:
 *   - How to create an EventFilter with select clauses
 *   - How to subscribe to a node's EventNotifier for alarm events
 *   - How to extract alarm fields (Source, Message, Severity) from notifications
 *   - How alarm events differ from data change notifications
 *
 * Target server: opc.tcp://localhost:48410
 */
public class _31_IncomingAlarms
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener,
                   CertificateValidator, MonitoredItemNotificationListener, SubscriptionListener {

    public static void main(String[] args) {
        new _31_IncomingAlarms().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 31 - Incoming Alarms", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 31: Incoming Alarms     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  OPC UA Alarms & Conditions (Part 9) defines how servers     ║");
            System.out.println("║  report process alarms. Clients subscribe to the event       ║");
            System.out.println("║  source node's EventNotifier and receive alarm notifications.║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Create an EventFilter for alarm events                  ║");
            System.out.println("║    * Subscribe to a node's EventNotifier                     ║");
            System.out.println("║    * Extract alarm fields from event notifications           ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 21 (Alarm Conditions)      ║");
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
            client.getSubscriptionManager().addSubscriptionListener(this);

            System.out.print("  Connecting ... ");
            client.connect();
            System.out.println("OK");
            System.out.println();

            // -- Step 4: Create event subscription with alarm filter -----------
            UaSubscription subscription = client.getSubscriptionManager().createSubscription();

            // Ask user which alarm types to filter for
            System.out.println("  Select alarm filter:");
            System.out.println("    1 - All conditions");
            System.out.println("    2 - Dialogs");
            System.out.println("    3 - Alarms");
            System.out.println("    4 - Limit alarms");
            System.out.println("    5 - Discrete alarms");
            System.out.print("  Choice [1]: ");
            String filterChoice = reader.readLine().trim();
            System.out.println();

            // SimpleAttributeOperand selects a field from an event type.
            // Each entry defines: the event type NodeId, the browse path to the field,
            // the attribute (always Value for event fields) and an optional index range.
            // The order here defines the index in EventFieldList.getEventFields().
            // Select clauses define which event fields the server should return.
            // f[0]=EventId, f[1]=EventType, f[2]=SourceName, f[3]=Message,
            // f[4]=Severity, f[5]=ConditionName, f[6]=Retain, f[7]=ActiveState
            SimpleAttributeOperand[] selectClauses = {
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "EventId") },       Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "EventType") },     Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "SourceName") },    Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "Message") },       Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "Severity") },      Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.ConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "ConditionName") }, Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.ConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "Retain") },        Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.AlarmConditionType,
                        new QualifiedName[]{ new QualifiedName(0, "ActiveState") },   Attributes.Value, null),
            };

            // EventFilter wraps the select clauses (which fields) and an optional
            // where clause (which events to include). null = no filtering, receive all events.
            // Build a where clause based on the user's filter choice using the OfType operator.
            com.plccom.opc.ua.core.ContentFilter whereClause = null;
            NodeId filterTypeId;
            switch (filterChoice) {
                case "2":  filterTypeId = Identifiers.DialogConditionType; break;
                case "3":  filterTypeId = Identifiers.AlarmConditionType; break;
                case "4":  filterTypeId = Identifiers.ExclusiveLimitAlarmType; break;
                case "5":  filterTypeId = Identifiers.DiscreteAlarmType; break;
                default:   filterTypeId = Identifiers.ConditionType; break;
            }
            // OfType operator: returns true if the event is of the specified type or a subtype.
            com.plccom.opc.ua.core.LiteralOperand typeOperand = new com.plccom.opc.ua.core.LiteralOperand();
            typeOperand.setValue(new Variant(filterTypeId));
            com.plccom.opc.ua.core.ContentFilterElement ofTypeElement = new com.plccom.opc.ua.core.ContentFilterElement(
                    com.plccom.opc.ua.core.FilterOperator.OfType,
                    new ExtensionObject[] { ExtensionObject.binaryEncode(typeOperand, client.getEncoderContext()) });
            whereClause = new com.plccom.opc.ua.core.ContentFilter(new com.plccom.opc.ua.core.ContentFilterElement[] { ofTypeElement });

            EventFilter eventFilter = new EventFilter(selectClauses, whereClause);

            // samplingInterval=0.0 for events: the server reports every event immediately.
            // The filter is wrapped in an ExtensionObject as required by the OPC UA spec.
            MonitoringParameters params = new MonitoringParameters();
            params.setSamplingInterval(0.0);
            params.setFilter(new ExtensionObject(eventFilter));

            // Subscribe to the Server node's EventNotifier (ObjectIds.Server, i=2253).
            // This is the OPC UA standard way — the server propagates events from all
            // RootNotifier nodes (like Reactor) up to the Server node via HasNotifier references.
            NodeId serverNodeId = Identifiers.Server;
            System.out.println("  Event source: Objects.Server  ->  " + serverNodeId);
            System.out.println();

            // Monitor the Server node's EventNotifier attribute.
            ReadValueId readValue = new ReadValueId(
                    serverNodeId, UaAttributes.EventNotifier.getValue(), null, null);

            List<MonitoredItemCreateRequest> requests = new ArrayList<>();
            requests.add(new MonitoredItemCreateRequest(readValue, MonitoringMode.Reporting, params));
            subscription.createMonitoredItems(requests, this);

            System.out.println("  Listening for alarm events - they appear below.");

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

    // Not used - we monitor the EventNotifier attribute, not a data variable.
    @Override
    public void onValueNotification(MonitoredItem item, DataValue value) { }

    // Called for each incoming alarm event notification.
    // ef.getEventFields() returns one Variant per selectClause, in the same order:
    // f[0]=EventId, f[1]=EventType, f[2]=SourceName, f[3]=Message, f[4]=Severity,
    // f[5]=ConditionName, f[6]=Retain, f[7]=ActiveState
    @Override
    public void onEventNotification(MonitoredItem item, EventFieldList ef) {
        Variant[] f = ef.getEventFields();
        if (f == null || f.length < 5) return;

        String eventType = f[1] != null ? resolveEventTypeName(f[1].getValue()) : "?";
        String source    = f[2] != null ? String.valueOf(f[2].getValue()) : "?";
        String message   = f[3] != null && f[3].getValue() instanceof LocalizedText
                ? ((LocalizedText) f[3].getValue()).getText() : String.valueOf(f[3] != null ? f[3].getValue() : "?");
        String severity  = f[4] != null ? String.valueOf(f[4].getValue()) : "?";
        String condName  = f.length > 5 && f[5] != null ? String.valueOf(f[5].getValue()) : "";
        Boolean retain   = f.length > 6 && f[6] != null && f[6].getValue() instanceof Boolean
                ? (Boolean) f[6].getValue() : null;

        // ActiveState (f[7]) determines ON/OFF. It's a LocalizedText ("Active"/"Inactive").
        // For non-alarm events (simple events, dialogs) this field may be null.
        String activeStateText = f.length > 7 && f[7] != null && f[7].getValue() instanceof LocalizedText
                ? ((LocalizedText) f[7].getValue()).getText() : null;
        boolean isActive = "Active".equals(activeStateText);

        String status;
        if (retain == null) {
            status = "EVENT    ";
        } else if (isActive) {
            status = "ALARM ON ";
        } else if (retain) {
            status = "ALARM OFF";  // inactive but unacknowledged
        } else {
            status = "CLEARED  ";  // inactive and acknowledged
        }

        System.out.println();
        System.out.println("  [" + status + "] " + java.time.LocalTime.now().toString().substring(0, 8));
        System.out.println("    Type      : " + eventType);
        System.out.println("    Source    : " + source);
        if (!condName.isEmpty())
            System.out.println("    Alarm     : " + condName);
        System.out.println("    Message   : " + message);
        System.out.println("    Severity  : " + severity);
        if (retain != null)
            System.out.println("    Retain    : " + retain + "  (" + (isActive ? "active" : "inactive") + ", "
                    + (retain ? "unacked" : "acked") + ")");
    }

    /**
     * Resolves an EventType NodeId to a human-readable OPC UA type name.
     * Covers all standard Alarm & Condition types from OPC UA Part 9.
     *
     * @param value the EventType field value (expected to be a NodeId)
     * @return the type name, or the NodeId string if unknown
     */
    private static String resolveEventTypeName(Object value) {
        if (!(value instanceof NodeId)) return String.valueOf(value);
        NodeId id = (NodeId) value;
        if (id.getNamespaceIndex() != 0 || !(id.getValue() instanceof Number))
            return id.toString();
        switch (((Number) id.getValue()).intValue()) {
            case 2041:  return "BaseEventType";
            case 2782:  return "ConditionType";
            case 2830:  return "DialogConditionType";
            case 2881:  return "AcknowledgeableConditionType";
            case 2915:  return "AlarmConditionType";
            case 2955:  return "LimitAlarmType";
            case 9341:  return "ExclusiveLimitAlarmType";
            case 9482:  return "ExclusiveLevelAlarmType";
            case 9623:  return "ExclusiveRateOfChangeAlarmType";
            case 9764:  return "ExclusiveDeviationAlarmType";
            case 9906:  return "NonExclusiveLimitAlarmType";
            case 10060: return "NonExclusiveLevelAlarmType";
            case 10214: return "NonExclusiveRateOfChangeAlarmType";
            case 10368: return "NonExclusiveDeviationAlarmType";
            case 10523: return "DiscreteAlarmType";
            case 10637: return "OffNormalAlarmType";
            case 10751: return "TripAlarmType";
            case 11753: return "SystemOffNormalAlarmType";
            case 13225: return "CertificateExpirationAlarmType";
            case 17080: return "DiscrepancyAlarmType";
            case 18347: return "InstrumentDiagnosticAlarmType";
            case 18496: return "SystemDiagnosticAlarmType";
            case 19297: return "TrustListOutOfDateAlarmType";
            default:    return id.toString();
        }
    }

    // Called when the server reports a status change on the subscription.
    @Override public void onStatusChangeNotification(UaSubscription s, StatusCode sc) { }

    // Called when a Publish request to the server fails (e.g. network error).
    @Override public void onPublishFailure(ServiceResultException e) { }

    // Called when notification messages were lost (sequence number gap).
    @Override public void onNotificationDataLost(UaSubscription s) { }

    // Called when the server sends a keep-alive for the subscription.
    @Override public void onSubscriptionKeepAlive(UaSubscription s, DateTime t) { }

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
                new LocalizedText("PLCcom_Workshop_31", "en"), endpoint);

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
            appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_31", "secretpassword");
            if (appCert == null || !appCert.checkValidity())
                appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_31", 720, "Indi.An GmbH")
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

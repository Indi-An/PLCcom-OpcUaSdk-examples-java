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
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.DateTime;
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
 * Workshop 61 - Simple Events
 *
 * OPC UA events are fire-and-forget notifications from the server. Unlike
 * alarms (which are conditions with persistent state), simple events have
 * no state - they are emitted once and then gone.
 *
 * This workshop subscribes to BaseEventType events on a specific node and
 * receives Message, Severity and Time fields for each event.
 *
 * What you will learn:
 *   - How to create an EventFilter for BaseEventType
 *   - How to subscribe to a node's EventNotifier for simple events
 *   - How to extract Message, Severity and Time from event notifications
 *   - The difference between simple events and alarm conditions
 *
 * Target server: opc.tcp://localhost:48410
 */
public class _61_Client_SimpleEvents
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener,
                   CertificateValidator, MonitoredItemNotificationListener, SubscriptionListener {

    public static void main(String[] args) {
        new _61_Client_SimpleEvents().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 61 - Simple Events", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 61: Simple Events       ║");
            System.out.println("║                                                              ║");
            System.out.println("║  OPC UA events are fire-and-forget notifications from the    ║");
            System.out.println("║  server. Unlike alarms they have no persistent state.        ║");
            System.out.println("║  This workshop subscribes to BaseEventType events.           ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Create an EventFilter for BaseEventType                 ║");
            System.out.println("║    * Subscribe to a node's EventNotifier                     ║");
            System.out.println("║    * Extract Message, Severity and Time from events          ║");
            System.out.println("║                                                              ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 61 (Simple Events)         ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
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
            client.getSubscriptionManager().addSubscriptionListener(this);

            System.out.print("  Connecting ... ");
            client.connect();
            System.out.println("OK");
            System.out.println();

            // -- Step 4: Subscribe to BaseEventType events -----------------------
            UaSubscription subscription = client.getSubscriptionManager().createSubscription();

            // Select the standard BaseEventType fields.
            // The field index in onEventNotification matches the order here:
            // f[0]=EventId, f[1]=SourceName, f[2]=Message, f[3]=Severity, f[4]=Time
            SimpleAttributeOperand[] selectClauses = {
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "EventId") },    Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "SourceName") }, Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "Message") },    Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "Severity") },   Attributes.Value, null),
                new SimpleAttributeOperand(Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, "Time") },       Attributes.Value, null),
            };

            MonitoringParameters params = new MonitoringParameters();
            params.setSamplingInterval(0.0);
            params.setFilter(new ExtensionObject(new EventFilter(selectClauses, null)));

            // The Machine1 node is the event source. We subscribe to it directly
            // because the C# server does not propagate events up to the Server node.
            NodeId machine1NodeId = client.getNodeIdByPath("Objects.Plant.Machine1");
            System.out.println("  Event source: Objects.Plant.Machine1  ->  " + machine1NodeId);
            System.out.println();

            List<MonitoredItemCreateRequest> requests = new ArrayList<>();
            requests.add(new MonitoredItemCreateRequest(
                    new ReadValueId(machine1NodeId, UaAttributes.EventNotifier.getValue(), null, null),
                    MonitoringMode.Reporting, params));
            subscription.createMonitoredItems(requests, this);

            System.out.println("  Listening for events - notifications appear below.");

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

    // Called for each incoming event notification.
    // f[0]=EventId, f[1]=SourceName, f[2]=Message (LocalizedText), f[3]=Severity, f[4]=Time
    @Override
    public void onEventNotification(MonitoredItem item, EventFieldList ef) {
        Variant[] f = ef.getEventFields();
        if (f == null || f.length < 5) return;
        String source   = f[1] != null ? String.valueOf(f[1].getValue()) : "?";
        String message  = f[2] != null && f[2].getValue() instanceof LocalizedText
                ? ((LocalizedText) f[2].getValue()).getText() : "?";
        String severity = f[3] != null ? String.valueOf(f[3].getValue()) : "?";
        String time     = f[4] != null && f[4].getValue() instanceof DateTime
                ? formatUtc((DateTime) f[4].getValue()) : "?";
        System.out.printf("  [EVENT] %s  Source=%-15s Severity=%-5s %s%n", time, source, severity, message);
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
     * Loads an existing certificate or creates a new self-signed one.
     *
     * @param certFile path to the .der certificate file
     * @param password password for the private key
     * @param alias    common name (CN) for a new certificate
     * @return the loaded or newly created key pair
     * @throws Exception if certificate creation or loading fails
     */
    static String formatUtc(DateTime dt) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss.SSS 'UTC'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(dt.getCalendar(java.util.TimeZone.getTimeZone("UTC")).getTime());
    }

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
                new LocalizedText("PLCcom_Workshop_61", "en"), endpoint);
        
        config.setDefaultPublishingInterval(1000.0);

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
            appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_61", "secretpassword");
            if (appCert == null || !appCert.checkValidity())
                appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_61", 720, "Indi.An GmbH")
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

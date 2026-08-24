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
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.builtintypes.UnsignedInteger;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.MonitoredItem;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaClientCertificate;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.UaSubscription;
import com.plccom.opc.ua.client.application.listener.MonitoredItemNotificationListener;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.Attributes;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.HistoryData;
import com.plccom.opc.ua.core.EventFieldList;
import com.plccom.opc.ua.core.Identifiers;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.MonitoredItemCreateRequest;
import com.plccom.opc.ua.core.MonitoringMode;
import com.plccom.opc.ua.core.MonitoringParameters;
import com.plccom.opc.ua.core.ReadValueId;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Workshop 41 - Historical Data Read
 *
 * OPC UA Historical Access (Part 11) lets clients read past values of
 * variables using the HistoryRead service. The server must have history
 * enabled on the variable (Historizing = true) - see Server Workshop 31.
 *
 * This workshop demonstrates all HistoryRead operations:
 *   Subscribe     - monitor live values via subscription
 *   ReadRaw       - read recorded values as-is
 *   ReadModified  - read values that were changed after recording
 *   ReadAtTime    - read values at specific evenly-spaced timestamps
 *   ReadProcessed - read aggregated values (Average, Min, Max, ...)
 *
 * For history write operations (Insert, Update, Replace, Delete)
 * see Workshop 42 (Historical Data Update).
 *
 * Target server: opc.tcp://localhost:48410
 */
public class _41_HistoricalData
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _41_HistoricalData().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 41 - Historical Data Read", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 41: Historical Data     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  OPC UA Historical Access lets you read past values.         ║");
            System.out.println("║  The server stores timestamped values and returns them       ║");
            System.out.println("║  on request - essential for trend analysis and reporting.    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Subscribe to live data changes                          ║");
            System.out.println("║    * ReadRaw: read recorded values as-is                     ║");
            System.out.println("║    * ReadModified: values changed after recording            ║");
            System.out.println("║    * ReadAtTime: values at specific timestamps               ║");
            System.out.println("║    * ReadProcessed: aggregated values (Average, Min, Max)    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  For write operations see Workshop 42 (Historical Update)    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 31 (Historical Access)     ║");
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
                System.err.println("  No endpoints found. Is Server Workshop 31 running?");
                System.out.println("  Press ENTER to exit.");
                try { System.in.read(); } catch (Exception ignored) { }
                return;
            }

            endpoints = UaClient.sortBySecurityLevel(endpoints, SortDirection.Asc);

            System.out.println("  " + endpoints.length + " endpoint(s) found:");
            System.out.println();
            for (int i = 0; i < endpoints.length; i++) {
                System.out.println("  [" + i + "] " + OpcUaDisplayUtils.toDisplayString(endpoints[i]));
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


            // -- Step 2: Build client configuration -------------------------------
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

            // -- Step 3: Resolve NodeId by browse path ----------------------------
            // Server Workshop 31 creates: Plant -> Sensor -> Temperature
            String histPath = "Objects.Plant.Sensor.Temperature";
            NodeId nodeId = client.getNodeIdByPath(histPath);
            if (nodeId == null) {
                System.out.println("  Could not find '" + histPath + "'.");
                System.out.println("  Is Server Workshop 31 running and recording history?");
                System.out.println("  Press ENTER to exit.");
                reader.readLine();
                client.close();
                return;
            }
            System.out.println("  Temperature NodeId: " + nodeId);
            System.out.println();

            // -- Step 4: Command loop ---------------------------------------------
            while (true) {
                System.out.println("  Select operation:");
                System.out.println("  1 - Subscribe     (live data changes via subscription)");
                System.out.println("  2 - ReadRaw       (all recorded values as stored)");
                System.out.println("  3 - ReadModified  (values changed after recording)");
                System.out.println("  4 - ReadAtTime    (values at evenly-spaced timestamps)");
                System.out.println("  5 - ReadProcessed (aggregated values: Average, Min, Max)");
                System.out.println("  6 - Exit");
                System.out.print("  > ");

                input = reader.readLine();
                if (input == null || input.trim().equals("6")) break;

                try {
                    switch (input.trim()) {

                        case "1": { // Subscribe - monitor live values via subscription
                            UaSubscription subscription = client.getSubscriptionManager()
                                    .createSubscription(1000.0);

                            // Build a MonitoredItemCreateRequest for the Temperature node
                            ReadValueId readValueId = new ReadValueId(
                                    nodeId, Attributes.Value, null, null);
                            MonitoringParameters params = new MonitoringParameters(
                                    UnsignedInteger.valueOf(1),  // clientHandle
                                    500.0,                       // samplingInterval ms
                                    null,                        // no filter
                                    UnsignedInteger.valueOf(10), // queueSize
                                    true);                       // discardOldest
                            MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
                                    readValueId, MonitoringMode.Reporting, params);

                            subscription.createMonitoredItems(
                                    request,
                                    new MonitoredItemNotificationListener() {
                                        @Override
                                        public void onValueNotification(MonitoredItem mi, DataValue value) {
                                            System.out.printf("  %s  T=%-10s  %s%n",
                                                    value.getSourceTimestamp(),
                                                    value.getValue().getValue(),
                                                    value.getStatusCode());
                                        }
                                        @Override
                                        public void onEventNotification(MonitoredItem mi, EventFieldList fields) {
                                        }
                                    });

                            System.out.println("  Monitoring... press ENTER to stop.");
                            reader.readLine();
                            client.getSubscriptionManager().deleteSubscription(
                                    subscription.getSubscriptionId());
                            break;
                        }

                        case "2": { // ReadRaw - all recorded values as stored
                            // isReadModified=false: return original recorded values
                            DateTime end   = DateTime.currentTime();
                            DateTime start = DateTime.fromMillis(end.getTimeInMillis() - 10 * 60 * 1000L);
                            HistoryData data = client.readRaw(nodeId, start, end, false);
                            printValues(data);
                            break;
                        }

                        case "3": { // ReadModified - only values changed after recording
                            // isReadModified=true: return only values that were modified
                            // after they were originally recorded (e.g. via HistoryUpdate)
                            DateTime end   = DateTime.currentTime();
                            DateTime start = DateTime.fromMillis(end.getTimeInMillis() - 10 * 60 * 1000L);
                            HistoryData data = client.readRaw(nodeId, start, end, true);
                            printValues(data);
                            break;
                        }

                        case "4": { // ReadAtTime - values at 10 timestamps, 5s apart, ending now
                            // Per OPC UA Part 11 §6.5.5:
                            //   Raw          = exact stored value at that timestamp
                            //   Interpolated = calculated from surrounding values
                            //   BadNoData    = no usable value found before this timestamp
                            System.out.println();
                            System.out.println("  ReadAtTime: 10 timestamps, 5s apart, ending now.");
                            System.out.println("  Raw          = exact stored value");
                            System.out.println("  Interpolated = calculated from surrounding values (OPC UA Part 11 §6.5.5)");
                            System.out.println("  BadNoData    = no usable value found before this timestamp");
                            System.out.println();
                            DateTime start = DateTime.fromMillis(
                                    DateTime.currentTime().getTimeInMillis() - 45 * 1000L);
                            HistoryData data = client.readAtTime(nodeId, start, 10, 5000, false);
                            printValues(data);
                            break;
                        }

                        case "5": { // ReadProcessed - server computes aggregate per interval
                            // The server calculates aggregates (Average, Min, Max, etc.)
                            // over each processing interval. Reduces data volume for long ranges.
                            // Use the standard OPC UA Average aggregate function NodeId.
                            NodeId aggregateId = Identifiers.AggregateFunction_Average;
                            DateTime end   = DateTime.currentTime();
                            DateTime start = DateTime.fromMillis(end.getTimeInMillis() - 5 * 60 * 1000L);
                            HistoryData data = client.readProcessed(nodeId, aggregateId, start, end, 60000);
                            printValues(data);
                            break;
                        }

                        default:
                            System.out.println("  Unknown option.");
                    }
                } catch (Exception ex) {
                    System.out.println("  Error: " + ex.getMessage());
                }

                System.out.println();
            }

            // -- Step 5: Disconnect -----------------------------------------------
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

    static void printValues(HistoryData data) {
        if (data == null || data.getDataValues() == null || data.getDataValues().length == 0) {
            System.out.println("  (no values)");
            return;
        }
        DataValue[] values = data.getDataValues();
        for (DataValue v : values) {
            String ts = v.getSourceTimestamp() != null
                    ? v.getSourceTimestamp().toString()
                    : "(no timestamp)";
            String val = (v.getValue() != null && v.getValue().getValue() != null)
                    ? v.getValue().getValue().toString()
                    : "";
            String sc = OpcUaDisplayUtils.toDisplayString(v.getStatusCode());
            System.out.printf("  %-35s  Value=%-12s  %s%n", ts, val, sc);
        }
        System.out.println("  => " + values.length + " values");
    }


    // ── Event handlers ──────────────────────────────────────────────────────

    @Override
    public void onSessionKeepAlive(ServerStatusDataType status, ServerState state) {
    }

    @Override
    public void onSessionConnectionStateChanged(boolean isConnected) {
        if (isConnected) {
            System.out.println("  [Connected] Session established");
        } else {
            System.out.println("  [ConnectionLost] Connection lost");
        }
    }

    @Override
    public StatusCode validateCertificate(Cert cert) {
        return StatusCode.GOOD;
    }

    @Override
    public StatusCode validateCertificate(ApplicationDescription app, Cert cert) {
        return StatusCode.GOOD;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
                new LocalizedText("PLCcom_Workshop_41", "en"), endpoint);

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
            appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_41", "secretpassword");
            if (appCert == null || !appCert.checkValidity())
                appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_41", 720, "Indi.An GmbH")
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

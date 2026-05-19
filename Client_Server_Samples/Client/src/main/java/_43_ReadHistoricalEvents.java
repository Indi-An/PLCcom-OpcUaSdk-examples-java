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
import com.plccom.opc.ua.builtintypes.DateTime;
import com.plccom.opc.ua.builtintypes.ExtensionObject;
import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.QualifiedName;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.builtintypes.UnsignedInteger;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaClientCertificate;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.Attributes;
import com.plccom.opc.ua.core.DeleteEventDetails;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.EventFilter;
import com.plccom.opc.ua.core.HistoryEvent;
import com.plccom.opc.ua.core.HistoryEventFieldList;
import com.plccom.opc.ua.core.HistoryReadRequest;
import com.plccom.opc.ua.core.HistoryReadResult;
import com.plccom.opc.ua.core.HistoryReadValueId;
import com.plccom.opc.ua.core.HistoryUpdateResult;
import com.plccom.opc.ua.core.Identifiers;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.ReadEventDetails;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.core.SimpleAttributeOperand;
import com.plccom.opc.ua.core.TimestampsToReturn;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import com.plccom.opc.ua.utils.StackUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Workshop 43 - Read Historical Events
 *
 * In addition to historical data values, OPC UA servers can store
 * historical events (alarms, state changes, operator actions).
 * This workshop reads past events from the server using HistoryRead.
 *
 * What you will learn:
 *   - How to read historical events for a time range
 *   - How to specify event filter fields (which properties to retrieve)
 *   - How to interpret historical event results
 *   - How to delete historical events by EventId
 *
 * Target server: opc.tcp://localhost:48410
 */
public class _43_ReadHistoricalEvents
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _43_ReadHistoricalEvents().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 43 - Read Historical Events", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 43: Read Hist. Events   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  OPC UA servers can store historical events (alarms, state   ║");
            System.out.println("║  changes, operator actions). This workshop reads past        ║");
            System.out.println("║  events from the server using HistoryRead.                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Read historical events for a time range                 ║");
            System.out.println("║    * Specify event filter fields                             ║");
            System.out.println("║    * Interpret historical event results                      ║");
            System.out.println("║    * Delete historical events by EventId                     ║");
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

            // -- Step 3: Resolve the reactor node (event source) ------------------
            // Server Workshop 33 creates: Plant -> Reactor with EnableHistoryEvents()
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

            // -- Step 4: Build the event filter -----------------------------------
            // Specify which event fields to retrieve in the history result.
            // The server stores fields in UaAlarm.FIELD_* order and returns them
            // in that same order regardless of the filter select clause order.
            // Order: EventId(0), EventType(1), SourceNode(2), SourceName(3),
            //        Time(4), Message(5), Severity(6)
            String[] fieldNames = { "EventId", "EventType", "SourceNode", "SourceName", "Time", "Message", "Severity" };
            SimpleAttributeOperand[] selectClauses = new SimpleAttributeOperand[fieldNames.length];
            for (int i = 0; i < fieldNames.length; i++) {
                selectClauses[i] = new SimpleAttributeOperand(
                        Identifiers.BaseEventType,
                        new QualifiedName[]{ new QualifiedName(0, fieldNames[i]) },
                        Attributes.Value, null);
            }
            EventFilter eventFilter = new EventFilter(selectClauses, null);
            int eventIdIndex = 0;
            ArrayList<HistoryEventFieldList> lastEvents = new ArrayList<>();

            // -- Step 5: Command loop ----------------------------------------------
            while (true) {
                System.out.println("  Select operation:");
                System.out.println("  1 - Read    (read historical events, last 24 hours)");
                System.out.println("  2 - Delete  (delete all last-read events by EventId)");
                System.out.println("  3 - Exit");
                System.out.print("  > ");

                String input = reader.readLine();
                if (input == null || input.trim().equals("3")) break;

                switch (input.trim()) {

                    case "1": {
                        lastEvents = readHistoricalEvents(client, nodeId, eventFilter,
                                fieldNames, eventIdIndex);
                        break;
                    }

                    case "2": {
                        if (lastEvents.isEmpty()) {
                            System.out.println("  No events loaded yet. Use option 1 first.");
                        } else {
                            deleteEvents(client, nodeId, lastEvents, eventIdIndex);
                        }
                        break;
                    }

                    default:
                        System.out.println("  Unknown option.");
                }
                System.out.println();
            }

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

    ArrayList<HistoryEventFieldList> readHistoricalEvents(UaClient client, NodeId nodeId,
            EventFilter eventFilter, String[] fieldNames, int eventIdIndex) {
        ArrayList<HistoryEventFieldList> result = new ArrayList<>();
        try {
            Calendar cStart = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cStart.add(Calendar.DAY_OF_YEAR, -1);
            DateTime startTime = new DateTime(cStart);
            DateTime endTime   = new DateTime(Calendar.getInstance(TimeZone.getTimeZone("UTC")));

            ReadEventDetails readEventDetails = new ReadEventDetails(
                    UnsignedInteger.valueOf(100), startTime, endTime, eventFilter);
            HistoryReadValueId nodeToRead = new HistoryReadValueId();
            nodeToRead.setNodeId(nodeId);
            HistoryReadRequest request = new HistoryReadRequest(null,
                    ExtensionObject.binaryEncode(readEventDetails, client.getEncoderContext()),
                    TimestampsToReturn.Both, false,
                    new HistoryReadValueId[]{ nodeToRead });

            HistoryReadResult[] results = client.historyRead(request).getResults();
            if (results == null || results.length == 0 || results[0].getStatusCode().isBad()) {
                System.out.println("  Status: " +
                        (results != null && results.length > 0 ? results[0].getStatusCode() : "null"));
                System.out.println("  Tip: Let Server Workshop 33 run for a while to accumulate events.");
                return result;
            }

            HistoryEvent historyEvent = (HistoryEvent) results[0].getHistoryData()
                    .decode(StackUtils.getDefaultSerializer(), client.getEncoderContext(), null);

            if (historyEvent == null || historyEvent.getEvents() == null
                    || historyEvent.getEvents().length == 0) {
                System.out.println("  (no historical events found)");
                System.out.println("  Tip: Let Server Workshop 33 run for a while to accumulate events.");
                return result;
            }

            HistoryEventFieldList[] events = historyEvent.getEvents();
            System.out.println("  " + events.length + " historical event(s) found:");
            System.out.println();
            for (HistoryEventFieldList ev : events) {
                if (ev.getEventFields() == null) continue;
                result.add(ev);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(ev.getEventFields().length, fieldNames.length); i++) {
                    Object val = ev.getEventFields()[i] != null ? ev.getEventFields()[i].getValue() : null;
                    if (val == null) continue;
                    String display = (i == eventIdIndex && val instanceof ByteString)
                            ? byteArrayToHex(((ByteString) val).getValue()) : val.toString();
                    sb.append("  ").append(fieldNames[i]).append("=").append(display);
                }
                System.out.println(sb.toString());
            }
        } catch (Exception ex) {
            System.out.println("  Error: " + ex.getMessage());
        }
        return result;
    }

    void deleteEvents(UaClient client, NodeId nodeId,
            ArrayList<HistoryEventFieldList> events, int eventIdIndex) {
        try {
            ArrayList<ByteString> eventIds = new ArrayList<>();
            for (HistoryEventFieldList ev : events) {
                if (ev.getEventFields() == null || eventIdIndex >= ev.getEventFields().length) continue;
                Object eid = ev.getEventFields()[eventIdIndex] != null
                        ? ev.getEventFields()[eventIdIndex].getValue() : null;
                if (eid instanceof ByteString)
                    eventIds.add((ByteString) eid);
            }
            if (eventIds.isEmpty()) {
                System.out.println("  No EventIds found to delete.");
                return;
            }
            // DeleteEventDetails takes the node and an array of EventIds to remove.
            // The server returns a single result for the entire batch operation.
            DeleteEventDetails deleteDetails = new DeleteEventDetails(
                    nodeId, eventIds.toArray(new ByteString[0]));
            HistoryUpdateResult[] deleteResults = client.historyUpdate(
                    ExtensionObject.binaryEncode(deleteDetails, client.getEncoderContext()))
                    .getResults();
            System.out.println("  Deleted " + eventIds.size() + " event(s)  Result=" +
                    (deleteResults != null && deleteResults.length > 0
                            ? deleteResults[0].getStatusCode() : "(no result)"));
        } catch (Exception ex) {
            System.out.println("  Error: " + ex.getMessage());
        }
    }

    static String byteArrayToHex(byte[] ba) {
        if (ba == null) return "(null)";
        StringBuilder sb = new StringBuilder(ba.length * 2);
        for (byte b : ba) sb.append(String.format("%02x", b));
        return sb.toString();
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
                new LocalizedText("PLCcom_Workshop_43", "en"), endpoint);

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
            appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_43", "secretpassword");
            if (appCert == null || !appCert.checkValidity())
                appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_43", 720, "Indi.An GmbH")
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

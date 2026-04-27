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
import com.plccom.opc.ua.builtintypes.Variant;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaCertificateManager;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.HistoryData;
import com.plccom.opc.ua.core.HistoryUpdateResult;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.core.StatusCodes;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import com.plccom.opc.ua.transport.security.KeyPair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Workshop 42 - Historical Data Update
 *
 * OPC UA Historical Access (Part 11) also allows clients to modify the
 * history stored on the server. This is useful for:
 *   * Correcting wrong values recorded by a sensor
 *   * Back-filling missing data (e.g. after a server restart)
 *   * Removing erroneous entries
 *
 * This workshop demonstrates all HistoryUpdate operations:
 *   Insert         - add a new value (fails if timestamp already exists)
 *   Update         - insert or replace (upsert)
 *   Replace        - replace an existing value (fails if not exists)
 *   Remove         - remove a value by timestamp
 *   DeleteRaw      - delete all values in a time range
 *   DeleteModified - delete modified values in a time range
 *   DeleteAtTime   - delete values at specific timestamps
 *
 * For read operations see Workshop 41 (Historical Data Read).
 *
 * Target server: opc.tcp://localhost:48410
 */
public class _42_HistoricalDataUpdate
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    public static void main(String[] args) {
        new _42_HistoricalDataUpdate().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 42 - Historical Data Update", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 42: Historical Update   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  OPC UA allows clients to modify history stored on the       ║");
            System.out.println("║  server - useful for correcting values, back-filling         ║");
            System.out.println("║  missing data or removing erroneous entries.                 ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Insert: add a new value at a specific timestamp         ║");
            System.out.println("║    * Update: insert or replace (upsert)                      ║");
            System.out.println("║    * Replace: replace an existing value                      ║");
            System.out.println("║    * Remove: remove a value by timestamp                     ║");
            System.out.println("║    * DeleteRaw: delete all values in a time range            ║");
            System.out.println("║    * DeleteModified: delete modified values in a range       ║");
            System.out.println("║    * DeleteAtTime: delete values at specific timestamps      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  For read operations see Workshop 41 (Historical Read)       ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 32 (Historical Update)     ║");
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
                System.err.println("  No endpoints found. Is Server Workshop 32 running?");
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

            // -- Step 2: Build configuration and connect --------------------------
            ClientConfiguration config = new ClientConfiguration(
                    new LocalizedText("PLCcom_Workshop_42", "en"), endpoint);

            if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
                KeyPair cert = loadOrCreateCertificate(
                        "CertificateStores/PLCcom_Workshop_42.der", "secretpassword", "PLCcom_Workshop_42");
                config.setInstanceCertificate(cert);
            }

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
            // Server Workshop 32 creates: Plant -> Sensor -> Temperature
            String histPath = "Objects.Plant.Sensor.Temperature";
            NodeId nodeId = client.getNodeIdByPath(histPath);
            if (nodeId == null) {
                System.out.println("  Could not find '" + histPath + "'.");
                System.out.println("  Is Server Workshop 32 running?");
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
                System.out.println("  1 - Insert         (add new value, fails if timestamp exists)");
                System.out.println("  2 - Update         (insert or replace - upsert)");
                System.out.println("  3 - Replace        (replace existing, fails if not exists)");
                System.out.println("  4 - Remove         (remove value at current timestamp)");
                System.out.println("  5 - DeleteRaw      (delete all values in last 2 minutes)");
                System.out.println("  6 - DeleteModified (delete modified values in last 2 minutes)");
                System.out.println("  7 - DeleteAtTime   (delete values at 5 specific timestamps)");
                System.out.println("  8 - ReadRaw        (verify: read back last 10 minutes)");
                System.out.println("  9 - Exit");
                System.out.print("  > ");

                input = reader.readLine();
                if (input == null || input.trim().equals("9")) break;

                try {
                    switch (input.trim()) {

                        case "1": { // Insert - add a new value, fails if timestamp already exists
                            System.out.print("  Value to insert: ");
                            double val = Double.parseDouble(reader.readLine().trim());
                            DateTime now = DateTime.currentTime();
                            DataValue dv = new DataValue(new Variant(val),
                                    new StatusCode(StatusCodes.Good_EntryInserted), now, now);
                            HistoryUpdateResult result = client.insertHistoryValues(nodeId,
                                    new DataValue[]{ dv });
                            printResult(result);
                            break;
                        }

                        case "2": { // Update - insert if not exists, replace if exists (upsert)
                            System.out.print("  Value to update: ");
                            double val = Double.parseDouble(reader.readLine().trim());
                            DateTime now = DateTime.currentTime();
                            DataValue dv = new DataValue(new Variant(val),
                                    new StatusCode(StatusCodes.Good_EntryInserted), now, now);
                            HistoryUpdateResult result = client.updateHistoryValues(nodeId,
                                    new DataValue[]{ dv });
                            printResult(result);
                            break;
                        }

                        case "3": { // Replace - replace existing value, fails if not exists
                            // First read the most recent value to get an existing timestamp
                            DateTime end   = DateTime.currentTime();
                            DateTime start = DateTime.fromMillis(end.getTimeInMillis() - 10 * 60 * 1000L);
                            HistoryData existing = client.readRaw(nodeId, start, end, false);
                            if (existing == null || existing.getDataValues() == null
                                    || existing.getDataValues().length == 0) {
                                System.out.println("  No existing values to replace. Insert first.");
                                break;
                            }
                            // Replace the most recent value
                            DataValue last = existing.getDataValues()[existing.getDataValues().length - 1];
                            System.out.printf("  Replacing value at %s (was %s)%n",
                                    last.getSourceTimestamp(), last.getValue().getValue());
                            System.out.print("  New value: ");
                            double val = Double.parseDouble(reader.readLine().trim());
                            DataValue dv = new DataValue(new Variant(val),
                                    StatusCode.GOOD, last.getSourceTimestamp(), last.getSourceTimestamp());
                            HistoryUpdateResult result = client.replaceHistoryValues(nodeId,
                                    new DataValue[]{ dv });
                            printResult(result);
                            break;
                        }

                        case "4": { // Remove - remove the most recent value
                            // First read the most recent value to get an existing timestamp
                            DateTime end   = DateTime.currentTime();
                            DateTime start = DateTime.fromMillis(end.getTimeInMillis() - 10 * 60 * 1000L);
                            HistoryData existing = client.readRaw(nodeId, start, end, false);
                            if (existing == null || existing.getDataValues() == null
                                    || existing.getDataValues().length == 0) {
                                System.out.println("  No existing values to remove.");
                                break;
                            }
                            // Remove the most recent value
                            DataValue last = existing.getDataValues()[existing.getDataValues().length - 1];
                            System.out.printf("  Removing value at %s (value=%s)%n",
                                    last.getSourceTimestamp(), last.getValue().getValue());
                            DataValue dv = new DataValue(new Variant(null),
                                    StatusCode.GOOD, last.getSourceTimestamp(), last.getSourceTimestamp());
                            HistoryUpdateResult result = client.removeHistoryValues(nodeId,
                                    new DataValue[]{ dv });
                            printResult(result);
                            break;
                        }

                        case "5": { // DeleteRaw - delete all values in a time range
                            // isModified=false: delete original recorded values
                            DateTime end   = DateTime.currentTime();
                            DateTime start = DateTime.fromMillis(end.getTimeInMillis() - 2 * 60 * 1000L);
                            HistoryUpdateResult result = client.deleteRaw(nodeId, start, end, false);
                            printResult(result);
                            break;
                        }

                        case "6": { // DeleteModified - delete modified values in a time range
                            // isModified=true: delete only values that were modified
                            // after original recording (e.g. via Insert/Update/Replace)
                            DateTime end   = DateTime.currentTime();
                            DateTime start = DateTime.fromMillis(end.getTimeInMillis() - 2 * 60 * 1000L);
                            HistoryUpdateResult result = client.deleteRaw(nodeId, start, end, true);
                            printResult(result);
                            break;
                        }

                        case "7": { // DeleteAtTime - delete values at their exact stored timestamps
                            // DeleteAtTime requires exact timestamp matches.
                            // We first read existing values, pick up to 5, then delete them by timestamp.
                            DateTime end7   = DateTime.currentTime();
                            DateTime start7 = DateTime.fromMillis(end7.getTimeInMillis() - 2 * 60 * 1000L);
                            HistoryData existing7 = client.readRaw(nodeId, start7, end7, false);
                            if (existing7 == null || existing7.getDataValues() == null
                                    || existing7.getDataValues().length == 0) {
                                System.out.println("  No existing values to delete.");
                                break;
                            }
                            DataValue[] vals7 = existing7.getDataValues();
                            int count7 = Math.min(5, vals7.length);
                            DateTime[] times7 = new DateTime[count7];
                            System.out.printf("  Before: %d values in last 2 minutes%n", vals7.length);
                            System.out.println("  Deleting these " + count7 + " values:");
                            for (int k = 0; k < count7; k++) {
                                times7[k] = vals7[k].getSourceTimestamp();
                                System.out.printf("    [%d] %s  value=%s%n", k,
                                        times7[k], vals7[k].getValue().getValue());
                            }
                            HistoryUpdateResult result = client.deleteAtTime(nodeId, times7);
                            printResult(result);
                            // Verify: read back to show remaining values
                            HistoryData after7 = client.readRaw(nodeId, start7, end7, false);
                            int remaining = after7 != null && after7.getDataValues() != null
                                    ? after7.getDataValues().length : 0;
                            System.out.printf("  After:  %d values remaining in last 2 minutes%n", remaining);
                            break;
                        }

                        case "8": { // ReadRaw - verify changes by reading back
                            DateTime end   = DateTime.currentTime();
                            DateTime start = DateTime.fromMillis(end.getTimeInMillis() - 10 * 60 * 1000L);
                            HistoryData data = client.readRaw(nodeId, start, end, false);
                            _41_HistoricalData.printValues(data);
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

    static void printResult(HistoryUpdateResult result) {
        if (result == null) { System.out.println("  Result: null"); return; }
        System.out.println("  Result: " + OpcUaDisplayUtils.toDisplayString(result.getStatusCode()));
        StatusCode[] perItem = result.getOperationResults();
        if (perItem != null && perItem.length > 0) {
            for (int i = 0; i < perItem.length; i++) {
                String sc = perItem[i] != null && perItem[i].isGood()
                        ? "OK"
                        : OpcUaDisplayUtils.toDisplayString(perItem[i]);
                System.out.printf("    [%d] %s%n", i, sc);
            }
        }
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

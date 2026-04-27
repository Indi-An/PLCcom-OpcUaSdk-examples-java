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
import com.plccom.opc.ua.builtintypes.ExtensionObject;
import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaCertificateManager;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.complextypes.UaComplexTypeSystem;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.BrowseDescription;
import com.plccom.opc.ua.core.BrowseDirection;
import com.plccom.opc.ua.core.BrowseResponse;
import com.plccom.opc.ua.core.BrowseResultMask;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.NodeClass;
import com.plccom.opc.ua.core.ReferenceDescription;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import com.plccom.opc.ua.transport.security.KeyPair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Workshop 51 - Complex Data Types
 *
 * OPC UA supports several levels of data complexity:
 *
 *   Level 1 - Scalar variable        e.g. double, string, bool
 *   Level 2 - Array of scalars       e.g. double[], string[]
 *   Level 3 - Flat struct            fields grouped in one ExtensionObject
 *   Level 4 - Nested struct          struct containing another struct
 *   Level 5 - Struct with arrays     struct fields that are arrays
 *   Level 6 - Array of structs       array of ExtensionObjects
 *
 * Structs are transmitted as ExtensionObjects (binary-encoded blobs).
 * The client must load the server's Type Dictionary first so the SDK
 * can decode the binary payload into named fields.
 *
 * Required server: Server Workshop 15 (Custom Types)
 * opc.tcp://localhost:48410
 */
public class _51_ComplexTypes
        implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

    private UaClient client;
    private UaComplexTypeSystem typeSystem;

    public static void main(String[] args) {
        new _51_ComplexTypes().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 51 - Complex Types", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 51: Complex Types       ║");
            System.out.println("║                                                              ║");
            System.out.println("║  OPC UA supports several levels of data complexity:          ║");
            System.out.println("║    Level 1 - Scalar variable  (double, string, bool)         ║");
            System.out.println("║    Level 2 - Array of scalars (double[], string[])           ║");
            System.out.println("║    Level 3 - Flat struct      (fields in ExtensionObject)    ║");
            System.out.println("║    Level 4 - Nested struct    (struct inside struct)         ║");
            System.out.println("║    Level 5 - Struct with arrays (array fields in struct)     ║");
            System.out.println("║    Level 6 - Array of structs (array of ExtensionObjects)    ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 15 (Custom Types)          ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // TODO: Replace with your license credentials from your license e-mail
            String licenseUser   = "<Enter your UserName here>";
            String licenseSerial = "<Enter your Serial here>";

            // -- Step 1: Discover and select endpoint -----------------------------
            EndpointDescription[] endpoints = UaClient.discoverEndpoints(
                    new URI("opc.tcp://localhost:48410"), this);

            if (endpoints == null || endpoints.length == 0) {
                System.err.println("  No endpoints found. Is Server Workshop 15 running?");
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
                    new LocalizedText("PLCcom_Workshop_51", "en"), endpoint);

            if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
                KeyPair cert = loadOrCreateCertificate(
                        "CertificateStores/PLCcom_Workshop_51.der", "secretpassword", "PLCcom_Workshop_51");
                config.setInstanceCertificate(cert);
            }

            config.setCertificateValidator(this);

            client = new UaClient(licenseUser, licenseSerial, config);
            System.out.println("  License: " + client.getLicenceMessage());
            System.out.println();

            client.addSessionKeepAliveListener(this);
            client.addSessionConnectionStateChangeListener(this);

            System.out.print("  Connecting ... ");
            client.connect();
            System.out.println("OK");
            System.out.println();

            // -- Step 3: Load the server Type Dictionary --------------------------
            // This is the key step for complex types: the SDK downloads the server's
            // binary type descriptions so ExtensionObjects can be decoded into
            // named fields (UaStructuredValue with getField/setField).
            // Without this step, structs arrive as raw byte[] and cannot be decoded.
            System.out.print("  Loading server Type Dictionary ... ");
            typeSystem = client.getComplexTypeSystem();
            typeSystem.load();
            System.out.println("OK");

            if (typeSystem.getDefinitionCount() > 0) {
                System.out.println("  " + typeSystem.getDefinitionCount() + " custom type(s) loaded:");
                typeSystem.getTypeNames().forEach((id, name) ->
                        System.out.println("    " + name + "  [" + id + "]"));
            } else {
                System.out.println("  No custom structures found.");
            }
            System.out.println();

            // -- Step 4: Command loop ---------------------------------------------
            while (true) {
                System.out.println("  Select operation:");
                System.out.println("  1 - Read scalar variable          (Hierarchy.CNC_Machine_01.MainMotor.Speed)");
                System.out.println("  2 - Read array of scalars         (StructData.Sensor_Struct.Readings)");
                System.out.println("  3 - Read flat struct              (StructData.Motor_Struct)");
                System.out.println("  4 - Write flat struct field       (StructData.Motor_Struct.Speed)");
                System.out.println("  5 - Read nested struct            (StructData.Plant_Struct)");
                System.out.println("  6 - Read struct with array fields (StructData.Sensor_Struct)");
                System.out.println("  7 - Read array of structs         (StructData.Motor_Array)");
                System.out.println("  8 - Write array of structs element(StructData.Motor_Array.[1].Speed)");
                System.out.println("  9 - Exit");
                System.out.print("  > ");

                String input = reader.readLine();
                System.out.println();
                if (input == null || input.trim().equals("9")) break;

                try {
                    switch (input.trim()) {
                        case "1": readScalar();           break;
                        case "2": readArrayOfScalars();   break;
                        case "3": readFlatStruct();        break;
                        case "4": writeFlatStructField(reader); break;
                        case "5": readNestedStruct();      break;
                        case "6": readStructWithArrays();  break;
                        case "7": readArrayOfStructs();    break;
                        case "8": writeArrayOfStructs(reader); break;
                        default:  System.out.println("  Unknown option."); break;
                    }
                } catch (Exception ex) {
                    System.out.println("  Error: " + ex.getMessage());
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

    // ── Level 1: Scalar variable ──────────────────────────────────────────────

    void readScalar() throws Exception {
        // A scalar variable is the simplest case - just readValue by browse path.
        // Server 15 Part A creates an Object hierarchy with individual Variables.
        String path = "Objects.Hierarchy.CNC_Machine_01.MainMotor.Speed";
        DataValue value = client.readValue(client.getNodeIdByPath(path)).getResults()[0];
        System.out.println("  Path:   " + path);
        System.out.println("  Value:  " + value.getValue().getValue());
        System.out.println("  Status: " + value.getStatusCode());
        System.out.println("  Time:   " + value.getSourceTimestamp());
    }

    // ── Level 2: Array of scalars ─────────────────────────────────────────────

    void readArrayOfScalars() throws Exception {
        // An array variable has ValueRank=OneDimension.
        // ReadValue returns the array directly - no ExtensionObject needed.
        // Server 15 Part D creates SensorDataType with a double[4] Readings field.
        String path = "Objects.StructData.Sensor_Struct.Readings";
        DataValue value = client.readValue(client.getNodeIdByPath(path)).getResults()[0];
        System.out.println("  Path:   " + path);
        System.out.println("  Status: " + value.getStatusCode());
        Object val = value.getValue().getValue();
        if (val instanceof double[]) {
            double[] arr = (double[]) val;
            System.out.println("  Type:   double[" + arr.length + "]");
            for (int i = 0; i < arr.length; i++)
                System.out.println("  [" + i + "] = " + arr[i]);
        } else {
            System.out.println("  Value:  " + typeSystem.toDisplayString(val));
        }
    }

    // ── Level 3: Flat struct ──────────────────────────────────────────────────

    void readFlatStruct() throws Exception {
        // A struct variable holds an ExtensionObject.
        // After loading the Type Dictionary, the SDK decodes it into a
        // UaStructuredValue with named fields accessible via getField().
        // Server 15 Part B creates MotorDataType with Speed, Temperature, Running.
        String path = "Objects.StructData.Motor_Struct";
        DataValue value = client.readValue(client.getNodeIdByPath(path)).getResults()[0];
        System.out.println("  Path:   " + path);
        System.out.println("  Status: " + value.getStatusCode());
        printExtensionObject(value.getValue().getValue(), "  ");
    }

    // ── Level 3: Write flat struct field ──────────────────────────────────────

    void writeFlatStructField(BufferedReader reader) throws Exception {
        // Individual struct fields are exposed as child Variable nodes.
        // Writing a child node updates that field and the parent struct value.
        System.out.print("  New Speed value: ");
        double newSpeed;
        try { newSpeed = Double.parseDouble(reader.readLine().trim()); }
        catch (NumberFormatException e) { System.out.println("  Invalid value."); return; }

        String fieldPath = "Objects.StructData.Motor_Struct.Speed";
        StatusCode result = client.writeValue(client.getNodeIdByPath(fieldPath), newSpeed);
        System.out.println("  Written " + newSpeed + " to " + fieldPath);
        System.out.println("  Result: " + result);

        // Read back to verify
        System.out.println("  Reading back Motor_Struct:");
        readFlatStruct();
    }

    // ── Level 4: Nested struct ────────────────────────────────────────────────

    void readNestedStruct() throws Exception {
        // A nested struct contains another struct as a field.
        // Server 15 Part C creates PlantDataType with Motor and Machine fields.
        // Child nodes use dotted paths: Plant_Struct.Motor.Speed
        String path = "Objects.StructData.Plant_Struct";
        DataValue value = client.readValue(client.getNodeIdByPath(path)).getResults()[0];
        System.out.println("  Path:   " + path);
        System.out.println("  Status: " + value.getStatusCode());
        System.out.println();
        System.out.println("  Top-level fields:");
        printExtensionObject(value.getValue().getValue(), "  ");
        System.out.println();

        // Read individual nested fields via child node paths
        System.out.println("  Nested field access via child nodes:");
        String[] nestedPaths = {
            "Objects.StructData.Plant_Struct.PlantName",
            "Objects.StructData.Plant_Struct.Motor.Speed",
            "Objects.StructData.Plant_Struct.Motor.Temperature",
            "Objects.StructData.Plant_Struct.Machine.State",
            "Objects.StructData.Plant_Struct.Machine.CycleCount"
        };
        for (String p : nestedPaths) {
            DataValue v = client.readValue(client.getNodeIdByPath(p)).getResults()[0];
            String fieldName = p.substring(p.lastIndexOf('.') + 1);
            System.out.printf("  %20s = %s%n", fieldName, v.getValue().getValue());
        }
    }

    // ── Level 5: Struct with array fields ─────────────────────────────────────

    void readStructWithArrays() throws Exception {
        // A struct can have array fields (e.g. double[4]).
        // Server 15 Part D creates SensorDataType with Readings[4] and Thresholds[2].
        String path = "Objects.StructData.Sensor_Struct";
        DataValue value = client.readValue(client.getNodeIdByPath(path)).getResults()[0];
        System.out.println("  Path:   " + path);
        System.out.println("  Status: " + value.getStatusCode());
        printExtensionObject(value.getValue().getValue(), "  ");
        System.out.println();

        // Array fields are also accessible as child nodes
        System.out.println("  Array fields via child nodes:");
        DataValue readings   = client.readValue(client.getNodeIdByPath(
                "Objects.StructData.Sensor_Struct.Readings")).getResults()[0];
        DataValue thresholds = client.readValue(client.getNodeIdByPath(
                "Objects.StructData.Sensor_Struct.Thresholds")).getResults()[0];
        System.out.println("  Readings   = " + typeSystem.toDisplayString(readings.getValue().getValue()));
        System.out.println("  Thresholds = " + typeSystem.toDisplayString(thresholds.getValue().getValue()));
    }

    // ── Level 6: Read array of structs ────────────────────────────────────────

    void readArrayOfStructs() throws Exception {
        // An array of structs has child nodes for each element.
        // The element BrowseNames are "Motor_Array[0]", "Motor_Array[1]", etc.
        // We browse the parent node to find the element NodeIds.
        System.out.println("  Reading Motor_Array elements via child nodes:");
        System.out.println("  (Each element is a separate ExtensionObject)");
        System.out.println();

        NodeId arrayNodeId = client.getNodeIdByPath("Objects.StructData.Motor_Array");
        List<ReferenceDescription> children = browseChildren(arrayNodeId);

        int elemIndex = 0;
        for (ReferenceDescription child : children) {
            String browseName = child.getBrowseName() != null ? child.getBrowseName().getName() : "";
            if (!browseName.contains("[")) continue;

            NodeId elemNodeId = client.getEncoderContext().getNamespaceTable()
                    .toNodeId(child.getNodeId());
            DataValue value = client.readValue(elemNodeId).getResults()[0];
            System.out.println("  Motor_Array[" + elemIndex + "] (" + browseName + "):");
            printExtensionObject(value.getValue().getValue(), "    ");

            // Also read individual fields via child nodes of the element
            List<ReferenceDescription> fields = browseChildren(elemNodeId);
            for (ReferenceDescription field : fields) {
                NodeId fieldNodeId = client.getEncoderContext().getNamespaceTable()
                        .toNodeId(field.getNodeId());
                DataValue fieldVal = client.readValue(fieldNodeId).getResults()[0];
                System.out.println("    " + field.getBrowseName().getName()
                        + " = " + fieldVal.getValue().getValue());
            }
            System.out.println();
            elemIndex++;
        }
    }

    // ── Level 6: Write array of structs element ───────────────────────────────

    void writeArrayOfStructs(BufferedReader reader) throws Exception {
        System.out.print("  New Speed for Motor_Array[1]: ");
        double newSpeed;
        try { newSpeed = Double.parseDouble(reader.readLine().trim()); }
        catch (NumberFormatException e) { System.out.println("  Invalid value."); return; }

        NodeId arrayNodeId = client.getNodeIdByPath("Objects.StructData.Motor_Array");
        List<ReferenceDescription> children = browseChildren(arrayNodeId);

        // Collect element nodes (BrowseName contains "[")
        List<ReferenceDescription> elemNodes = new ArrayList<>();
        for (ReferenceDescription c : children)
            if (c.getBrowseName() != null && c.getBrowseName().getName().contains("["))
                elemNodes.add(c);

        if (elemNodes.size() < 2) {
            System.out.println("  Motor_Array has fewer than 2 elements.");
            return;
        }

        // Find Speed child of element [1]
        NodeId elem1NodeId = client.getEncoderContext().getNamespaceTable()
                .toNodeId(elemNodes.get(1).getNodeId());
        List<ReferenceDescription> fieldNodes = browseChildren(elem1NodeId);

        NodeId speedNodeId = null;
        for (ReferenceDescription f : fieldNodes) {
            if (f.getBrowseName() != null && "Speed".equals(f.getBrowseName().getName())) {
                speedNodeId = client.getEncoderContext().getNamespaceTable().toNodeId(f.getNodeId());
                break;
            }
        }

        if (speedNodeId == null) { System.out.println("  Speed field not found."); return; }

        StatusCode result = client.writeValue(speedNodeId, newSpeed);
        System.out.println("  Written " + newSpeed + " to Motor_Array[1].Speed");
        System.out.println("  Result: " + result);

        // Read back element [1]
        System.out.println("  Reading back Motor_Array[1]:");
        DataValue value = client.readValue(elem1NodeId).getResults()[0];
        printExtensionObject(value.getValue().getValue(), "    ");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<ReferenceDescription> browseChildren(NodeId nodeId) throws Exception {
        BrowseDescription browse = new BrowseDescription();
        browse.setNodeId(nodeId);
        browse.setBrowseDirection(BrowseDirection.Forward);
        browse.setIncludeSubtypes(true);
        browse.setNodeClassMask(NodeClass.Variable, NodeClass.Object);
        browse.setResultMask(BrowseResultMask.All);
        BrowseResponse response = client.browseFull(browse);
        List<ReferenceDescription> result = new ArrayList<>();
        if (response.getResults() != null)
            for (com.plccom.opc.ua.core.BrowseResult br : response.getResults())
                if (br.getReferences() != null)
                    for (ReferenceDescription ref : br.getReferences())
                        result.add(ref);
        return result;
    }

    /**
     * Prints the fields of an ExtensionObject decoded as UaStructuredValue.
     * After typeSystem.load() the SDK can decode structs into named fields.
     * Handles nested structs, array fields and primitive values.
     */
    void printExtensionObject(Object val, String indent) {
        if (val == null) { System.out.println(indent + "(null)"); return; }

        if (val instanceof ExtensionObject[]) {
            ExtensionObject[] arr = (ExtensionObject[]) val;
            for (int i = 0; i < arr.length; i++) {
                System.out.println(indent + "[" + i + "]:");
                printExtensionObject(arr[i], indent + "  ");
            }
            return;
        }

        // toDisplayString handles ExtensionObject decode, UaStructuredValue,
        // arrays and all primitives - no type-checking needed here.
        System.out.println(indent + typeSystem.toDisplayString(val));
    }

    // ── Event handlers ──────────────────────────────────────────────────────

    @Override
    public void onSessionKeepAlive(ServerStatusDataType status, ServerState state) { }

    @Override
    public void onSessionConnectionStateChanged(boolean isConnected) {
        if (isConnected)
            System.out.println("  [Connected] Session established");
        else
            System.out.println("  [ConnectionLost] Connection lost");
    }

    @Override
    public StatusCode validateCertificate(Cert cert) { return StatusCode.GOOD; }

    @Override
    public StatusCode validateCertificate(ApplicationDescription app, Cert cert) { return StatusCode.GOOD; }

    static KeyPair loadOrCreateCertificate(String certFile, String password, String alias) throws Exception {
        java.io.File f = new java.io.File(certFile);
        f.getParentFile().mkdirs();
        if (!f.isFile())
            return UaCertificateManager.createSelfSignedCertificate(certFile, alias, password, 720, "Indi.An GmbH");
        else
            return UaCertificateManager.getCertificate(certFile, password);
    }
}

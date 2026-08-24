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

// ==============================================================================
// PLCcom OPC UA Server SDK - Workshop 13: Methods
//
// OPC UA Methods are callable functions in the server's address space.
// A client can invoke a method by sending a Call service request - similar
// to calling a remote procedure (RPC). Methods can have typed input
// arguments and return typed output arguments.
//
// Typical use cases:
//   * Reset a counter or clear an alarm
//   * Start/stop a machine or process
//   * Calculate a value on the server side (e.g. unit conversion)
//   * Trigger a firmware update or configuration change
//   * Write a value with server-side validation and side effects
//
// Methods appear in the address space as child nodes of an Object or Folder.
// Clients can browse to them and see their input/output argument definitions.
// In UA Expert: right-click a method node -> "Call..." to invoke it.
//
// What you will learn:
//   * How to create a method without arguments (Reset)
//   * How to create a method with input and output arguments (Add, Multiply)
//   * How to create a method that modifies server-side state (SetTemperature)
//   * How to define argument types and descriptions
//   * How method calls interact with variables and subscriptions
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.builtintypes.Variant;
import com.plccom.opc.ua.core.Argument;
import com.plccom.opc.ua.core.Identifiers;
import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.UaRolePermissions;
import com.plccom.opc.ua.server.application.UaServer;
import com.plccom.opc.ua.server.application.UaServerCertificate;
import com.plccom.opc.ua.server.application.UaServerCertificateStore;
import com.plccom.opc.ua.server.application.UaServerConfiguration;
import com.plccom.opc.ua.server.application.UaEndpointHostMode;
import com.plccom.opc.ua.server.application.UaServerNodes.UaFolder;
import com.plccom.opc.ua.server.application.UaServerNodes.UaVariable;

public class _13_Methods {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 13 - Methods", 1000);

        // Important !!!!!!!!!!!!!!!!!!
        // Enter your Username + Serial here! Please note: with blank fields the library runs
        // for 15 minutes during a debug session. Both values can also come
        // from configuration or an environment variable.
        // Free trial license (14 days, uninterrupted): https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/
        String licenseUser   = "";
        String licenseSerial = "";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 13: Methods             ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Methods are callable functions in the address space.        ║");
        System.out.println("║  Clients invoke them via the OPC UA Call service.            ║");
        System.out.println("║                                                              ║");
        System.out.println("║  This example creates four methods:                          ║");
        System.out.println("║    Reset()                   - resets CycleCount to 0        ║");
        System.out.println("║    Add(A, B) -> Sum          - returns A + B                 ║");
        System.out.println("║    Multiply(A, B) -> Product - returns A x B                 ║");
        System.out.println("║    SetTemperature(value)     - updates a server variable     ║");
        System.out.println("║                                                              ║");
        System.out.println("║  In UA Expert: right-click a method -> Call...               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // =============================================================================
        // Step 1: Configure and start the server
        // =============================================================================
        UaServerConfiguration config = createConfig();

        printConfig(config);

        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            // Accepts all client certificates automatically - suitable for development.
            // Remove this listener to activate PKI-based validation via the store above.
            server.addCertificateValidationListener(e -> e.setAccept(true));

            // ValuesWritten fires AFTER a successful write — the client already received Good.
            // If WriteValidation rejects, this does NOT fire.
            server.addValuesWrittenListener(items -> {
                for (UaServer.UaWrittenItem item : items)
                    System.out.println("  << Written: " + item.getPath() + " = " + item.getValueAsString());
            });

            // WriteValidation — called BEFORE any client write is committed to the address space.
            // All internal checks (AccessLevel, DataType, Permissions) have already passed.
            // The handler receives ALL items of the write request as a batch.
            // Set item.setStatusCode() to any Bad_* value to reject that specific item.
            // If not handled or StatusCode remains Good, the write proceeds normally.
            //
            // You can also MODIFY the value before it is written by calling item.setValue().
            // The modified value is then stored in the address space instead of the original.
            //
            // !! IMPORTANT — PERFORMANCE WARNING !!
            // This handler runs synchronously on the server's write thread.
            // Any blocking operation (device I/O, database, slow network) will stall
            // the entire write request and can block other clients as well.
            //
            // If you need to forward the value to a device, prefer one of these patterns:
            //   a) Accept immediately (Good) and forward asynchronously via CompletableFuture or a queue.
            //      The OPC UA client gets a fast response; the device update happens in the background.
            //   b) If you must wait for the device, always use a short timeout (e.g. 500 ms)
            //      and return BadTimeout or BadNoCommunication if the device does not respond in time.
            //
            // Never block indefinitely inside this handler.
            server.addWriteValidationListener(items -> {
                for (UaServer.UaWriteValidationItem item : items) {
                    // Example: forward to device, reject on failure
                    // boolean ok = plc.writeValue(item.getPath(), item.getValue());
                    // if (!ok) item.setStatusCode(new StatusCode(StatusCodes.Bad_NoCommunication));
                    item.setStatusCode(StatusCode.GOOD);
                    System.out.println("  >> WriteValidation: " + item.getPath() + " = " + item.getValue());
                }
            });

            System.out.print("  Starting server ... ");
            try {
                server.start(config);
            } catch (Exception ex) {
                System.out.println("FAILED: " + ex.getMessage());
                System.out.println("  Press ENTER to exit.");
                System.in.read();
                PLCcomConsole.close();
                return;
            }
            System.out.println("OK");
            for (String addr : config.getBaseAddresses())
                System.out.println("  Endpoint: " + addr);
            System.out.println();

            // =============================================================================
            // Step 2: Create the address space with variables
            // =============================================================================
            // These variables will be read and modified by the methods below.
            UaFolder plant   = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaFolder machine = server.createFolder(plant, "Machine1", UaRolePermissions.WITHOUT_RESTRICTIONS);

            UaVariable<Long>   counter = server.createVariable(machine, "CycleCount",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Long.class, 0L, false);
            UaVariable<Double> temp    = server.createVariable(machine, "Temperature",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 22.0, false);

            System.out.println("── Address space ────────────────────────────────────────────");
            System.out.printf("  Int64   %-20s %s  = 0%n",    counter.getName(), counter.getNodeId());
            System.out.printf("  Double  %-20s %s  = 22.0%n", temp.getName(),    temp.getNodeId());
            System.out.println();

            // =============================================================================
            // Step 3: Create methods
            // =============================================================================
            // Methods are created under an Object or Folder node.
            // The callback lambda is invoked when a client calls the method via the
            // OPC UA Call service. Input arguments arrive as Variant[], output arguments
            // are returned as Variant[].

            // -- Method 1: Reset (no arguments) ------------------------------------------
            // The simplest form — no inputs, no outputs.
            // Resets the CycleCount variable to zero.
            server.createMethod(machine, "Reset",
                    inputs -> {
                        counter.setValue(0L);
                        System.out.println("  [METHOD] Reset() -> CycleCount = 0");
                        return new Variant[0];
                    },
                    UaRolePermissions.WITHOUT_RESTRICTIONS);

            // -- Method 2: Add (two inputs, one output) ----------------------------------
            // Methods with arguments require Argument descriptors that define:
            //   Name        — displayed in the client's call dialog
            //   DataType    — OPC UA data type NodeId (e.g. Identifiers.Double)
            //   ValueRank   — -1 for scalar, 1 for one-dimensional array
            //   Description — tooltip shown in the client
            server.createMethod(machine, "Add",
                    inputs -> {
                        double a = ((Number) inputs[0].getValue()).doubleValue();
                        double b = ((Number) inputs[1].getValue()).doubleValue();
                        double sum = a + b;
                        System.out.printf("  [METHOD] Add(%.1f, %.1f) = %.1f%n", a, b, sum);
                        return new Variant[]{ new Variant(sum) };
                    },
                    new Argument[]{ arg("A", Identifiers.Double, "First operand"),
                                    arg("B", Identifiers.Double, "Second operand") },
                    new Argument[]{ arg("Sum", Identifiers.Double, "Result of A + B") },
                    UaRolePermissions.WITHOUT_RESTRICTIONS);

            // -- Method 3: Multiply (two inputs, one output) -----------------------------
            server.createMethod(machine, "Multiply",
                    inputs -> {
                        double a = ((Number) inputs[0].getValue()).doubleValue();
                        double b = ((Number) inputs[1].getValue()).doubleValue();
                        double product = a * b;
                        System.out.printf("  [METHOD] Multiply(%.1f, %.1f) = %.1f%n", a, b, product);
                        return new Variant[]{ new Variant(product) };
                    },
                    new Argument[]{ arg("A", Identifiers.Double, "First factor"),
                                    arg("B", Identifiers.Double, "Second factor") },
                    new Argument[]{ arg("Product", Identifiers.Double, "Result of A x B") },
                    UaRolePermissions.WITHOUT_RESTRICTIONS);

            // -- Method 4: SetTemperature (modifies server state) -------------------------
            // Methods can read and write server-side variables.
            // After this call, all clients subscribed to Temperature will receive
            // a DataChange notification with the new value — automatically.
            server.createMethod(machine, "SetTemperature",
                    inputs -> {
                        double newTemp = ((Number) inputs[0].getValue()).doubleValue();
                        temp.setValue(newTemp);
                        System.out.printf("  [METHOD] SetTemperature(%.1f) -> Temperature updated%n", newTemp);
                        return new Variant[0];
                    },
                    new Argument[]{ arg("NewTemperature", Identifiers.Double,
                            "New temperature value in Celsius") },
                    null,
                    UaRolePermissions.WITHOUT_RESTRICTIONS);

            System.out.println("── Methods under Machine1 ──────────────────────────────────");
            System.out.println("  Reset()                    -> resets CycleCount to 0");
            System.out.println("  Add(A, B) -> Sum           -> returns A + B");
            System.out.println("  Multiply(A, B) -> Product  -> returns A x B");
            System.out.println("  SetTemperature(value)      -> updates Temperature variable");
            System.out.println();

            // =============================================================================
            // Step 4: myObjectNode / myMethodNode for Client Workshop 24
            // =============================================================================
            // Client Workshop 24 calls a method that receives a structured argument
            // encoded as an ExtensionObject. The method decodes the fields and returns
            // a confirmation string.
            com.plccom.opc.ua.server.application.UaServerNodes.UaObject myObjectNode =
                    server.createObject(plant, "myObjectNode", UaRolePermissions.WITHOUT_RESTRICTIONS);
            System.out.println("  myObjectNode NodeId = " + myObjectNode.getNodeId());

            server.createMethod(myObjectNode.getNodeId(), "myMethodNode",
                    inputs -> {
                        String result;
                        try {
                            Object val = inputs[0].getValue();
                            if (val instanceof com.plccom.opc.ua.builtintypes.ExtensionObject) {
                                com.plccom.opc.ua.builtintypes.ExtensionObject ext =
                                        (com.plccom.opc.ua.builtintypes.ExtensionObject) val;
                                Object bodyObj = ext.getObject();
                                byte[] body = null;
                                if (bodyObj instanceof com.plccom.opc.ua.builtintypes.ByteString)
                                    body = ((com.plccom.opc.ua.builtintypes.ByteString) bodyObj).getValue();
                                else if (bodyObj instanceof byte[])
                                    body = (byte[]) bodyObj;
                                if (body != null) {
                                    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(body)
                                            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                                    int v1 = buf.getInt();
                                    String v2 = readUaString(buf);
                                    int v3 = buf.getInt();
                                    int v4 = buf.getInt();
                                    String v5 = readUaString(buf);
                                    result = "Received: " + v1 + " | " + v2 + " | " + v3 + " | " + v4 + " | " + v5;
                                    System.out.println("  [METHOD] myMethodNode called: " + v1 + ", " + v2 + ", " + v3 + ", " + v4 + ", " + v5);
                                } else {
                                    result = "No body in ExtensionObject";
                                }
                            } else {
                                result = "No input received";
                            }
                        } catch (Exception ex) {
                            result = "Error: " + ex.getMessage();
                            System.out.println("  [METHOD] myMethodNode error: " + ex.getMessage());
                        }
                        return new Variant[]{ new Variant(result) };
                    },
                    new Argument[]{ arg("DataStructure_One", Identifiers.Structure,
                            "Encoded struct: int, string, int, int, string") },
                    new Argument[]{ arg("Result", Identifiers.String, "Confirmation string") },
                    UaRolePermissions.WITHOUT_RESTRICTIONS);

            System.out.println("── myObjectNode (for Client Workshop 24) ───────────────────");
            System.out.println("  myMethodNode(DataStructure_One) -> Result");
            System.out.println("  Input: ExtensionObject with BinaryEncoded { int, string, int, int, string }");
            System.out.println();

            // =============================================================================
            // Step 5: myObjectNode_Advanced / myMethodNode for Client Workshop 25
            // =============================================================================
            // Client Workshop 25 calls a method with a nested structure:
            //   DataStructure_One = {
            //     int, string, DataStructure_Two (embedded ExtensionObject),
            //     int, DataStructure_Two[] (array of ExtensionObjects), int
            //   }
            //   DataStructure_Two = { int, string, int }
            com.plccom.opc.ua.server.application.UaServerNodes.UaObject myObjectNodeAdv =
                    server.createObject(plant, "myObjectNode_Advanced", UaRolePermissions.WITHOUT_RESTRICTIONS);
            System.out.println("  myObjectNode_Advanced NodeId = " + myObjectNodeAdv.getNodeId());

            server.createMethod(myObjectNodeAdv.getNodeId(), "myMethodNode",
                    inputs -> {
                        String result;
                        try {
                            Object val = inputs[0].getValue();
                            if (val instanceof com.plccom.opc.ua.builtintypes.ExtensionObject) {
                                com.plccom.opc.ua.builtintypes.ExtensionObject ext =
                                        (com.plccom.opc.ua.builtintypes.ExtensionObject) val;
                                Object bodyObj = ext.getObject();
                                byte[] body = null;
                                if (bodyObj instanceof com.plccom.opc.ua.builtintypes.ByteString)
                                    body = ((com.plccom.opc.ua.builtintypes.ByteString) bodyObj).getValue();
                                else if (bodyObj instanceof byte[])
                                    body = (byte[]) bodyObj;
                                if (body != null) {
                                    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(body)
                                            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                                    int v1 = buf.getInt();                    // myIntValue1
                                    String v2 = readUaString(buf);            // myStringValue2
                                    // embedded DataStructure_Two as ExtensionObject
                                    String embSummary = decodeDataStructureTwo(buf);
                                    int v3 = buf.getInt();                    // myIntValue3
                                    // array of DataStructure_Two
                                    int arrCount = buf.getInt();
                                    StringBuilder arrSummary = new StringBuilder();
                                    for (int ai = 0; ai < arrCount; ai++) {
                                        if (ai > 0) arrSummary.append(", ");
                                        arrSummary.append("[").append(decodeDataStructureTwo(buf)).append("]");
                                    }
                                    int v4 = buf.getInt();                    // trailing int

                                    result = "Received: v1=" + v1 + " | v2=" + v2
                                            + " | emb=[" + embSummary + "]"
                                            + " | v3=" + v3
                                            + " | arr(" + arrCount + ")={" + arrSummary + "}"
                                            + " | v4=" + v4;
                                    System.out.println("  [METHOD_ADV] " + result);
                                } else {
                                    result = "No body in ExtensionObject";
                                }
                            } else {
                                result = "No input received";
                            }
                        } catch (Exception ex) {
                            result = "Error: " + ex.getMessage();
                            System.out.println("  [METHOD_ADV] error: " + ex.getMessage());
                        }
                        return new Variant[]{ new Variant(result) };
                    },
                    new Argument[]{ arg("DataStructure_One", Identifiers.Structure,
                            "Nested struct: int, string, DataStructure_Two, int, DataStructure_Two[], int") },
                    new Argument[]{ arg("Result", Identifiers.String, "Confirmation string") },
                    UaRolePermissions.WITHOUT_RESTRICTIONS);

            System.out.println("── myObjectNode_Advanced (for Client Workshop 25) ──────────");
            System.out.println("  myMethodNode(DataStructure_One) -> Result");
            System.out.println("  Input: nested struct { int, string, DataStructure_Two, int, DataStructure_Two[], int }");
            System.out.println();

            // =============================================================================
            // Step 5: Wait
            // =============================================================================
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running on: opc.tcp://localhost:48410             ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Try in UA Expert:                                           ║");
            System.out.println("║  * Browse Objects -> Plant -> Machine1                       ║");
            System.out.println("║  * Right-click Reset -> Call                                 ║");
            System.out.println("║  * Right-click Add -> Call, enter A=10 and B=20              ║");
            System.out.println("║  * Call SetTemperature(42.5) and watch Temperature change    ║");
            System.out.println("║  * Subscribe to Temperature, then call SetTemperature again  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Use Client Workshop 24 to call myMethodNode with a          ║");
            System.out.println("║  structured DataStructure_One argument.                      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Press ENTER to exit.                                        ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Server stopped.");
        }
        PLCcomConsole.close();
    }

    // =============================================================================
    // Helper: arg — creates an OPC UA Argument descriptor
    // =============================================================================
    private static Argument arg(String name, NodeId dataType, String description) {
        return new Argument(name, dataType, -1, null,
                new LocalizedText(description, LocalizedText.NO_LOCALE));
    }

    // =============================================================================
    // Helper: readUaString — reads a UA binary-encoded string from a ByteBuffer
    // =============================================================================
    // OPC UA binary encoding: 4 bytes length (little-endian), then UTF-8 bytes.
    // Length -1 means null string.
    private static String readUaString(java.nio.ByteBuffer buf) {
        int len = buf.getInt();
        if (len < 0) return null;
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    // =============================================================================
    // Helper: decodeDataStructureTwo — decodes an embedded ExtensionObject
    // =============================================================================
    // Reads the OPC UA binary-encoded ExtensionObject header (TypeId + encoding
    // byte + body length) then decodes DataStructure_Two = { int, string, int }.
    private static String decodeDataStructureTwo(java.nio.ByteBuffer buf) {
        // Skip TypeId (NodeId): read encoding byte to determine length
        int nodeIdEnc = buf.get() & 0x3F;
        if (nodeIdEnc == 0x00) {          // TwoByte
            buf.get();
        } else if (nodeIdEnc == 0x01) {   // FourByte
            buf.get(); buf.getShort();
        } else if (nodeIdEnc == 0x02) {   // Numeric
            buf.getShort(); buf.getInt();
        } else if (nodeIdEnc == 0x03) {   // String
            buf.getShort();
            int sLen = buf.getInt();
            if (sLen > 0) buf.position(buf.position() + sLen);
        } else if (nodeIdEnc == 0x04) {   // Guid
            buf.getShort(); buf.position(buf.position() + 16);
        } else if (nodeIdEnc == 0x05) {   // ByteString
            buf.getShort();
            int bLen = buf.getInt();
            if (bLen > 0) buf.position(buf.position() + bLen);
        }
        // Encoding byte (1 = binary body)
        buf.get();
        // Body length
        // Decode DataStructure_Two fields: int, string, int
        int e1 = buf.getInt();
        String e2 = readUaString(buf);
        int e3 = buf.getInt();
        return e1 + ", " + e2 + ", " + e3;
    }

    // =============================================================================
    // Helper: createConfig
    // =============================================================================
    // Helper: createConfig
    // =============================================================================
    // Returns the server configuration. All available options are listed here
    // with a description and the default value. Adjust to your needs.
    private static UaServerConfiguration createConfig() throws Exception {
        UaServerConfiguration config = new UaServerConfiguration();

        // ── Application Identity ──────────────────────────────────────────────
        // ApplicationName: human-readable name shown to connecting clients
        //   and embedded in the server certificate.
        config.setApplicationName("PLCcom Workshop 13 - Methods");

        // ApplicationUri: globally unique identifier for this server instance.
        //   Must match the URI in the server certificate.
        //   Recommended format: urn:<host>:<company>:<product>
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:13");

        // ProductUri: URI identifying the software product (not the instance).
        //   Typically a URL pointing to the product page.
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");

        // NamespaceUri: URI for this server's application address space (ns=2).
        //   Use a stable URI based on your company domain.
        //   Default: null (auto-generated as ApplicationUri + "/nodes").
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/methods");

        // ── ServerStatus/BuildInfo ────────────────────────────────────────────
        // These values appear under Server/ServerStatus/BuildInfo in the OPC UA
        // address space and identify the software to connecting clients.
        // Default: empty string.
        config.setManufacturerName("My Company GmbH");
        config.setProductName("My OPC UA Server");
        config.setSoftwareVersion("1.0.0");
        config.setBuildNumber("42");

        // ── Endpoints ─────────────────────────────────────────────────────────
        // The URLs clients connect to. Multiple endpoints are supported.
        //   opc.tcp   — binary protocol, best performance, recommended
        //   opc.https — SOAP/XML over HTTPS, for firewall-friendly scenarios
        // Default: empty (binds to all local interfaces on port 4840).
        config.setBaseAddresses(java.util.Arrays.asList(
                "opc.tcp://localhost:48410",
                "opc.https://localhost:48411"));

        // ── Security Policies ─────────────────────────────────────────────────
        // Which encryption algorithms to offer on the endpoints.
        // getRecommendedSecurityModes() returns:
        //   None (no encryption, for development only)
        //   Basic256Sha256, Aes128_Sha256_RsaOaep, Aes256_Sha256_RsaPss
        //   each with Sign + SignAndEncrypt
        config.setSecurityModes(UaServer.getRecommendedSecurityModes());

        // ── User Authentication ───────────────────────────────────────────────
        // Which authentication methods to accept from connecting clients.
        //   Anonymous   — no credentials required
        //   UserName    — username + password (see server.getUserManager())
        //   Certificate — X.509 client certificate (see server.getUserManager())
        // Default: Anonymous + SecureUsernamePassword.
        config.setUserTokenPolicies(java.util.Arrays.asList(
                UserTokenPolicy.ANONYMOUS));

        // AutoAcceptUntrustedCertificates: skip client certificate validation.
        // WARNING: only for development/testing — never use in production!
        // Default: false.
        config.setAutoAcceptUntrustedCertificates(false);

        // ── Session & Connection ──────────────────────────────────────────────
        // MaxSessionCount: maximum number of concurrent client sessions.
        // Default: 100. 0 = unlimited.
        config.setMaxSessionCount(100);

        // ShutdownDelay: seconds the server waits for clients to disconnect
        // gracefully when stop() is called. Default: 5.
        config.setShutdownDelay(5);

        // HttpsMutualTls: require the client TLS certificate to match the OPC UA
        // application certificate sent in CreateSession. Default: false.
        config.setHttpsMutualTls(false);

        // ── Local Discovery Server (LDS) ──────────────────────────────────────
        // RegisterWithDiscoveryServer: register with a LDS so that clients can
        // discover this server via FindServers without knowing its URL.
        // Default: false.
        config.setRegisterWithDiscoveryServer(false);

        // ── VendorServerInfo ──────────────────────────────────────────────────
        // These values appear under Server/VendorServerInfo in the OPC UA
        // address space and identify your product to connecting clients.
        // null = the corresponding node is not created. Default: null.
        config.setVendorName("My Company GmbH");
        config.setVendorProductName("My OPC UA Server");
        config.setVendorProductVersion("1.0.0");

        // ── HTTPS TLS Policies ────────────────────────────────────────────────
        // Which TLS versions to offer on the opc.https endpoint.
        // IMPORTANT: if null, the opc.https endpoint is NOT activated (CRA compliance).
        // Must be set explicitly to enable HTTPS.
        config.setHttpsSecurityPolicies(java.util.Arrays.asList(
                com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_2_PFS,
                com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_3));

        // ── OperationLimits ───────────────────────────────────────────────────
        // These values appear under Server/ServerCapabilities/OperationLimits.
        // Clients read these to size their request batches correctly.
        // 0 = no limit imposed by this server (not recommended for production).
        config.setMaxNodesPerRead(1000);                          // max nodes per Read request
        config.setMaxNodesPerWrite(1000);                         // max nodes per Write request
        config.setMaxNodesPerBrowse(1000);                        // max nodes per Browse/BrowseNext
        config.setMaxNodesPerHistoryReadData(100);                // max nodes per HistoryRead (data)
        config.setMaxNodesPerHistoryReadEvents(100);              // max nodes per HistoryRead (events)
        config.setMaxNodesPerHistoryUpdateData(100);              // max nodes per HistoryUpdate (data)
        config.setMaxNodesPerHistoryUpdateEvents(100);            // max nodes per HistoryUpdate (events)
        config.setMaxNodesPerMethodCall(200);                     // max nodes per Method Call
        config.setMaxNodesPerRegisterNodes(1000);                 // max nodes per RegisterNodes
        config.setMaxNodesPerTranslateBrowsePathsToNodeIds(1000); // max nodes per TranslateBrowsePaths
        config.setMaxNodesPerNodeManagement(1000);                // max nodes per AddNodes/DeleteNodes
        config.setMaxMonitoredItemsPerCall(1000);                 // max items per CreateMonitoredItems
        // AsConfigured (default) = endpoints use exactly the host from BaseAddresses
        // NormalizeToHostname    = replace localhost/127.0.0.1 with the machine name
        config.setEndpointHostMode(UaEndpointHostMode.AsConfigured);

        // ── Certificate Store ─────────────────────────────────────────────────
        // Build the certificate store: one APPLICATION cert for the OPC UA secure channel,
        // plus one default HTTPS certificate presented at every opc.https TLS handshake.
        // load() tries to load all certs from disk; getMissingOrExpired() returns any
        // that are missing or expired so they can be rebuilt individually.
        java.util.List<UaServerCertificate> certs = new java.util.ArrayList<>();
        certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_13",
                config.getApplicationUri(), 720, "Indi.An GmbH",
                UaServerCertificate.CertificateRole.APPLICATION));
        // One default HTTPS certificate for all opc.https ports. The SDK presents it at the
        // TLS handshake for any opc.https port that has no specifically assigned certificate.
        // To serve an official domain certificate on a port, create another HTTPS certificate
        // and assign it: config.assignHttpsCertificateToPort(port, cert).
        UaServerCertificate httpsDefault = new UaServerCertificate("./pki", "secretpassword", "https-default", "urn:https-default:https", 720, "Indi.An GmbH", UaServerCertificate.CertificateRole.HTTPS);
        certs.add(httpsDefault);
        config.setDefaultHttpsCertificate(httpsDefault);

        // Try to load all certificates from disk into the store.
        // Certificates that are missing or cannot be read remain in the store
        // but are marked as not ready (isReady() = false).
        UaServerCertificateStore store = UaServerCertificateStore.load("./pki", certs);

        // getMissingOrExpired() returns all certificates that are either:
        //   - not present on disk (first run)
        //   - expired (NotAfter < now)
        //   - could not be loaded (wrong password, corrupt file)
        // Each of these is rebuilt as a new self-signed certificate.
        // build(true) overwrites any existing file - safe because we only
        // reach this for certs that are missing or no longer valid.
        for (UaServerCertificate missing : store.getMissingOrExpired())
            missing.build(true);

        // Hand the fully populated store to the configuration.
        // UaServer.start() will use it to set up the secure channel and
        // create the PKI directory structure (trusted/, rejected/, issuer/).

        config.setCertificateStore(store);
        return config;
    }

    private static void printConfig(UaServerConfiguration config) {
        System.out.println("── Active Server Configuration ──────────────────────────────");
        System.out.println("  ApplicationName  : " + config.getApplicationName());
        System.out.println("  ApplicationUri   : " + config.getApplicationUri());
        System.out.println("  NamespaceUri     : " + (config.getNamespaceUri() != null ? config.getNamespaceUri() : "(default)"));
        System.out.println("  ManufacturerName : " + (config.getManufacturerName().isEmpty() ? "(not set)" : config.getManufacturerName()));
        System.out.println("  ProductName      : " + (config.getProductName().isEmpty() ? "(not set)" : config.getProductName()));
        System.out.println("  SoftwareVersion  : " + (config.getSoftwareVersion().isEmpty() ? "(not set)" : config.getSoftwareVersion()));
        System.out.println("  BuildNumber      : " + (config.getBuildNumber().isEmpty() ? "(not set)" : config.getBuildNumber()));
        System.out.println();
        System.out.println("  Endpoints:");
        for (String addr : config.getBaseAddresses())
            System.out.println("    " + addr);
        System.out.println();
        System.out.println("  Certificate Store:");
        if (config.getCertificateStore() != null)
            System.out.println("    " + config.getCertificateStore());
        else
            System.out.println("    (not set)");
        System.out.println();
        System.out.println("  VendorServerInfo (Server/VendorServerInfo):");
        System.out.println("    VendorName           = " + (config.getVendorName() != null ? config.getVendorName() : "(not set)"));
        System.out.println("    VendorProductName    = " + (config.getVendorProductName() != null ? config.getVendorProductName() : "(not set)"));
        System.out.println("    VendorProductVersion = " + (config.getVendorProductVersion() != null ? config.getVendorProductVersion() : "(not set)"));
        System.out.println();
        System.out.println("  OperationLimits (Server/ServerCapabilities/OperationLimits):");
        System.out.printf("    MaxNodesPerRead                          = %d%n", config.getMaxNodesPerRead());
        System.out.printf("    MaxNodesPerWrite                         = %d%n", config.getMaxNodesPerWrite());
        System.out.printf("    MaxNodesPerBrowse                        = %d%n", config.getMaxNodesPerBrowse());
        System.out.printf("    MaxNodesPerHistoryReadData               = %d%n", config.getMaxNodesPerHistoryReadData());
        System.out.printf("    MaxNodesPerHistoryReadEvents             = %d%n", config.getMaxNodesPerHistoryReadEvents());
        System.out.printf("    MaxNodesPerHistoryUpdateData             = %d%n", config.getMaxNodesPerHistoryUpdateData());
        System.out.printf("    MaxNodesPerHistoryUpdateEvents           = %d%n", config.getMaxNodesPerHistoryUpdateEvents());
        System.out.printf("    MaxNodesPerMethodCall                    = %d%n", config.getMaxNodesPerMethodCall());
        System.out.printf("    MaxNodesPerRegisterNodes                 = %d%n", config.getMaxNodesPerRegisterNodes());
        System.out.printf("    MaxNodesPerTranslateBrowsePathsToNodeIds = %d%n", config.getMaxNodesPerTranslateBrowsePathsToNodeIds());
        System.out.printf("    MaxNodesPerNodeManagement                = %d%n", config.getMaxNodesPerNodeManagement());
        System.out.printf("    MaxMonitoredItemsPerCall                 = %d%n", config.getMaxMonitoredItemsPerCall());
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println();
    }

}
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
// PLCcom OPC UA Server SDK - Workshop 11: Simple Server
//
// The starting point for all server workshops. This example creates a fully
// functional OPC UA server that any compliant client can connect to, browse,
// read, write and subscribe to.
//
// The key concepts demonstrated here form the foundation for every OPC UA
// server application:
//
//   1. Configuration — set up endpoints, security and certificates
//   2. Address space — create folders and variables that clients can see
//   3. Data types    — each variable has a specific OPC UA data type
//   4. Value push    — update values from code; subscribed clients are
//                      notified automatically (no polling needed)
//   5. Client writes — react to values written by OPC UA clients
//
// The address space built here is intentionally simple:
//   Objects
//     └─ Plant
//         └─ Line1
//             └─ Machine1
//                 ├─ Temperature   (Double)     = 21.5
//                 ├─ Pressure      (Float)      = 1.013
//                 ├─ RPM           (Int32)      = 1500
//                 ├─ IsRunning     (Boolean)    = true
//                 ├─ Status        (String)     = "Idle"
//                 ├─ LastUpdate    (DateTime)   = now
//                 ├─ SerialNumber  (String)     = "SN-2025-001"  [ReadOnly]
//                 └─ Setpoints     (Double[])   = [20, 25, 30]
//
// What you will learn:
//   * How to configure and start an OPC UA server
//   * How to create a folder hierarchy in the address space
//   * How to create scalar and array variables of different data types
//   * How to mark a variable as read-only
//   * How to react when an OPC UA client writes a value (ValuesWritten)
//   * How to push value changes to subscribed clients from a background loop
//   * How to use the Path property to identify nodes by their browse path
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
//                                or: opc.https://localhost:48411
// ==============================================================================

import com.plccom.opc.ua.builtintypes.DateTime;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.UaRolePermissions;
import com.plccom.opc.ua.server.application.UaServer;
import com.plccom.opc.ua.server.application.UaServerCertificate;
import com.plccom.opc.ua.server.application.UaServerCertificateStore;
import com.plccom.opc.ua.server.application.UaServerConfiguration;
import com.plccom.opc.ua.server.application.UaEndpointHostMode;
import com.plccom.opc.ua.server.application.UaServerNodes.UaFolder;
import com.plccom.opc.ua.server.application.UaServerNodes.UaVariable;
import java.util.Random;

public class _11_SimpleServer {

	public static void main(String[] args) throws Exception {

		PLCcomConsole.open("Workshop 11 - Simple Server", 1000);

		// TODO: Replace with your license credentials from your license e-mail
		String licenseUser = "<Enter your UserName here>";
		String licenseSerial = "<Enter your Serial here>";

		System.out.println("╔══════════════════════════════════════════════════════════════╗");
		System.out.println("║  PLCcom OPC UA Server SDK - Workshop 11: Simple Server       ║");
		System.out.println("║                                                              ║");
		System.out.println("║  This example creates a minimal OPC UA server with:          ║");
		System.out.println("║    * Folder hierarchy  (Plant -> Line1 -> Machine1)          ║");
		System.out.println("║    * Scalar variables  (Double, Float, Int, Bool, String)    ║");
		System.out.println("║    * Array variable    (Double[])                            ║");
		System.out.println("║    * Read-only variable (SerialNumber)                       ║");
		System.out.println("║    * Client write notifications (ValuesWritten event)        ║");
		System.out.println("║    * Continuous value push loop (1-second interval)          ║");
		System.out.println("║                                                              ║");
		System.out.println("║  Every node created here is immediately visible to any       ║");
		System.out.println("║  connected OPC UA client. The Path property shows the        ║");
		System.out.println("║  dot-separated browse path from the Objects root.            ║");
		System.out.println("╚══════════════════════════════════════════════════════════════╝");
		System.out.println();

		// =============================================================================
		// Step 1: Configure the server
		// =============================================================================
		// All server settings are defined in createConfig() below.
		// See that method for a full description of every available option.
		UaServerConfiguration config = createConfig();

		printConfig(config);

		// =============================================================================
		// Step 2: Create the server and wire up events
		// =============================================================================
		try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

			System.out.println("  License: " + server.getLicenceMessage());
			System.out.println();

			// Accept all client certificates automatically.
			// WARNING: Do NOT use this in production! Use the PKI trust store instead.
			server.addCertificateValidationListener(e -> e.setAccept(true));

			// ValuesWritten fires whenever an OPC UA client writes one or more values.
			// Each UaWrittenItem contains:
			// getPath() — dot-separated browse path (e.g.
			// "Objects.Plant.Line1.Machine1.RPM")
			// getNodeId() — the OPC UA NodeId (e.g. ns=2;i=6)
			// getValue() — the value written by the client
			// This is the primary way to react to client writes from your application code.
			// ValuesWritten fires AFTER a successful write — the client already received
			// Good.
			// If WriteValidation rejects, this does NOT fire.
			server.addValuesWrittenListener(items -> {
				for (UaServer.UaWrittenItem item : items)
					System.out.println("  << Written: " + item.getPath() + " = " + item.getValueAsString());
			});

			// WriteValidation — called BEFORE any client write is committed to the address
			// space.
			// All internal checks (AccessLevel, DataType, Permissions) have already passed.
			// The handler receives ALL items of the write request as a batch.
			// Set item.setStatusCode() to any Bad_* value to reject that specific item.
			// If not handled or StatusCode remains Good, the write proceeds normally.
			//
			// You can also MODIFY the value before it is written by calling
			// item.setValue().
			// The modified value is then stored in the address space instead of the
			// original.
			//
			// !! IMPORTANT — PERFORMANCE WARNING !!
			// This handler runs synchronously on the server's write thread.
			// Any blocking operation (device I/O, database, slow network) will stall
			// the entire write request and can block other clients as well.
			//
			// If you need to forward the value to a device, prefer one of these patterns:
			// a) Accept immediately (Good) and forward asynchronously via CompletableFuture
			// or a queue.
			// The OPC UA client gets a fast response; the device update happens in the
			// background.
			// b) If you must wait for the device, always use a short timeout (e.g. 500 ms)
			// and return BadTimeout or BadNoCommunication if the device does not respond in
			// time.
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

			// Session events: log a line whenever a client connects or disconnects.
			server.addSessionListener(new UaServer.UaSessionListener() {
				@Override
				public void onSessionCreated(UaServer.UaSessionInfo s) {
					String name = s.getSessionName() != null ? s.getSessionName() : "(unnamed)";
					String uri = s.getClientUri() != null ? "  [" + s.getClientUri() + "]" : "";
					System.out.println("  >> Client connected:    \"" + name + "\"" + uri);
				}

				@Override
				public void onSessionClosed(UaServer.UaSessionInfo s) {
					String name = s.getSessionName() != null ? s.getSessionName() : "(unnamed)";
					System.out.println("  << Client disconnected: \"" + name + "\"");
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
			// Step 3: Build the address space
			// =============================================================================
			// The address space is the tree of nodes that clients can browse.
			// Folders organize the structure, Variables hold the actual data.
			// All nodes are immediately visible to connected clients.
			//
			// Every node has a Path property — the dot-separated browse path from root.
			// Example: "Objects.Plant.Line1.Machine1.Temperature"
			// This path is useful for logging, debugging and the server read/write API.
			System.out.println("── Building address space ───────────────────────────────────");

			// Create a folder hierarchy: Objects -> Plant -> Line1 -> Machine1
			// WITHOUT_RESTRICTIONS: all authenticated users (and anonymous in WS11) have
			// full access.
			// In Workshop 12 you will see how to restrict access per role.
			UaFolder plant = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);
			UaFolder line1 = server.createFolder(plant, "Line1", UaRolePermissions.WITHOUT_RESTRICTIONS);
			UaFolder machine = server.createFolder(line1, "Machine1", UaRolePermissions.WITHOUT_RESTRICTIONS);

			System.out.printf("  Folder    %-20s %s%n", plant.getName(), plant.getNodeId());
			System.out.printf("  Folder    %-20s %s%n", line1.getName(), line1.getNodeId());
			System.out.printf("  Folder    %-20s %s%n", machine.getName(), machine.getNodeId());

			// Create scalar variables — each has a specific OPC UA data type.
			// The Class<T> parameter determines the DataType attribute:
			// Double.class -> Double, Float.class -> Float, Integer.class -> Int32,
			// Boolean.class -> Boolean, String.class -> String, DateTime.class -> DateTime
			// WITHOUT_RESTRICTIONS: all users have full read/write access.
			// The readOnly parameter controls the AccessLevel attribute independently.
			UaVariable<Double> temperature = server.createVariable(machine, "Temperature",
					UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, 21.5, false);
			UaVariable<Float> pressure = server.createVariable(machine, "Pressure",
					UaRolePermissions.WITHOUT_RESTRICTIONS, Float.class, 1.013f, false);
			UaVariable<Integer> rpm = server.createVariable(machine, "RPM", UaRolePermissions.WITHOUT_RESTRICTIONS,
					Integer.class, 1500, false);
			UaVariable<Boolean> running = server.createVariable(machine, "IsRunning",
					UaRolePermissions.WITHOUT_RESTRICTIONS, Boolean.class, true, false);
			UaVariable<String> status = server.createVariable(machine, "Status", UaRolePermissions.WITHOUT_RESTRICTIONS,
					String.class, "Idle", false);
			UaVariable<DateTime> lastUpdate = server.createVariable(machine, "LastUpdate",
					UaRolePermissions.WITHOUT_RESTRICTIONS, DateTime.class, DateTime.currentTime(), false);

			// Read-only variable: clients can read but not write.
			// The server returns BadNotWritable on any write attempt.
			UaVariable<String> serialNo = server.createVariable(machine, "SerialNumber",
					UaRolePermissions.WITHOUT_RESTRICTIONS, String.class, "SN-2025-001", true);

			// Array variable: ValueRank is automatically set to OneDimension.
			// Clients see a Double[] value with 3 elements.
			UaVariable<Double[]> setpoints = server.createArrayVariable(machine, "Setpoints",
					UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class, new Double[] { 20.0, 25.0, 30.0 }, false);

			System.out.printf("  Double    %-20s %s  = 21.5%n", temperature.getName(), temperature.getNodeId());
			System.out.printf("  Float     %-20s %s  = 1.013%n", pressure.getName(), pressure.getNodeId());
			System.out.printf("  Int32     %-20s %s  = 1500%n", rpm.getName(), rpm.getNodeId());
			System.out.printf("  Boolean   %-20s %s  = true%n", running.getName(), running.getNodeId());
			System.out.printf("  String    %-20s %s  = Idle%n", status.getName(), status.getNodeId());
			System.out.printf("  DateTime  %-20s %s  = now%n", lastUpdate.getName(), lastUpdate.getNodeId());
			System.out.printf("  String    %-20s %s  = SN-2025-001 [ReadOnly]%n", serialNo.getName(),
					serialNo.getNodeId());
			System.out.printf("  Double[]  %-20s %s  = [20, 25, 30]%n", setpoints.getName(), setpoints.getNodeId());
			System.out.println();

			// =============================================================================
			// Step 4: Connect a client and explore
			// =============================================================================
			System.out.println("╔══════════════════════════════════════════════════════════════╗");
			System.out.println("║  Server is running. Connect with any OPC UA client to:       ║");
			System.out.println("║  opc.tcp://localhost:48410                                   ║");
			System.out.println("║  opc.https://localhost:48411                                 ║");
			System.out.println("║                                                              ║");
			System.out.println("║  Try:                                                        ║");
			System.out.println("║  * Browse Objects -> Plant -> Line1 -> Machine1              ║");
			System.out.println("║  * Subscribe to Temperature, RPM, Status                     ║");
			System.out.println("║  * Write a new value to RPM or Status                        ║");
			System.out.println("║  * Try writing to SerialNumber (should fail — ReadOnly)      ║");
			System.out.println("║  * Watch the ValuesWritten output in this console            ║");
			System.out.println("║                                                              ║");
			System.out.println("║  Press ENTER to start the value push loop.                   ║");
			System.out.println("╚══════════════════════════════════════════════════════════════╝");
			System.in.read();

			// =============================================================================
			// Step 5: Push value changes to subscribed clients
			// =============================================================================
			// Setting variable.setValue() automatically triggers a DataChange notification
			// to all clients that have an active subscription on that variable.
			// This is the OPC UA publish/subscribe model — no polling needed on the client.
			//
			// The value push runs in the main thread here for simplicity.
			// In production, you would typically update values from a PLC driver,
			// a database poller, or any other data source running on a background thread.
			System.out.println("  Pushing values every second... (CTRL+C or ENTER to exit)");
			System.out.println();

			Random rng = new Random();
			long cycle = 0;
			final boolean[] stop = { false };

			// Background thread waits for ENTER to stop the loop
			Thread exitThread = new Thread(() -> {
				try {
					System.in.read();
				} catch (Exception ignored) {
				}
				stop[0] = true;
			});
			exitThread.setDaemon(true);
			exitThread.start();

			while (!stop[0]) {
				cycle++;

				// Each call to setValue() notifies all subscribed clients immediately
				temperature.setValue(Math.round((20.0 + rng.nextDouble() * 10.0) * 100.0) / 100.0);
				pressure.setValue((float) (Math.round((0.9 + rng.nextDouble() * 0.3) * 1000.0) / 1000.0));
				rpm.setValue(1400 + rng.nextInt(200));
				running.setValue(cycle % 30 != 0); // simulate a stop every 30 seconds
				status.setValue(running.getValue() ? "Running" : "Stopped");
				lastUpdate.setValue(DateTime.currentTime());

				PLCcomConsole
						.replaceLastLine(String.format("  [%4d]  Temp=%5.1f\u00b0C  P=%.3fbar  RPM=%4d  State=%-8s",
								cycle, temperature.getValue(), pressure.getValue(), rpm.getValue(), status.getValue()));

				Thread.sleep(1000);
			}
			System.out.println();
			System.out.println("  Server stopped.");
		}
		PLCcomConsole.close();
	}

	// =============================================================================
	// Helper: createConfig
	// =============================================================================
	// Returns the server configuration. All available options are listed here
	// with a description and the default value. Adjust to your needs.
	private static UaServerConfiguration createConfig() throws Exception {
		UaServerConfiguration config = new UaServerConfiguration();

		// ── Application Identity ──────────────────────────────────────────────
		// ApplicationName: human-readable name shown to connecting clients
		// and embedded in the auto-generated server certificate.
		config.setApplicationName("PLCcom Workshop 11 - Simple Server");

		// ApplicationUri: globally unique identifier for this server instance.
		// Must match the URI in the server certificate.
		// Recommended format: urn:<host>:<company>:<product>
		config.setApplicationUri("urn:localhost:PLCcom:Workshop:11");

		// ProductUri: URI identifying the software product (not the instance).
		// Typically a URL pointing to the product page.
		config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");

		// NamespaceUri: URI for this server's application address space (ns=2).
		// Use a stable URI based on your company domain.
		// Default: null (auto-generated as ApplicationUri + "/nodes").
		config.setNamespaceUri("http://indi-an.com/opcua/workshop/simple-server");

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
		// opc.tcp — binary protocol, best performance, recommended
		// opc.https — SOAP/XML over HTTPS, for firewall-friendly scenarios
		// Default: empty (binds to all local interfaces on port 4840).
		config.setBaseAddresses(java.util.Arrays.asList("opc.tcp://localhost:48410", "opc.https://localhost:48411"));

		// ── Security Policies ─────────────────────────────────────────────────
		// Which encryption algorithms to offer on the endpoints.
		// getRecommendedSecurityModes() returns:
		// None (no encryption, for development only)
		// Basic256Sha256, Aes128_Sha256_RsaOaep, Aes256_Sha256_RsaPss
		// each with Sign + SignAndEncrypt
		config.setSecurityModes(UaServer.getRecommendedSecurityModes());

		// ── User Authentication ───────────────────────────────────────────────
		// Which authentication methods to accept from connecting clients.
		// Anonymous — no credentials required
		// UserName — username + password (see server.getUserManager())
		// Certificate — X.509 client certificate (see server.getUserManager())
		// Default: Anonymous + SecureUsernamePassword.
		config.setUserTokenPolicies(java.util.Arrays.asList(UserTokenPolicy.ANONYMOUS));

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

		// DiscoveryServerUrl: URL of the LDS to register with.
		// null = use the standard LDS at opc.tcp://localhost:4840.
		// config.setDiscoveryServerUrl("opc.tcp://localhost:4840");

		// ── VendorServerInfo ──────────────────────────────────────────────────
		// These values appear under Server/VendorServerInfo in the OPC UA
		// address space and identify your product to connecting clients.
		// null = the corresponding node is not created. Default: null.
		config.setVendorName("My Company GmbH");
		config.setVendorProductName("My OPC UA Server");
		config.setVendorProductVersion("1.0.0");

		// ── HTTPS TLS Policies ────────────────────────────────────────────────
		// Which TLS versions to offer on the opc.https endpoint.
		// Only relevant when at least one opc.https:// base address is configured.
		// IMPORTANT: if null, the opc.https endpoint is NOT activated (CRA compliance).
		// Must be set explicitly to enable HTTPS.
		config.setHttpsSecurityPolicies(
				java.util.Arrays.asList(com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_2_PFS,
						com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_3));

		// ── OperationLimits ───────────────────────────────────────────────────
		// These values appear under Server/ServerCapabilities/OperationLimits.
		// Clients read these to size their request batches correctly.
		// 0 = no limit imposed by this server (not recommended for production).
		config.setMaxNodesPerRead(1000); // max nodes per Read request
		config.setMaxNodesPerWrite(1000); // max nodes per Write request
		config.setMaxNodesPerBrowse(1000); // max nodes per Browse/BrowseNext
		config.setMaxNodesPerHistoryReadData(100); // max nodes per HistoryRead (data)
		config.setMaxNodesPerHistoryReadEvents(100); // max nodes per HistoryRead (events)
		config.setMaxNodesPerHistoryUpdateData(100); // max nodes per HistoryUpdate (data)
		config.setMaxNodesPerHistoryUpdateEvents(100); // max nodes per HistoryUpdate (events)
		config.setMaxNodesPerMethodCall(200); // max nodes per Method Call
		config.setMaxNodesPerRegisterNodes(1000); // max nodes per RegisterNodes
		config.setMaxNodesPerTranslateBrowsePathsToNodeIds(1000); // max nodes per TranslateBrowsePaths
		config.setMaxNodesPerNodeManagement(1000); // max nodes per AddNodes/DeleteNodes
		config.setMaxMonitoredItemsPerCall(1000); // max items per CreateMonitoredItems
		// AsConfigured (default) = endpoints use exactly the host from BaseAddresses
		// NormalizeToHostname = replace localhost/127.0.0.1 with the machine name
		config.setEndpointHostMode(UaEndpointHostMode.AsConfigured);

		// Build the certificate store: one APPLICATION cert for the OPC UA secure
		// channel,
		// plus one default HTTPS certificate presented at every opc.https TLS handshake.
		// load() tries to load all certs from disk; getMissingOrExpired() returns any
		// that are missing or expired so they can be rebuilt individually.
		java.util.List<UaServerCertificate> certs = new java.util.ArrayList<>();
		certs.add(new UaServerCertificate("./pki", "secretpassword", "PLCcom_Workshop_11", config.getApplicationUri(),
				720, "Indi.An GmbH", UaServerCertificate.CertificateRole.APPLICATION));
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
		// - not present on disk (first run)
		// - expired (NotAfter < now)
		// - could not be loaded (wrong password, corrupt file)
		// Each of these is rebuilt as a new self-signed certificate.
		// build(true) overwrites any existing file - safe because we only
		// reach this for certs that are missing or no longer valid.
		for (UaServerCertificate missing : store.getMissingOrExpired())
			missing.build(true);

		// Hand the fully populated store to the configuration.
		// UaServer.start() will use it to set up the secure channel and
		// create the PKI directory structure (trusted/, rejected/, issuers/).

		config.setCertificateStore(store);
		return config;
	}

	// =============================================================================
	// Helper: printConfig
	// =============================================================================
	// Prints the active server configuration to the console so you can verify
	// all settings at a glance before the server starts accepting connections.

	// =============================================================================
	// Helper: printConfig
	// =============================================================================
	// Prints the active server configuration to the console so you can verify
	// all settings at a glance before the server starts accepting connections.
	private static void printConfig(UaServerConfiguration config) {
		System.out.println("── Active Server Configuration ──────────────────────────────────────────────");
		System.out.println("  ApplicationName  : " + config.getApplicationName());
		System.out.println("  ApplicationUri   : " + config.getApplicationUri());
		System.out.println(
				"  NamespaceUri     : " + (config.getNamespaceUri() != null ? config.getNamespaceUri() : "(default)"));
		System.out.println("  ManufacturerName : "
				+ (config.getManufacturerName().isEmpty() ? "(not set)" : config.getManufacturerName()));
		System.out.println(
				"  ProductName      : " + (config.getProductName().isEmpty() ? "(not set)" : config.getProductName()));
		System.out.println("  SoftwareVersion  : "
				+ (config.getSoftwareVersion().isEmpty() ? "(not set)" : config.getSoftwareVersion()));
		System.out.println(
				"  BuildNumber      : " + (config.getBuildNumber().isEmpty() ? "(not set)" : config.getBuildNumber()));
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
		System.out.println("    VendorName           = "
				+ (config.getVendorName() != null ? config.getVendorName() : "(not set)"));
		System.out.println("    VendorProductName    = "
				+ (config.getVendorProductName() != null ? config.getVendorProductName() : "(not set)"));
		System.out.println("    VendorProductVersion = "
				+ (config.getVendorProductVersion() != null ? config.getVendorProductVersion() : "(not set)"));
		System.out.println();
		System.out.println("  OperationLimits (Server/ServerCapabilities/OperationLimits):");
		System.out.printf("    MaxNodesPerRead                          = %d%n", config.getMaxNodesPerRead());
		System.out.printf("    MaxNodesPerWrite                         = %d%n", config.getMaxNodesPerWrite());
		System.out.printf("    MaxNodesPerBrowse                        = %d%n", config.getMaxNodesPerBrowse());
		System.out.printf("    MaxNodesPerHistoryReadData               = %d%n",
				config.getMaxNodesPerHistoryReadData());
		System.out.printf("    MaxNodesPerHistoryReadEvents             = %d%n",
				config.getMaxNodesPerHistoryReadEvents());
		System.out.printf("    MaxNodesPerHistoryUpdateData             = %d%n",
				config.getMaxNodesPerHistoryUpdateData());
		System.out.printf("    MaxNodesPerHistoryUpdateEvents           = %d%n",
				config.getMaxNodesPerHistoryUpdateEvents());
		System.out.printf("    MaxNodesPerMethodCall                    = %d%n", config.getMaxNodesPerMethodCall());
		System.out.printf("    MaxNodesPerRegisterNodes                 = %d%n", config.getMaxNodesPerRegisterNodes());
		System.out.printf("    MaxNodesPerTranslateBrowsePathsToNodeIds = %d%n",
				config.getMaxNodesPerTranslateBrowsePathsToNodeIds());
		System.out.printf("    MaxNodesPerNodeManagement                = %d%n", config.getMaxNodesPerNodeManagement());
		System.out.printf("    MaxMonitoredItemsPerCall                 = %d%n", config.getMaxMonitoredItemsPerCall());
		System.out.println("─────────────────────────────────────────────────────────────────────────────");
		System.out.println();
	}

}
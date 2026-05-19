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
// PLCcom OPC UA Client SDK - Workshop 13: Connect with User Authentication
//
// Workshop 12 connected anonymously. Many production servers require
// username/password authentication. This workshop shows how to set
// user credentials on the ClientConfiguration before connecting.
//
// OPC UA supports three user identity types:
//   Anonymous   - no credentials (see Workshop 12)
//   UserName    - classic username + password (this workshop)
//   Certificate - X.509 client certificate (see Workshop 14)
//
// Server Workshop 12 defines three users:
//   viewer   / viewer123   -> Role.Observer  (read-only)
//   operator / operator123 -> Role.Operator  (read + write + call)
//   admin    / admin123    -> Role.Engineer  (full access)
//
// The role only has effect because Server Workshop 12 calls setRolePermissions()
// on its nodes. Without that, all authenticated users would have identical access.
//
// What you will learn:
//   * How to set username/password credentials on a session
//   * How UserIdentity is passed to the server during ActivateSession
//   * How to handle authentication failures
//   * How role-based access control affects Read, Write and Call
//
// Target server: opc.tcp://localhost:48410
// (Start Server Workshop 12 for a server that requires authentication)
// ==============================================================================

import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaClientCertificate;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.UserIdentity;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.CallMethodRequest;
import com.plccom.opc.ua.core.CallMethodResult;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

public class _13_ConnectWithUserAuth
		implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

	public static void main(String[] args) {
		new _13_ConnectWithUserAuth().start();
	}

	void start() {

		PLCcomConsole.open("Workshop 13 - Connect with User Authentication", 1000);

		try {
			System.out.println("╔═══════════════════════════════════════════════════════════════╗");
			System.out.println("║  PLCcom OPC UA Client SDK - Workshop 13: User Authentication  ║");
			System.out.println("║                                                               ║");
			System.out.println("║  Server Workshop 12 defines three users:                      ║");
			System.out.println("║    viewer   / viewer123   -> Observer  (read-only)            ║");
			System.out.println("║    operator / operator123 -> Operator  (read + write + call)  ║");
			System.out.println("║    admin    / admin123    -> Engineer  (full access)          ║");
			System.out.println("║                                                               ║");
			System.out.println("║  Roles only take effect because the server calls              ║");
			System.out.println("║  setRolePermissions() on its nodes.                           ║");
			System.out.println("║                                                               ║");
			System.out.println("║  Required server: Server Workshop 12 (User Authentication)    ║");
			System.out.println("║  opc.tcp://localhost:48410                                    ║");
			System.out.println("╚═══════════════════════════════════════════════════════════════╝");
			System.out.println();

			// TODO: Replace with your license credentials from your license e-mail
			String licenseUser = "<Enter your UserName here>";
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
				try {
					System.in.read();
				} catch (Exception ignored) {
				}
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
			String input = reader.readLine();
			int index = -1;
			try {
				index = Integer.parseInt(input.trim());
			} catch (NumberFormatException ignored) {
			}

			if (index < 0 || index >= endpoints.length) {
				System.err.println("  Invalid endpoint index.");
				System.out.println("  Press ENTER to exit.");
				try {
					System.in.read();
				} catch (Exception ignored) {
				}
				return;
			}

			EndpointDescription endpoint = endpoints[index];
			System.out.println();
			System.out.println("  Selected: " + OpcUaDisplayUtils.toDisplayString(endpoint));
			System.out.println();

			// -- Step 2: Enter user credentials -----------------------------------
			// The server validates credentials during ActivateSession.
			// If wrong, connect() throws Bad_IdentityTokenRejected or Bad_UserAccessDenied.
			System.out.print("  Username: ");
			String username = reader.readLine().trim();
			System.out.print("  Password: ");
			String password = readPassword();
			System.out.println();

			UserIdentity userIdentity = new UserIdentity(username, password);

			// -- Step 3: Build client configuration -------------------------------
			// createConfig() builds the ClientConfiguration for the selected endpoint.
			// It handles certificate creation/loading automatically based on the
			// endpoint security mode and transport protocol.
			ClientConfiguration config = createConfig(endpoint, userIdentity);
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

			System.out.print("  Connecting as '" + username + "' ... ");
			client.connect();
			System.out.println("OK");
			System.out.println("  Session connected: " + client.isConnected());
			System.out.println();

			// -- Step 5: Test role-based access -----------------------------------
			System.out.println("── Role-based access test ───────────────────────────────────────");

			// Read Temperature - allowed for all roles (Observer, Operator, Engineer)
			NodeId temperatureId = client.getNodeIdByPath("Objects.Plant.Temperature");
			Object value = client.readValue(temperatureId).getResults()[0].getValue().getValue();
			System.out.println("  Read  Temperature = " + value + "  -> OK");

			// Write Temperature - Observer gets BadUserAccessDenied because the server
			// called setRolePermissions() granting Write only to Operator and Engineer.
			StatusCode writeResult = client.writeValue(temperatureId, 99.9);
			if (writeResult.isGood())
				System.out.println("  Write Temperature = 99.9   -> OK (role allows write)");
			else
				System.out.println("  Write Temperature = 99.9   -> " + writeResult + " (role does not allow write)");
			System.out.println();

			// Call Reset - Observer gets BadUserAccessDenied (allowRead gives Browse, not
			// Call).
			// Operator and Engineer succeed.
			NodeId resetId = client.getNodeIdByPath("Objects.Plant.Reset");
			NodeId plantId = client.getNodeIdByPath("Objects.Plant");
			try {
				CallMethodResult[] results = client.call(new CallMethodRequest(plantId, resetId, null));
				StatusCode callStatus = results[0].getStatusCode();
				if (callStatus.isGood())
					System.out.println("  Call  Reset              -> OK (role allows call)");
				else
					System.out.println("  Call  Reset              -> " + callStatus + " (role does not allow call)");
			} catch (Exception ex) {
				System.out.println("  Call  Reset              -> " + ex.getMessage() + " (role does not allow call)");
			}
			System.out.println();

			// -- Step 6: Disconnect -----------------------------------------------
			System.out.println("  Press ENTER to disconnect and exit.");
			reader.readLine();

			if (client.isConnected())
				client.close();
			System.out.println("  Disconnected.");

		} catch (Exception e) {
			System.err.println("  Error: " + e.getMessage());
			e.printStackTrace();
			System.out.println("  Press ENTER to exit.");
			try {
				System.in.read();
			} catch (Exception ignored) {
			}
		} finally {
			PLCcomConsole.close();
		}
	}

	// ── Certificate validation ───────────────────────────────────────────────

	// Called when the server presents its certificate during the secure channel
	// handshake. For development we accept all certificates here.
	@Override
	public StatusCode validateCertificate(Cert cert) {
		System.out.println("  [Certificate] Accepted: " + (cert != null ? cert.certificate.getSubjectDN() : "null"));
		return StatusCode.GOOD;
	}

	@Override
	public StatusCode validateCertificate(ApplicationDescription app, Cert cert) {
		return validateCertificate(cert);
	}

	// ── Session events ───────────────────────────────────────────────────────

	@Override
	public void onSessionKeepAlive(ServerStatusDataType status, ServerState state) {
	}

	@Override
	public void onSessionConnectionStateChanged(boolean isConnected) {
		if (isConnected)
			System.out.println("  [Connected] Session established");
		else
			System.out.println("  [ConnectionLost] Connection lost");
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	// Reads a password from the console, masking each character with '*'.
	static String readPassword() {
		StringBuilder password = new StringBuilder();
		try {
			java.io.Console console = System.console();
			if (console != null) {
				// Console available — use built-in password masking
				char[] chars = console.readPassword();
				if (chars != null)
					password.append(new String(chars));
			} else {
				// IDE / redirected input — read plain (no masking possible)
				BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				String line = reader.readLine();
				if (line != null)
					password.append(line.trim());
			}
		} catch (Exception ignored) {
		}
		return password.toString();
	}

	// =============================================================================
	// Helper: createConfig
	// =============================================================================
	// Builds the ClientConfiguration for the selected endpoint.
	//
	// Certificate handling:
	// Application certificate — required for Sign / SignAndEncrypt endpoints.
	// HTTPS certificate — required for opc.https:// endpoints (any SecurityMode).
	//
	// UaClientCertificate derives file paths automatically from the PKI base
	// directory:
	// pki/own/certs/<alias>.der <- certificate
	// pki/own/private/<alias>.pem <- private key
	//
	// load() returns null if the certificate does not exist yet or cannot be read.
	// build(true) creates a new self-signed certificate, overwriting any existing
	// file.
	static ClientConfiguration createConfig(EndpointDescription endpoint, UserIdentity userIdentity) throws Exception {

		ClientConfiguration config = new ClientConfiguration(new LocalizedText("PLCcom_Workshop_13", "en"), endpoint,
				userIdentity);

		// HTTPS Certificate — required for opc.https:// endpoints, independent of
		// SecurityMode.
		// The hostname is extracted from the endpoint URL and used as the certificate
		// alias.
		UaClientCertificate httpsCert = null;
		if (endpoint.getEndpointUrl() != null && endpoint.getEndpointUrl().toLowerCase().startsWith("opc.https://")) {
			String host = new java.net.URI(endpoint.getEndpointUrl()).getHost();
			httpsCert = UaClientCertificate.load("./pki", host, "secretpassword");
			if (httpsCert == null || !httpsCert.checkValidity())
				httpsCert = new UaClientCertificate("./pki", "secretpassword", host, 720, "Indi.An GmbH").build(true);
		}

		// Application Certificate — required for secured endpoints (Sign or
		// SignAndEncrypt).
		// Not needed for SecurityMode.None (unencrypted connections).
		UaClientCertificate appCert = null;
		if (endpoint.getSecurityMode() != MessageSecurityMode.None) {
			appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_13", "secretpassword");
			if (appCert == null || !appCert.checkValidity())
				appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_13", 720, "Indi.An GmbH")
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
		System.out.println("  PKI Store : "
				+ (config.getCertificateStorePath() != null ? config.getCertificateStorePath() : "(not set)"));
		System.out.println("─────────────────────────────────────────────────────────────────────────────");
		System.out.println();
	}

}

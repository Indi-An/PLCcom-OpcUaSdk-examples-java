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

import com.plccom.opc.ua.builtintypes.LocalizedText;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.client.application.ClientConfiguration;
import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.UaClientCertificate;
import com.plccom.opc.ua.client.application.UserIdentity;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.MessageSecurityMode;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Workshop 14 - Connect with Certificate Authentication
 *
 * Workshop 13 used username/password. For machine-to-machine communication,
 * X.509 certificate authentication is more secure and does not require storing
 * passwords. The client presents a certificate and the server validates it
 * against its trusted certificate store.
 *
 * OPC UA supports three user identity types: Anonymous - no credentials (see
 * Workshop 12) UserName - classic username + password (see Workshop 13)
 * Certificate - X.509 client certificate (this workshop)
 *
 * What you will learn: - How to load or create an X.509 user certificate - How
 * to create a UserIdentity from a certificate - How certificate authentication
 * differs from username/password - How the server validates the user
 * certificate
 *
 * Target server: opc.tcp://localhost:48410 (Start any Server SDK workshop
 * first, e.g. Server Workshop 11)
 */
public class _14_ConnectWithCertAuth
		implements SessionKeepAliveListener, SessionConnectionStateChangeListener, CertificateValidator {

	public static void main(String[] args) {
		new _14_ConnectWithCertAuth().start();
	}

	void start() {

		PLCcomConsole.open("Workshop 14 - Connect with Certificate Authentication", 1000);

		try {
			System.out.println("╔══════════════════════════════════════════════════════════════╗");
			System.out.println("║  PLCcom OPC UA Client SDK - Workshop 14: Certificate Auth    ║");
			System.out.println("║                                                              ║");
			System.out.println("║  For machine-to-machine communication, X.509 certificate     ║");
			System.out.println("║  authentication is more secure than username/password.       ║");
			System.out.println("║  The client presents a certificate that the server validates ║");
			System.out.println("║  against its trusted certificate store.                      ║");
			System.out.println("║                                                              ║");
			System.out.println("║  What you will learn:                                        ║");
			System.out.println("║    * Load an X.509 certificate from a .der file              ║");
			System.out.println("║    * Set certificate-based UserIdentity on a session         ║");
			System.out.println("║    * Difference to username/password authentication          ║");
			System.out.println("║                                                              ║");
			System.out.println("║  opc.tcp://localhost:48410                                   ║");
			System.out.println("╚══════════════════════════════════════════════════════════════╝");
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

			UaClientCertificate userCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_14_User",
					"secretpassword");
			if (userCert == null || !userCert.checkValidity())
				userCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_14_User", 720,
						"Indi.An GmbH").build(true);

			UserIdentity userIdentity = new UserIdentity(userCert.getCertificate(), userCert.getPrivateKey());

			// -- Step 3: Build client configuration -------------------------------
			// createConfig() builds the ClientConfiguration for the selected endpoint.
			// It handles certificate creation/loading automatically based on the
			// endpoint security mode and transport protocol.
			ClientConfiguration config = createConfig(endpoint, userIdentity);
			printConfig(config);

			// Registers this class as the certificate validator for the server
			// certificate. The validateCertificate() method below accepts all
			// certificates — suitable for development and testing.
			// Remove this listener to activate PKI-based validation via the store above.
			config.setCertificateValidator(this);

			// -- Step 6: Create client and register event listeners ---------------
			UaClient client = new UaClient(licenseUser, licenseSerial, config);
			System.out.println("  License: " + client.getLicenceMessage());
			System.out.println();

			client.addSessionKeepAliveListener(this);
			client.addSessionConnectionStateChangeListener(this);

			// -- Step 7: Connect --------------------------------------------------
			System.out.print("  Connecting with certificate ... ");
			client.connect();
			System.out.println("OK");
			System.out.println("  Session connected: " + client.isConnected());
			System.out.println();

			// -- Step 8: Disconnect -----------------------------------------------
			System.out.println("  Press ENTER to disconnect and exit.");
			reader.readLine();

			if (client.isConnected()) {
				client.close();
			}
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

	// ── Event handlers ──────────────────────────────────────────────────────

	// Called periodically by the server to confirm the session is still alive.
	// The ServerStatusDataType contains server diagnostics (uptime, build info);
	// the ServerState reflects the server's current run state (Running, etc.).
	@Override
	public void onSessionKeepAlive(ServerStatusDataType status, ServerState state) {
		System.out.println("  [KeepAlive] Server state: " + state);
	}

	// Called whenever the session connects or disconnects (e.g. network
	// interruption). The SDK attempts automatic reconnection in the background;
	// isConnected=true signals a successful (re)connect.
	@Override
	public void onSessionConnectionStateChanged(boolean isConnected) {
		if (isConnected) {
			System.out.println("  [Connected] Session established");
		} else {
			System.out.println("  [ConnectionLost] Connection lost");
		}
	}

	// Called when the server presents its certificate during the secure channel
	// handshake. Return StatusCode.GOOD to accept, or StatusCode.BAD to reject.
	// For development we accept all certificates here. In production, verify
	// the certificate against a trusted certificate store.
	@Override
	public StatusCode validateCertificate(Cert cert) {
		System.out.println("  [Certificate] Accepted: " + (cert != null ? cert.certificate.getSubjectDN() : "null"));
		return StatusCode.GOOD;
	}

	// Overload called when the server presents both an ApplicationDescription
	// and a certificate. The ApplicationDescription can be used to cross-check
	// the certificate's common name against the server's application URI.
	@Override
	public StatusCode validateCertificate(ApplicationDescription app, Cert cert) {
		return validateCertificate(cert);
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

		ClientConfiguration config = new ClientConfiguration(new LocalizedText("PLCcom_Workshop_14", "en"), endpoint,
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
			appCert = UaClientCertificate.load("./pki", "PLCcom_Workshop_14", "secretpassword");
			if (appCert == null || !appCert.checkValidity())
				appCert = new UaClientCertificate("./pki", "secretpassword", "PLCcom_Workshop_14", 720, "Indi.An GmbH")
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

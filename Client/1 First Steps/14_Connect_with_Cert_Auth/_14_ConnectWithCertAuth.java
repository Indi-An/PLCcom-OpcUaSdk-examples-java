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
import com.plccom.opc.ua.client.application.UaCertificateManager;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.client.application.UserIdentity;
import com.plccom.opc.ua.client.application.listener.SessionConnectionStateChangeListener;
import com.plccom.opc.ua.client.application.listener.SessionKeepAliveListener;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.core.ServerState;
import com.plccom.opc.ua.core.ServerStatusDataType;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;
import com.plccom.opc.ua.transport.security.KeyPair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Workshop 14 - Connect with Certificate Authentication
 *
 * Workshop 13 used username/password. For machine-to-machine communication,
 * X.509 certificate authentication is more secure and does not require
 * storing passwords. The client presents a certificate and the server
 * validates it against its trusted certificate store.
 *
 * OPC UA supports three user identity types:
 *   Anonymous   - no credentials (see Workshop 12)
 *   UserName    - classic username + password (see Workshop 13)
 *   Certificate - X.509 client certificate (this workshop)
 *
 * What you will learn:
 *   - How to load or create an X.509 user certificate
 *   - How to create a UserIdentity from a certificate
 *   - How certificate authentication differs from username/password
 *   - How the server validates the user certificate
 *
 * Target server: opc.tcp://localhost:48410
 * (Start any Server SDK workshop first, e.g. Server Workshop 11)
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

            // -- Step 3: Load or create the application instance certificate ------
            // This certificate identifies the client application to the server.
            // It is used for the secure channel (transport-level security) and is
            // separate from the user certificate used for authentication.
            KeyPair appCert = loadOrCreateCertificate(
                    "CertificateStores/PLCcom_Workshop_14.der", "secretpassword", "PLCcom_Workshop_14");
            System.out.println("  Application certificate: ready");

            // -- Step 4: Load or create the user certificate ----------------------
            // The user certificate is the identity token - it proves WHO is
            // connecting. For this workshop we create a self-signed certificate.
            // In production, use a dedicated user certificate issued by your PKI.
            // The server must trust this certificate (add it to its trusted
            // user certificates store).
            KeyPair userCert = loadOrCreateCertificate(
                    "CertificateStores/PLCcom_Workshop_14_User.der", "secretpassword", "PLCcom_Workshop_14_User");
            System.out.println("  User certificate:        ready");
            System.out.println();

            // -- Step 5: Build configuration with certificate identity -----------
            // UserIdentity with a certificate: the client signs an activation
            // token with the private key. The server verifies the signature
            // against the certificate - no password is transmitted at all.
            UserIdentity userIdentity = new UserIdentity(userCert.getCertificate(), userCert.getPrivateKey());

            ClientConfiguration config = new ClientConfiguration(
                    new LocalizedText("PLCcom_Workshop_14", "en"), endpoint, userIdentity);

            config.setInstanceCertificate(appCert);

            // Register this class as the certificate validator for the server
            // certificate. The validateCertificate() method below accepts all
            // certificates. In production, verify against a trusted store.
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
            try { System.in.read(); } catch (Exception ignored) { }
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
        System.out.println("  [Certificate] Accepted: "
                + (cert != null ? cert.certificate.getSubjectDN() : "null"));
        return StatusCode.GOOD;
    }

    // Overload called when the server presents both an ApplicationDescription
    // and a certificate. The ApplicationDescription can be used to cross-check
    // the certificate's common name against the server's application URI.
    @Override
    public StatusCode validateCertificate(ApplicationDescription app, Cert cert) {
        return validateCertificate(cert);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Loads an existing application instance certificate from disk, or creates
     * a new self-signed certificate if none exists yet. The certificate is
     * stored as a .der file alongside a matching private key file.
     *
     * @param certFile path to the .der certificate file
     * @param password password used to protect the private key
     * @param alias    common name (CN) used when creating a new certificate
     * @return the loaded or newly created key pair
     * @throws Exception if certificate creation or loading fails
     */
    static KeyPair loadOrCreateCertificate(String certFile, String password, String alias) throws Exception {
        java.io.File f = new java.io.File(certFile);
        f.getParentFile().mkdirs();
        if (!f.isFile())
            return UaCertificateManager.createSelfSignedCertificate(certFile, alias, password, 720, "Indi.An GmbH");
        else
            return UaCertificateManager.getCertificate(certFile, password);
    }
}

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

import com.plccom.opc.ua.client.application.OpcUaDisplayUtils;
import com.plccom.opc.ua.client.application.SortDirection;
import com.plccom.opc.ua.client.application.UaClient;
import com.plccom.opc.ua.core.EndpointDescription;
import com.plccom.opc.ua.builtintypes.StatusCode;
import com.plccom.opc.ua.core.ApplicationDescription;
import com.plccom.opc.ua.transport.security.Cert;
import com.plccom.opc.ua.transport.security.CertificateValidator;

import java.net.URI;

public class _11_DiscoverServer implements CertificateValidator {

    public static void main(String[] args) {

        new _11_DiscoverServer().start();
    }

    void start() {

        PLCcomConsole.open("Workshop 11 - Discover Server", 1000);

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA Client SDK - Workshop 11: Discover Server     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Before connecting to an OPC UA server, you need to know     ║");
            System.out.println("║  which endpoints it offers. The discovery process queries    ║");
            System.out.println("║  the server for all available endpoints including their      ║");
            System.out.println("║  security policies and transport protocols.                  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * How to discover endpoints filtered by transport         ║");
            System.out.println("║      protocol (discoverEndpoints)                            ║");
            System.out.println("║    * How to discover all endpoints incl. all transports      ║");
            System.out.println("║    * How to provide a CertificateValidator for opc.https     ║");
            System.out.println("║    * How to sort endpoints by security level                 ║");
            System.out.println("║    * How to read endpoint details (URL, security mode,       ║");
            System.out.println("║      security policy, security level)                        ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required server: Server Workshop 11 (Simple Server)         ║");
            System.out.println("║  opc.tcp://localhost:48410                                   ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // -- Step 1: Define the discovery URL ------------------------------
            String serverUrl = "opc.tcp://localhost:48410";

            System.out.println("  Discovery URL: " + serverUrl);
            System.out.println("  Querying endpoints...");
            System.out.println();

            // -- Step 2: Define a certificate validator ------------------------
            // For opc.tcp, the validator is optional (pass null).
            // For opc.https, a CertificateValidator is required - it controls
            // which server TLS certificates are accepted.
            // In production, verify the certificate against a trusted store.
            CertificateValidator validator = this;

            // -- Step 3: Discover endpoints (filtered by transport protocol) ---
            // discoverEndpoints() returns only endpoints whose transport matches
            // the scheme of the discovery URL. With opc.tcp://... only opc.tcp
            // endpoints are returned; with opc.https://... only opc.https ones.
            // No session is created - this is an anonymous, lightweight call.
            EndpointDescription[] endpoints = UaClient.discoverEndpoints(new URI(serverUrl), validator);

            if (endpoints == null || endpoints.length == 0) {
                System.err.println("  No endpoints found. Is the server running?");
                return;
            }

            // -- Step 4: Sort by security level and display --------------------
            // sortBySecurityLevel(Asc) puts the least secure endpoint (None) first.
            // This makes index 0 always the easiest to connect to for testing.
            // In production, choose the highest security level your environment supports.
            endpoints = UaClient.sortBySecurityLevel(endpoints, SortDirection.Asc);

            System.out.println("  Found " + endpoints.length + " endpoint(s) matching transport '" + new URI(serverUrl).getScheme() + "':");
            System.out.println();

            int counter = 0;
            for (EndpointDescription ep : endpoints) {
                System.out.println("  [" + counter++ + "] " + OpcUaDisplayUtils.toDisplayString(ep));
            }

            // -- Step 5: Discover ALL endpoints (all transports) ---------------
            // discoverAllEndpoints() returns every endpoint the server offers,
            // regardless of transport. Use this if you need to see all available
            // transports (opc.tcp, opc.https, ...) in one call.
            System.out.println();
            System.out.println("  All endpoints (all transports):");
            System.out.println();

            EndpointDescription[] allEndpoints = UaClient.discoverAllEndpoints(new URI(serverUrl), validator);
            allEndpoints = UaClient.sortBySecurityLevel(allEndpoints, SortDirection.Asc);

            counter = 0;
            for (EndpointDescription ep : allEndpoints) {
                System.out.println("  [" + counter++ + "] " + OpcUaDisplayUtils.toDisplayString(ep));
            }

        } catch (Exception e) {
            System.err.println("  Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("  Press ENTER to exit.");
            try { System.in.read(); } catch (Exception ignored) { }
            PLCcomConsole.close();
        }
    }

    // ── Event handlers ──────────────────────────────────────────────────────

    // Called when the server presents its certificate - both during opc.https
    // discovery (TLS) and when a security policy other than None is used.
    // Inspect the certificate and return StatusCode.GOOD to accept or
    // StatusCode.BAD to reject. In production, verify against a trusted store.
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
}

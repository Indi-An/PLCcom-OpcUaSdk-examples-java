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
// PLCcom OPC UA Server SDK - Workshop 12b: Custom Auth Validator
//
// Workshop 12a used the built-in user database (addUser) and OPC UA RolePermissions
// to control access. This workshop demonstrates the alternative approach:
//
//   IUaCredentialValidator  — replaces username/password validation entirely.
//                             No addUser() calls needed.
//
//   IUaPermissionValidator  — replaces the built-in RolePermissions enforcement.
//                             No setRolePermissions() on nodes needed.
//                             Nodes are created with WITHOUT_RESTRICTIONS.
//
// The same three users and the same access rules as Workshop 12a are implemented,
// but entirely in custom validator classes — no OPC UA role concepts involved.
//
// Users:
//   admin    / admin123    -> full access  (read, write, call)
//   operator / operator123 -> read + write + call
//   viewer   / viewer123   -> read only   (browse, read, subscribe)
//
// What you will learn:
//   * How to replace built-in authentication with a custom credential validator
//   * How to replace built-in RolePermissions with a custom permission validator
//   * How every access check is logged to the console
//   * That nodes must use WITHOUT_RESTRICTIONS when a permission validator is active
//
// Connect with any OPC UA client to: opc.tcp://localhost:48410
// ==============================================================================

import com.plccom.opc.ua.builtintypes.Variant;
import com.plccom.opc.ua.core.UserTokenPolicy;
import com.plccom.opc.ua.server.application.IUaCredentialValidator;
import com.plccom.opc.ua.server.application.IUaPermissionValidator;
import com.plccom.opc.ua.server.application.UaNodeContext;
import com.plccom.opc.ua.server.application.UaPermissionCheck;
import com.plccom.opc.ua.server.application.UaRolePermissions;
import com.plccom.opc.ua.server.application.UaServer;
import com.plccom.opc.ua.server.application.UaServerConfiguration;
import com.plccom.opc.ua.server.application.UaEndpointHostMode;
import com.plccom.opc.ua.server.application.UaServerNodes.UaFolder;
import com.plccom.opc.ua.server.application.UaServerNodes.UaVariable;
import com.plccom.opc.ua.server.application.UaSessionContext;

import java.util.HashMap;
import java.util.Map;

public class _12b_CustomAuthValidator {

    public static void main(String[] args) throws Exception {

        PLCcomConsole.open("Workshop 12b - Custom Auth Validator", 1000);

        // TODO: Replace with your license credentials from your license e-mail
        String licenseUser   = "<Enter your UserName here>";
        String licenseSerial = "<Enter your Serial here>";

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  PLCcom OPC UA Server SDK - Workshop 12b: Custom Validator   ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Same users and access rules as Workshop 12a, but using      ║");
        System.out.println("║  IUaCredentialValidator and IUaPermissionValidator instead   ║");
        System.out.println("║  of addUser() and setRolePermissions().                      ║");
        System.out.println("║                                                              ║");
        System.out.println("║    admin    / admin123    -> full access                     ║");
        System.out.println("║    operator / operator123 -> read + write + call             ║");
        System.out.println("║    viewer   / viewer123   -> read only                       ║");
        System.out.println("║                                                              ║");
        System.out.println("║  Anonymous access is disabled - you MUST log in.             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // =============================================================================
        // Step 1: Configure the server
        // =============================================================================
        UaServerConfiguration config = createConfig();
        printConfig(config);

        // =============================================================================
        // Step 2: Create server and register custom validators
        // =============================================================================
        // IUaCredentialValidator replaces addUser() — no user database needed.
        // IUaPermissionValidator replaces setRolePermissions() — no OPC UA roles needed.
        // The two validators are independent: you can use either or both.
        try (UaServer server = new UaServer(licenseUser, licenseSerial)) {

            System.out.println("  License: " + server.getLicenceMessage());
            System.out.println();

            // Register the custom credential validator.
            // Called during ActivateSession to verify username/password.
            server.setCredentialValidator(new MyCredentialValidator());

            // Register the custom permission validator.
            // Called for every Browse, Read, Write, Call, Subscribe etc.
            // When set, the built-in RolePermissions on nodes are bypassed entirely.
            server.setPermissionValidator(new MyPermissionValidator());

            System.out.println("── Validators ──────────────────────────────────────────────");
            System.out.println("  CredentialValidator : MyCredentialValidator (custom auth)");
            System.out.println("  PermissionValidator : MyPermissionValidator (custom access)");
            System.out.println();

            // =============================================================================
            // Step 3: Handle certificate validation and session events
            // =============================================================================
            server.addCertificateValidationListener(e -> e.setAccept(true));

            server.addSessionListener(new UaServer.UaSessionListener() {
                @Override
                public void onSessionCreated(UaServer.UaSessionInfo s) {
                    String name = s.getSessionName() != null ? s.getSessionName() : "(unnamed)";
                    System.out.println("  >> Client connected:    \"" + name + "\"");
                }
                @Override
                public void onSessionClosed(UaServer.UaSessionInfo s) {
                    String name = s.getSessionName() != null ? s.getSessionName() : "(unnamed)";
                    System.out.println("  << Client disconnected: \"" + name + "\"");
                }
            });

            server.addValuesWrittenListener(items -> {
                for (UaServer.UaWrittenItem item : items)
                    System.out.println("  << OPC Write: " + item.getPath()
                            + " (" + item.getNodeId() + ") = " + item.getValueAsString());
            });

            // =============================================================================
            // Step 4: Start server
            // =============================================================================
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
            // Step 5: Build address space
            // =============================================================================
            // IMPORTANT: When using IUaPermissionValidator, nodes must be created with
            // WITHOUT_RESTRICTIONS. The permission validator takes full control of access
            // decisions — the stack must not pre-filter via RolePermissions.
            UaFolder plant = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);
            UaVariable<Double>  temp = server.createVariable(plant, "Temperature",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Double.class,  22.0,  false);
            UaVariable<Integer> rpm  = server.createVariable(plant, "RPM",
                    UaRolePermissions.WITHOUT_RESTRICTIONS, Integer.class, 1500,  false);

            server.createMethod(plant, "Reset",
                    inputs -> {
                        temp.setValue(22.0);
                        rpm.setValue(1500);
                        System.out.println("  << Reset called");
                        return new Variant[0];
                    },
                    UaRolePermissions.WITHOUT_RESTRICTIONS);

            System.out.println("── Address space ────────────────────────────────────────────");
            System.out.printf("  Double  %-12s %s  = 22.0%n", temp.getName(), temp.getNodeId());
            System.out.printf("  Int32   %-12s %s  = 1500%n", rpm.getName(),  rpm.getNodeId());
            System.out.println("  Method  Reset");
            System.out.println("  (access controlled by MyPermissionValidator)");
            System.out.println();

            // =============================================================================
            // Step 6: Wait
            // =============================================================================
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  Server is running - authentication required.                ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Test with Client Workshop 13:                               ║");
            System.out.println("║  * viewer/viewer123   -> read OK, write/call rejected        ║");
            System.out.println("║  * operator/operator123 -> read + write + call OK            ║");
            System.out.println("║  * admin/admin123     -> full access                         ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Watch [AUTH] and [PERM] log lines in this console.          ║");
            System.out.println("║  Press ENTER to exit.                                        ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.in.read();

            System.out.println("  Server stopped.");
        }
        PLCcomConsole.close();
    }

    // =============================================================================
    // MyCredentialValidator
    // =============================================================================
    // Replaces the built-in addUser() database. Called during ActivateSession.
    // Returns true to accept the credentials, false to reject them.
    static class MyCredentialValidator implements IUaCredentialValidator {

        private static final Map<String, String> USERS = new HashMap<String, String>();
        static {
            USERS.put("admin",    "admin123");
            USERS.put("operator", "operator123");
            USERS.put("viewer",   "viewer123");
        }

        @Override
        public boolean validateCredentials(String userName, String password) {
            String expected = USERS.get(userName);
            boolean ok = expected != null && expected.equals(password);
            System.out.println("  [AUTH] " + userName + " -> " + (ok ? "accepted" : "rejected"));
            return ok;
        }

        @Override
        public boolean validateCertificate(byte[] certificateData) {
            System.out.println("  [AUTH CERT] certificate presented -> accepted");
            return true;
        }
    }

    // =============================================================================
    // MyPermissionValidator
    // =============================================================================
    // Replaces the built-in RolePermissions enforcement. Called for every access check.
    // Returns true to allow the operation, false to deny it.
    //
    // Access rules (same as Workshop 12a):
    //   admin    -> full access
    //   operator -> all except HistoryWrite
    //   viewer   -> Browse + Read + Subscribe + ReadRolePermissions only
    static class MyPermissionValidator implements IUaPermissionValidator {

        @Override
        public boolean validatePermission(UaSessionContext session, UaNodeContext node,
                                          UaPermissionCheck check) {
            String user = session.getUserName();
            boolean allowed;

            if ("admin".equals(user)) {
                allowed = true;
            } else if ("operator".equals(user)) {
                allowed = check != UaPermissionCheck.HISTORY_WRITE;
            } else if ("viewer".equals(user)) {
                allowed = check == UaPermissionCheck.BROWSE
                       || check == UaPermissionCheck.READ
                       || check == UaPermissionCheck.SUBSCRIBE
                       || check == UaPermissionCheck.READ_ROLE_PERMISSIONS;
            } else {
                allowed = false; // unknown or anonymous user
            }

            String nodeName = node.getPath() != null ? node.getPath() : node.getNodeId().toString();
            System.out.printf("  [PERM] %-10s %-25s %-35s -> %s%n",
                    user != null ? user : "(anon)", check, nodeName,
                    allowed ? "ALLOW" : "DENY");
            return allowed;
        }
    }

    // =============================================================================
    // Helper: createConfig
    // =============================================================================
    private static UaServerConfiguration createConfig() {
        UaServerConfiguration config = new UaServerConfiguration();

        config.setApplicationName("PLCcom Workshop 12b - Custom Auth Validator");
        config.setApplicationUri("urn:localhost:PLCcom:Workshop:12b");
        config.setProductUri("https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/");
        config.setNamespaceUri("http://indi-an.com/opcua/workshop/custom-auth-validator");

        config.setManufacturerName("My Company GmbH");
        config.setProductName("My OPC UA Server");
        config.setSoftwareVersion("1.0.0");
        config.setBuildNumber("42");

        config.setBaseAddresses(java.util.Arrays.asList(
                "opc.tcp://localhost:48410",
                "opc.https://localhost:48411"));

        config.setSecurityModes(UaServer.getRecommendedSecurityModes());

        // Anonymous is intentionally NOT listed — clients must authenticate.
        config.setUserTokenPolicies(java.util.Arrays.asList(
                UserTokenPolicy.SECURE_USERNAME_PASSWORD,
                UserTokenPolicy.SECURE_CERTIFICATE));

        config.setCertificateStorePath("./pki");
        config.setCertificateLifetimeInMonths(60);
        config.setAutoAcceptUntrustedCertificates(false);

        config.setMaxSessionCount(100);
        config.setShutdownDelay(5);

        config.setVendorName("My Company GmbH");
        config.setVendorProductName("My OPC UA Server");
        config.setVendorProductVersion("1.0.0");

        config.setHttpsSecurityPolicies(java.util.Arrays.asList(
                com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_2_PFS,
                com.plccom.opc.ua.transport.security.HttpsSecurityPolicy.TLS_1_3));

        config.setMaxNodesPerRead(1000);
        config.setMaxNodesPerWrite(1000);
        config.setMaxNodesPerBrowse(1000);
        config.setMaxNodesPerHistoryReadData(100);
        config.setMaxNodesPerHistoryReadEvents(100);
        config.setMaxNodesPerHistoryUpdateData(100);
        config.setMaxNodesPerHistoryUpdateEvents(100);
        config.setMaxNodesPerMethodCall(200);
        config.setMaxNodesPerRegisterNodes(1000);
        config.setMaxNodesPerTranslateBrowsePathsToNodeIds(1000);
        config.setMaxNodesPerNodeManagement(1000);
        config.setMaxMonitoredItemsPerCall(1000);
        // AsConfigured (default) = endpoints use exactly the host from BaseAddresses
        // NormalizeToHostname    = replace localhost/127.0.0.1 with the machine name
        config.setEndpointHostMode(UaEndpointHostMode.AsConfigured);

        return config;
    }

    // =============================================================================
    // Helper: printConfig
    // =============================================================================
    private static void printConfig(UaServerConfiguration config) {
        System.out.println("── Active Server Configuration ──────────────────────────────");
        System.out.println("  ApplicationName  : " + config.getApplicationName());
        System.out.println("  ApplicationUri   : " + config.getApplicationUri());
        System.out.println("  NamespaceUri     : " + config.getNamespaceUri());
        System.out.println();
        System.out.println("  Endpoints:");
        for (String addr : config.getBaseAddresses())
            System.out.println("    " + addr);
        System.out.println();
        System.out.println("  Authentication: UserName + Certificate (Anonymous disabled)");
        System.out.println("─────────────────────────────────────────────────────────────");
        System.out.println();
    }
}

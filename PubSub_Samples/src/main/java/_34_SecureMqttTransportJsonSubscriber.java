// MIT License
// Copyright (c) Indi.An GmbH

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.cert.X509Certificate;

import com.plccom.opc.ua.pubsub.sdk.NetworkInterfaces;
import com.plccom.opc.ua.pubsub.sdk.PubSubTransportMode;
import com.plccom.opc.ua.pubsub.sdk.UaPubSubCertificateValidationListener;
import com.plccom.opc.ua.pubsub.sdk.UaPubSubMqttTls;
import com.plccom.opc.ua.pubsub.sdk.UaSubscriber;
import com.plccom.opc.ua.pubsub.sdk.UaSubscriberConfiguration;
import com.plccom.opc.ua.pubsub.transport.mqtt.MqttBodyEncoding;
import com.plccom.opc.ua.pubsub.transport.mqtt.MqttProtocolVersion;
import com.plccom.opc.ua.pubsub.transport.mqtt.MqttTlsConfiguration;

/**
 * Demonstrates a high-level OPC UA PubSub subscriber.
 *
 * Workshop note:
 * The comments are intentionally close to the code. A customer should be able to
 * see how the subscriber selects a PubSub transport, how it maps incoming fields
 * to logical names, and where application code receives the data.
 */
public final class _34_SecureMqttTransportJsonSubscriber {

    private _34_SecureMqttTransportJsonSubscriber() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 34 - Secure MQTT JSON Subscriber");

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 34: Secure MQTT JSON║");
            System.out.println("║                                                              ║");
            System.out.println("║  This subscriber receives JSON PubSub through MQTT over TLS. ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Configure MQTT PubSub with the high-level API           ║");
            System.out.println("║    * Register a DataReceived listener                        ║");
            System.out.println("║    * Read named field values from incoming messages          ║");
            System.out.println("║    * Stop the subscriber cleanly with ENTER                  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required broker: mqtts://localhost:8883, publisher 33       ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // TODO: Replace with your license credentials from your license e-mail.
            String licenseUser = "<Enter your UserName here>";
            String licenseSerial = "<Enter your Serial here>";

            // MQTT workshops can be started with an optional protocol argument.
            // Without an argument the helper uses the default version documented
            // in the README. The selected version is applied to publisher and
            // subscriber so MQTT 3 and MQTT 5 behaviour can be compared.
            MqttProtocolVersion protocolVersion = PubSubWorkshopHelper.parseMqttProtocolVersion(args);
            MqttBodyEncoding bodyEncoding = PubSubWorkshopHelper.parseJsonBodyEncoding(args);
            PubSubWorkshopHelper.printMqttProtocolVersion(protocolVersion);
            PubSubWorkshopHelper.printJsonBodyEncoding(bodyEncoding);
            // This listener is the explicit user decision point. It receives the
            // complete certificate chain presented by the MQTT broker. Returning
            // true accepts this TLS validation call; returning false rejects the
            // connection. Because this is an explicit override, the SDK does not
            // create, read or write the PKI store for this TLS validation.
            UaPubSubCertificateValidationListener brokerCertificateValidation =
                    certificateChain -> {
                System.out.println("Broker certificate validation.");
                for (int ii = 0; ii < certificateChain.length; ii++) {
                    X509Certificate certificate = certificateChain[ii];
                    System.out.println("Certificate[" + ii + "] Subject: "
                            + certificate.getSubjectX500Principal().getName());
                    System.out.println("Certificate[" + ii + "] Issuer : "
                            + certificate.getIssuerX500Principal().getName());
                }
                System.out.println("Decision: true (accept this TLS validation).");
                System.out.println();
                return true;
            };

            MqttTlsConfiguration tlsConfiguration =
                    UaPubSubMqttTls.builder("pki")
                            .withCertificateValidationListener(
                                    brokerCertificateValidation)
                            .withHostnameVerifier((host, session) -> "localhost".equalsIgnoreCase(host))
                            .build();

            // -- Step 1: Configure the subscriber ------------------------------
            // Build() creates the high-level subscriber configuration. The name
            // is local to this application and is useful when logging or
            // diagnosing several subscribers in the same process.
            UaSubscriberConfiguration config = UaSubscriberConfiguration.Build("SecureEnergySubscriber")
                    // NetworkInterfaces.All lets the operating system choose the
                    // outgoing or listening adapter. In a production system you
                    // can replace this with a concrete adapter name.
                    .WithNetworkInterface(NetworkInterfaces.All)
                    // WithTransport selects both the PubSub mapping and the
                    // endpoint URL. For UDP this is an opc.udp:// URL; for MQTT
                    // it is the broker URL used by the MQTT adapter.
                    .WithTransport(PubSubTransportMode.BrokerMqttJson, "mqtts://localhost:8883")
                    // The DataSetReader selects the PublisherId and DataSet name
                    // expected from the matching publisher. Messages from other
                    // publishers or data sets are ignored by this reader.
                    .AddDataSetReader("opcua:Workshop33", "EnergyMeter", ds -> ds
                            // The fields are listed by their logical PubSub names.
                            // Keeping them visible here makes the workshop easy to
                            // read and lets the print code below address values by name.
                            .AddField("Voltage")
                            .AddField("Current")
                            .AddField("Power")
                            .AddField("Energy"))
                    // mqttProtocolVersion applies the selected MQTT protocol to
                    // the transport adapter. The PubSub configuration above stays
                    // unchanged; only the broker protocol details differ.
                    .mqttProtocolVersion(protocolVersion)
                    // mqttBodyEncoding must match the publisher. MQTT 5 can also
                    // carry a Content Type, but this setting remains the expected
                    // fallback and is required for MQTT 3.1.1.
                    .mqttBodyEncoding(bodyEncoding)
                    // mqttTlsConfiguration enables the TLS broker connection.
                    // PubSub payload encoding stays the same; only the transport
                    // channel between application and broker is protected.
                    .mqttTlsConfiguration(tlsConfiguration)
                    .build();

            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            // -- Step 2: Create the subscriber --------------------------------
            // UaSubscriber owns the receiving transport and dispatches decoded
            // PubSub messages to the listener registered below.
            try (UaSubscriber subscriber = new UaSubscriber(licenseUser, licenseSerial, config)) {
                subscriber.addErrorListener(PubSubWorkshopHelper::printPubSubError);
                // The DataReceived listener is where application code receives
                // decoded PubSub data. The high-level event exposes values by the
                // same logical field names used in the publisher configuration.
                subscriber.addDataReceivedListener(event -> {
                    System.out.printf("EnergyMeter: Voltage=%s V, Current=%s A, Power=%s W, Energy=%s kWh%n",
                            PubSubWorkshopHelper.formatDecimalField(event, "Voltage", "%5.1f"),
                            PubSubWorkshopHelper.formatDecimalField(event, "Current", "%4.2f"),
                            PubSubWorkshopHelper.formatDecimalField(event, "Power", "%6.1f"),
                            PubSubWorkshopHelper.formatDecimalField(event, "Energy", "%7.4f"));
                });

                // Start begins receiving from the configured transport. For MQTT
                // it subscribes to broker topics; for UDP it starts listening on
                // the configured endpoint.
                subscriber.Start();
                System.out.println("License: " + subscriber.getLicenceMessage());
                System.out.println("Subscriber started. Press ENTER to stop.");
                System.out.println();
                // -- Step 3: Wait while messages are received -----------------
                // Keep the process alive until the user presses ENTER. Data is
                // received asynchronously by the listener above.
                while (!PubSubWorkshopHelper.isEnterPressed(input)) {
                    Thread.sleep(250L);
                }
                input.readLine();
                // Stop unsubscribes or closes the listening socket before the
                // try-with-resources block releases the remaining resources.
                subscriber.Stop();
            }
        } catch (Exception ex) {
            System.out.println("Error: " + ex);
            Throwable reason = null;
            Throwable current = ex.getCause();
            while (current != null) {
                if (current.getMessage() != null
                        && current.getMessage().trim().length() > 0) {
                    reason = current;
                }
                current = current.getCause();
            }
            if (reason != null && !String.valueOf(ex.getMessage())
                    .contains(reason.getMessage())) {
                System.out.println("Reason: " + reason.getMessage());
            }
            String verboseProperty = System.getProperty(
                    "plccom.pubsub.workshop.verboseErrors");
            String verboseEnvironment = System.getenv(
                    "PLCCOM_PUBSUB_WORKSHOP_VERBOSE_ERRORS");
            boolean verboseErrors = "true".equalsIgnoreCase(verboseProperty)
                    || "1".equals(verboseProperty)
                    || "yes".equalsIgnoreCase(verboseProperty)
                    || "true".equalsIgnoreCase(verboseEnvironment)
                    || "1".equals(verboseEnvironment)
                    || "yes".equalsIgnoreCase(verboseEnvironment);
            if (verboseErrors) {
                ex.printStackTrace(System.out);
            } else {
                System.out.println("Enable verbose workshop errors with "
                        + "-Dplccom.pubsub.workshop.verboseErrors=true "
                        + "or PLCCOM_PUBSUB_WORKSHOP_VERBOSE_ERRORS=true.");
            }
            System.out.println();
            System.out.println("Press ENTER to exit.");
            PubSubWorkshopHelper.waitForEnterAndCloseConsole();
        } finally {
            PubSubWorkshopHelper.closeConsole();
        }
    }

}

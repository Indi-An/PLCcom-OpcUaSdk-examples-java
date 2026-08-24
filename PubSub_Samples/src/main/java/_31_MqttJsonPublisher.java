// MIT License
// Copyright (c) Indi.An GmbH

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Random;

import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.pubsub.sdk.NetworkInterfaces;
import com.plccom.opc.ua.pubsub.sdk.PubSubTransportMode;
import com.plccom.opc.ua.pubsub.sdk.UaPublisher;
import com.plccom.opc.ua.pubsub.sdk.UaPublisherConfiguration;
import com.plccom.opc.ua.pubsub.transport.mqtt.MqttBodyEncoding;
import com.plccom.opc.ua.pubsub.transport.mqtt.MqttProtocolVersion;

/**
 * Demonstrates a high-level OPC UA PubSub publisher.
 *
 * Workshop note:
 * The comments are intentionally close to the code. A customer should be able to
 * read this file from top to bottom and understand which part configures the
 * PubSub connection, which part defines the data set, and which part changes the
 * values that are published on the wire.
 */
public final class _31_MqttJsonPublisher {

    private _31_MqttJsonPublisher() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 31 - MQTT JSON Publisher");

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 31: MQTT JSON     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  This publisher sends JSON PubSub messages through MQTT.     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Configure MQTT PubSub with the high-level API           ║");
            System.out.println("║    * Keep transport setup separate from field updates        ║");
            System.out.println("║    * Publish values with WriteValue                          ║");
            System.out.println("║    * Interoperate with the matching subscriber workshop      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required broker: mqtt://localhost:1883, subscriber 32       ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // Important !!!!!!!!!!!!!!!!!!
            // Enter your Username + Serial here! Please note: with blank fields the library runs
            // for 15 minutes during a debug session. Both values can also come
            // from configuration or an environment variable.
            // Free trial license (14 days, uninterrupted): https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/
            String licenseUser = "";
            String licenseSerial = "";

            // MQTT workshops can be started with an optional protocol argument.
            // Without an argument the helper uses the default version documented
            // in the README. The selected version is applied to publisher and
            // subscriber so MQTT 3 and MQTT 5 behaviour can be compared.
            MqttProtocolVersion protocolVersion = PubSubWorkshopHelper.parseMqttProtocolVersion(args);
            MqttBodyEncoding bodyEncoding = PubSubWorkshopHelper.parseJsonBodyEncoding(args);
            PubSubWorkshopHelper.printMqttProtocolVersion(protocolVersion);
            PubSubWorkshopHelper.printJsonBodyEncoding(bodyEncoding);

            // -- Step 1: Configure the publisher -------------------------------
            // Build() starts the fluent publisher configuration.
            // The first argument is a readable publisher name for diagnostics.
            // The second argument is the PublisherId that is encoded into PubSub
            // messages so subscribers can filter the source explicitly.
            UaPublisherConfiguration config = UaPublisherConfiguration.Build("EnergyPublisher", "opcua:Workshop31")
                    // NetworkInterfaces.All lets the operating system choose the
                    // outgoing or listening adapter. In a production system you
                    // can replace this with a concrete adapter name.
                    .WithNetworkInterface(NetworkInterfaces.All)
                    // WithTransport selects both the PubSub mapping and the
                    // endpoint URL. For UDP this is an opc.udp:// URL; for MQTT
                    // it is the broker URL used by the MQTT adapter.
                    .WithTransport(PubSubTransportMode.BrokerMqttJson, "mqtt://localhost:1883")
                    // WithPublishingInterval defines the cyclic publisher tick.
                    // The publisher checks the data store every 1000 ms and
                    // sends a PubSub message when the data set is due.
                    .WithPublishingInterval(1000)
                    // AddDataSet declares the logical data set that appears in
                    // PubSub metadata. The field names below are the stable names
                    // used later by WriteValue() and by the subscriber workshops.
                    .AddDataSet("EnergyMeter", ds -> ds
                            // Each field maps a logical PubSub name to an OPC UA
                            // NodeId in the publisher data store. The workshop
                            // writes values by field name; the NodeId keeps the
                            // data model compatible with OPC UA semantics.
                            .AddField("Voltage", new NodeId(2, 3001))
                            .AddField("Current", new NodeId(2, 3002))
                            .AddField("Power", new NodeId(2, 3003))
                            .AddField("Energy", new NodeId(2, 3004))
                            // KeyFrameCount controls the DeltaFrame mechanism:
                            // every 10th message is a full KeyFrame; messages in
                            // between may contain only changed values.
                            .WithKeyFrameCount(10)
                            // WithInterval defines how often this data set is
                            // evaluated. Here it matches the publisher tick.
                            .WithInterval(1000))
                    // mqttProtocolVersion applies the selected MQTT protocol to
                    // the transport adapter. The PubSub configuration above stays
                    // unchanged; only the broker protocol details differ.
                    .mqttProtocolVersion(protocolVersion)
                    // mqttBodyEncoding selects whether JSON is sent as plain UTF-8
                    // JSON or as gzip-compressed JSON. Start both workshops with
                    // the same argument, for example "gzip mqtt5".
                    .mqttBodyEncoding(bodyEncoding)
                    .build();

            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            Random random = new Random();

            // -- Step 2: Create and start the publisher ------------------------
            // UaPublisher owns the transport sockets or MQTT client. try-with-
            // resources makes sure those resources are closed even when an error
            // occurs while the workshop is running.
            try (UaPublisher publisher = new UaPublisher(licenseUser, licenseSerial, config)) {
                publisher.addErrorListener(PubSubWorkshopHelper::printPubSubError);
                // Start activates the publishing engine. From this point on the
                // configured transport is open and cyclic messages can be sent.
                publisher.Start();
                System.out.println("License: " + publisher.getLicenceMessage());
                System.out.println("Publisher started. Press ENTER to stop.");
                System.out.println();

                double totalEnergy = 0.0;
                // -- Step 3: Simulate application values ----------------------
                // A real application would read these values from its process or
                // from an OPC UA address space. The workshop generates changing
                // values so the subscriber can show live updates immediately.
                while (!PubSubWorkshopHelper.isEnterPressed(input)) {
                    double voltage = 229.0 + random.nextDouble() * 3.0;
                    double current = 2.5 + random.nextDouble();
                    double power = voltage * current;
                    totalEnergy += power / 3600000.0;
                    // WriteValue updates the publisher data store. The next
                    // publishing cycle encodes the current values into the outgoing
                    // PubSub NetworkMessage.
                    publisher.WriteValue("EnergyMeter", "Voltage", voltage);
                    publisher.WriteValue("EnergyMeter", "Current", current);
                    publisher.WriteValue("EnergyMeter", "Power", power);
                    publisher.WriteValue("EnergyMeter", "Energy", totalEnergy);

                    System.out.printf("Voltage=%5.1f V, Current=%4.2f A, Power=%6.1f W, Energy=%7.4f kWh%n",
                            voltage, current, power, totalEnergy);
                    Thread.sleep(1000L);
                }
                input.readLine();

                // -- Step 4: Stop the publisher -------------------------------
                // Stop closes the publishing path before the try-with-resources
                // block disposes the remaining transport resources.
                publisher.Stop();
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

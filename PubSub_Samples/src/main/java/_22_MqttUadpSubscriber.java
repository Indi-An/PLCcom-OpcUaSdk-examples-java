// MIT License
// Copyright (c) Indi.An GmbH

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.plccom.opc.ua.pubsub.sdk.NetworkInterfaces;
import com.plccom.opc.ua.pubsub.sdk.PubSubTransportMode;
import com.plccom.opc.ua.pubsub.sdk.UaSubscriber;
import com.plccom.opc.ua.pubsub.sdk.UaSubscriberConfiguration;
import com.plccom.opc.ua.pubsub.transport.mqtt.MqttProtocolVersion;

/**
 * Demonstrates a high-level OPC UA PubSub subscriber.
 *
 * Workshop note:
 * The comments are intentionally close to the code. A customer should be able to
 * see how the subscriber selects a PubSub transport, how it maps incoming fields
 * to logical names, and where application code receives the data.
 */
public final class _22_MqttUadpSubscriber {

    private _22_MqttUadpSubscriber() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 22 - MQTT UADP Subscriber");

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 22: MQTT UADP     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  This subscriber receives UADP messages through MQTT.        ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Configure MQTT PubSub with the high-level API           ║");
            System.out.println("║    * Register a DataReceived listener                        ║");
            System.out.println("║    * Read named field values from incoming messages          ║");
            System.out.println("║    * Stop the subscriber cleanly with ENTER                  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required broker: mqtt://localhost:1883, publisher 21        ║");
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
            PubSubWorkshopHelper.printMqttProtocolVersion(protocolVersion);

            // -- Step 1: Configure the subscriber ------------------------------
            // Build() creates the high-level subscriber configuration. The name
            // is local to this application and is useful when logging or
            // diagnosing several subscribers in the same process.
            UaSubscriberConfiguration config = UaSubscriberConfiguration.Build("MotorSubscriber")
                    // NetworkInterfaces.All lets the operating system choose the
                    // outgoing or listening adapter. In a production system you
                    // can replace this with a concrete adapter name.
                    .WithNetworkInterface(NetworkInterfaces.All)
                    // WithTransport selects both the PubSub mapping and the
                    // endpoint URL. For UDP this is an opc.udp:// URL; for MQTT
                    // it is the broker URL used by the MQTT adapter.
                    .WithTransport(PubSubTransportMode.BrokerMqttUadp, "mqtt://localhost:1883")
                    // The DataSetReader selects the PublisherId and DataSet name
                    // expected from the matching publisher. Messages from other
                    // publishers or data sets are ignored by this reader.
                    .AddDataSetReader("opcua:Workshop21", "MotorData", ds -> ds
                            // The fields are listed by their logical PubSub names.
                            // Keeping them visible here makes the workshop easy to
                            // read and lets the print code below address values by name.
                            .AddField("Speed")
                            .AddField("Current")
                            .AddField("Temperature"))
                    // mqttProtocolVersion applies the selected MQTT protocol to
                    // the transport adapter. The PubSub configuration above stays
                    // unchanged; only the broker protocol details differ.
                    .mqttProtocolVersion(protocolVersion)
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
                    System.out.printf("MotorData: Speed=%s rpm, Current=%s A, Temperature=%s °C%s%n",
                            PubSubWorkshopHelper.formatDecimalField(event, "Speed", "%6.1f"),
                            PubSubWorkshopHelper.formatDecimalField(event, "Current", "%4.2f"),
                            PubSubWorkshopHelper.formatDecimalField(event, "Temperature", "%5.2f"),
                            event.isDeltaFrame() ? " (DeltaFrame)" : " (KeyFrame)");
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

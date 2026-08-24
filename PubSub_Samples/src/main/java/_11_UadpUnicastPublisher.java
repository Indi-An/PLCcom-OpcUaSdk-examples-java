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
import com.plccom.opc.ua.pubsub.sdk.UaPubSubDiscoveryEvent;

/**
 * Demonstrates a high-level OPC UA PubSub publisher.
 *
 * Workshop note:
 * The comments are intentionally close to the code. A customer should be able to
 * read this file from top to bottom and understand which part configures the
 * PubSub connection, which part defines the data set, and which part changes the
 * values that are published on the wire.
 */
public final class _11_UadpUnicastPublisher {

    private _11_UadpUnicastPublisher() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 11 - UADP Unicast Publisher");

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 11: UADP Unicast        ║");
            System.out.println("║                                                              ║");
            System.out.println("║  This publisher sends temperature values with UADP over UDP. ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Build a fluent UaPublisherConfiguration                 ║");
            System.out.println("║    * Map PubSub fields to OPC UA NodeIds                     ║");
            System.out.println("║    * Start the publisher and update values with WriteValue   ║");
            System.out.println("║    * Observe KeyFrames and DeltaFrames in the subscriber     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required subscriber: Workshop 12, opc.udp://localhost:4840  ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // Important !!!!!!!!!!!!!!!!!!
            // Enter your Username + Serial here! Please note: with blank fields the library runs
            // for 15 minutes during a debug session. Both values can also come
            // from configuration or an environment variable.
            // Free trial license (14 days, uninterrupted): https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/
            String licenseUser = "";
            String licenseSerial = "";

            // -- Step 1: Configure the publisher -------------------------------
            // Build() starts the fluent publisher configuration.
            // The first argument is a readable publisher name for diagnostics.
            // The second argument is the PublisherId that is encoded into PubSub
            // messages so subscribers can filter the source explicitly.
            UaPublisherConfiguration config = UaPublisherConfiguration.Build("TemperaturePublisher", "opcua:Workshop11")
                    // NetworkInterfaces.All lets the operating system choose the
                    // outgoing or listening adapter. In a production system you
                    // can replace this with a concrete adapter name.
                    .WithNetworkInterface(NetworkInterfaces.All)
                    // WithTransport selects both the PubSub mapping and the
                    // endpoint URL. For UDP this is an opc.udp:// URL; for MQTT
                    // it is the broker URL used by the MQTT adapter.
                    .WithTransport(PubSubTransportMode.DirectUnicast, "opc.udp://localhost:4840")
                    // WithDiscovery configures the metadata endpoint on the
                    // separate discovery port used by the matching workshop.
                    .WithDiscovery("opc.udp://localhost:4841")
                    // WithPublishingInterval defines the cyclic publisher tick.
                    // The publisher checks the data store every 1000 ms and
                    // sends a PubSub message when the data set is due.
                    .WithPublishingInterval(1000)
                    // AddDataSet declares the logical data set that appears in
                    // PubSub metadata. The field names below are the stable names
                    // used later by WriteValue() and by the subscriber workshops.
                    .AddDataSet("Temperatures", ds -> ds
                            // Each field maps a logical PubSub name to an OPC UA
                            // NodeId in the publisher data store. The workshop
                            // writes values by field name; the NodeId keeps the
                            // data model compatible with OPC UA semantics.
                            .AddField("Sensor1", new NodeId(2, 1001))
                            .AddField("Sensor2", new NodeId(2, 1002))
                            .AddField("Sensor3", new NodeId(2, 1003))
                            // KeyFrameCount controls the DeltaFrame mechanism:
                            // every 10th message is a full KeyFrame; messages in
                            // between may contain only changed values.
                            .WithKeyFrameCount(10)
                            // WithInterval defines how often this data set is
                            // evaluated. Here it matches the publisher tick.
                            .WithInterval(1000))
                    .build();

            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            Random random = new Random();

            // -- Step 2: Create and start the publisher ------------------------
            // UaPublisher owns the transport sockets or MQTT client. try-with-
            // resources makes sure those resources are closed even when an error
            // occurs while the workshop is running.
            try (UaPublisher publisher = new UaPublisher(licenseUser, licenseSerial, config)) {
                publisher.addErrorListener(PubSubWorkshopHelper::printPubSubError);
                // Discovery diagnostics make the metadata exchange visible in
                // the console. When Workshop 12 starts, this listener shows the
                // incoming request and the DataSetMetaData response.
                publisher.addDiscoveryListener(event -> {
                    if (event.getType() == UaPubSubDiscoveryEvent.Type.REQUEST_RECEIVED) {
                        System.out.printf("[Discovery] DataSetMetaData request received from %s for %s (WriterId=%d).%n",
                                discoveryPeer(event),
                                event.getDataSetName(),
                                event.getDataSetWriterId());
                    } else if (event.getType() == UaPubSubDiscoveryEvent.Type.RESPONSE_SENT) {
                        System.out.printf("[Discovery] DataSetMetaData response sent to %s. Fields: %s%n",
                                discoveryPeer(event),
                                String.join(", ", event.getFieldNames()));
                    }
                });

                // Start activates the publishing engine. From this point on the
                // configured transport is open and cyclic messages can be sent.
                publisher.Start();
                System.out.println("  License: " + publisher.getLicenceMessage());
                System.out.println("Publisher started. Press ENTER to stop.");
                System.out.println("Discovery listener active on opc.udp://localhost:4841.");
                System.out.println();

                // -- Step 3: Simulate application values ----------------------
                // A real application would read these values from its process or
                // from an OPC UA address space. The workshop generates changing
                // values so the subscriber can show live updates immediately.
                while (!PubSubWorkshopHelper.isEnterPressed(input)) {
                    double sensor1 = 20.0 + random.nextDouble() * 4.0;
                    double sensor2 = 21.0 + random.nextDouble() * 3.0;
                    double sensor3 = 19.5 + random.nextDouble() * 5.0;
                    // WriteValue updates the publisher data store. The next
                    // publishing cycle encodes the current values into the outgoing
                    // PubSub NetworkMessage.
                    publisher.WriteValue("Temperatures", "Sensor1", sensor1);
                    publisher.WriteValue("Temperatures", "Sensor2", sensor2);
                    publisher.WriteValue("Temperatures", "Sensor3", sensor3);

                    System.out.printf("Sensor1=%5.2f °C, Sensor2=%5.2f °C, Sensor3=%5.2f °C%n",
                            sensor1, sensor2, sensor3);
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

    private static String discoveryPeer(UaPubSubDiscoveryEvent event) {
        if (event.getPeerAddress() == null) {
            return "<unknown>";
        }
        return event.getPeerAddress().getHostAddress() + ":" + event.getPeerPort();
    }
}

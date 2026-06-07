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

/**
 * Demonstrates a high-level OPC UA PubSub publisher.
 *
 * Workshop note:
 * The comments are intentionally close to the code. A customer should be able to
 * read this file from top to bottom and understand which part configures the
 * PubSub connection, which part defines the data set, and which part changes the
 * values that are published on the wire.
 */
public final class _15_UadpBroadcastPublisher {

    private _15_UadpBroadcastPublisher() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 15 - UADP Broadcast Publisher");

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 15: UADP Broadcast      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  This publisher sends the same pressure fields via broadcast.║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Build a fluent UaPublisherConfiguration                 ║");
            System.out.println("║    * Map PubSub fields to OPC UA NodeIds                     ║");
            System.out.println("║    * Start the publisher and update values with WriteValue   ║");
            System.out.println("║    * Observe KeyFrames and DeltaFrames in the subscriber     ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required subscriber: Workshop 16, opc.udp://255.255.255.255 ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // TODO: Replace with your license credentials from your license e-mail.
            String licenseUser = "<Enter your UserName here>";
            String licenseSerial = "<Enter your Serial here>";

            // -- Step 1: Configure the publisher -------------------------------
            // Build() starts the fluent publisher configuration.
            // The first argument is a readable publisher name for diagnostics.
            // The second argument is the PublisherId that is encoded into PubSub
            // messages so subscribers can filter the source explicitly.
            UaPublisherConfiguration config = UaPublisherConfiguration.Build("BroadcastPublisher", "opcua:Workshop15")
                    // NetworkInterfaces.All lets the operating system choose the
                    // outgoing or listening adapter. In a production system you
                    // can replace this with a concrete adapter name.
                    .WithNetworkInterface(NetworkInterfaces.All)
                    // WithTransport selects both the PubSub mapping and the
                    // endpoint URL. For UDP this is an opc.udp:// URL; for MQTT
                    // it is the broker URL used by the MQTT adapter.
                    .WithTransport(PubSubTransportMode.DirectBroadcast, "opc.udp://255.255.255.255:4840")
                    // WithPublishingInterval defines the cyclic publisher tick.
                    // The publisher checks the data store every 1000 ms and
                    // sends a PubSub message when the data set is due.
                    .WithPublishingInterval(1000)
                    // AddDataSet declares the logical data set that appears in
                    // PubSub metadata. The field names below are the stable names
                    // used later by WriteValue() and by the subscriber workshops.
                    .AddDataSet("PressureReadings", ds -> ds
                            // Each field maps a logical PubSub name to an OPC UA
                            // NodeId in the publisher data store. The workshop
                            // writes values by field name; the NodeId keeps the
                            // data model compatible with OPC UA semantics.
                            .AddField("Inlet", new NodeId(2, 1101))
                            .AddField("Outlet", new NodeId(2, 1102))
                            .AddField("Differential", new NodeId(2, 1103))
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
                // Start activates the publishing engine. From this point on the
                // configured transport is open and cyclic messages can be sent.
                publisher.Start();
                System.out.println("License: " + publisher.getLicenceMessage());
                System.out.println("Publisher started. Press ENTER to stop.");
                System.out.println();

                // -- Step 3: Simulate application values ----------------------
                // A real application would read these values from its process or
                // from an OPC UA address space. The workshop generates changing
                // values so the subscriber can show live updates immediately.
                while (!PubSubWorkshopHelper.isEnterPressed(input)) {
                    double inlet = 5.0 + random.nextDouble();
                    double outlet = 4.2 + random.nextDouble();
                    double differential = inlet - outlet;
                    // WriteValue updates the publisher data store. The next
                    // publishing cycle encodes the current values into the outgoing
                    // PubSub NetworkMessage.
                    publisher.WriteValue("PressureReadings", "Inlet", inlet);
                    publisher.WriteValue("PressureReadings", "Outlet", outlet);
                    publisher.WriteValue("PressureReadings", "Differential", differential);

                    System.out.printf("Inlet=%5.2f bar, Outlet=%5.2f bar, Differential=%5.2f bar%n",
                            inlet, outlet, differential);
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

// MIT License
// Copyright (c) Indi.An GmbH

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.plccom.opc.ua.pubsub.sdk.NetworkInterfaces;
import com.plccom.opc.ua.pubsub.sdk.PubSubTransportMode;
import com.plccom.opc.ua.pubsub.sdk.UaSubscriber;
import com.plccom.opc.ua.pubsub.sdk.UaSubscriberConfiguration;

/**
 * Demonstrates a high-level OPC UA PubSub subscriber.
 *
 * Workshop note:
 * The comments are intentionally close to the code. A customer should be able to
 * see how the subscriber selects a PubSub transport, how it maps incoming fields
 * to logical names, and where application code receives the data.
 */
public final class _14_UadpMulticastSubscriber {

    private _14_UadpMulticastSubscriber() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 14 - UADP Multicast Subscriber");

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 14: UADP Multicast      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  This subscriber joins the multicast pressure stream.        ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Build a fluent UaSubscriberConfiguration                ║");
            System.out.println("║    * Register a DataReceived listener                        ║");
            System.out.println("║    * Read named fields from high-level PubSub events         ║");
            System.out.println("║    * Stop the subscriber cleanly with ENTER                  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required publisher: Workshop 13, opc.udp://239.0.0.1:4840   ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // TODO: Replace with your license credentials from your license e-mail.
            String licenseUser = "<Enter your UserName here>";
            String licenseSerial = "<Enter your Serial here>";

            // -- Step 1: Configure the subscriber ------------------------------
            // Build() creates the high-level subscriber configuration. The name
            // is local to this application and is useful when logging or
            // diagnosing several subscribers in the same process.
            UaSubscriberConfiguration config = UaSubscriberConfiguration.Build("PressureSubscriber")
                    // NetworkInterfaces.All lets the operating system choose the
                    // outgoing or listening adapter. In a production system you
                    // can replace this with a concrete adapter name.
                    .WithNetworkInterface(NetworkInterfaces.All)
                    // WithTransport selects both the PubSub mapping and the
                    // endpoint URL. For UDP this is an opc.udp:// URL; for MQTT
                    // it is the broker URL used by the MQTT adapter.
                    .WithTransport(PubSubTransportMode.DirectMulticast, "opc.udp://239.0.0.1:4840")
                    // The DataSetReader selects the PublisherId and DataSet name
                    // expected from the matching publisher. Messages from other
                    // publishers or data sets are ignored by this reader.
                    .AddDataSetReader("opcua:Workshop13", "PressureReadings", ds -> ds
                            // The fields are listed by their logical PubSub names.
                            // Keeping them visible here makes the workshop easy to
                            // read and lets the print code below address values by name.
                            .AddField("Inlet")
                            .AddField("Outlet")
                            .AddField("Differential"))
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
                    System.out.printf("PressureReadings: Inlet=%s bar, Outlet=%s bar, Differential=%s bar%s%n",
                            PubSubWorkshopHelper.formatDecimalField(event, "Inlet", "%5.2f"),
                            PubSubWorkshopHelper.formatDecimalField(event, "Outlet", "%5.2f"),
                            PubSubWorkshopHelper.formatDecimalField(event, "Differential", "%5.2f"),
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

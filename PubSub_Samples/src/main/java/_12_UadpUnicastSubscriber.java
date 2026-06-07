// MIT License
// Copyright (c) Indi.An GmbH

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

import com.plccom.opc.ua.builtintypes.Variant;
import com.plccom.opc.ua.pubsub.sdk.NetworkInterfaces;
import com.plccom.opc.ua.pubsub.sdk.PubSubTransportMode;
import com.plccom.opc.ua.pubsub.sdk.UaSubscriber;
import com.plccom.opc.ua.pubsub.sdk.UaSubscriberConfiguration;

/**
 * Demonstrates a high-level OPC UA PubSub subscriber with dynamic field discovery.
 * <p>
 * Workshop 12 is the receiving side of Workshop 11. The subscriber listens for
 * UADP NetworkMessages on UDP and uses the publisher's discovery endpoint to
 * request the DataSetMetaData at runtime. No field names are configured in this
 * workshop; the received event is printed by iterating over the discovered field
 * map.
 * </p>
 */
public final class _12_UadpUnicastSubscriber {

    private _12_UadpUnicastSubscriber() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 12 - UADP Unicast Subscriber");

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 12: UADP Unicast        ║");
            System.out.println("║                                                              ║");
            System.out.println("║  This subscriber receives UADP temperature values via UDP    ║");
            System.out.println("║  and discovers the DataSet field layout from the publisher.  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Build a fluent UaSubscriberConfiguration                ║");
            System.out.println("║    * Request DataSetMetaData through PubSub discovery        ║");
            System.out.println("║    * Print received fields without hard-coded field names    ║");
            System.out.println("║    * Distinguish KeyFrames and DeltaFrames                   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required publisher: Workshop 11, opc.udp://localhost:4840   ║");
            System.out.println("║  Discovery endpoint: opc.udp://localhost:4841                ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // TODO: Replace with your license credentials from your license e-mail.
            String licenseUser = "<Enter your UserName here>";
            String licenseSerial = "<Enter your Serial here>";

            // -- Step 1: Configure the subscriber ------------------------------
            // Build() creates the high-level subscriber configuration. The name
            // is local to this application and is useful when logging or
            // diagnosing several subscribers in the same process.
            UaSubscriberConfiguration config = UaSubscriberConfiguration.Build("TemperatureSubscriber")
                    // NetworkInterfaces.All lets the operating system choose the
                    // outgoing or listening adapter. In a production system you
                    // can replace this with a concrete adapter name.
                    .WithNetworkInterface(NetworkInterfaces.All)
                    // WithTransport selects both the PubSub mapping and the
                    // endpoint URL. For UDP this is an opc.udp:// URL; for MQTT
                    // it is the broker URL used by the MQTT adapter.
                    .WithTransport(PubSubTransportMode.DirectUnicast, "opc.udp://localhost:4840")
                    // WithDiscovery points to the publisher's metadata listener.
                    // The subscriber requests DataSetMetaData there and learns
                    // the field names and field order dynamically.
                    .WithDiscovery("opc.udp://localhost:4841")
                    // The reader below intentionally does not list fields.
                    // This makes Workshop 12 use PubSub discovery: it requests
                    // DataSetMetaData from Workshop 11 and learns Sensor1,
                    // Sensor2 and Sensor3 at runtime.
                    //
                    // If you already know the exact field layout, you can use
                    // the static form instead. Static AddField() entries always
                    // take precedence over discovered metadata, just as in the
                    // high-level PubSub API:
                    //
                    // .AddDataSetReader("opcua:Workshop11", "Temperatures", ds -> ds
                    //         .AddField("Sensor1")
                    //         .AddField("Sensor2")
                    //         .AddField("Sensor3"))
                    .AddDataSetReader("opcua:Workshop11", "Temperatures")
                    .build();

            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

            // -- Step 2: Create the subscriber --------------------------------
            // UaSubscriber owns the receiving transport and dispatches decoded
            // PubSub messages to the listener registered below.
            try (UaSubscriber subscriber = new UaSubscriber(licenseUser, licenseSerial, config)) {
                subscriber.addErrorListener(PubSubWorkshopHelper::printPubSubError);
                final int[] messageCount = new int[] { 0 };
                final Map<String, Variant> latestFields = new LinkedHashMap<String, Variant>();

                // Discovery diagnostics make the automatic metadata exchange
                // visible. The subscriber first sends a DataSetMetaData request
                // and then applies the field names returned by Workshop 11.
                subscriber.addDiscoveryListener(event -> {
                    switch (event.getType()) {
                    case REQUEST_SENT:
                        System.out.printf("[Discovery] DataSetMetaData request sent to %s for %s (WriterId=%d).%n",
                                event.getEndpointUrl(),
                                event.getDataSetName(),
                                event.getDataSetWriterId());
                        break;
                    case RESPONSE_RECEIVED:
                        System.out.printf("[Discovery] DataSetMetaData response received for %s.%n",
                                event.getDataSetName());
                        break;
                    case METADATA_APPLIED:
                        latestFields.clear();
                        for (String fieldName : event.getFieldNames()) {
                            latestFields.put(fieldName, null);
                        }
                        System.out.printf("[Discovery] Applied fields for %s: %s%n",
                                event.getDataSetName(),
                                String.join(", ", event.getFieldNames()));
                        break;
                    default:
                        break;
                    }
                });

                // The DataReceived listener is where application code receives
                // decoded PubSub data. DeltaFrames only contain changed fields,
                // so the workshop keeps a small current-value snapshot and prints
                // all discovered fields once the first complete KeyFrame arrived.
                subscriber.addDataReceivedListener(event -> {
                    if (event.getFields().isEmpty()) {
                        return;
                    }
                    latestFields.putAll(event.getFields());
                    if (!hasCompleteSnapshot(latestFields)) {
                        return;
                    }
                    messageCount[0]++;
                    StringBuilder fields = new StringBuilder();
                    for (Map.Entry<String, Variant> field : latestFields.entrySet()) {
                        if (fields.length() > 0) {
                            fields.append("  ");
                        }
                        fields.append(field.getKey()).append('=')
                                .append(PubSubWorkshopHelper.formatDiscoveredField(field.getValue()));
                    }

                    System.out.printf("[%05d] %s %s: %s%n",
                            messageCount[0],
                            event.isDeltaFrame() ? "[DEL]" : "[KEY]",
                            event.getDataSetName(),
                            fields);
                });

                // Start begins receiving from the configured transport. For this
                // workshop the subscriber listens on the UDP endpoint above and
                // retries discovery until the matching publisher is available.
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

                // -- Step 4: Stop the subscriber ------------------------------
                // Stop closes the listening socket before try-with-resources
                // releases the remaining resources.
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

    private static boolean hasCompleteSnapshot(Map<String, Variant> fields) {
        for (Variant value : fields.values()) {
            if (value == null) {
                return false;
            }
        }
        return !fields.isEmpty();
    }
}

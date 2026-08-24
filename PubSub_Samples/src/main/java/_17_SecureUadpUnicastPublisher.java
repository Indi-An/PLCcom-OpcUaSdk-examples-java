// MIT License
// Copyright (c) Indi.An GmbH

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Random;

import com.plccom.opc.ua.builtintypes.NodeId;
import com.plccom.opc.ua.pubsub.encoding.uadp.UadpMessageSecurityPolicy;
import com.plccom.opc.ua.pubsub.sdk.NetworkInterfaces;
import com.plccom.opc.ua.pubsub.sdk.PubSubTransportMode;
import com.plccom.opc.ua.pubsub.sdk.UaPublisher;
import com.plccom.opc.ua.pubsub.sdk.UaPublisherConfiguration;
import com.plccom.opc.ua.pubsub.sdk.UaUadpSecurityConfiguration;
import com.plccom.opc.ua.pubsub.sdk.UaUadpSecurityMode;

/**
 * Demonstrates a high-level OPC UA PubSub publisher with UADP message security.
 *
 * Workshop note:
 * Workshop 11 sends UADP over UDP in the clear. This workshop keeps exactly the
 * same publisher structure but adds OPC UA Part 14 message security: every
 * NetworkMessage is signed and its DataSetMessage payload is encrypted before it
 * leaves the socket. The comments stay close to the code so a customer can read
 * the file top to bottom and see which part turns on security, which part defines
 * the data set, and which part changes the values that are published on the wire.
 */
public final class _17_SecureUadpUnicastPublisher {

    private _17_SecureUadpUnicastPublisher() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 17 - Secure UADP Unicast Publisher");

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 17: Secure UADP Unicast ║");
            System.out.println("║                                                              ║");
            System.out.println("║  This publisher signs and encrypts UADP messages over UDP.   ║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Turn on UADP message security (SignAndEncrypt)          ║");
            System.out.println("║    * Provide the shared key material both sides must match   ║");
            System.out.println("║    * Publish secured values with WriteValue                  ║");
            System.out.println("║    * Interoperate with the matching secure subscriber        ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required subscriber: Workshop 18, opc.udp://localhost:4840  ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            System.out.println();

            // Important !!!!!!!!!!!!!!!!!!
            // Enter your Username + Serial here! Please note: with blank fields the library runs
            // for 15 minutes during a debug session. Both values can also come
            // from configuration or an environment variable.
            // Free trial license (14 days, uninterrupted): https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/
            String licenseUser = "";
            String licenseSerial = "";

            // -- Step 1: Build the shared UADP security configuration ----------
            // UADP message security protects the PubSub NetworkMessage itself, so
            // it works on the connection-less UDP transport where there is no TLS
            // channel. The Publisher and the Subscriber must run with byte-for-byte
            // identical key material; the helper below builds that shared secret so
            // both workshops derive the very same keys. See createSharedSecurity()
            // for the didactic details and the SKS production note.
            UaUadpSecurityConfiguration security = createSharedSecurity();

            // -- Step 2: Configure the publisher -------------------------------
            // Build() starts the fluent publisher configuration.
            // The first argument is a readable publisher name for diagnostics.
            // The second argument is the PublisherId that is encoded into PubSub
            // messages so subscribers can filter the source explicitly.
            UaPublisherConfiguration config = UaPublisherConfiguration.Build("SecureTemperaturePublisher", "opcua:Workshop17")
                    // NetworkInterfaces.All lets the operating system choose the
                    // outgoing or listening adapter. In a production system you
                    // can replace this with a concrete adapter name.
                    .WithNetworkInterface(NetworkInterfaces.All)
                    // WithTransport selects both the PubSub mapping and the
                    // endpoint URL. UADP message security is only available on the
                    // direct UDP transport; requesting it on an MQTT transport makes
                    // build() fail on purpose.
                    //
                    // The exact same message security also protects one-to-many
                    // delivery. UADP secures the NetworkMessage itself, not a
                    // connection, so it does not care whether the datagram is sent
                    // to one peer, a multicast group or the whole segment. To secure
                    // a multicast or broadcast scenario, keep the uadpSecurity(...)
                    // line below unchanged and only swap the transport here:
                    //   .WithTransport(PubSubTransportMode.DirectMulticast, "opc.udp://239.0.0.1:4840")
                    //   .WithTransport(PubSubTransportMode.DirectBroadcast, "opc.udp://255.255.255.255:4840")
                    // Every receiver that holds the same group key can verify and
                    // decrypt the message; everyone else sees only ciphertext. This
                    // is why message security, not TLS, is the OPC UA answer for
                    // datagram transports: TLS needs a point-to-point connection and
                    // cannot secure one-to-many delivery.
                    .WithTransport(PubSubTransportMode.DirectUnicast, "opc.udp://localhost:4840")
                    // uadpSecurity attaches the security configuration from Step 1.
                    // From here on the publisher encodes secured NetworkMessages:
                    // the payload is encrypted and the whole message is signed.
                    .uadpSecurity(security)
                    // WithPublishingInterval defines the cyclic publisher tick.
                    // The publisher checks the data store every 1000 ms and
                    // sends a secured PubSub message when the data set is due.
                    .WithPublishingInterval(1000)
                    // AddDataSet declares the logical data set. The field names
                    // below are the stable names used later by WriteValue() and by
                    // the matching secure subscriber workshop.
                    .AddDataSet("SecureTemperatures", ds -> ds
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

            // -- Step 3: Create and start the publisher ------------------------
            // UaPublisher owns the transport sockets. try-with-resources makes sure
            // those resources are closed even when an error occurs while the
            // workshop is running.
            try (UaPublisher publisher = new UaPublisher(licenseUser, licenseSerial, config)) {
                publisher.addErrorListener(PubSubWorkshopHelper::printPubSubError);

                // Start activates the publishing engine. From this point on the
                // secured transport is open and cyclic messages can be sent.
                publisher.Start();
                System.out.println("License: " + publisher.getLicenceMessage());
                System.out.println("Secure publisher started. Press ENTER to stop.");
                System.out.println("Security mode: SignAndEncrypt (payload encrypted, message signed).");
                System.out.println();

                // -- Step 4: Simulate application values ----------------------
                // A real application would read these values from its process or
                // from an OPC UA address space. The workshop generates changing
                // values so the subscriber can show live updates immediately.
                while (!PubSubWorkshopHelper.isEnterPressed(input)) {
                    double sensor1 = 20.0 + random.nextDouble() * 4.0;
                    double sensor2 = 21.0 + random.nextDouble() * 3.0;
                    double sensor3 = 19.5 + random.nextDouble() * 5.0;
                    // WriteValue updates the publisher data store. The next
                    // publishing cycle encrypts and signs the current values into
                    // the outgoing secured PubSub NetworkMessage.
                    publisher.WriteValue("SecureTemperatures", "Sensor1", sensor1);
                    publisher.WriteValue("SecureTemperatures", "Sensor2", sensor2);
                    publisher.WriteValue("SecureTemperatures", "Sensor3", sensor3);

                    System.out.printf("Sensor1=%5.2f °C, Sensor2=%5.2f °C, Sensor3=%5.2f °C%n",
                            sensor1, sensor2, sensor3);
                    Thread.sleep(1000L);
                }
                input.readLine();

                // -- Step 5: Stop the publisher -------------------------------
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

    /**
     * Builds the shared UADP message security configuration used by Workshop 17
     * and Workshop 18.
     * <p>
     * The signing key, the encryption key and the key nonce together form the
     * shared secret. Both sides must derive byte-for-byte identical material, so
     * this workshop hard-codes a demonstration key that the publisher and the
     * subscriber compute the same way. This is the didactically simplest variant:
     * a single static key sequence with a fixed SecurityTokenId.
     * </p>
     * <p>
     * In production the keys are never hard-coded. A SecurityKeyService (SKS)
     * hands out rotating keys per SecurityGroup, and both sides feed the returned
     * key data into {@link UaUadpSecurityConfiguration}. The high-level API also
     * offers a runtime-backed overload,
     * {@code uadpSecurity(config, UaUadpSecurityKeyRuntime)}, which performs timed
     * key rollover for exactly that SKS scenario. The static key below is the
     * starting point; the SKS runtime is the next step up.
     * </p>
     *
     * @return the shared sign-and-encrypt security configuration
     */
    private static UaUadpSecurityConfiguration createSharedSecurity() {
        // PUBSUB_AES128_CTR selects the symmetric algorithm and, with it, the
        // exact lengths of the signing key, the encrypting key and the key nonce.
        UadpMessageSecurityPolicy policy = UadpMessageSecurityPolicy.PUBSUB_AES128_CTR;

        // The concatenated key data is signingKey || encryptingKey || keyNonce.
        // We derive its total length from the policy so the demonstration key is
        // always the right size, then fill it with a simple, reproducible pattern.
        // Because Workshop 18 builds the identical byte array, both sides share the
        // same secret. Replace this with real SKS key data for production use.
        int keyLength = policy.getSigningKeyLength()
                + policy.getEncryptingKeyLength()
                + policy.getKeyNonceLength();
        byte[] sharedKeyData = new byte[keyLength];
        for (int i = 0; i < sharedKeyData.length; i++) {
            sharedKeyData[i] = (byte) (1 + i);
        }

        // withConcatenatedKeyData binds the key bytes to the first SecurityTokenId
        // (42 here). The securityMode SIGN_AND_ENCRYPT encrypts the payload and
        // signs the complete message. The MessageNonce random part is a fixed
        // four-byte value shared by both sides; the Publisher appends the running
        // sequence counter to it for every message.
        return UaUadpSecurityConfiguration
                .withConcatenatedKeyData(42L, policy, sharedKeyData)
                .securityMode(UaUadpSecurityMode.SIGN_AND_ENCRYPT)
                .messageNonceRandomPart(new byte[] { 1, 2, 3, 4 })
                .build();
    }
}

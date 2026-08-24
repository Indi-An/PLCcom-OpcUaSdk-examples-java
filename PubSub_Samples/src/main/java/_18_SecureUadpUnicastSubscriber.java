// MIT License
// Copyright (c) Indi.An GmbH

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.plccom.opc.ua.pubsub.encoding.uadp.UadpMessageSecurityPolicy;
import com.plccom.opc.ua.pubsub.sdk.NetworkInterfaces;
import com.plccom.opc.ua.pubsub.sdk.PubSubTransportMode;
import com.plccom.opc.ua.pubsub.sdk.UaSubscriber;
import com.plccom.opc.ua.pubsub.sdk.UaSubscriberConfiguration;
import com.plccom.opc.ua.pubsub.sdk.UaUadpSecurityConfiguration;
import com.plccom.opc.ua.pubsub.sdk.UaUadpSecurityMode;

/**
 * Demonstrates a high-level OPC UA PubSub subscriber with UADP message security.
 *
 * Workshop note:
 * Workshop 12 receives UADP over UDP in the clear. This workshop is the receiving
 * side of Workshop 17: it configures the very same OPC UA Part 14 message security
 * so it can verify each NetworkMessage signature and decrypt the DataSetMessage
 * payload. The subscriber only sees the plain field values after the security
 * check succeeds; a tampered message or a wrong key is reported through the error
 * listener and never reaches the DataReceived listener.
 */
public final class _18_SecureUadpUnicastSubscriber {

    private _18_SecureUadpUnicastSubscriber() {
    }

    public static void main(String[] args) {
        PubSubWorkshopHelper.openConsole("Workshop 18 - Secure UADP Unicast Subscriber");

        try {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  PLCcom OPC UA PubSub SDK - Workshop 18: Secure UADP Unicast ║");
            System.out.println("║                                                              ║");
            System.out.println("║  This subscriber verifies and decrypts secured UADP over UDP.║");
            System.out.println("║                                                              ║");
            System.out.println("║  What you will learn:                                        ║");
            System.out.println("║    * Turn on UADP message security (SignAndEncrypt)          ║");
            System.out.println("║    * Provide the same shared keys as the publisher           ║");
            System.out.println("║    * Read decrypted field values in the DataReceived event   ║");
            System.out.println("║    * Stop the subscriber cleanly with ENTER                  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Required publisher: Workshop 17, opc.udp://localhost:4840   ║");
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
            // The subscriber must present exactly the same key material as the
            // publisher, otherwise the signature check and the decryption fail. The
            // helper below builds the identical shared secret used by Workshop 17;
            // both files derive the same bytes so the two sides interoperate. See
            // createSharedSecurity() for the didactic details and the SKS note.
            UaUadpSecurityConfiguration security = createSharedSecurity();

            // -- Step 2: Configure the subscriber ------------------------------
            // Build() creates the high-level subscriber configuration. The name
            // is local to this application and is useful when logging or
            // diagnosing several subscribers in the same process.
            UaSubscriberConfiguration config = UaSubscriberConfiguration.Build("SecureTemperatureSubscriber")
                    // NetworkInterfaces.All lets the operating system choose the
                    // outgoing or listening adapter. In a production system you
                    // can replace this with a concrete adapter name.
                    .WithNetworkInterface(NetworkInterfaces.All)
                    // WithTransport selects both the PubSub mapping and the
                    // endpoint URL. UADP message security is only available on the
                    // direct UDP transport; requesting it on an MQTT transport makes
                    // build() fail on purpose.
                    //
                    // Message security works the same way for one-to-many delivery,
                    // because UADP secures the NetworkMessage, not a connection. To
                    // receive a secured multicast or broadcast stream, keep the
                    // uadpSecurity(...) line below unchanged and only match the
                    // publisher's transport here:
                    //   .WithTransport(PubSubTransportMode.DirectMulticast, "opc.udp://239.0.0.1:4840")
                    //   .WithTransport(PubSubTransportMode.DirectBroadcast, "opc.udp://localhost:4840")
                    // Any subscriber that holds the same group key verifies and
                    // decrypts the stream; a receiver without the key sees only
                    // ciphertext. TLS could not do this: it needs a point-to-point
                    // connection and cannot secure one-to-many delivery, so message
                    // security is the OPC UA answer for datagram transports.
                    .WithTransport(PubSubTransportMode.DirectUnicast, "opc.udp://localhost:4840")
                    // uadpSecurity attaches the same security configuration as the
                    // publisher. From here on the subscriber verifies the signature
                    // and decrypts the payload before it hands out any field value.
                    .uadpSecurity(security)
                    // The DataSetReader selects the PublisherId and DataSet name
                    // expected from Workshop 17. Messages from other publishers or
                    // data sets are ignored by this reader. The fields are listed
                    // by their logical PubSub names so the print code below can
                    // address the decrypted values by name.
                    .AddDataSetReader("opcua:Workshop17", "SecureTemperatures", ds -> ds
                            .AddField("Sensor1")
                            .AddField("Sensor2")
                            .AddField("Sensor3"))
                    .build();

            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

            // -- Step 3: Create the subscriber --------------------------------
            // UaSubscriber owns the receiving transport and dispatches decoded
            // PubSub messages to the listener registered below.
            try (UaSubscriber subscriber = new UaSubscriber(licenseUser, licenseSerial, config)) {
                subscriber.addErrorListener(PubSubWorkshopHelper::printPubSubError);

                // The DataReceived listener is where application code receives
                // decoded PubSub data. The high-level event only fires after the
                // signature was verified and the payload was decrypted, so the
                // values printed here are the plain sensor readings again.
                subscriber.addDataReceivedListener(event -> {
                    System.out.printf("SecureTemperatures: Sensor1=%s °C, Sensor2=%s °C, Sensor3=%s °C%s%n",
                            PubSubWorkshopHelper.formatDecimalField(event, "Sensor1", "%5.2f"),
                            PubSubWorkshopHelper.formatDecimalField(event, "Sensor2", "%5.2f"),
                            PubSubWorkshopHelper.formatDecimalField(event, "Sensor3", "%5.2f"),
                            event.isDeltaFrame() ? " (DeltaFrame)" : " (KeyFrame)");
                });

                // Start begins receiving on the configured UDP endpoint. Incoming
                // datagrams are verified and decrypted with the shared keys before
                // the listener above sees any data.
                subscriber.Start();
                System.out.println("License: " + subscriber.getLicenceMessage());
                System.out.println("Secure subscriber started. Press ENTER to stop.");
                System.out.println("Security mode: SignAndEncrypt (signature verified, payload decrypted).");
                System.out.println();

                // -- Step 4: Wait while messages are received -----------------
                // Keep the process alive until the user presses ENTER. Data is
                // received asynchronously by the listener above.
                while (!PubSubWorkshopHelper.isEnterPressed(input)) {
                    Thread.sleep(250L);
                }
                input.readLine();

                // -- Step 5: Stop the subscriber ------------------------------
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
        // Because Workshop 17 builds the identical byte array, both sides share the
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

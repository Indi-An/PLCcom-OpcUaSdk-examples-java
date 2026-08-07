# PLCcom OPC UA PubSub SDK for Java - PubSub Samples

<img src="https://www.indi-an.com//wp-content/uploads/2026/03/PLCcom_720.png" width="200" alt="PLCcom Logo">

This folder contains all hands-on workshop examples for the **PLCcom OPC UA PubSub SDK for Java**. The PubSub SDK is delivered as an add-on to the PLCcom OPC UA SDK and provides publisher and subscriber APIs for OPC UA PubSub communication.

- **UDP UADP workshops** - unicast, multicast and broadcast without an MQTT broker
- **MQTT UADP workshops** - binary OPC UA PubSub messages through an MQTT broker
- **MQTT JSON workshops** - JSON encoded OPC UA PubSub messages through an MQTT broker
- **Secure MQTT workshops** - MQTT over TLS with visible broker certificate validation

The shared Swing console window helper is provided by the sibling Maven project `../PLCcom.Console`.

## Architecture Overview

<img src="../assets/pubsub_overview.svg" width="920" alt="PLCcom OPC UA PubSub SDK for Java overview">

---

## Requirements

- Java 11 or newer
- Maven 3.6 or newer
- Any Java IDE (IntelliJ IDEA, Eclipse, NetBeans, VS Code)
- A valid PLCcom OPC UA SDK / PubSub license or trial license
- For MQTT workshops: an MQTT broker on `mqtt://localhost:1883`
- For secure MQTT workshops: a TLS-enabled MQTT broker on `mqtts://localhost:8883`

The workshop code is compiled with Java 1.8 source/target compatibility, so it remains suitable for Java 8 compatible customer applications while still building cleanly on modern JDKs.

## Getting Started

### 1. Add your license credentials

Each workshop file contains a placeholder for your license credentials:

```java
String licenseUser   = "<Enter your UserName here>";
String licenseSerial = "<Enter your Serial here>";
```

Replace these with the credentials from your license e-mail. A free trial license is available at the [PLCcom download page](https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/).

### 2. Build

```bash
mvn clean compile
```

### 3. Run a workshop pair

Most PubSub workshops are publisher/subscriber pairs. Start the subscriber first, then run the matching publisher in a second console:

```bash
mvn exec:java -Dexec.mainClass=_12_UadpUnicastSubscriber
mvn exec:java -Dexec.mainClass=_11_UadpUnicastPublisher
```

You can also open the project in your IDE and run the `main()` method of each workshop directly.

---

## PubSub Pairing

Start the subscriber first, then start the matching publisher in a second console.

**UDP UADP Unicast**  
Subscriber: `_12_UadpUnicastSubscriber`  
Publisher: `_11_UadpUnicastPublisher`  
Focus: brokerless UDP unicast with PubSub discovery metadata.

**UDP UADP Multicast**  
Subscriber: `_14_UadpMulticastSubscriber`  
Publisher: `_13_UadpMulticastPublisher`  
Focus: one publisher sends to a multicast group that one or more subscribers can join.

**UDP UADP Broadcast**  
Subscriber: `_16_UadpBroadcastSubscriber`  
Publisher: `_15_UadpBroadcastPublisher`  
Focus: broadcast delivery on the local network.

**MQTT UADP**  
Subscriber: `_22_MqttUadpSubscriber`  
Publisher: `_21_MqttUadpPublisher`  
Focus: compact binary UADP messages through an MQTT broker.

**Secure MQTT UADP**  
Subscriber: `_24_SecureMqttTransportUadpSubscriber`  
Publisher: `_23_SecureMqttTransportUadpPublisher`  
Focus: MQTT over TLS with UADP encoding and broker certificate validation.

**MQTT JSON**  
Subscriber: `_32_MqttJsonSubscriber`  
Publisher: `_31_MqttJsonPublisher`  
Focus: OPC UA PubSub JSON messages through an MQTT broker.

**Secure MQTT JSON**  
Subscriber: `_34_SecureMqttTransportJsonSubscriber`  
Publisher: `_33_SecureMqttTransportJsonPublisher`  
Focus: MQTT over TLS with JSON encoding and broker certificate validation.

---

## Workshop Overview

**Direct UDP transports**

- `_11_UadpUnicastPublisher` publishes UADP DataSetMessages to `opc.udp://localhost:4840` and answers discovery requests on `opc.udp://localhost:4841`.
- `_12_UadpUnicastSubscriber` receives unicast UADP messages and discovers the field layout dynamically.
- `_13_UadpMulticastPublisher` sends pressure values to a multicast group.
- `_14_UadpMulticastSubscriber` joins that multicast group and prints the received pressure values.
- `_15_UadpBroadcastPublisher` sends pressure values to a UDP broadcast address.
- `_16_UadpBroadcastSubscriber` receives broadcast UADP messages.

**MQTT transports**

- `_21_MqttUadpPublisher` publishes UADP NetworkMessages through an MQTT broker.
- `_22_MqttUadpSubscriber` receives MQTT UADP messages from the broker.
- `_31_MqttJsonPublisher` publishes OPC UA PubSub JSON data messages through MQTT.
- `_32_MqttJsonSubscriber` receives OPC UA PubSub JSON data messages through MQTT.

**Secure MQTT transports**

- `_23_SecureMqttTransportUadpPublisher` publishes UADP NetworkMessages through MQTT over TLS.
- `_24_SecureMqttTransportUadpSubscriber` receives secure MQTT UADP messages and validates the broker certificate.
- `_33_SecureMqttTransportJsonPublisher` publishes OPC UA PubSub JSON messages through MQTT over TLS.
- `_34_SecureMqttTransportJsonSubscriber` receives secure MQTT JSON messages and validates the broker certificate.

---

## Maven Dependency

The workshop POM references the PubSub add-on for the release version used by this repository:

```xml
<dependency>
    <groupId>com.indi-an.plccom</groupId>
    <artifactId>plccom-opc-ua-pubsub</artifactId>
    <version>10.7.2</version>
</dependency>
```

The main PLCcom OPC UA SDK and the PLCcom MQTT transport components are pulled transitively by the PubSub add-on. You do not need to declare them again for these workshops.

---

## MQTT Notes

MQTT workshops use automatic protocol selection by default. The SDK tries MQTT 5.0 first and falls back to MQTT 3.1.1 when the broker clearly rejects MQTT 5.0.

Optional arguments can be passed to both sides of a workshop pair:

- `auto` - Default behavior: prefer MQTT 5.0 and fall back to MQTT 3.1.1 when needed.
- `mqtt5` - Use MQTT 5.0 explicitly.
- `mqtt3` - Use MQTT 3.1.1 explicitly.

Example:

```bash
mvn exec:java -Dexec.mainClass=_22_MqttUadpSubscriber -Dexec.args=mqtt3
```

The secure MQTT workshops use `mqtts://localhost:8883`. They show broker certificate validation directly in the workshop code. The validation listener receives the complete broker certificate chain and returns `true` to accept or `false` to reject the TLS validation call.

In production code you can remove the explicit validation listener and rely on the SDK PKI workflow:

- a broker certificate is accepted when it lies in `pki/trusted/certs/`, or when a CA of its chain lies in `pki/trusted/certs/` or `pki/issuer/certs/`, and every certificate of that chain is within its validity period
- `pki/issuer/certs/` holds the trusted CA certificates; a CA placed there takes effect on the first connection attempt
- every refused certificate is copied to `pki/rejected/certs/` and the connection fails - this covers a missing trust anchor as well as an expired or not-yet-valid certificate, so a time-invalid certificate lands there even when its CA lies in `pki/issuer/certs/`
- to trust a refused certificate, review it in `pki/rejected/certs/` and move it by hand to `pki/trusted/certs/` (this broker) or copy its CA to `pki/issuer/certs/` (every certificate that CA signed); it is then accepted while it is time-valid
- optional own client certificates for mutual TLS live below `pki/own/certs/` and `pki/own/private/`

Do not unconditionally accept certificates in production applications.

---

## Troubleshooting

If a workshop reports that a UDP port is already in use, another publisher or subscriber is already bound to the same endpoint. Stop the other process and start the pair again.

If an MQTT workshop does not receive data, make sure the broker is running, start the subscriber before the publisher, and use the same MQTT protocol argument on both sides when you choose `mqtt3` or `mqtt5`.

Workshop errors are printed as concise messages by default. For a full Java stack trace, start a workshop with:

```bash
-Dplccom.pubsub.workshop.verboseErrors=true
```

---

## ⚠️ Important Safety Notice

The examples in this repository are **for demonstration purposes only** and **must not** be used in production, safety-critical, or industrial environments without your own thorough review and validation.

The author disclaims all liability - direct, indirect, incidental, or consequential - arising from the use or misuse of these examples.

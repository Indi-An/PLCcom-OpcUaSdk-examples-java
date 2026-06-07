# PLCcom OPC UA SDK for Java — Client & Server Samples

<img src="https://www.indi-an.com//wp-content/uploads/2026/03/PLCcom_720.png" width="200" alt="PLCcom Logo">

This folder contains all hands-on workshop examples for the **PLCcom OPC UA SDK for Java** — both client and server side in a single Maven project.

- **Client/** — OPC UA client workshops: connect, browse, read/write, subscriptions, alarms, history, events
- **Server/** — OPC UA server workshops: data access, alarms, history, events, reverse connect

The shared Swing console window helper is provided by the sibling Maven project `../PLCcom.Console`.

## Architecture Overview

<img src="../assets/sdk_overview.svg" width="920" alt="PLCcom OPC UA SDK for Java overview">

---

## Requirements

- Java 11 or newer
- Maven 3.6 or newer
- Any Java IDE (IntelliJ IDEA, Eclipse, NetBeans, VS Code)

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

### 3. Run a workshop

Open the desired workshop class in your IDE and run its `main()` method directly, or use Maven:

```bash
mvn exec:java -Dexec.mainClass=_11_SimpleServer
mvn exec:java -Dexec.mainClass=_21_ReadWriteByNodeId
```

---

## Client ↔ Server Pairing

Many client workshops are designed to work with a specific server workshop. Start the server first, then run the matching client:

**First connection**

- `11 Discover Server` works with any reachable OPC UA server and shows endpoint discovery.
- `12 Connect Endpoint` pairs with `11 Simple Server` and shows the full connect/disconnect lifecycle.
- `13 Connect with User Auth` pairs with `12a User Authentication` for username/password login.
- `14 Connect with Cert Auth` pairs with `12a User Authentication` for certificate login.

**Browse, Data Access and Methods**

- `15 Browse by NodeId` and `16 Browse by Path` pair with `11 Simple Server`.
- `21 Read/Write by NodeId` and `22 Read/Write by Path` pair with `11 Simple Server`.
- `23 Monitoring Items` pairs with `11 Simple Server` and shows subscriptions.
- `24 Simple Method Calls` and `25 Advanced Calls with Structs` pair with `13 Methods`.
- `26 Read Attributes` pairs with `14 Variables and Arrays`.
- `27 Registered Read/Write` pairs with `11 Simple Server`.

**Complex Types**

- `51 Complex Types` pairs with `15 Custom Types`.

**Alarms, History and Events**

- `31 Incoming Alarms`, `32 Alarm List` and `33 Alarm Conditions` pair with `21 Alarm Conditions`.
- `41 Historical Data` pairs with `31 Historical Access`.
- `42 Historical Data Update` pairs with `32 Historical Update`.
- `43 Read Historical Events` and `44 Monitoring Historical Events` pair with `33 Historical Events`.
- `61 Simple Events` pairs with `61 Simple Events`.

**Reverse Connect**

- `71 Reverse Connect` pairs with `71 Reverse Connect`.

---

## ⚠️ Important Safety Notice

The examples in this repository are **for demonstration purposes only** and **must _not_** be used in production, safety-critical, or industrial environments without your own thorough review and validation.

The author disclaims all liability — direct, indirect, incidental, or consequential — arising from the use or misuse of these examples.

# PLCcom OPC UA SDK for Java — Client & Server Samples

<img src="https://www.indi-an.com//wp-content/uploads/2026/03/PLCcom_720.png" width="200" alt="PLCcom Logo">

This folder contains all hands-on workshop examples for the **PLCcom OPC UA SDK for Java** — both client and server side in a single Maven project.

- **Client/** — OPC UA client workshops: connect, browse, read/write, subscriptions, alarms, history, events
- **Server/** — OPC UA server workshops: data access, alarms, history, events, reverse connect
- **PLCcomConsole/** — shared Swing console window helper used by all workshops

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

| Client | Server | Topic |
|--------|--------|-------|
| 11 Discover Server | *any server* | Endpoint discovery |
| 12 Connect Endpoint | 11 Simple Server | Basic connection |
| 13 Connect with User Auth | 12a User Authentication | Username/password login |
| 14 Connect with Cert Auth | 12a User Authentication | Certificate login |
| 15–16 Browse | 11 Simple Server | Browse address space |
| 21–22 Read/Write | 11 Simple Server | Data Access |
| 23 Monitoring Items | 11 Simple Server | Subscriptions |
| 24 Simple Method Calls | 13 Methods | Method calls |
| 25 Advanced Calls with Structs | 13 Methods | Nested struct arguments |
| 26 Read Attributes | 14 Variables and Arrays | Node attributes |
| 27 Registered Read/Write | 11 Simple Server | Registered nodes |
| 31–33 Alarm Conditions | 21 Alarm Conditions | Alarms |
| 41 Historical Data | 31 Historical Access | Read history |
| 42 Historical Data Update | 32 Historical Update | Write history |
| 43 Read Historical Events | 33 Historical Events | Event history |
| 44 Monitoring Historical Events | 33 Historical Events | Event history subscription |
| 51 Complex Types | 15 Custom Types | Structured data types |
| 61 Simple Events | 61 Simple Events | Events |
| 71 Reverse Connect | 71 Reverse Connect | Firewall traversal |

---

## ⚠️ Important Safety Notice

The examples in this repository are **for demonstration purposes only** and **must _not_** be used in production, safety-critical, or industrial environments without your own thorough review and validation.

The author disclaims all liability — direct, indirect, incidental, or consequential — arising from the use or misuse of these examples.

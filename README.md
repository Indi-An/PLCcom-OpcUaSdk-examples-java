# PLCcom OPC UA SDK for Java — Workshop Examples

<img src="https://www.indi-an.com//wp-content/uploads/2026/03/PLCcom_720.png" width="200" alt="PLCcom Logo">

This repository provides hands-on workshop examples for developers using the **PLCcom OPC UA SDK for Java**. The examples show how straightforward it is to integrate OPC UA into your Java applications — both as a client connecting to existing servers and as a server exposing your own data.

## Easy to Use — Address Nodes by Path or NodeId

PLCcom supports two ways to address OPC UA nodes: the classic approach using NodeIds (`ns=2;i=12345`) and — unique to PLCcom — by browse path (`Objects.Plant.Line1.Machine1.Temperature`), just like navigating a folder structure. The SDK resolves the path to the corresponding NodeId in the background:

```java
// Resolve a node by path — no cryptic NodeId needed!
NodeId nodeId = client.getNodeIdByPath("Objects.Plant.Line1.Machine1.Temperature");

// Read a value
DataValue value = client.readValue(nodeId);

// Write a value
client.writeValue(nodeId, 23.5);
```

This makes your code **readable, maintainable, and independent of server-specific NodeId assignments**. Of course, classic NodeId-based access is fully supported too.

---

## ⚠️ Important Safety Notice

The examples in this repository are **for demonstration purposes only** and **must _not_** be used in production, safety-critical, or industrial environments without your own thorough review and validation.
**Use at your own risk!** Deploying these examples in real systems may lead to personal injury, property damage, or environmental harm and is **strictly prohibited**.

The author disclaims all liability — direct, indirect, incidental, or consequential — arising from the use or misuse of these examples.

---

## Licensing Information

**Examples License:**
All examples in this repository are released under the **MIT License**. You are free to use, modify, and distribute them according to the MIT license terms.

**PLCcom Library License:**
The **PLCcom OPC UA SDK** itself is proprietary software and is **NOT** included under the MIT license. To use the library in your own projects you must acquire an appropriate license and accept the EULA. More information: [https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/](https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/)

**Trial License:**
A free trial license is available at the [PLCcom download page](https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/).

---

## Overview of PLCcom OPC UA SDK for Java

PLCcom OPC UA SDK is a highly optimized and modern SDK designed specifically for Java developers to provide convenient client and server access for OPC UA (Open Platform Communications Unified Architecture). The library is available as a Maven dependency — no native libraries, no API calls necessary.

### Key Features
- **Path-based node addressing** — access nodes by browse path (e.g. `Objects.Plant.Line1.Temperature`)
- Easy to use — many operations require just a single line of code
- Automatic Connect, Reconnect, and Disconnect functionality
- Active keep-alive monitoring of the server state
- Full **Client SDK** and **Server SDK** in a single artifact
- Support for **opc.tcp** and **opc.https** transport protocols
- Support for the most common OPC UA feature sets:
  - Data Access
  - Alarms and Conditions
  - Historical Data and Historical Events
  - Complex / Structured Data Types
  - Simple Events
  - Reverse Connect
  - NodeSet2 XML Import
- Extensive workshops for Java included

For a full list of supported features and detailed documentation, visit the [official documentation](https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/).

---

## Workshop Overview

### Client Workshops

| # | Workshop | Description |
|---|----------|-------------|
| **1 First Steps** | | |
| 11 | Discover Server | Discover available OPC UA servers on the network |
| 12 | Connect Endpoint | Connect to a server endpoint and establish a session |
| 13 | Connect with User Auth | Authenticate with username and password |
| 14 | Connect with Cert Auth | Authenticate with X.509 certificates |
| 15 | Browse by NodeId | Navigate the address space using NodeIds |
| 16 | Browse by Path | Navigate the address space using browse paths |
| 19 | Enable Debug Tracing | Enable diagnostic tracing for troubleshooting |
| **2 Data Access** | | |
| 21 | Read/Write by NodeId | Read and write values using NodeIds |
| 22 | Read/Write by Path | Read and write values using browse paths |
| 23 | Monitoring Items | Subscribe to value changes with monitored items |
| 24 | Simple Method Calls | Call OPC UA methods with input and output arguments |
| 25 | Advanced Calls with Structs | Call methods with nested structures and arrays |
| 26 | Read Attributes | Read node attributes (DataType, Description, etc.) |
| 27 | Registered Read/Write | High-performance read/write with registered nodes |
| **3 Alarm Conditions** | | |
| 31 | Incoming Alarms | Subscribe to and display incoming alarm events |
| 32 | Alarm List | Maintain a live list of all active alarms |
| 33 | Alarm Conditions | Acknowledge, confirm and comment on alarms |
| **4 Historical Data** | | |
| 41 | Historical Data | Read historical values (ReadRaw, ReadAtTime, ReadProcessed) |
| 42 | Historical Data Update | Insert, update, replace and delete historical values |
| 43 | Read Historical Events | Read past events from the server history |
| 44 | Monitoring Historical Events | Subscribe to historical event notifications |
| **5 Complex Datatypes** | | |
| 51 | Complex Types | Read and decode structured/complex data types |
| **6 Simple Events** | | |
| 61 | Simple Events | Subscribe to and display event notifications |
| **7 Reverse Connect** | | |
| 71 | Reverse Connect | Server-initiated connections through firewalls |

### Server Workshops

| # | Workshop | Description |
|---|----------|-------------|
| **1 Data Access** | | |
| 11 | Simple Server | Basic OPC UA server with variables |
| 12a | User Authentication | Username/password and certificate authentication with roles |
| 12b | Custom Auth Validator | Custom credential and permission validators |
| 13 | Methods | Expose callable methods in the address space |
| 14 | Variables and Arrays | Various data types, properties, callbacks and array variables |
| 15 | Custom Types | Define and expose custom structured types (Structs) |
| 16 | Multiple Namespaces | Organize nodes across multiple namespaces |
| 17 | Dynamic Nodes | Create and remove nodes at runtime |
| 19 | Advanced Server | Production-grade server combining all Data Access features |
| **2 Alarms and Events** | | |
| 21 | Alarm Conditions | Implement alarm conditions with full state management |
| **3 Historical Data** | | |
| 31 | Historical Access | Store and serve historical data values |
| 32 | Historical Update | Accept Insert, Update, Replace, Remove and Delete from clients |
| 33 | Historical Events | Record and serve historical events |
| 34 | Custom History Store | Implement `UaHistoryStore` for any storage back-end (CSV demo) |
| 35 | Custom Event History Store | Implement `UaEventHistoryStore` for any storage back-end (CSV demo) |
| **4 NodeSet Import** | | |
| 41 | NodeSet Import | Import OPC UA NodeSet2 XML files into the address space |
| **5 Logging** | | |
| 51 | Logging | Configure server-side logging and route to your framework |
| **6 Simple Events** | | |
| 61 | Simple Events | Fire events from the server with severity levels |
| **7 Reverse Connect** | | |
| 71 | Reverse Connect | Server-initiated connections through firewalls |

### Client ↔ Server Pairing

Many client workshops are designed to work with a specific server workshop. Start the server first, then run the matching client:

| Client | Server | Topic |
|--------|--------|-------|
| 11 Discover Server | *any server* | Endpoint discovery |
| 12 Connect Endpoint | 11 Simple Server | Basic connection |
| 13 Connect with User Auth | 12a User Authentication | Username/password login |
| 14 Connect with Cert Auth | 12a User Authentication | Certificate login |
| 15 Browse by NodeId | 11 Simple Server | Browse address space |
| 16 Browse by Path | 11 Simple Server | Browse by path |
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

## Requirements

- Java 11 or newer
- Maven 3.6 or newer
- Any Java IDE (IntelliJ IDEA, Eclipse, VS Code with Java extension)

## Getting Started

### 1. Clone this repository

```bash
git clone https://github.com/Indi-An/PLCcom-OpcUaSdk-examples-java.git
cd PLCcom-OpcUaSdk-examples-java
```

### 2. Add your license credentials

Each workshop file contains a placeholder for your license credentials:

```java
String licenseUser   = "<Enter your UserName here>";
String licenseSerial = "<Enter your Serial here>";
```

Replace these with the credentials from your license e-mail. A free trial license is available at the [PLCcom download page](https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/).

### 3. Build

```bash
mvn clean compile
```

### 4. Run a workshop

Open the desired workshop class in your IDE and run its `main()` method directly, or use Maven:

```bash
# Example: run Server Workshop 11
mvn exec:java -pl Server -Dexec.mainClass=_11_SimpleServer

# Example: run Client Workshop 21
mvn exec:java -pl Client -Dexec.mainClass=_21_ReadWriteByNodeId
```

---

## Project Structure

```
PLCcom-OpcUaSdk-examples-java/
├── Client/
│   ├── 1 First Steps/
│   │   ├── 11_Discover_Server/
│   │   └── ...
│   ├── 2 Data Access/
│   ├── 3 Alarm Conditions/
│   ├── 4 Historical Data/
│   ├── 5 Complex Datatypes/
│   ├── 6 Simple Events/
│   ├── 7 Reverse Connect/
│   └── pom.xml
├── Server/
│   ├── 1 Data Access/
│   ├── 2 Alarms and Events/
│   ├── 3 Historical Data/
│   ├── 4 NodeSet Import/
│   │   └── 41_NodeSet_Import/
│   │       └── PLCcom_Workshop_NodeSet.xml
│   ├── 5 Logging/
│   ├── 6 Simple Events/
│   ├── 7 Reverse Connect/
│   └── pom.xml
├── pom.xml          ← parent POM (multi-module)
├── .gitignore
├── LICENSE
└── README.md
```

---

##### Trademark Information
All product names, company names, and trademarks referenced in this repository are trademarks or registered trademarks of their respective owners. There is no affiliation between the mentioned trademarks or their owners and Indi.An GmbH. Any mention of trademarks is solely for reference purposes.

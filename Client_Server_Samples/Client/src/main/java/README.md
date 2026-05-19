# PLCcom OPC UA SDK for Java — Client Workshop Classes

<img src="https://www.indi-an.com//wp-content/uploads/2026/03/PLCcom_720.png" width="200" alt="PLCcom Logo">

All client workshop Java files are located directly in this folder (default package). Each class has its own `main()` method and can be run independently.

---

## 1 First Steps

These workshops cover the basics of discovering and connecting to OPC UA servers.

| Class | What you will learn |
|-------|---------------------|
| `_11_DiscoverServer` | Discover available endpoints, sort by security level, filter by transport |
| `_12_ConnectEndpoint` | Connect to a server endpoint, KeepAlive and ConnectionState events |
| `_13_ConnectWithUserAuth` | Authenticate with username and password, role-based access |
| `_14_ConnectWithCertAuth` | Authenticate with X.509 client certificate |
| `_15_BrowseByNodeId` | Browse the address space starting from a known NodeId |
| `_16_BrowseByPath` | Resolve a dot-separated path to a NodeId, reverse lookup |
| `_19_EnableDebugTracing` | Enable SLF4J debug logging, redirect to file |

**Target server:** `opc.tcp://localhost:48410` (Server Workshop 11)

---

## 2 Data Access

These workshops cover reading, writing, subscribing and calling methods on OPC UA servers.

| Class | What you will learn |
|-------|---------------------|
| `_21_ReadWriteByNodeId` | Read and write values using NodeIds directly |
| `_22_ReadWriteByPath` | Read and write values using browse paths |
| `_23_MonitoringItems` | Subscribe to value changes with monitored items |
| `_24_SimpleMethodCalls` | Call OPC UA methods with input and output arguments |
| `_25_AdvancedCallsWithStructs` | Call methods with nested structures and arrays (BinaryEncoder) |
| `_26_ReadAttributes` | Read all node attributes (DataType, AccessLevel, Description, etc.) |
| `_27_RegisteredReadWrite` | High-performance read/write with pre-registered nodes |

**Target server:** `opc.tcp://localhost:48410` (Server Workshop 11 / 13 / 14)

---

## 3 Alarm Conditions

These workshops cover subscribing to, displaying and interacting with OPC UA alarm conditions.

| Class | What you will learn |
|-------|---------------------|
| `_31_IncomingAlarms` | Create an EventFilter, subscribe to EventNotifier, receive alarm notifications |
| `_32_AlarmList` | Use the Retain flag to maintain a live list of active alarms |
| `_33_AlarmConditions` | Acknowledge, confirm, add comments, enable/disable conditions |

**Target server:** `opc.tcp://localhost:48410` (Server Workshop 21)

---

## 4 Historical Data

These workshops cover reading, writing and subscribing to historical data and events.

| Class | What you will learn |
|-------|---------------------|
| `_41_HistoricalData` | ReadRaw, ReadModified, ReadAtTime (with interpolation), ReadProcessed (aggregates) |
| `_42_HistoricalDataUpdate` | Insert, Update, Replace, Remove, DeleteRaw, DeleteModified, DeleteAtTime |
| `_43_ReadHistoricalEvents` | Read past events from the server history by time range, delete by EventId |
| `_44_MonitoringHistoricalEvents` | Subscribe to live events from a history-enabled source node |

**Target servers:** WS41 → Server 31, WS42 → Server 32, WS43+44 → Server 33

---

## 5 Complex Datatypes

| Class | What you will learn |
|-------|---------------------|
| `_51_ComplexTypes` | Read scalar, array, flat struct, nested struct, struct with arrays, array of structs |

OPC UA structured types are transmitted as `ExtensionObject`. Load the server Type Dictionary once with `getComplexTypeSystem().load()` — the SDK then decodes structs into named fields automatically.

**Target server:** `opc.tcp://localhost:48410` (Server Workshop 15)

---

## 6 Simple Events

| Class | What you will learn |
|-------|---------------------|
| `_61_Client_SimpleEvents` | Create an EventFilter for BaseEventType, subscribe to EventNotifier, receive events |

Events carry: EventId, SourceName, Time (UTC), Message, Severity.

**Target server:** `opc.tcp://localhost:48410` (Server Workshop 61)

---

## 7 Reverse Connect

| Class | What you will learn |
|-------|---------------------|
| `_71_Client_ReverseConnect` | Open a listen port, wait for ReverseHello, establish session over server-initiated connection |

**Target server:** `opc.tcp://localhost:48410` (Server Workshop 71)
**Listen URL:** `opc.tcp://localhost:48500`

---

## Licensing Information

**Examples License:**
All examples in this repository are released under the **MIT License**. You are free to use, modify, and distribute them according to the MIT license terms.

**PLCcom Library License:**
The **PLCcom OPC UA SDK** itself is proprietary software and is **NOT** included under the MIT license. To use the library in your own projects you must acquire an appropriate license and accept the EULA. More information: [https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/](https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/)

**Trial License:**
A free trial license is available at the [PLCcom download page](https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/).

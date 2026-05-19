# PLCcom OPC UA SDK for Java — Server Workshop Classes

<img src="https://www.indi-an.com//wp-content/uploads/2026/03/PLCcom_720.png" width="200" alt="PLCcom Logo">

All server workshop Java files are located directly in this folder (default package). Each class has its own `main()` method and can be run independently.

---

## 1 Data Access

These workshops show how to build OPC UA servers that expose process data to clients.

| Class | What you will learn |
|-------|---------------------|
| `_11_SimpleServer` | Create folders and variables, push value changes, react to client writes |
| `_12a_UserAuthentication` | Username/password and certificate authentication, role-based permissions |
| `_12b_CustomAuthValidator` | Replace built-in auth with `IUaCredentialValidator` / `IUaPermissionValidator` |
| `_13_Methods` | Expose callable methods with input/output arguments and nested struct types |
| `_14_VariablesAndArrays` | Data types, properties, EURange, EngineeringUnits, callbacks, array variables |
| `_15_CustomTypes` | Define and expose custom structured data types (Structs) |
| `_16_MultipleNamespaces` | Organize nodes across multiple namespaces |
| `_17_DynamicNodes` | Create and remove nodes at runtime |
| `_19_AdvancedServer` | Production-grade server combining all Data Access features |

**Default endpoint:** `opc.tcp://localhost:48410`

---

## 2 Alarms and Events

| Class | What you will learn |
|-------|---------------------|
| `_21_AlarmConditions` | AlarmConditionType, ExclusiveLimitAlarmType, DiscreteAlarmType, DialogConditionType |

Supports: `activate()`, `deactivate()`, `acknowledge()`, `confirm()`, `enable()`, `disable()`, shelving.

**Default endpoint:** `opc.tcp://localhost:48410`

---

## 3 Historical Data

| Class | What you will learn |
|-------|---------------------|
| `_31_HistoricalAccess` | Enable history on variables, serve ReadRaw / ReadAtTime / ReadProcessed |
| `_32_HistoricalUpdate` | Accept Insert, Update, Replace, Remove and DeleteRaw from clients |
| `_33_HistoricalEvents` | Record and serve historical events via HistoryRead |
| `_34_CustomHistoryStore` | Implement `UaHistoryStore` for any storage back-end (CSV demo) |
| `_35_CustomEventHistoryStore` | Implement `UaEventHistoryStore` for any storage back-end (CSV demo) |

WS 34 and 35 use CSV files to demonstrate the pattern — replace with your own database or time-series store.

**Default endpoint:** `opc.tcp://localhost:48410`

---

## 4 NodeSet Import

| Class | What you will learn |
|-------|---------------------|
| `_41_NodeSetImport` | Import type definitions and instances from a NodeSet2 XML file |

The included `PLCcom_Workshop_NodeSet.xml` defines SensorType, MotorType and several instances.
OPC UA Companion Specifications (DI, Machinery, PackML, etc.) are distributed as NodeSet files and can be imported the same way.

**Default endpoint:** `opc.tcp://localhost:48410`

---

## 5 Logging

| Class | What you will learn |
|-------|---------------------|
| `_51_Logging` | Register a `UaLogListener`, set the log level, route to SLF4J / Log4j / java.util.logging |

Log levels: None, Error, Warning, Info, Debug.

**Default endpoint:** `opc.tcp://localhost:48410`

---

## 6 Simple Events

| Class | What you will learn |
|-------|---------------------|
| `_61_Server_SimpleEvents` | Enable event notifications on a node, fire events with severity levels |

Events propagate upward automatically: Machine1 → Plant → Objects → Server.

**Default endpoint:** `opc.tcp://localhost:48410`

---

## 7 Reverse Connect

| Class | What you will learn |
|-------|---------------------|
| `_71_Server_ReverseConnect` | Configure the server to connect to the client (not the other way around) |

**Normal endpoint:** `opc.tcp://localhost:48410`
**Reverse Connect target:** `opc.tcp://localhost:48500` (client must listen here)

---

## Licensing Information

**Examples License:**
All examples in this repository are released under the **MIT License**. You are free to use, modify, and distribute them according to the MIT license terms.

**PLCcom Library License:**
The **PLCcom OPC UA SDK** itself is proprietary software and is **NOT** included under the MIT license. To use the library in your own projects you must acquire an appropriate license and accept the EULA. More information: [https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/](https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-overview/)

**Trial License:**
A free trial license is available at the [PLCcom download page](https://www.indi-an.com/en/plccom/opc-ua-sdk/opcua-download/).

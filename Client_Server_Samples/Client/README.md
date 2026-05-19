# PLCcom OPC UA SDK for Java — Client Workshops

<img src="https://www.indi-an.com//wp-content/uploads/2026/03/PLCcom_720.png" width="200" alt="PLCcom Logo">

This folder contains all OPC UA **client** workshop examples for the PLCcom OPC UA SDK for Java.

All Java source files are located in `src/main/java/` — see [`src/main/java/README.md`](src/main/java/README.md) for a detailed description of each workshop class.

---

## Workshop Groups

| Group | Workshops |
|-------|-----------|
| **1 First Steps** | 11 Discover Server, 12 Connect Endpoint, 13 User Auth, 14 Cert Auth, 15 Browse by NodeId, 16 Browse by Path, 19 Debug Tracing |
| **2 Data Access** | 21 Read/Write by NodeId, 22 Read/Write by Path, 23 Monitoring Items, 24 Simple Method Calls, 25 Advanced Calls with Structs, 26 Read Attributes, 27 Registered Read/Write |
| **3 Alarm Conditions** | 31 Incoming Alarms, 32 Alarm List, 33 Alarm Conditions |
| **4 Historical Data** | 41 Historical Data, 42 Historical Data Update, 43 Read Historical Events, 44 Monitoring Historical Events |
| **5 Complex Datatypes** | 51 Complex Types |
| **6 Simple Events** | 61 Simple Events |
| **7 Reverse Connect** | 71 Reverse Connect |

---

## Easy to Use — Address Nodes by Path or NodeId

PLCcom supports two ways to address OPC UA nodes: the classic approach using NodeIds (`ns=2;i=12345`) and — unique to PLCcom — by browse path (`Objects.Plant.Line1.Machine1.Temperature`):

```java
// Resolve a node by path — no cryptic NodeId needed!
NodeId nodeId = client.getNodeIdByPath("Objects.Plant.Line1.Machine1.Temperature");

// Read a value
DataValue value = client.readValue(nodeId);

// Write a value
client.writeValue(nodeId, 23.5);
```

---

##### Trademark Information
All product names, company names, and trademarks referenced here are trademarks or registered trademarks of their respective owners.

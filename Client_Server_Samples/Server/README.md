# PLCcom OPC UA SDK for Java — Server Workshops

<img src="https://www.indi-an.com//wp-content/uploads/2026/03/PLCcom_720.png" width="200" alt="PLCcom Logo">

This folder contains all OPC UA **server** workshop examples for the PLCcom OPC UA SDK for Java.

All Java source files are located in `src/main/java/` — see [`src/main/java/README.md`](src/main/java/README.md) for a detailed description of each workshop class.

---

## Workshop Groups

| Group | Workshops |
|-------|-----------|
| **1 Data Access** | 11 Simple Server, 12a User Authentication, 12b Custom Auth Validator, 13 Methods, 14 Variables and Arrays, 15 Custom Types, 16 Multiple Namespaces, 17 Dynamic Nodes, 19 Advanced Server |
| **2 Alarms and Events** | 21 Alarm Conditions |
| **3 Historical Data** | 31 Historical Access, 32 Historical Update, 33 Historical Events, 34 Custom History Store, 35 Custom Event History Store |
| **4 NodeSet Import** | 41 NodeSet Import |
| **5 Logging** | 51 Logging |
| **6 Simple Events** | 61 Simple Events |
| **7 Reverse Connect** | 71 Reverse Connect |

---

## Easy to Use — Build a Server in Minutes

PLCcom makes it straightforward to expose your data via OPC UA. Create folders, variables and methods with just a few lines of code:

```java
UaFolder plant    = server.createFolder("Plant", UaRolePermissions.WITHOUT_RESTRICTIONS);
UaFolder machine  = server.createFolder(plant, "Machine1", UaRolePermissions.WITHOUT_RESTRICTIONS);

UaVariable<Double> temperature = server.createVariable(
        machine, "Temperature", UaRolePermissions.WITHOUT_RESTRICTIONS,
        Double.class, 21.5, false);

// Push a new value — all subscribed clients are notified automatically
temperature.setValue(23.7);
```

**Default endpoint:** `opc.tcp://localhost:48410`

---

##### Trademark Information
All product names, company names, and trademarks referenced here are trademarks or registered trademarks of their respective owners.

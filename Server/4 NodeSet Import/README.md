# 4 NodeSet Import - Server (Java)

This workshop shows how to import OPC UA NodeSet2 XML files into the server address space.

| # | Workshop | What you will learn |
|---|----------|---------------------|
| 41 | NodeSet Import | Import type definitions and instances from a NodeSet2 XML file |

The included PLCcom_Workshop_NodeSet.xml defines:
- SensorType (ns=2) - Value, Unit, InAlarm
- MotorType (ns=3) - Speed, Running, SerialNumber
- Instances: Sensors/TempSensor1, Sensors/PressureSensor1, Motors/Motor1, Motors/Motor2

OPC UA Companion Specifications (DI, Machinery, PackML, etc.) are distributed as NodeSet files
and can be imported the same way.

**Default endpoint:** `opc.tcp://localhost:48410`
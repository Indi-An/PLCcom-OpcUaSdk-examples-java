# 6 Simple Events - Server (Java)

This workshop shows how to fire OPC UA events from a server node.

| # | Workshop | What you will learn |
|---|----------|---------------------|
| 61 | Simple Events | Enable event notifications on a node and fire events with severity levels |

Events are different from DataChange notifications:
- DataChange - a variable value changed (subscription-based)
- Event      - something happened at a source node (discrete occurrence)

Events propagate upward automatically: Machine1 -> Plant -> Objects -> Server.

**Default endpoint:** `opc.tcp://localhost:48410`
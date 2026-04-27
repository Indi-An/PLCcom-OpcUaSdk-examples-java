# 5 Complex Datatypes - Client (Java)

This workshop covers reading and decoding structured/complex OPC UA data types.

| # | Workshop | What you will learn |
|---|----------|---------------------|
| 51 | Complex Types | Read structured data types (Structs) and decode their fields |

OPC UA structured types are transmitted as `ExtensionObject` and must be decoded
using the server-provided type description. PLCcom handles this automatically.

**Target server:** `opc.tcp://localhost:48410` (Server Workshop 15)
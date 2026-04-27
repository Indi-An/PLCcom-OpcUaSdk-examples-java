# 3 Historical Data - Server (Java)

These workshops show how to store and serve historical data and events via OPC UA HistoryRead.

| # | Workshop | What you will learn |
|---|----------|---------------------|
| 31 | Historical Access | Enable history on variables, serve ReadRaw / ReadAtTime / ReadProcessed |
| 32 | Historical Update | Accept Insert, Update, Replace, Remove and DeleteRaw from clients |
| 33 | Historical Events | Record and serve historical events via HistoryRead |
| 34 | Custom History Store | Implement UaHistoryStore for any storage back-end (CSV demo) |
| 35 | Custom Event History Store | Implement UaEventHistoryStore for any storage back-end (CSV demo) |

WS 34 and 35 use CSV files to demonstrate the pattern - replace with your own database or time-series store.

**Default endpoint:** `opc.tcp://localhost:48410`
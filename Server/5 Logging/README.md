# 5 Logging - Server (Java)

This workshop shows how to subscribe to SDK log messages and route them to your own logging framework.

| # | Workshop | What you will learn |
|---|----------|---------------------|
| 51 | Logging | Register a UaLogListener, set the log level, route to SLF4J / Log4j / java.util.logging |

Log levels (from least to most verbose):
- None    - disable all logging (default)
- Error   - only errors that affect functionality
- Warning - errors + warnings (recommended for production)
- Info    - errors + warnings + service calls
- Debug   - everything including internal stack details (very verbose)

**Default endpoint:** `opc.tcp://localhost:48410`
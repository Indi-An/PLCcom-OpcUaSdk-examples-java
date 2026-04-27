# 7 Reverse Connect - Server (Java)

This workshop shows how to implement server-initiated connections for firewall traversal.

| # | Workshop | What you will learn |
|---|----------|---------------------|
| 71 | Reverse Connect | Configure the server to connect to the client (not the other way around) |

Why Reverse Connect?
- The server is behind a firewall that blocks incoming connections
- The server is in a protected OT/ICS network, the client is in IT/cloud
- The server has a dynamic IP address

How it works (OPC UA Part 6 Section 7.1.3):
1. The client opens a listening port (e.g. 48500)
2. The server periodically sends a ReverseHello to that port
3. The client accepts the connection and establishes a normal OPC UA session

Normal endpoint:          opc.tcp://localhost:48410
Reverse Connect target:   opc.tcp://localhost:48500 (client must listen here)
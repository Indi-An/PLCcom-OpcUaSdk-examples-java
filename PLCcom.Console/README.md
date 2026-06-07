# PLCcom Console

`PLCcom.Console` is a small shared Maven project used by the workshop examples in this repository.

It provides `PLCcomConsole`, a Swing-based console window that redirects `System.out` and `System.err` into a dedicated workshop window. This makes the examples pleasant to run from an IDE, where a clear standalone output window is often easier to follow than the built-in console view.

## Maven Coordinates

```xml
<dependency>
    <groupId>com.indi-an.plccom</groupId>
    <artifactId>plccom-console</artifactId>
    <version>1.0.0</version>
</dependency>
```

The root Maven build includes this module before the client/server and PubSub workshop projects, so a normal repository build works without installing anything by hand.

## Usage

```java
PLCcomConsole.open("Workshop 11 - Simple Server", 1000);

System.out.println("Workshop output appears in the PLCcom console window.");

PLCcomConsole.close();
```

The helper is intentionally small and independent from the PLCcom OPC UA SDK itself. It exists only to make the workshop output easier to read.

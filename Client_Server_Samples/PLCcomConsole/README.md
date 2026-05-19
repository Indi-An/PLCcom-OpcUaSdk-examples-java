# PLCcomConsole

<img src="https://www.indi-an.com//wp-content/uploads/2026/03/PLCcom_720.png" width="200" alt="PLCcom Logo">

Shared Swing-based console window helper used by all client and server workshops.

---

## Usage

```java
// Open a console window with a title and width in pixels
PLCcomConsole.open("Workshop 11 - Simple Server", 1000);

// All System.out and System.err output appears in the console window

// Close the console window when done
PLCcomConsole.close();
```

`PLCcomConsole.open()` redirects `System.out` and `System.err` to a Swing text area, so workshop examples display their output in a dedicated window when launched from an IDE or via double-click — instead of the IDE's built-in console.

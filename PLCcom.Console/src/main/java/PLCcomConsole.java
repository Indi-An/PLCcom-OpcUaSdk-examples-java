// MIT License
// Copyright (c) Indi.An GmbH
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * PLCcom Console - a Swing-based console window that replaces the default
 * Java console. Intercepts System.out, System.err and System.in so that
 * all existing println() calls and readLine() calls work without any changes
 * to the application code.
 *
 * Usage:
 *   PLCcomConsole.open("My Application Title");
 *   // from here on, System.out.println() writes to the console window
 *   // and System.in.read() reads from the input field
 *
 * The console window:
 *   - Black background, green text (stdout), red text (stderr)
 *   - Monospaced font for clean alignment
 *   - Input field at the bottom with ENTER to submit
 *   - Auto-scrolls to the latest output
 *   - Closes the JVM when the window is closed
 */
public class PLCcomConsole {

    // ── Colors & Font ─────────────────────────────────────────────────────────
    private static final Color BG          = new Color(12, 12, 12);
    private static final Color FG_STDOUT   = new Color(204, 204, 204);
    private static final Color FG_STDERR   = new Color(231, 72, 86);
    private static final Color FG_INPUT    = new Color(58, 150, 221);
    private static final Color FG_PROMPT   = new Color(19, 161, 14);
    private static final Color INPUT_BG    = new Color(30, 30, 30);
    private static final Color BORDER_COLOR = new Color(63, 63, 70);
    private static final int DEFAULT_FONT_SIZE = 15;
    private static final Font  CONSOLE_FONT =
            new Font("Consolas", Font.PLAIN, configuredFontSize());

    // ── Configuration ─────────────────────────────────────────────────────────
    private static int maxLines = 1000;

    // ── Swing components ──────────────────────────────────────────────────────
    private static JFrame          frame;
    private static JTextPane       textPane;
    private static StyledDocument  doc;
    private static JTextField      inputField;
    private static JLabel          promptLabel;

    // ── Stream plumbing ───────────────────────────────────────────────────────
    private static final LinkedBlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    private static final PipedOutputStream pipedOut = new PipedOutputStream();
    private static PipedInputStream        pipedIn;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Opens the PLCcom Console window with default 1000 line buffer.
     *
     * @param title the window title shown in the title bar
     */
    public static void open(String title) {
        open(title, 1000);
    }

    /**
     * Opens the PLCcom Console window with a custom line buffer size.
     *
     * @param title    the window title shown in the title bar
     * @param maxLines maximum number of lines to keep (oldest lines are removed)
     */
    public static void open(String title, int maxLines) {
        PLCcomConsole.maxLines = maxLines;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }

        SwingUtilities.invokeLater(() -> buildUI(title));

        // Give Swing time to build the window before we redirect streams
        try { Thread.sleep(200); } catch (InterruptedException ignored) { }

        redirectStreams();
    }

    /**
     * Replaces the last line in the console with the given text.
     * Equivalent to Console.Write("\r...") in C#.
     *
     * @param text replacement text
     */
    public static void replaceLastLine(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                Element root = doc.getDefaultRootElement();
                int lastLine = root.getElementCount() - 1;
                if (lastLine >= 0) {
                    Element line = root.getElement(lastLine);
                    int start = line.getStartOffset();
                    int end   = line.getEndOffset() - 1;
                    if (end > start)
                        doc.remove(start, end - start);
                }
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setForeground(attrs, FG_STDOUT);
                StyleConstants.setFontFamily(attrs, CONSOLE_FONT.getFamily());
                StyleConstants.setFontSize(attrs, CONSOLE_FONT.getSize());
                doc.insertString(doc.getLength(), text, attrs);
                textPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) { }
        });
    }

    /**
     * Prints a line to the console in the default output color.
     * Equivalent to System.out.println(text).
     *
     * @param text text to print
     */
    public static void println(String text) {
        System.out.println(text);
    }

    /**
     * Prints an empty line.
     */
    public static void println() {
        System.out.println();
    }

    /**
     * Reads a line of input from the console input field.
     * Blocks until the user presses ENTER.
     *
     * @param prompt the prompt text shown before the input field
     * @return the text entered by the user, never null
     */
    public static String readLine(String prompt) {
        SwingUtilities.invokeLater(() -> {
            promptLabel.setText(prompt);
            inputField.setEnabled(true);
            inputField.requestFocusInWindow();
        });
        try {
            return inputQueue.take();
        } catch (InterruptedException e) {
            return "";
        } finally {
            SwingUtilities.invokeLater(() -> {
                promptLabel.setText("");
                inputField.setEnabled(false);
                inputField.setText("");
            });
        }
    }

    /**
     * Waits for the user to press ENTER with a default "Press ENTER..." prompt.
     */
    public static void waitForEnter() {
        readLine("  Press ENTER to exit...");
    }

    /**
     * Closes the console window and exits the JVM.
     * Call this at the end of main() after the last System.in.read().
     */
    public static void close() {
        try { Thread.sleep(100); } catch (InterruptedException ignored) { }
        if (frame != null) {
            frame.dispose();
        }
        System.exit(0);
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private static void buildUI(String title) {
        frame = new JFrame(title + " — PLCcom Console");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setMinimumSize(new Dimension(600, 400));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG);

        // Output area
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(BG);
        textPane.setForeground(FG_STDOUT);
        textPane.setFont(CONSOLE_FONT);
        textPane.setCaretColor(FG_STDOUT);
        textPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        doc = textPane.getStyledDocument();

        textPane.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (inputField.isEnabled()) inputField.requestFocusInWindow();
            }
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    String selected = textPane.getSelectedText();
                    if (selected != null && !selected.isEmpty()) {
                        java.awt.datatransfer.StringSelection sel =
                                new java.awt.datatransfer.StringSelection(selected);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(BG);
        scrollPane.getViewport().setBackground(BG);
        scrollPane.getVerticalScrollBar().setBackground(INPUT_BG);

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(4, 0));
        inputPanel.setBackground(INPUT_BG);
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        promptLabel = new JLabel("");
        promptLabel.setFont(CONSOLE_FONT);
        promptLabel.setForeground(FG_PROMPT);

        inputField = new JTextField();
        inputField.setFont(CONSOLE_FONT);
        inputField.setBackground(INPUT_BG);
        inputField.setForeground(FG_INPUT);
        inputField.setCaretColor(FG_INPUT);
        inputField.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        inputField.setEnabled(false);

        inputField.addActionListener(e -> {
            String text = inputField.getText();
            inputField.setText("");
            // Echo the input to the output area
            appendText("  " + promptLabel.getText() + text + "\n", FG_INPUT);
            // Feed into inputQueue (for readLine) AND into the pipe (for System.in.read)
            inputQueue.offer(text);
            try {
                pipedOut.write((text + "\n").getBytes());
                pipedOut.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        inputPanel.add(promptLabel, BorderLayout.WEST);
        inputPanel.add(inputField, BorderLayout.CENTER);

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(inputPanel, BorderLayout.SOUTH);

        frame.addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override public void windowGainedFocus(java.awt.event.WindowEvent e) {
                if (inputField.isEnabled()) inputField.requestFocusInWindow();
            }
            @Override public void windowLostFocus(java.awt.event.WindowEvent e) { }
        });

        frame.setVisible(true);
        inputField.requestFocusInWindow();
    }

    // ── Stream redirection ────────────────────────────────────────────────────

    private static void redirectStreams() {
        // Redirect System.out
        try {
            System.setOut(new PrintStream(new ConsoleOutputStream(FG_STDOUT), true, "UTF-8"));
            System.setErr(new PrintStream(new ConsoleOutputStream(FG_STDERR), true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException ignored) {
            System.setOut(new PrintStream(new ConsoleOutputStream(FG_STDOUT), true));
            System.setErr(new PrintStream(new ConsoleOutputStream(FG_STDERR), true));
        }

        // Redirect System.err

        // Redirect System.in via pipe - activates input field when read() is called
        try {
            pipedIn = new PipedInputStream(pipedOut) {
                @Override
                public synchronized int read() throws IOException {
                    // Small delay so any pending System.out output is rendered first
                    try { Thread.sleep(50); } catch (InterruptedException ignored) { }
                    SwingUtilities.invokeLater(() -> {
                        inputField.setEnabled(true);
                        inputField.requestFocusInWindow();
                    });
                    int result = super.read();
                    SwingUtilities.invokeLater(() -> inputField.setEnabled(false));
                    return result;
                }
                @Override
                public synchronized int read(byte[] b, int off, int len) throws IOException {
                    try { Thread.sleep(50); } catch (InterruptedException ignored) { }
                    SwingUtilities.invokeLater(() -> {
                        inputField.setEnabled(true);
                        inputField.requestFocusInWindow();
                    });
                    int result = super.read(b, off, len);
                    SwingUtilities.invokeLater(() -> inputField.setEnabled(false));
                    return result;
                }
            };
            System.setIn(pipedIn);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Output stream that writes to the text pane ────────────────────────────

    private static class ConsoleOutputStream extends OutputStream {
        private final Color color;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        ConsoleOutputStream(Color color) {
            this.color = color;
        }

        @Override
        public void write(int b) {
            buffer.write(b);
            if (b == '\n') flush();
        }

        @Override
        public void flush() {
            if (buffer.size() > 0) {
                try {
                    String text = buffer.toString("UTF-8");
                    buffer.reset();
                    appendText(text, color);
                } catch (Exception ignored) { }
            }
        }
    }

    // ── Append text to the styled document ───────────────────────────────────

    private static void appendText(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setForeground(attrs, color);
                StyleConstants.setFontFamily(attrs, CONSOLE_FONT.getFamily());
                StyleConstants.setFontSize(attrs, CONSOLE_FONT.getSize());
                doc.insertString(doc.getLength(), text, attrs);

                // Remove oldest lines if buffer exceeds maxLines
                Element root = doc.getDefaultRootElement();
                while (root.getElementCount() > maxLines) {
                    Element first = root.getElement(0);
                    doc.remove(0, first.getEndOffset());
                }

                // Auto-scroll to bottom
                textPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) { }
        });
    }

    private static int configuredFontSize() {
        String value = System.getProperty("plccom.console.fontSize");
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_FONT_SIZE;
        }
        try {
            int fontSize = Integer.parseInt(value.trim());
            if (fontSize >= 8 && fontSize <= 48) {
                return fontSize;
            }
        } catch (NumberFormatException ignored) {
        }
        return DEFAULT_FONT_SIZE;
    }
}

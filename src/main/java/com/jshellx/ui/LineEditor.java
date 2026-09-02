package com.jshellx.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.jshellx.context.CommandHistory;
import com.jshellx.parser.Command;

/**
 * A tiny readline replacement. (Version 1: type, backspace, Enter, Ctrl-C, Ctrl-D.)
 *
 * <p>
 * With a {@link RawTerminal} it edits the line itself. Without one (Windows, piped stdin)
 * it falls back to the operating system's own line editing via {@link BufferedReader}.
 * Raw mode is entered only while a line is being edited, so child processes always see
 * a normal terminal.
 */
public final class LineEditor {

  // @formatter:off
  /** Outcome of one {@link #readLine} call. */
  public sealed interface Result permits Line, Interrupted, Eof {}
  public record Line(String text) implements Result {}
  public record Interrupted() implements Result {}
  public record Eof() implements Result {}
  // @formatter:on

  private final InputStream in;
  private final PrintStream out;
  private final CommandHistory history;
  private final RawTerminal terminal; // null => cooked fallback
  private BufferedReader cooked;

  public LineEditor(InputStream in, PrintStream out, CommandHistory history, RawTerminal terminal) {
    this.in = in;
    this.out = out;
    this.history = history;
    this.terminal = terminal;
  }

  public boolean isRaw() {
    return terminal != null;
  }

  public Result readLine(String prompt) throws IOException {
    if (terminal == null) return readCooked(prompt);
    terminal.enterRaw();
    try {
      return new Edit(prompt).run();
    } finally {
      terminal.restore();
    }
  }

  // ---- fallback: the OS does the editing ---- //

  private Result readCooked(String prompt) throws IOException {
    if (cooked == null) cooked = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    out.print(prompt);
    out.flush();
    String line = cooked.readLine();
    return line == null ? new Eof() : new Line(line);
  }

  /**
   * Returns the input stream to read from. In raw mode, we must use JLine's
   * terminal input stream (on Windows, System.in doesn't deliver individual
   * keystrokes even after the console is set to raw mode).
   */
  private InputStream rawInput() {
    return terminal != null ? terminal.input() : in;
  }

  /** Reads one UTF-8 encoded code point straight from the byte stream, or -1 at EOF. */
  private int readCodePoint() throws IOException {
    InputStream stream = rawInput();
    int b = stream.read();
    if (b < 0x80) return b; // ASCII, or -1
    int extra = b >= 0xF0 ? 3 : b >= 0xE0 ? 2 : b >= 0xC0 ? 1 : 0;
    int cp = extra == 3 ? b & 0x07 : extra == 2 ? b & 0x0F : extra == 1 ? b & 0x1F : b;
    for (int i = 0; i < extra; i++) {
      int next = stream.read();
      if (next < 0) break;
      cp = (cp << 6) | (next & 0x3F);
    }
    return cp;
  }

  // ---- raw-mode editing session ---- //

  private final class Edit {
    private final String prompt;
    private final StringBuilder buf = new StringBuilder();
    private int pos; // cursor, as an index into buf
    private int hist = history.size(); // history cursor (size() = the line being typed)
    private String draft = ""; // the line being typed, saved while browsing history

    Edit(String prompt) {
      this.prompt = prompt;
    }

    Result run() throws IOException {
      out.print(prompt);
      out.flush();
      while (true) {
        int c = readCodePoint();
        switch (c) {
          case -1 -> {
            return new Eof();
          }
          case '\r', '\n' -> {
            out.print("\r\n");
            out.flush();
            return new Line(buf.toString());
          }
          case 3 -> { // Ctrl-C
            out.print("^C\r\n");
            out.flush();
            return new Interrupted();
          }
          case 4 -> { // Ctrl-D: EOF on an empty line, otherwise ignored for now
            if (buf.isEmpty()) {
              out.print("\r\n");
              out.flush();
              return new Eof();
            }
          }

          case 16 -> historyPrevious();
          case 14 -> historyNext();
          case 8, 127 -> backspace();

          default -> {
            if (c >= 32) insert(c);
          } // printable characters
        }
        redraw();
      }
    }

    private void insert(int cp) {
      buf.insert(pos, new String(Character.toChars(cp)));
      pos += Character.charCount(cp);
    }

    private void backspace() {
      if (pos == 0) return;
      int previous = buf.offsetByCodePoints(pos, -1);
      buf.delete(previous, pos);
      pos = previous;
    }

    /** Repaints the whole line and puts the cursor back where it belongs. */
    private void redraw() {
      out.print("\r" + prompt + buf + "\u001B[K"); // CR, prompt, text, erase to end of line
      int tail = buf.codePointCount(pos, buf.length());
      if (tail > 0) out.print("\u001B[" + tail + "D"); // move cursor left past the tail
      out.flush();
    }

    // ---- history --------------------------------------------------------------------- //
    private void historyPrevious() {
      if (hist == 0) return;
      if (hist == history.size()) draft = buf.toString();
      buf.setLength(0);
      buf.append(history.get(--hist));
      pos = buf.length();
    }

    private void historyNext() {
      if (hist >= history.size()) return;
      hist++;
      buf.setLength(0);
      buf.append(hist == history.size() ? draft : history.get(hist));
      pos = buf.length();
    }
  }
}
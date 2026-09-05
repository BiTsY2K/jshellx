package com.jshellx.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.jshellx.context.CommandHistory;

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
          // @formatter:off
          case -1 -> { return new Eof(); }
          case '\r', '\n' -> { out.print("\r\n"); out.flush(); return new Line(buf.toString()); }
          case 3 -> { out.print("^C\r\n"); out.flush(); return new Interrupted(); } // Ctrl-C 
          
          case 4 -> { 
            // Ctrl-D: EOF on an empty line, otherwise ignored for now
            if (buf.isEmpty()) { out.print("\r\n"); out.flush(); return new Eof(); } 
            deleteForward();
          }

          case  1 -> pos = 0; // Ctrl-A
          case  5 -> pos = buf.length(); // Ctrl-E
          case  2 -> left();  // Ctrl-B
          case  6 -> right(); // Ctrl-F
          case 16 -> historyPrevious(); // Ctrl-P
          case 14 -> historyNext();     // Ctrl-N
          case 11 -> buf.setLength(pos);              // Ctrl-K
          case 21 -> { buf.delete(0, pos); pos = 0; } // Ctrl-U
          case 23 -> deleteWordBack();  // Ctrl-W
          case 27 -> escapeSequence();

          case 8, 127 -> backspace();

          default -> { if (c >= 32) insert(c); } // printable characters
          // @formatter:on
        }
        redraw();
      }
    }

    private void insert(int cp) {
      int oldPos = pos;
      buf.insert(oldPos, new String(Character.toChars(cp)));
      pos = oldPos + Character.charCount(cp);
    }

    // @formatter:off
    private void left() { if (pos > 0) pos = buf.offsetByCodePoints(pos, -1); }
    private void right() { if (pos < buf.length()) pos = buf.offsetByCodePoints(pos, 1); }
    // @formatter:on

    private void deleteForward() {
      if (pos < buf.length()) buf.delete(pos, buf.offsetByCodePoints(pos, 1));
    }

    private void backspace() {
      if (pos == 0) return;
      int previous = buf.offsetByCodePoints(pos, -1);
      buf.delete(previous, pos);
      pos = previous;
    }

    private void deleteWordBack() {
      int start = wordStart(pos);
      buf.delete(start, pos);
      pos = start;
    }

    private int wordStart(int from) {
      int p = from;
      while (p > 0 && buf.charAt(p - 1) == ' ') p--;
      while (p > 0 && buf.charAt(p - 1) != ' ') p--;
      return p;
    }

    private int wordEnd(int from) {
      int p = from;
      while (p < buf.length() && buf.charAt(p) == ' ') p++;
      while (p < buf.length() && buf.charAt(p) != ' ') p++;
      return p;
    }

    /** Repaints the whole line and puts the cursor back where it belongs. */
    private void redraw() {
      StringBuilder sb = new StringBuilder();
      // Build the entire ANSI sequence in memory first to avoid multiple redraw ticks
      sb.append("\r").append(prompt).append(buf).append("\u001B[K"); // CR, prompt, text, erase to end of line
      int tail = buf.codePointCount(pos, buf.length());
      if (tail > 0) sb.append("\u001B[").append(tail).append("D"); // move cursor left past the tail
      out.print(sb.toString());
      out.flush();
    }

    // ---- escape sequences: arrows ---------------------------------------------------- //
    private void escapeSequence() throws IOException {
      InputStream in = rawInput();
      if (in.available() == 0) return; // a lone ESC key press: ignore

      int intro = in.read();
      if (intro != '[' && intro != 'O') return; // Alt+key: ignore

      int last;
      StringBuilder params = new StringBuilder();
      while (true) {
        last = in.read();
        if (last < 0 || (last >= 0x40 && last <= 0x7E)) break; // final byte of the sequence
        params.append((char) last);
      }

      boolean ctrl = params.toString().endsWith(";5");

      // @formatter:off
      switch (last) {
        case 'A' -> historyPrevious();
        case 'B' -> historyNext();
        case 'C' -> { if (ctrl) pos = wordEnd(pos); else right(); }
        case 'D' -> { if (ctrl) pos = wordStart(pos); else left(); }
        case 'H' -> pos = 0;
        case 'F' -> pos = buf.length();
        case '~' -> {
          switch (params.toString()) {
            case "1", "7" -> pos = 0;
            case "4", "8" -> pos = buf.length();
            default -> { }
          }
        }
        default -> { }
      }
      // @formatter:on
    }

    // ---- history --------------------------------------------------------------------- //
    private void historyPrevious() {
      if (hist == 0) return;
      if (hist == history.size()) draft = buf.toString();
      showHistory(history.get(--hist));
    }

    private void historyNext() {
      if (hist >= history.size()) return;
      showHistory((++hist == history.size()) ? draft : history.get(hist));
    }

    private void showHistory(String history) {
      buf.setLength(0);
      buf.append(history);
      pos = buf.length();
    }
  }
}
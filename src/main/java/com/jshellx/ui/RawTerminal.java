package com.jshellx.ui;

import java.io.IOException;
import java.util.Optional;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Switches the controlling terminal between "cooked" mode (the OS edits the line) and "raw"
 * mode (we receive every key) by using JLine's cross-platform terminal abstraction.
 *
 * <p>
 * On Linux/macOS JLine uses the native PTY; on Windows 10+ it uses the Console API
 * ({@code kernel32.dll}) to enable Virtual Terminal Input. When stdin is not a terminal
 * (piped input, IDE console, ...) {@link #open()} returns empty.
 */
public final class RawTerminal {

  private final Terminal terminal;
  private final Attributes saved;
  private boolean raw;

  private RawTerminal(Terminal terminal, Attributes saved) {
    this.terminal = terminal;
    this.saved = saved;
  }

  /**
   * Tries to open a real system terminal. Returns empty when stdin is not a
   * terminal (pipes, IDE console, CI) or if the terminal cannot be opened.
   */
  public static Optional<RawTerminal> open() {
    try {
      Terminal terminal = TerminalBuilder.builder()
          .system(true) // use system terminal (not dumb)
          .jansi(false) // we already handle ANSI ourselves
          .build();

      // JLine returns a "dumb" terminal when it can't detect a real one.
      if (terminal.getType().equals(Terminal.TYPE_DUMB)
          || terminal.getType().equals(Terminal.TYPE_DUMB_COLOR)) {
        terminal.close();
        return Optional.empty();
      }

      Attributes saved = terminal.getAttributes();
      RawTerminal raw = new RawTerminal(terminal, new Attributes(saved));

      // Never leave the user's terminal in raw mode, even after a crash or kill.
      Runtime.getRuntime().addShutdownHook(new Thread(raw::restore));
      return Optional.of(raw);

    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /**
   * Returns the JLine terminal's input stream, which must be used for reading
   * in raw mode (on Windows, System.in cannot read individual keystrokes).
   */
  public java.io.InputStream input() {
    return terminal.input();
  }

  /** No line buffering, no echo, no signal keys (Ctrl-C arrives as byte 3), 1-byte reads. */
  public synchronized void enterRaw() throws IOException {
    Attributes attrs = new Attributes(saved);

    // Disable canonical mode (no line buffering)
    attrs.setLocalFlag(Attributes.LocalFlag.ICANON, false);
    // Disable echo
    attrs.setLocalFlag(Attributes.LocalFlag.ECHO, false);
    // Disable signal generation from Ctrl-C/Ctrl-Z so we receive them as bytes
    attrs.setLocalFlag(Attributes.LocalFlag.ISIG, false);
    // Disable extended input processing
    attrs.setLocalFlag(Attributes.LocalFlag.IEXTEN, false);
    // Disable XON/XOFF flow control (Ctrl-S/Ctrl-Q)
    attrs.setInputFlag(Attributes.InputFlag.IXON, false);

    // Minimum 1 byte, no timeout
    attrs.setControlChar(Attributes.ControlChar.VMIN, 1);
    attrs.setControlChar(Attributes.ControlChar.VTIME, 0);

    terminal.setAttributes(attrs);
    raw = true;
  }

  public synchronized void restore() {
    if (!raw) return;
    terminal.setAttributes(saved);
    raw = false;
  }
}
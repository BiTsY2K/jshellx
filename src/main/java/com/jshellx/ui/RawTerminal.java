package com.jshellx.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.jshellx.context.ShellContext;

/**
 * Switches the controlling terminal between "cooked" mode (the OS edits the line) and "raw"
 * mode (we receive every key) by running the POSIX {@code stty} utility on our own stdin.
 *
 * <p>
 * {@link #open()} returns empty on Windows and whenever stdin is not a terminal (pipes, IDE
 * console, CI): {@code stty -g} fails there, which doubles as tty detection.
 */
public final class RawTerminal {

  private final String saved; // output of 'stty -g': the settings to restore
  private boolean raw;

  private RawTerminal(String saved) {
    this.saved = saved;
  }

  /**
   * Tries to open a real system terminal. Returns empty when stdin is not a
   * terminal (pipes, IDE console, CI) or if the terminal cannot be opened.
   */
  public static Optional<RawTerminal> open() {
    if (ShellContext.isWindows()) return Optional.empty();
    try {
      String saved = stty("-g").strip();
      if (saved.isEmpty()) return Optional.empty();

      RawTerminal rawTerminal = new RawTerminal(saved);

      // Never leave the user's terminal in raw mode, even after a crash or kill.
      Runtime.getRuntime().addShutdownHook(new Thread(rawTerminal::restore));
      return Optional.of(rawTerminal);

    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /** No line buffering, no echo, no signal keys (Ctrl-C arrives as byte 3), 1-byte reads. */
  public synchronized void enterRaw() throws IOException {
    // Disable canonical mode (no line buffering), echo, signal generation from Ctrl-C/Ctrl-Z so we receive them as
    // bytes, disable extended input processing, XON/XOFF flow control (Ctrl-S/Ctrl-Q)
    stty("-icanon", "-echo", "-isig", "-iexten", "-ixon", "min", "1", "time", "0");
    raw = true;
  }

  public synchronized void restore() {
    if (!raw) return;
    raw = false;
    try {
      stty(saved);
    } catch (IOException e) {
      // best effort: no other way to reset the terminal
    }
  }

  private static String stty(String... args) throws IOException {
    List<String> command = new ArrayList<>();
    command.add("stty");
    command.addAll(List.of(args));
    ProcessBuilder pb = new ProcessBuilder(command);
    Process p = pb.redirectInput(ProcessBuilder.Redirect.INHERIT) // stty acts on its stdin: our terminal
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
    String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    try {
      if (p.waitFor() != 0)
        throw new IOException("stty " + String.join(" ", args) + " failed");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while running stty", e);
    }
    return output;
  }
}

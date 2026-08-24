package com.jshellx.ui;

import com.jshellx.context.ShellContext;

/**
 * Minimal ANSI escape-sequence helper (replaces Jansi). Honors the NO_COLOR
 * convention.
 */
public final class Ansi {

  public static final String RESET = "\u001B[0m";
  public static final String BOLD = "\u001B[1m";
  public static final String RED = "\u001B[31m";
  public static final String GREEN = "\u001B[32m";
  public static final String BLUE = "\u001B[34m";
  public static final String MAGENTA = "\u001B[35m";
  public static final String CYAN = "\u001B[96m";

  private static boolean enabled = System.getenv("NO_COLOR") == null;

  // @formatter:off
  private Ansi() {}
  public static void setEnabled(boolean value) { enabled = value; }
  // @formatter:on

  /**
   * Wraps {@code text} in the given style codes (a no-op when colors are
   * disabled).
   */
  public static String style(String text, String... codes) {
    if (!enabled || codes.length == 0) return text;
    return String.join("", codes) + text + RESET;
  }

  /**
   * Legacy Windows consoles only interpret ANSI sequences once "virtual terminal
   * processing" is on. Java cannot flip that flag itself, but launching any
   * console child process (cmd.exe) makes Windows 10+ switch it on for the
   * console. Harmles elsewhere.
   */
  public static void enableWindowsVt() {
    if (!ShellContext.isWindows()) return;
    try {
      new ProcessBuilder("cmd", "/c", "").inheritIO().start().waitFor();
    } catch (Exception ignored) {
      // colors may show up as raw escape codes; set NO_COLOR=1 to disable them
    }
  }
}
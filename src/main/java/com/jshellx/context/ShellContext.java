package com.jshellx.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

public final class ShellContext {

  private static final boolean WINDOWS = System.getProperty("os.name", "")
      .toLowerCase(Locale.ROOT).contains("win");

  private Path cwd;
  private int exitCode;
  private int lastExitCode;
  private boolean running = true;

  public static ShellContext fromSystem() {
    return new ShellContext(Path.of(System.getProperty("user.dir")));
  }

  public ShellContext(Path cwd) {
    this.cwd = cwd.toAbsolutePath().normalize();
  }

  public void requestExit(int code) {
    this.exitCode = code;
    this.running = false;
  }

  // @formatter:off
  public boolean isRunning()  { return running; }
  public int exitCode()       { return exitCode; }
  public int lastExitCode()   { return lastExitCode; }
  
  public void setLastExitCode(int code) { this.lastExitCode = code; }
  public static boolean isWindows()      { return WINDOWS; }

  public String home()  { return System.getProperty("user.home"); }
  public Path cwd()     { return cwd; }
  // @formatter:on

  public void changeDirectory(String target) throws IOException {
    Path next = null;
    try {
      next = cwd.resolve(target).normalize();
    } catch (InvalidPathException e) {
      throw new IOException(String.format("invalid path: %s", target));
    }

    if (!Files.exists(next)) throw new IOException(String.format("no such file or directory: %s", target));
    if (!Files.isDirectory(next)) throw new IOException(String.format("no such file or directory: %s", target));
    cwd = next;
  }
}

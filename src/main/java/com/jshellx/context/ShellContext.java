package com.jshellx.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mutable state of one shell session: working directory, environment variables,
 * aliases, last exit status and the "keep running" flag.
 *
 * <p>
 * The JVM cannot change its own working directory, so the shell tracks its own
 * {@code cwd} and passes it to every child process via
 * {@link ProcessBuilder#directory}.
 */
public final class ShellContext {

  private static final boolean WINDOWS = System.getProperty("os.name", "")
      .toLowerCase(Locale.ROOT).contains("win");

  private Path cwd;
  private int exitCode;
  private int lastExitCode;
  private boolean running = true;

  private final Map<String, String> env;
  private final Map<String, String> aliases = new TreeMap<>();
  private final CommandHistory history = new CommandHistory();

  public static ShellContext fromSystem() {
    return new ShellContext(Path.of(System.getProperty("user.dir")), System.getenv());
  }

  public ShellContext(Path cwd, Map<String, String> environment) {
    this.cwd = cwd.toAbsolutePath().normalize();
    // Windows variable names are case-insensitive; POSIX ones are not.
    this.env = WINDOWS ? new TreeMap<>(String.CASE_INSENSITIVE_ORDER) : new TreeMap<>();
    this.env.putAll(environment);
    this.env.put("PWD", this.cwd.toString());
  }

  // @formatter:off
  public static boolean isWindows() { return WINDOWS; }

  public Path cwd() { return cwd; }
  
  // ---- status / lifecycle ------------------------------------------------- //
  public boolean isRunning()  { return running; }
  public int exitCode()       { return exitCode; }
  public int lastExitCode()             { return lastExitCode; }
  public void setLastExitCode(int code) { this.lastExitCode = code; }
  
  public void requestExit(int code) { 
    this.exitCode = code; 
    this.running = false; 
  }


  /** Resolves a (possibly relative) path against the shell's working directory. */
  public Path resolve(String path) { return cwd.resolve(path).normalize(); }
  
  // ---- environment -------------------------------------------------------- //
  public Map<String, String> environment()  { return Collections.unmodifiableMap(env); }
  public void setEnv(String name, String value) { env.put(name, value); }
  public String getEnv(String name) { return env.get(name); }
  public void unsetEnv(String name) { env.remove(name); }

  // ---- history ------------------------------------------------------------ //
  public CommandHistory history()   { return history; }
  
  // ---- aliases ------------------------------------------------------------ //
  public Map<String, String> aliases()    { return Collections.unmodifiableMap(aliases); }
  public boolean removeAlias(String name) { return aliases.remove(name) != null; }
  public void setAlias(String name, String value) { aliases.put(name, value); }

  // @formatter:on

  public String home() {
    String home = env.get("HOME");
    if (home == null || home.isBlank()) home = env.get("USERPROFILE");
    return (home == null || home.isBlank()) ? System.getProperty("user.home") : home;
  }

  public void changeDirectory(String target) throws IOException {
    Path next = null;
    try {
      next = cwd.resolve(target).normalize();
    } catch (InvalidPathException e) {
      throw new IOException(String.format("invalid path: %s", target));
    }

    if (!Files.exists(next)) throw new IOException(String.format("no such file or directory: %s", target));
    if (!Files.isDirectory(next)) throw new IOException(String.format("no such file or directory: %s", target));
    env.put("OLDPWD", cwd.toString());
    cwd = next;
    env.put("PWD", next.toString());
  }
}

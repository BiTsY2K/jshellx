package com.jshellx.exec;

import com.jshellx.context.ShellContext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Starts OS processes with the shell's cwd and environment. */
public final class ProcessLauncher {

  /** cmd.exe internals that have no standalone .exe (dir, type, copy, ...). */
  private static final Set<String> WINDOWS_INTERNALS = Set.of(
      "dir", "type", "copy", "del", "erase", "md", "mkdir", "rd", "rmdir", "ren",
      "rename", "move", "echo", "ver", "vol", "mklink", "assoc", "date", "time",
      "start", "title", "path");

  /** Raised when the command is neither a builtin nor found on PATH. */
  public static final class CommandNotFoundException extends IOException {
    private final String command;

    public CommandNotFoundException(String command) {
      super("command not found: " + command);
      this.command = command;
    }

    public String command() {
      return command;
    }
  }

  private final ExecutableFinder finder;

  public ProcessLauncher(ExecutableFinder finder) {
    this.finder = finder;
  }

  public Process start(List<String> args, ShellContext ctx,
      ProcessBuilder.Redirect in, ProcessBuilder.Redirect out) throws IOException {
    ProcessBuilder pb = new ProcessBuilder(commandLine(args, ctx));
    pb.directory(ctx.cwd().toFile());
    pb.environment().clear();
    pb.environment().putAll(ctx.environment());
    pb.redirectInput(in);
    pb.redirectOutput(out);
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    return pb.start();
  }

  private List<String> commandLine(List<String> args, ShellContext ctx) throws CommandNotFoundException {
    String name = args.get(0);
    List<String> cmd = new ArrayList<>(args);

    Optional<Path> exe = finder.find(name, ctx);
    if (exe.isPresent()) {
      cmd.set(0, exe.get().toString()); // absolute path: no JVM-cwd ambiguity
      return cmd;
    }
    if (ShellContext.isWindows() && WINDOWS_INTERNALS.contains(name.toLowerCase(Locale.ROOT))) {
      cmd.addAll(0, List.of("cmd.exe", "/c"));
      return cmd;
    }
    throw new CommandNotFoundException(name);
  }
}
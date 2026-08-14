package com.jshellx.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.BuiltinRegistry;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;
import com.jshellx.exec.ProcessLauncher;
import com.jshellx.exec.ProcessLauncher.CommandNotFoundException;
import com.jshellx.parser.Command;
import com.jshellx.parser.CommandParser;
import com.jshellx.parser.ParseException;
import com.jshellx.parser.Pipeline;

import static java.lang.ProcessBuilder.Redirect.INHERIT;

public final class Shell {

  private ShellContext ctx;
  private BuiltinRegistry builtins;
  private final CommandParser parser;
  private final ProcessLauncher launcher;
  private BufferedReader in;
  private IoStreams io;

  public Shell(ShellContext ctx, BuiltinRegistry builtins, CommandParser parser,
      ProcessLauncher launcher, BufferedReader in, IoStreams io) {
    this.ctx = ctx;
    this.builtins = builtins;
    this.parser = parser;
    this.launcher = launcher;
    this.in = in;
    this.io = io;
  }

  /** Runs until {@code exit} or Ctrl-D. @return the process exit code */
  public int run() throws IOException {
    while (ctx.isRunning()) {
      io.out().printf("jshellx %s> ", ctx.cwd());
      io.out().flush();

      String line = in.readLine();
      if (line == null) break;

      try {
        Optional<Pipeline> pipeline = parser.parse(line);
        if (pipeline.isEmpty()) continue;
        io.out().println("parsed: " + pipeline.get()); // TEMPORARY: see the structure
        ctx.setLastExitCode(execute(pipeline.get().stages().get(0))); // first stage only, for now
      } catch (ParseException e) {
        io.err().println("jshellx: " + e.getMessage());
        ctx.setLastExitCode(2);
        continue;
      }
    }

    return ctx.exitCode();
  }

  private int execute(Command cmd) {
    Optional<BuiltinCommand> builtin = builtins.find(cmd.name());
    if (builtin.isPresent()) {
      return builtin.get().execute(cmd.args().subList(1, cmd.args().size()), ctx, io);
    }

    try {
      Process process = launcher.start(cmd.args(), ctx, INHERIT, INHERIT);
      int exitCode = process.waitFor();
      if (exitCode != 0) io.out().printf("[exit %s]%n", exitCode);
      return exitCode;
    } catch (CommandNotFoundException e) {
      io.err().println("jshellx: command not found: " + e.command());
      return 127; // the conventional "not found" status
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      io.err().printf("jshellx: command execution interrupted%n%s", e.getMessage());
      return 130;
    } catch (IOException e) {
      io.err().println("jshellx: " + e.getMessage());
      return 1;
    }

  }
}

package com.jshellx.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
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
import static java.lang.ProcessBuilder.Redirect.PIPE;

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
        ctx.setLastExitCode(parser.parse(line).map(this::execute).orElse(0));
      } catch (ParseException e) {
        io.err().println("jshellx: " + e.getMessage());
        ctx.setLastExitCode(2);
        continue;
      }
    }

    return ctx.exitCode();
  }

  private int execute(Pipeline pipeline) {
    if (pipeline.stages().size() > 1) {
      io.err().println("jshellx: pipelines are not supported yet");
      return 2;
    }

    Command cmd = pipeline.stages().get(0);
    List<AutoCloseable> opened = new ArrayList<>(); // everything we must close afterwards
    List<Thread> pumps = new ArrayList<>();

    try {
      // ---- resolve redirections ---- //
      InputStream in = null; // null = the terminal's stdin
      if (cmd.stdinFile() != null) {
        in = Files.newInputStream(ctx.resolve(cmd.stdinFile()));
        opened.add(in);
      }
      OutputStream sink = null; // null = the terminal's stdout
      if (cmd.stdoutFile() != null) {
        sink = Files.newOutputStream(ctx.resolve(cmd.stdoutFile()), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            cmd.append() ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
        opened.add(sink);
      }

      // ---- built-in: runs inside this JVM ---- //
      Optional<BuiltinCommand> builtin = builtins.find(cmd.name());
      if (builtin.isPresent()) {
        PrintStream out = sink != null ? new PrintStream(sink, false, StandardCharsets.UTF_8) : io.out();
        int status = builtin.get().execute(cmd.args().subList(1, cmd.args().size()), ctx,
            new IoStreams(in != null ? in : io.in(), out, io.err()));
        out.flush();
        return status;
      }

      // ---- external program: a separate OS process ---- //
      Process p = launcher.start(cmd.args(), ctx,
          in != null ? PIPE : INHERIT, // PIPE = we feed/collect the bytes ourselves
          sink != null ? PIPE : INHERIT); // INHERIT = it talks to the terminal directly
      if (in != null) pumps.add(pump(in, p.getOutputStream()));
      if (sink != null) pumps.add(pump(p.getInputStream(), sink));

      int status = p.waitFor();
      if (status != 0) io.out().printf("[exit %s]%n", status);
      for (Thread t : pumps) t.join(); // make sure all bytes reached the file
      return status;

    } catch (CommandNotFoundException e) {
      io.err().println("jshellx: command not found: " + e.command());
      return 127; // the conventional "not found" status
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      io.err().printf("jshellx: command execution interrupted%n%s", e.getMessage());
      return 130;
    } catch (IOException e) {
      io.err().printf("jshellx: %s%n%s", describe(e), e.getMessage());
      return 1;
    } finally {
      for (AutoCloseable c : opened) {
        try {
          c.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  /**
   * Copies {@code from} into {@code to} on a daemon thread,
   * closing both when done.
   */
  private static Thread pump(InputStream from, OutputStream to) {
    return Thread.ofPlatform().daemon().name("jshellx-pump").start(() -> {
      try (from; to) {
        from.transferTo(to);
      } catch (IOException ignored) {
        // the other side went away: normal termination
      }
    });
  }

  private static String describe(IOException e) {
    return switch (e) {
      case NoSuchFileException nf -> "no such file or directory: " + nf.getFile();
      case AccessDeniedException ad -> "permission denied: " + ad.getFile();
      case FileSystemException fs when fs.getReason() != null -> fs.getFile() + ": " + fs.getReason();
      default -> String.valueOf(e.getMessage());
    };
  }
}

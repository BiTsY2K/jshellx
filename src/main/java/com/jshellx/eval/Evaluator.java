package com.jshellx.eval;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static java.lang.ProcessBuilder.Redirect.PIPE;

/**
 * Executes parsed pipelines.
 *
 * <ul>
 * <li><b>Built-ins</b> run in-process and synchronously (so {@code cd} really
 * changes the shell). When piped, their output is buffered in memory and handed
 * to the next stage.</li>
 * <li><b>External processes</b> are started concurrently; small "pump" threads
 * copy bytes between neighbouring stages, which makes mixed pipelines such as
 * {@code history | grep git > out.txt} work uniformly.</li>
 * <li>The first stage inherits the terminal's stdin and the last stage inherits
 * its stdout unless redirected, so interactive programs (python, vim, ...)
 * behave normally.</li>
 * </ul>
 * The pipeline's exit status is that of its last stage.
 */
public final class Evaluator {

  private final ShellContext ctx;
  private final BuiltinRegistry builtins;
  private final CommandParser parser;
  private final ProcessLauncher launcher;
  private final IoStreams terminal;
  private final Set<Process> running = ConcurrentHashMap.newKeySet();

  public Evaluator(ShellContext ctx, BuiltinRegistry builtins, CommandParser parser,
      ProcessLauncher launcher, IoStreams terminal) {
    this.ctx = ctx;
    this.builtins = builtins;
    this.parser = parser;
    this.launcher = launcher;
    this.terminal = terminal;
  }

  /** Parses and runs one input line. @return its exit status */
  public int evaluate(String line) {
    try {
      Optional<Pipeline> pipeline = parser.parse(line);
      terminal.out().println("parsed: " + pipeline.get()); // TEMPORARY: see the structure
      return pipeline.map(this::execute).orElse(0);
    } catch (ParseException e) {
      terminal.err().println("jshellx: " + e.getMessage());
      return 2;
    }
  }

  /** Called from the Ctrl-C handler: terminates whatever is currently running. */
  public void interrupt() {
    running.forEach(Process::destroy);
  }

  public int execute(Pipeline pipeline) {
    if (pipeline.stages().size() > 1) {
      terminal.err().println("jshellx: pipelines are not supported yet");
      return 2;
    }

    List<Command> stages = pipeline.stages();
    List<Closeable> opened = new ArrayList<>(); // everything we must close afterwards
    List<Thread> pumps = new ArrayList<>();
    List<Process> started = new ArrayList<>();

    Process lastProcess = null;
    InputStream upstream = null; // data flowing into the next stage; null = terminal stdin

    int status = 0;

    try {
      for (int i = 0; i < stages.size(); i++) {
        Command cmd = stages.get(0);
        boolean last = i == stages.size() - 1;

        // ---- resolve this stage's stdin / stdout ---- //
        InputStream in = upstream;
        if (cmd.stdinFile() != null) {
          if (in != null) in.close();
          in = Files.newInputStream(ctx.resolve(cmd.stdinFile()));
          opened.add(in);
        }
        OutputStream sink = null; // null = the terminal's stdout. explicit file target
        if (cmd.stdoutFile() != null) {
          sink = Files.newOutputStream(ctx.resolve(cmd.stdoutFile()),
              StandardOpenOption.CREATE, StandardOpenOption.WRITE,
              cmd.append() ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
          opened.add(sink);
        }

        // ---- built-in: runs inside this JVM ---- //
        Optional<BuiltinCommand> builtin = builtins.find(cmd.name());
        if (builtin.isPresent()) {
          // Output goes to: file, or an in-memory buffer (if piped on), or the terminal.
          ByteArrayOutputStream buffer = (sink == null && !last) ? new ByteArrayOutputStream() : null;
          OutputStream dest = sink != null ? sink : buffer;
          PrintStream out = dest != null ? new PrintStream(sink, false, StandardCharsets.UTF_8) : terminal.out();
          status = runBuiltin(builtin.get(), cmd, new IoStreams(in != null ? in : terminal.in(), out, terminal.err()));
          out.flush();

          if (in != null) in.close();
          if (sink != null) sink.close();

          lastProcess = null;
          upstream = last ? null
              : buffer != null ? new ByteArrayInputStream(buffer.toByteArray())
                  : InputStream.nullInputStream();

        } else {
          // ---- external program: a separate OS process ---- //
          Process p = launcher.start(cmd.args(), ctx,
              in != null ? PIPE : INHERIT, // PIPE = we feed/collect the bytes ourselves
              sink != null || !last ? PIPE : INHERIT); // INHERIT = it talks to the terminal directly

          started.add(p);
          running.add(p);

          if (in != null) pumps.add(pump(in, p.getOutputStream()));
          if (sink != null) pumps.add(pump(p.getInputStream(), sink));

          lastProcess = p;
          upstream = last ? null
              : sink != null ? InputStream.nullInputStream()
                  : p.getInputStream();
        }
      }

      // ---- wait for everything, like a real shell ---- //
      for (Process p : started) if (p == lastProcess) status = p.waitFor();
      for (Thread t : pumps) t.join(); // make sure all bytes reached the file
      if (status != 0) terminal.out().printf("[exit %s]%n", status);
      return status;

    } catch (CommandNotFoundException e) {
      abort(started, opened);
      terminal.err().println("jshellx: command not found: " + e.command());
      return 127; // the conventional "not found" status

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      abort(started, opened);
      terminal.err().printf("jshellx: command execution interrupted%n%s", e.getMessage());
      return 130;
    } catch (IOException e) {
      abort(started, opened);
      terminal.err().printf("jshellx: %s%n%s", describe(e), e.getMessage());
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

  private int runBuiltin(BuiltinCommand builtin, Command cmd, IoStreams io) {
    try {
      return builtin.execute(cmd.args().subList(1, cmd.args().size()), ctx, io);
    } catch (RuntimeException e) {
      io.err().println(cmd.name() + ": " + e);
      return 1;
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

  private void abort(List<Process> started, List<Closeable> opened) {
    started.forEach(Process::destroy);
    for (Closeable c : opened) {
      try {
        c.close();
      } catch (IOException ignored) {
      }
    }
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
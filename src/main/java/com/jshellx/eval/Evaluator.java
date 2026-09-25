package com.jshellx.eval;

import static java.lang.ProcessBuilder.Redirect.INHERIT;
import static java.lang.ProcessBuilder.Redirect.PIPE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FilterOutputStream;
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

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.BuiltinRegistry;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;
import com.jshellx.exec.ProcessLauncher;
import com.jshellx.exec.ProcessLauncher.CommandNotFoundException;
import com.jshellx.parser.Command;
import com.jshellx.parser.CommandList;
import com.jshellx.parser.CommandParser;
import com.jshellx.parser.ParseException;
import com.jshellx.parser.Pipeline;

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

  /** Runs one input line: pipelines joined by ';', '&&' and '||'. @return the last status */
  public int evaluate(String line) {
    CommandList list;
    try {
      list = parser.splitList(line);
    } catch (ParseException e) {
      terminal.err().println("jshellx: " + e.getMessage());
      return 2;
    }
    int status = ctx.lastExitCode();
    for (CommandList.Segment segment : list.segments()) {
      boolean run = switch (segment.connector()) {
        case ALWAYS -> true;
        case IF_SUCCESS -> status == 0;
        case IF_FAILURE -> status != 0;
      };
      if (!run) continue; // a skipped segment keeps the previous status
      status = evaluatePipeline(segment.source());
      ctx.setLastExitCode(status); // the next segment's $? must see this
      if (!ctx.isRunning() || status == 130) break; // `exit`, or Ctrl-C stops the rest of the line
    }
    return status;
  }

  /** Parses and runs one input line. @return its exit status */
  private int evaluatePipeline(String source) {
    try {
      Optional<Pipeline> pipeline = parser.parse(source);
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
    List<Command> stages = pipeline.stages();
    List<Closeable> opened = new ArrayList<>(); // everything we must close afterwards
    List<Thread> pumps = new ArrayList<>();
    List<Process> started = new ArrayList<>();

    Process lastProcess = null;
    InputStream upstream = null; // data flowing into the next stage; null = terminal stdin

    int status = 0;

    try {
      for (int i = 0; i < stages.size(); i++) {
        Command cmd = stages.get(i);
        boolean last = i == stages.size() - 1;

        // ---- resolve this stage's stdin / stdout ---- //
        InputStream in = upstream;
        if (cmd.stdinFile() != null) {
          if (in != null) in.close();
          in = Files.newInputStream(ctx.resolve(cmd.stdinFile()));
          opened.add(in);
        }
        // ---- resolve this stage's stdout / stderr ---- //
        OutputStream sink = open(cmd.stdout(), opened); // null = not a file
        OutputStream errSink = cmd.stderr().equals(cmd.stdout()) ? sink : open(cmd.stderr(), opened);
        boolean outDefault = cmd.stdout() instanceof Command.Default;
        boolean outToErr = cmd.stdout() instanceof Command.ToStderr; // '>&2': the terminal's stderr
        boolean errToOldOut = cmd.stderr() instanceof Command.ToStdout; // '2>&1' before any '>'
        // One stream for both: '&> f', '> f 2>&1', or '2>&1' with stdout left alone.
        boolean merged = (errSink != null && errSink == sink) || (errToOldOut && outDefault);
        // '2>&1 > f': stderr takes the place stdout had (terminal or pipe), stdout goes to the file.
        boolean errReplacesOut = errToOldOut && !outDefault;
        boolean outPiped = outDefault && !last; // stdout feeds the next stage
        boolean errPiped = errReplacesOut && !last; // only stderr feeds the next stage

        // ---- built-in: runs inside this JVM ---- //
        Optional<BuiltinCommand> builtin = builtins.find(cmd.name());
        if (builtin.isPresent()) {
          // Whatever flows on goes to an in-memory buffer, otherwise to the terminal or a file.
          ByteArrayOutputStream buffer = outPiped || errPiped ? new ByteArrayOutputStream() : null;
          PrintStream defaultOut = buffer != null ? new PrintStream(buffer, false, StandardCharsets.UTF_8) : terminal.out();
          PrintStream out = sink != null ? new PrintStream(sink, false, StandardCharsets.UTF_8)
              : outToErr ? terminal.err() : defaultOut;
          PrintStream err = merged ? out
              : errSink != null ? new PrintStream(errSink, false, StandardCharsets.UTF_8)
                  : errReplacesOut ? defaultOut : terminal.err();
          status = runBuiltin(builtin.get(), cmd, new IoStreams(in != null ? in : terminal.in(), out, err));
          out.flush();
          err.flush();
          defaultOut.flush();

          if (in != null) in.close();
          if (sink != null) sink.close();
          if (errSink != null && errSink != sink) errSink.close();

          lastProcess = null;
          upstream = last ? null
              : buffer != null ? new ByteArrayInputStream(buffer.toByteArray())
                  : InputStream.nullInputStream();

        } else {
          // ---- external program: a separate OS process ---- //
          // PIPE = we feed/collect the bytes ourselves, INHERIT = it talks to the terminal directly
          boolean errCaptured = !merged && (errSink != null || errReplacesOut);
          Process p = launcher.start(cmd.args(), ctx,
              in != null ? PIPE : INHERIT,
              sink != null || outToErr || outPiped ? PIPE : INHERIT,
              errCaptured ? PIPE : INHERIT,
              merged);

          started.add(p);
          running.add(p);

          if (in != null) pumps.add(pump(in, p.getOutputStream()));
          if (sink != null) pumps.add(pump(p.getInputStream(), sink));
          else if (outToErr) pumps.add(pump(p.getInputStream(), keepOpen(terminal.err())));
          if (errSink != null && !merged) pumps.add(pump(p.getErrorStream(), errSink));
          else if (errReplacesOut && last) pumps.add(pump(p.getErrorStream(), keepOpen(terminal.out())));

          lastProcess = p;
          upstream = last ? null
              : outPiped ? p.getInputStream()
                  : errPiped ? p.getErrorStream() : InputStream.nullInputStream();
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
          // best effort: we don't want to mask the original error
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

  /** Opens the file of a {@code ToFile} target; any other target is not a file and gives null. */
  private OutputStream open(Command.Output target, List<Closeable> opened) throws IOException {
    if (!(target instanceof Command.ToFile file)) return null;
    OutputStream stream = Files.newOutputStream(ctx.resolve(file.path()),
        StandardOpenOption.CREATE, StandardOpenOption.WRITE,
        file.append() ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
    opened.add(stream);
    return stream;
  }

  /** Lets {@link #pump} write to a terminal stream without closing it at the end. */
  private static OutputStream keepOpen(OutputStream out) {
    return new FilterOutputStream(out) {
      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
      }

      @Override
      public void close() throws IOException {
        flush();
      }
    };
  }

  private void abort(List<Process> started, List<Closeable> opened) {
    started.forEach(Process::destroy);
    for (Closeable c : opened) {
      try {
        c.close();
      } catch (IOException ignored) {
        // best effort: we don't want to mask the original error
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

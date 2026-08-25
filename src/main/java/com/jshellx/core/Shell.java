package com.jshellx.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;

import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;
import com.jshellx.eval.Evaluator;
import com.jshellx.ui.PromptRenderer;

import static com.jshellx.ui.Ansi.BOLD;
import static com.jshellx.ui.Ansi.CYAN;
import static com.jshellx.ui.Ansi.style;

public final class Shell {

  private static final String VERSION = "1.0.0";

  private final ShellContext ctx;
  private final Evaluator evaluator;
  private final PromptRenderer prompt;
  private final BufferedReader in;
  private final IoStreams io;

  public Shell(ShellContext ctx, Evaluator evaluator,
      PromptRenderer prompt, BufferedReader in, IoStreams io) {
    this.ctx = ctx;
    this.evaluator = evaluator;
    this.prompt = prompt;
    this.in = in;
    this.io = io;
  }

  /** Runs until {@code exit} or Ctrl-D. @return the process exit code */
  public int run() throws IOException {
    Path historyFile = Path.of(ctx.cwd().toString(), ".jshellx_history");
    ctx.history().load(historyFile);
    io.out().printf("%s  -  type 'help' for commands, Ctrl-D to quit%n", style("JshellX " + VERSION, CYAN, BOLD));

    try {
      while (ctx.isRunning()) {
        io.out().printf(prompt.render());
        io.out().flush();

        String line = in.readLine();
        if (line == null) break;
        if (line.isBlank()) continue;

        ctx.history().add(line);
        ctx.setLastExitCode(evaluator.evaluate(line));
      }
    } finally {
      ctx.history().save(historyFile);
    }

    return ctx.exitCode();
  }
}

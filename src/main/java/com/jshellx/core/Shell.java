package com.jshellx.core;

import java.io.BufferedReader;
import java.io.IOException;

import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;
import com.jshellx.eval.Evaluator;

public final class Shell {

  private final ShellContext ctx;
  private final Evaluator evaluator;
  private final BufferedReader in;
  private final IoStreams io;

  public Shell(ShellContext ctx, Evaluator evaluator, BufferedReader in, IoStreams io) {
    this.ctx = ctx;
    this.evaluator = evaluator;
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
      ctx.setLastExitCode(evaluator.evaluate(line));
    }

    return ctx.exitCode();
  }
}

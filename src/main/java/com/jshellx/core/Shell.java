package com.jshellx.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.BuiltinRegistry;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;
import com.jshellx.parser.ParseException;
import com.jshellx.parser.Token;
import com.jshellx.parser.Tokenizer;

public final class Shell {

  private ShellContext ctx;
  private BuiltinRegistry builtins;
  private BufferedReader in;
  private IoStreams io;

  private final Tokenizer tokenizer = new Tokenizer(!ShellContext.isWindows());

  public Shell(ShellContext ctx, BuiltinRegistry builtins,
      BufferedReader in, IoStreams io) {
    this.ctx = ctx;
    this.builtins = builtins;
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
      if (line.isBlank()) continue;

      List<String> tokens;
      try {
        tokens = tokenizer.tokenize(line).stream().map(Token::text).toList();
      } catch (ParseException e) {
        io.err().println("jshellx: " + e.getMessage());
        ctx.setLastExitCode(2);
        continue;
      }
      if (tokens.isEmpty()) continue;
      ctx.setLastExitCode(execute(tokens));
    }

    return ctx.exitCode();
  }

  private int execute(List<String> tokens) {
    Optional<BuiltinCommand> builtin = builtins.find(tokens.get(0));
    if (builtin.isPresent())
      return builtin.get().execute(tokens.subList(0, tokens.size()), ctx, io);

    try {
      Process process = new ProcessBuilder(tokens).directory(ctx.cwd().toFile()).inheritIO().start();
      int exitCode = process.waitFor();
      if (exitCode != 0) io.out().printf("[exit %s]%n", exitCode);
      return exitCode;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      io.err().printf("jshellx: command execution interrupted%n%s", e.getMessage());
      return 130;
    } catch (IOException e) {
      io.out().printf("jshellx: command not found: %s%n", tokens.get(0));
      return 127; // the conventional "not found" status
    }

  }
}

package com.jshellx.parser;

import com.jshellx.context.ShellContext;
import com.jshellx.parser.Token.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a raw input line into a {@link Pipeline}: tokenize, then build stages.
 */
public final class CommandParser {

  private final ShellContext ctx;
  private final Tokenizer tokenizer;

  public CommandParser(ShellContext ctx) {
    this.ctx = ctx;
    this.tokenizer = new Tokenizer(this::lookup, !ShellContext.isWindows());
  }

  /** Variable lookup used by the tokenizer. */
  private String lookup(String name) {
    if (name.equals("?")) return Integer.toString(ctx.lastExitCode());
    String value = ctx.getEnv(name);
    return (value == null && name.equals("HOME")) ? ctx.home() : value;
  }

  /** @return the pipeline, or empty for a blank line */
  public Optional<Pipeline> parse(String line) throws ParseException {
    List<Token> tokens = tokenizer.tokenize(line);
    if (tokens.isEmpty()) return Optional.empty();

    List<Command> stages = new ArrayList<>();
    List<String> args = new ArrayList<>();
    String stdin = null;
    String stdout = null;
    boolean append = false;

    for (int i = 0; i < tokens.size(); i++) {
      Token t = tokens.get(i);
      switch (t.type()) {
        case WORD -> args.add(t.text());
        case REDIRECT_IN, REDIRECT_OUT, REDIRECT_APPEND -> {
          // a redirection operator must be followed by a file name
          if (i + 1 >= tokens.size() || tokens.get(i + 1).type() != Type.WORD) {
            throw new ParseException("syntax error: expected a file name after '" + t.text() + "'");
          }
          String target = tokens.get(++i).text();
          switch (t.type()) {
            case REDIRECT_IN -> stdin = target;
            case REDIRECT_OUT -> {
              stdout = target;
              append = false;
            }
            default -> {
              stdout = target;
              append = true;
            }
          }
        }
        case PIPE -> { // close the current stage, start a new one
          stages.add(finishStage(args, stdin, stdout, append));
          args = new ArrayList<>();
          stdin = null;
          stdout = null;
          append = false;
        }
      }
    }
    stages.add(finishStage(args, stdin, stdout, append));
    return Optional.of(new Pipeline(stages));
  }

  private static Command finishStage(List<String> args, String in, String out, boolean append)
      throws ParseException {
    if (args.isEmpty()) throw new ParseException("syntax error: missing command");
    return new Command(args, in, out, append);
  }
}
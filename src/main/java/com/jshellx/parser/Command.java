package com.jshellx.parser;

import java.util.List;

/**
 * One pipeline stage: argv plus optional redirections.
 *
 * @param args      command name followed by its arguments (never empty)
 * @param stdinFile file for {@code <}, or null
 * @param stdout    where standard output goes ({@code >}, {@code >>}, {@code &>}, {@code >&2})
 * @param stderr    where standard error goes ({@code 2>}, {@code 2>>}, {@code &>}, {@code 2>&1})
 */
public record Command(List<String> args, String stdinFile, Output stdout, Output stderr) {

  // @formatter:off
  /** The destination of an output stream, after all redirections were applied left to right. */
  public sealed interface Output permits Default, ToFile, ToStdout, ToStderr {}

  /** The stream's normal destination: the terminal, or the next pipeline stage for stdout. */
  public record Default() implements Output {}
  public record ToFile(String path, boolean append) implements Output {}

  /** {@code 2>&1} while stdout still had its default destination. */
  public record ToStdout() implements Output {}

  /** {@code >&2} while stderr still had its default destination. */
  public record ToStderr() implements Output {}
  // @formatter:on

  public static final Output DEFAULT = new Default();

  public Command {
    args = List.copyOf(args);
    if (args.isEmpty()) throw new IllegalArgumentException("a command needs at least a name");
  }

  public String name() {
    return args.get(0);
  }
}

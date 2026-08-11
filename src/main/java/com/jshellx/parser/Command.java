package com.jshellx.parser;

import java.util.List;

/**
 * One pipeline stage: argv plus optional file redirections.
 *
 * @param args       command name followed by its arguments (never empty)
 * @param stdinFile  file for {@code <}, or null
 * @param stdoutFile file for {@code >} / {@code >>}, or null
 * @param append     true for {@code >>}
 */
public record Command(List<String> args, String stdinFile, String stdoutFile, boolean append) {
  public Command {
    args = List.copyOf(args);
    if (args.isEmpty()) throw new IllegalArgumentException("a command needs at least a name");
  }

  public String name() {
    return args.get(0);
  }
}
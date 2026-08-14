package com.jshellx.builtin.commands;

import java.util.List;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;

public final class EchoCommand implements BuiltinCommand {
  // @formatter:off
  @Override public String name()        { return "echo"; }
  @Override public String description() { return "Print arguments to standard output"; }
  @Override public String usage()       { return "echo [text ...]"; }
  // @formatter:on

  @Override
  public int execute(List<String> args, ShellContext ctx, IoStreams io) {
    io.out().println(String.join(" ", args.subList(0, args.size()))); // args includes the command name at index 0
    return 0;
  }
}

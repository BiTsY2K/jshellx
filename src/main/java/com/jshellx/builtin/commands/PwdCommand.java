package com.jshellx.builtin.commands;

import java.util.List;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;

public final class PwdCommand implements BuiltinCommand {
  // @formatter:off
  @Override public String name()        { return "pwd"; }
  @Override public String description() { return "Print the working directory"; }
  @Override public String usage()       { return "pwd"; }
  // @formatter:on

  @Override
  public int execute(List<String> args, ShellContext ctx, IoStreams io) {
    io.out().println(ctx.cwd());
    return 0;
  }
}

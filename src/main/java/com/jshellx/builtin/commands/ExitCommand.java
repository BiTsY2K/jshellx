package com.jshellx.builtin.commands;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;

import java.util.List;

public final class ExitCommand implements BuiltinCommand {

  // @formatter:off
  @Override public String name() { return "exit"; }
  @Override public String description() { return "Leave the shell (optionally with an exit code)"; }
  @Override public String usage() { return "exit [code]"; }
  // @formatter:on

  @Override
  public int execute(List<String> args, ShellContext ctx, IoStreams io) {
    int code = ctx.lastExitCode();
    if (!args.isEmpty()) {
      try {
        code = Integer.parseInt(args.get(0));
      } catch (NumberFormatException e) {
        io.err().println("exit: numeric argument required: " + args.get(0));
        code = 2;
      }
    }
    ctx.requestExit(code);
    return code;
  }
}
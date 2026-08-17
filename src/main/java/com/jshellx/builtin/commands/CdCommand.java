
package com.jshellx.builtin.commands;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;
import java.io.IOException;
import java.util.List;

public final class CdCommand implements BuiltinCommand {
  // @formatter:off
    @Override public String name() { return "cd"; }
    @Override public String description() { return "Change the working directory"; }
    @Override public String usage() { return "cd [directory]"; }
  // @formatter:on

  @Override
  public int execute(List<String> args, ShellContext ctx, IoStreams io) {
    if (args.size() > 1) {
      io.err().println("cd: too many arguments");
      return 1;
    }
    boolean echoTarget = args.isEmpty() ? false : (args.get(0).equals("-") ? true : false);
    String target = args.isEmpty() ? ctx.home() : (args.get(0).equals("-") ? ctx.getEnv("OLDPWD") : args.get(0));
    if (target == null) {
      io.err().println("cd: OLDPWD not set");
      return 1;
    }

    try {
      ctx.changeDirectory(target);
      if (echoTarget) io.out().println(ctx.cwd());
      return 0;
    } catch (IOException e) {
      io.err().println("cd: " + e.getMessage());
      return 1;
    }
  }
}
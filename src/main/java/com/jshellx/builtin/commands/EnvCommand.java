package com.jshellx.builtin.commands;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;

import java.util.List;

public final class EnvCommand implements BuiltinCommand {

  // @formatter:off
  @Override public String name()        { return "env"; }
  @Override public String description() { return "List, set (NAME=VALUE) or unset (-u NAME) variables"; }
  @Override public String usage()       { return "env [NAME=VALUE ...] | env -u NAME ... | env NAME"; }
  // @formatter:on

  @Override
  public int execute(List<String> args, ShellContext ctx, IoStreams io) {
    if (args.isEmpty()) {
      ctx.environment().forEach((k, v) -> io.out().println(k + "=" + v));
      return 0;
    }
    if (args.get(0).equals("-u")) {
      args.subList(1, args.size()).forEach(ctx::unsetEnv);
      return 0;
    }
    int status = 0;
    for (String arg : args) {
      int eq = arg.indexOf('=');
      if (eq > 0) {
        ctx.setEnv(arg.substring(0, eq), arg.substring(eq + 1));
      } else if (eq < 0) {
        String value = ctx.getEnv(arg);
        if (value == null) status = 1;
        else io.out().println(value);
      } else {
        io.err().println("env: invalid name: " + arg);
        status = 1;
      }
    }
    return status;
  }
}
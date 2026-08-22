package com.jshellx.builtin.commands;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;

import java.util.List;

public final class AliasCommand implements BuiltinCommand {
  // @formatter:off
  @Override public String name() { return "alias"; }
  @Override public String description() { return "Define or show aliases (-r removes)"; }
  @Override public String usage() { return "alias [name[=value] ...] | alias -r name ..."; }
  // @formatter:on

  @Override
  public int execute(List<String> args, ShellContext ctx, IoStreams io) {
    if (args.isEmpty()) {
      ctx.aliases().forEach((k, v) -> io.out().println(format(k, v)));
      return 0;
    }
    int status = 0;
    if (args.get(0).equals("-r")) {
      for (String name : args.subList(1, args.size())) {
        if (!ctx.removeAlias(name)) {
          io.err().println("alias: no such alias: " + name);
          status = 1;
        }
      }
      return status;
    }
    for (String arg : args) {
      int eq = arg.indexOf('=');
      if (eq < 0) {
        String value = ctx.aliases().get(arg);
        if (value == null) {
          io.err().println("alias: no such alias: " + arg);
          status = 1;
        } else {
          io.out().println(format(arg, value));
        }
      } else if (eq == 0) {
        io.err().println("alias: invalid alias name: " + arg);
        status = 1;
      } else {
        ctx.setAlias(arg.substring(0, eq), arg.substring(eq + 1));
      }
    }
    return status;
  }

  private static String format(String name, String value) {
    return "alias " + name + "='" + value.replace("'", "'\\''") + "'";
  }
}
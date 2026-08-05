package com.jshellx.builtin;

import java.util.List;

import com.jshellx.context.ShellContext;

public interface BuiltinCommand {

  // @formatter:off
  String name();
  String description();
  String usage();
  // @formatter:on

  /**
   * @param args arguments without the command name;
   * @return exit status (0 = success)
   */
  int execute(List<String> args, ShellContext ctx, IoStreams io);
}

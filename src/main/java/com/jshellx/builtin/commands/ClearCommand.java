
package com.jshellx.builtin.commands;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;

import java.util.List;

public final class ClearCommand implements BuiltinCommand {

  // @formatter:off
  @Override public String name() { return "clear"; }
  @Override public String description() { return "Clear the terminal screen"; }
  @Override public String usage() { return "clear"; }
  // @formatter:on

  @Override
  public int execute(List<String> args, ShellContext ctx, IoStreams io) {
    // ESC[H = cursor home, ESC[2J = erase screen, ESC[3J = erase scrollback
    io.out().print("\u001B[H\u001B[2J\u001B[3J");
    io.out().flush();
    return 0;
  }
}
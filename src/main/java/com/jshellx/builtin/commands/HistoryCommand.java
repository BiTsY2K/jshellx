package com.jshellx.builtin.commands;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.CommandHistory;
import com.jshellx.context.ShellContext;

import java.util.List;

public final class HistoryCommand implements BuiltinCommand {
  // @formatter:off
  @Override public String name() { return "history"; }
  @Override public String description() { return "Show command history (-c clears it)"; }
  @Override public String usage() { return "history [-c | N]"; }
  // @formatter:on

  @Override
  public int execute(List<String> args, ShellContext ctx, IoStreams io) {
    CommandHistory history = ctx.history();
    if (!args.isEmpty() && args.get(0).equals("-c")) {
      history.clear();
      return 0;
    }

    int limit = Integer.MAX_VALUE;
    if (!args.isEmpty()) {
      try {
        limit = Integer.parseInt(args.get(0));
      } catch (NumberFormatException e) {
        io.err().println("history: numeric argument required: " + args.get(0));
        return 2;
      }
    }

    int from = Math.max(0, history.size() - Math.max(limit, 0));
    for (int i = from; i < history.size(); i++) {
      io.out().printf("%5d  %s%n", i + 1, history.get(i));
    }
    return 0;
  }
}
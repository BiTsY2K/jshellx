package com.jshellx.builtin.commands;

import com.jshellx.builtin.BuiltinCommand;
import com.jshellx.builtin.BuiltinRegistry;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;

import java.util.List;
import java.util.Optional;

public final class HelpCommand implements BuiltinCommand {

  private static final String FOOTER = """

      Anything else is run as a system command (git, docker, python, ls, ...).
      Operators:  a | b   pipe      a > f   overwrite     a >> f   append     a < f   input
      Variables:  $NAME  ${NAME}  $?  (last exit status)   ~ (home directory)
      Prompt:     env JSHELLX_THEME=default|minimal|classic
      Keys:       Tab = complete, Up/Down = history, Ctrl-R = search, Ctrl-D = exit
      """;

  private final BuiltinRegistry registry;

  public HelpCommand(BuiltinRegistry registry) {
    this.registry = registry;
  }

  // @formatter:off
  @Override public String name() { return "help"; }
  @Override public String description() { return "Show this help or details for one command"; }
  @Override public String usage() { return "help [command]"; }
  // @formatter:on

  @Override
  public int execute(List<String> args, ShellContext ctx, IoStreams io) {
    if (args.isEmpty()) {
      io.out().println("JshellX built-in commands:");
      for (BuiltinCommand c : registry.all()) {
        io.out().printf("  %-9s %s%n", c.name(), c.description());
      }
      io.out().print(FOOTER);
      return 0;
    }
    Optional<BuiltinCommand> command = registry.find(args.get(0));
    if (command.isEmpty()) {
      io.err().println("help: no built-in named '" + args.get(0) + "'");
      return 1;
    }
    io.out().println("usage: " + command.get().usage());
    io.out().println(command.get().description());
    return 0;
  }
}
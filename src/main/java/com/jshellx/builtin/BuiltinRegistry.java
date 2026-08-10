package com.jshellx.builtin;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.jshellx.builtin.commands.EchoCommand;
import com.jshellx.builtin.commands.EnvCommand;
import com.jshellx.builtin.commands.PwdCommand;

public final class BuiltinRegistry {

  private final Map<String, BuiltinCommand> commands = new TreeMap<>();

  public BuiltinRegistry register(BuiltinCommand command) {
    commands.put(command.name(), command);
    return this;
  }

  public Optional<BuiltinCommand> find(String name) {
    return Optional.ofNullable(commands.get(name));
  }

  /** The standard JshellX command set. */
  public static BuiltinRegistry withDefaults() {
    BuiltinRegistry registry = new BuiltinRegistry();
    registry.register(new EchoCommand());
    registry.register(new PwdCommand());
    registry.register(new EnvCommand());
    return registry;
  }
}

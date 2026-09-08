package com.jshellx.builtin;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.jshellx.builtin.commands.AliasCommand;
import com.jshellx.builtin.commands.CdCommand;
import com.jshellx.builtin.commands.ClearCommand;
import com.jshellx.builtin.commands.EchoCommand;
import com.jshellx.builtin.commands.EnvCommand;
import com.jshellx.builtin.commands.ExitCommand;
import com.jshellx.builtin.commands.HelpCommand;
import com.jshellx.builtin.commands.HistoryCommand;
import com.jshellx.builtin.commands.PwdCommand;

public final class BuiltinRegistry {

  private final Map<String, BuiltinCommand> commands = new TreeMap<>();

  public BuiltinRegistry register(BuiltinCommand command) {
    commands.put(command.name(), command);
    return this;
  }

  public Collection<BuiltinCommand> all() {
    return Collections.unmodifiableCollection(commands.values());
  }

  public Optional<BuiltinCommand> find(String name) {
    return Optional.ofNullable(commands.get(name));
  }

  public Set<String> names() {
    return Collections.unmodifiableSet(commands.keySet());
  }

  /** The standard JshellX command set. */
  public static BuiltinRegistry withDefaults() {
    BuiltinRegistry registry = new BuiltinRegistry();
    registry.register(new AliasCommand());
    registry.register(new CdCommand());
    registry.register(new ClearCommand());
    registry.register(new EchoCommand());
    registry.register(new EnvCommand());
    registry.register(new ExitCommand());
    registry.register(new HelpCommand(registry));
    registry.register(new HistoryCommand());
    registry.register(new PwdCommand());
    return registry;
  }
}

package com.jshellx.ui;

import com.jshellx.builtin.BuiltinRegistry;
import com.jshellx.context.ShellContext;
import com.jshellx.exec.ExecutableFinder;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Command names (builtins, aliases, PATH executables) in command position, file names
 * elsewhere (directories only for the argument of {@code cd}).
 */
public final class ShellCompleter implements Completer {

  private final ShellContext ctx;
  private final BuiltinRegistry builtins;
  private final ExecutableFinder finder;

  public ShellCompleter(ShellContext ctx, BuiltinRegistry builtins, ExecutableFinder finder) {
    this.ctx = ctx;
    this.builtins = builtins;
    this.finder = finder;
  }

  @Override
  public Completion complete(String line, int cursor) {
    String before = line.substring(0, cursor);
    int start = before.length();
    while (start > 0 && !isBoundary(before, start - 1)) start--;

    String prefix = before.substring(start);
    String head = before.substring(0, start).stripTrailing();
    boolean commandPosition = head.isEmpty() || head.endsWith("|");

    List<String> candidates = (commandPosition && prefix.indexOf('/') < 0)
        ? commands(prefix)
        : paths(prefix, isCdArgument(head));
    return new Completion(start, candidates);
  }

  /** Does the character at {@code index} end a word? (An escaped space, "\ ", does not.) */
  private static boolean isBoundary(String text, int index) {
    char c = text.charAt(index);
    if (c == ' ' && index > 0 && text.charAt(index - 1) == '\\') return false;
    return c == ' ' || c == '\t' || c == '|' || c == '<' || c == '>';
  }

  private static boolean isCdArgument(String head) {
    return head.substring(head.lastIndexOf('|') + 1).strip().equals("cd");
  }

  private List<String> commands(String prefix) {
    Set<String> names = new TreeSet<>(builtins.names());
    names.addAll(ctx.aliases().keySet());
    names.addAll(finder.executableNames(ctx));
    return names.stream().filter(n -> n.startsWith(prefix)).toList();
  }

  private List<String> paths(String prefix, boolean dirsOnly) {
    int slash = prefix.lastIndexOf('/');
    String typedDir = slash >= 0 ? prefix.substring(0, slash + 1) : "";
    String namePart = unescape(prefix.substring(slash + 1));

    Path dir;
    try {
      String real = unescape(typedDir);
      if (real.startsWith("~")) real = ctx.home() + real.substring(1);
      dir = real.isEmpty() ? ctx.cwd() : ctx.resolve(real);
    } catch (InvalidPathException e) {
      return List.of();
    }
    if (!Files.isDirectory(dir)) return List.of();

    Set<String> found = new TreeSet<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path p : stream) {
        String name = p.getFileName().toString();
        if (!name.startsWith(namePart)) continue;
        if (name.startsWith(".") && !namePart.startsWith(".")) continue; // hide dotfiles
        boolean isDir = Files.isDirectory(p);
        if (dirsOnly && !isDir) continue;
        found.add(typedDir + escape(name) + (isDir ? "/" : ""));
      }
    } catch (IOException ignored) {
      // unreadable directory: offer nothing
    }
    return new ArrayList<>(found);
  }

  private static String escape(String name) {
    return ShellContext.isWindows() ? name : name.replace(" ", "\\ ");
  }

  private static String unescape(String text) {
    return text.replace("\\ ", " ");
  }
}
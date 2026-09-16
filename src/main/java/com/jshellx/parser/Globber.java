package com.jshellx.parser;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Expands a word holding unquoted wildcards ({@code *}, {@code ?}, {@code [a-z]}) into the
 * matching paths, sorted. Works one path component at a time, so {@code src/*}{@code /*.java}
 * only lists the directories it has to. A word that matches nothing is returned unchanged
 * (bash's default), and names starting with '.' only match a pattern that starts with '.'.
 */
final class Globber {

  private Globber() {}

  static List<String> expand(Token word, Path cwd, boolean windows) {
    String text = word.text();
    Set<Integer> wild = word.globChars();

    // Split into components, remembering which ones contain a wildcard.
    List<int[]> parts = new ArrayList<>(); // {start, end} of each component in `text`
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      boolean end = i == text.length();
      if (end || text.charAt(i) == '/' || (windows && text.charAt(i) == '\\')) {
        parts.add(new int[] { start, i });
        start = i + 1;
      }
    }

    // Each entry is a path prefix exactly as the user would have typed it.
    List<String> prefixes = List.of("");
    boolean matched = false;
    for (int p = 0; p < parts.size(); p++) {
      int from = parts.get(p)[0];
      int to = parts.get(p)[1];
      boolean last = p == parts.size() - 1;
      String separator = last ? "" : text.substring(to, to + 1);
      String component = text.substring(from, to);

      if (!hasWildcard(wild, from, to)) {
        List<String> next = new ArrayList<>();
        for (String prefix : prefixes) next.add(prefix + component + separator);
        prefixes = next;
        continue;
      }

      PathMatcher matcher;
      try {
        matcher = FileSystems.getDefault().getPathMatcher("glob:" + toJavaGlob(text, wild, from, to));
      } catch (IllegalArgumentException e) {
        return List.of(text); // PatternSyntaxException, e.g. an unclosed '[': treat the word literally
      }
      boolean showHidden = component.startsWith(".");
      List<String> next = new ArrayList<>();
      for (String prefix : prefixes) {
        Path dir = cwd.resolve(prefix.isEmpty() ? "." : prefix);
        if (!Files.isDirectory(dir)) continue;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
          for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (name.startsWith(".") && !showHidden) continue;
            if (!matcher.matches(Path.of(name))) continue;
            if (!last && !Files.isDirectory(entry)) continue; // 'a*/b' needs a directory
            next.add(prefix + name + separator);
          }
        } catch (IOException e) {
          // unreadable directory: it simply contributes no matches, as in bash
        }
      }
      prefixes = next;
      matched = true;
    }

    // A literal tail after a wildcard ('src/*/Main.java') must actually exist.
    List<String> result = new ArrayList<>();
    for (String path : prefixes) {
      if (Files.exists(cwd.resolve(path.isEmpty() ? "." : path))) result.add(path);
    }
    if (!matched || result.isEmpty()) return List.of(text);
    result.sort(null);
    return result;
  }

  private static boolean hasWildcard(Set<Integer> wild, int from, int to) {
    for (int i = from; i < to; i++) if (wild.contains(i)) return true;
    return false;
  }

  /** Escapes everything Java's glob syntax treats specially, except the unquoted wildcards. */
  private static String toJavaGlob(String text, Set<Integer> wild, int from, int to) {
    StringBuilder glob = new StringBuilder();
    for (int i = from; i < to; i++) {
      char c = text.charAt(i);
      if ("*?[]{}\\".indexOf(c) >= 0 && !wild.contains(i)) glob.append('\\');
      glob.append(c);
    }
    return glob.toString();
  }
}

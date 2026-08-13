package com.jshellx.exec;

import com.jshellx.context.ShellContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Locates executables using the <em>shell's</em> PATH (not the JVM's), so
 * {@code env PATH=...} takes effect immediately. Honors PATHEXT on Windows.
 */
public final class ExecutableFinder {

  private static final List<String> DEFAULT_PATHEXT = List.of(".COM", ".EXE", ".BAT", ".CMD");

  // Completion cache, invalidated whenever PATH changes.
  private String cachedPath;
  private Set<String> cachedNames = Set.of();

  public Optional<Path> find(String name, ShellContext ctx) {
    try {
      boolean hasSeparator = name.indexOf('/') >= 0
          || (ShellContext.isWindows() && name.indexOf('\\') >= 0);
      if (hasSeparator) return probe(ctx.resolve(name), ctx);

      for (Path dir : pathDirs(ctx)) {
        Optional<Path> hit = probe(dir.resolve(name), ctx);
        if (hit.isPresent()) return hit;
      }
    } catch (InvalidPathException ignored) {
      // fall through: treated as "not found"
    }
    return Optional.empty();
  }

  /** All command names reachable through PATH (for tab completion). */
  public synchronized Set<String> executableNames(ShellContext ctx) {
    String path = String.valueOf(ctx.getEnv("PATH"));
    if (path.equals(cachedPath)) return cachedNames;

    Set<String> names = new TreeSet<>();
    for (Path dir : pathDirs(ctx)) {
      if (!Files.isDirectory(dir)) continue;
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
        for (Path p : stream) {
          String file = p.getFileName().toString();
          if (ShellContext.isWindows()) {
            int dot = file.lastIndexOf('.');
            if (dot > 0 && pathExt(ctx).contains(file.substring(dot).toUpperCase(Locale.ROOT))) {
              names.add(file.substring(0, dot).toLowerCase(Locale.ROOT));
            }
          } else if (Files.isRegularFile(p) && Files.isExecutable(p)) {
            names.add(file);
          }
        }
      } catch (IOException ignored) {
        // unreadable PATH entry: skip
      }
    }
    cachedPath = path;
    cachedNames = names;
    return names;
  }

  private Optional<Path> probe(Path candidate, ShellContext ctx) {
    if (ShellContext.isWindows()) {
      List<String> exts = pathExt(ctx);
      String file = candidate.getFileName().toString().toUpperCase(Locale.ROOT);
      if (exts.stream().anyMatch(file::endsWith) && Files.isRegularFile(candidate)) {
        return Optional.of(candidate);
      }
      for (String ext : exts) {
        Path withExt = candidate.resolveSibling(candidate.getFileName() + ext);
        if (Files.isRegularFile(withExt)) return Optional.of(withExt);
      }
      return Optional.empty();
    }
    return Files.isRegularFile(candidate) && Files.isExecutable(candidate)
        ? Optional.of(candidate)
        : Optional.empty();
  }

  private static List<Path> pathDirs(ShellContext ctx) {
    String path = ctx.getEnv("PATH");
    List<Path> dirs = new ArrayList<>();
    if (path == null) return dirs;
    for (String part : path.split(File.pathSeparator)) {
      if (part.isBlank()) continue;
      try {
        dirs.add(Path.of(part));
      } catch (InvalidPathException ignored) {
        // skip malformed entries
      }
    }
    return dirs;
  }

  private static List<String> pathExt(ShellContext ctx) {
    String raw = ctx.getEnv("PATHEXT");
    if (raw == null || raw.isBlank()) return DEFAULT_PATHEXT;
    return List.of(raw.toUpperCase(Locale.ROOT).split(";"));
  }
}
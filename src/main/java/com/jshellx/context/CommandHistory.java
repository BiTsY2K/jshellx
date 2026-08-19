package com.jshellx.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory command history with simple file persistence
 * (one command per line).
 */
public final class CommandHistory {

  private static final int MAX_SAVED = 2;

  private final List<String> entries = new ArrayList<>();

  /** Adds a command; blank lines and immediate repeats are ignored. */
  public void add(String line) {
    if (line == null || line.isBlank()) return;
    if (!entries.isEmpty() && entries.get(entries.size() - 1).equals(line)) return;
    entries.add(line);
  }

  public void save(Path file) {
    try {
      int from = Math.max(0, entries.size() - MAX_SAVED);
      Files.write(file, entries.subList(from, entries.size()), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
    }
  }

}
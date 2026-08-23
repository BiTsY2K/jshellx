package com.jshellx.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory command history with simple file persistence
 * (one command per line).
 */
public final class CommandHistory {

  private static final int MAX_SAVED = 1000;

  private final List<String> entries = new ArrayList<>();

  // @formatter:off
  public void clear() { entries.clear(); }
  public int size()   { return entries.size(); }
  public String get(int index)  { return entries.get(index); }
  public List<String> entries() { return Collections.unmodifiableList(entries); }
  // @formatter:on

  /** Adds a command; blank lines and immediate repeats are ignored. */
  public void add(String line) {
    if (line == null || line.isBlank()) return;
    if (!entries.isEmpty() && entries.get(entries.size() - 1).equals(line)) return;
    entries.add(line);
  }

  public void load(Path file) {
    try {
      if (Files.isRegularFile(file)) {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
          add(line);
        }
      }
    } catch (IOException ignored) {
      // history is a convenience: start empty if unreadable
    }
  }

  public void save(Path file) {
    try {
      int from = Math.max(0, entries.size() - MAX_SAVED);
      Files.write(file, entries.subList(from, entries.size()), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
    }
  }

}
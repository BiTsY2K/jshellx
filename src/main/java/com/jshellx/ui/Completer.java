package com.jshellx.ui;

import java.util.List;

/** Tab-completion strategy used by {@link LineEditor}. */
public interface Completer {

  /**
   * @param start      index in the line where the word being completed begins
   * @param candidates full replacements for the text from {@code start} to the cursor
   */
  record Completion(int start, List<String> candidates) {
  }

  Completion complete(String line, int cursor);
}
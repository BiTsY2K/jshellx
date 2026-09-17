package com.jshellx.parser;

import java.util.Set;

/**
 * A lexical unit. Quotes and escapes are already resolved in {@code text}; {@code globChars} holds
 * the indexes of the unquoted {@code * ? [ ]} in it, the only ones that act as wildcards.
 */
public record Token(Type type, String text, Set<Integer> globChars) {
  public enum Type {
    WORD, PIPE, REDIRECT_IN, REDIRECT_OUT, REDIRECT_APPEND
  }

  // @formatter:off
  public Token { globChars = Set.copyOf(globChars); }
  public Token(Type type, String text) { this(type, text, Set.of()); }

  public boolean isGlob() { return !globChars.isEmpty(); }
  // @formatter:on

}

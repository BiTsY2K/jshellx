package com.jshellx.parser;

/** A lexical unit. Quotes and escapes are already resolved in {@code text}. */
public record Token(Type type, String text) {
  public enum Type {
    WORD
  }
}
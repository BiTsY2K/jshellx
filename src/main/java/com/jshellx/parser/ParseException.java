package com.jshellx.parser;

/** Thrown for malformed input (unterminated quotes, dangling pipes, ...). */
public class ParseException extends Exception {
  private static final long serialVersionUID = 1L;

  public ParseException(String message) {
    super(message);
  }
}
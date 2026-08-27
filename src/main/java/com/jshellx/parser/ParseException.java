package com.jshellx.parser;

/** Thrown for malformed input (unterminated quotes, dangling pipes, ...). */
public class ParseException extends Exception {
  public ParseException(String message) {
    super(message);
  }
}
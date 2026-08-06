package com.jshellx.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a command line into tokens: whitespace separation, single quotes
 * (literal),
 * double quotes, and backslash escapes.
 *
 * <p>
 * On Windows ({@code posixEscapes == false}) a backslash is kept literally
 * unless it
 * precedes a quote or space, so {@code C:\Users\me} keeps working.
 */
public final class Tokenizer {

  private boolean posixEscapes;

  public Tokenizer(boolean posixEscapes) {
    this.posixEscapes = posixEscapes;
  }

  public List<Token> tokenize(String input) throws ParseException {
    return new Run(input).run();
  }

  /** Per-call scanner state, so Tokenizer itself stays immutable. */
  private final class Run {
    private String s;
    private int n;

    private final List<Token> tokens = new ArrayList<>();
    private final StringBuilder word = new StringBuilder();
    private boolean inWord; // distinguishes "" (an empty word) from "no word yet"
    private int i;

    Run(String s) {
      this.s = s;
      this.n = s.length();
    }

    List<Token> run() throws ParseException {
      while (i < n) {
        char c = s.charAt(i);
        switch (c) {
          case ' ', '\t', '\r', '\n' -> {
            endWord();
            i++;
          }

          case '\'' -> singleQuoted();
          case '"' -> doubleQuoted();
          case '\\' -> backslash();

          default -> {
            word.append(c);
            inWord = true;
            i++;
          }
        }
      }
      endWord();
      return tokens;
    }

    private void endWord() {
      if (inWord) {
        tokens.add(new Token(Token.Type.WORD, word.toString()));
        word.setLength(0);
        inWord = false;
      }
    }

    private void singleQuoted() throws ParseException {
      int end = s.indexOf('\'', i + 1);
      if (end < 0) throw new ParseException("unterminated single quote");
      word.append(s, i + 1, end); // everything inside is literal
      inWord = true;
      i = end + 1;
    }

    private void doubleQuoted() throws ParseException {
      i++; // opening quote
      while (i < n) {
        char c = s.charAt(i);
        if (c == '"') {
          i++;
          inWord = true;
          return;
        }
        if (c == '\\' && i + 1 < n && isDoubleQuoteEscape(s.charAt(i + 1))) {
          word.append(s.charAt(i + 1));
          i += 2;
        } else {
          word.append(c);
          i++;
        }
      }
      throw new ParseException("unterminated double quote");
    }

    private boolean isDoubleQuoteEscape(char c) {
      return c == '"' || (posixEscapes && c == '\\');
    }

    private char peek(int offset) {
      return i + offset < n ? s.charAt(i + offset) : '\0';
    }

    private void backslash() {
      char next = peek(1);
      if (i + 1 < n && isEscapable(next)) {
        word.append(next);
        i += 2;
      } else {
        word.append('\\');
        i++;
      }
      inWord = true;
    }

    private boolean isEscapable(char c) {
      return " \t'\"".indexOf(c) >= 0 || (posixEscapes && c == '\\');
    }
  }
}

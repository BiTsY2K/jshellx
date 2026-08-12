package com.jshellx.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Splits a command line into tokens: whitespace separation, single quotes
 * (literal), double quotes, and backslash escapes.
 *
 * <p>
 * On Windows ({@code posixEscapes == false}) a backslash is kept literally
 * unless it precedes a quote or space, so {@code C:\Users\me} keeps working.
 */
public final class Tokenizer {

  private final Function<String, String> variables;
  private final boolean posixEscapes;

  /**
   * @param variables    resolves a variable name to its value (null = unset);
   *                     receives "?" for the last exit status
   * @param posixEscapes whether a backslash can escape another backslash
   */
  public Tokenizer(Function<String, String> variables, boolean posixEscapes) {
    this.variables = variables;
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
          case '~' -> tilde();

          case '$' -> {
            int before = word.length();
            i = expand(i);
            // An unquoted variable that expands to nothing must not create an empty word.
            if (word.length() > before) inWord = true;
          }

          case '|' -> {
            endWord();
            tokens.add(new Token(Token.Type.PIPE, "|"));
            i++;
          }
          case '<' -> {
            endWord();
            tokens.add(new Token(Token.Type.REDIRECT_IN, "<"));
            i++;
          }

          case '>' -> {
            endWord();
            if (peek(1) == '>') {
              tokens.add(new Token(Token.Type.REDIRECT_APPEND, ">>"));
              i += 2;
            } else {
              tokens.add(new Token(Token.Type.REDIRECT_OUT, ">"));
              i++;
            }
          }

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
        } else if (c == '$') {
          i = expand(i);
        } else {
          word.append(c);
          i++;
        }
      }
      throw new ParseException("unterminated double quote");
    }

    private boolean isDoubleQuoteEscape(char c) {
      return c == '"' || c == '$' || (posixEscapes && c == '\\');
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
      return " \t'\"$|<>~".indexOf(c) >= 0 || (posixEscapes && c == '\\');
    }

    private void tilde() {
      char next = peek(1);
      // A '~' only means "home" at the very start of a word, before '/', or alone.
      boolean eligible = !inWord && (next == '\0' || next == '/' ||
          next == '\\' || next == ' ' || next == '\t');
      if (eligible) {
        String home = variables.apply("HOME");
        word.append(home == null ? System.getProperty("user.home") : home);
      } else {
        word.append('~');
      }
      inWord = true;
      i++;
    }

    /**
     * Expands the variable reference starting at {@code at};
     * returns the next index.
     */
    private int expand(int at) throws ParseException {
      if (at + 1 >= n) {
        word.append('$');
        return at + 1;
      }
      char next = s.charAt(at + 1);
      if (next == '?') {
        append(variables.apply("?"));
        return at + 2;
      }
      if (next == '{') {
        int end = s.indexOf('}', at + 2);
        if (end < 0) throw new ParseException("bad substitution: missing '}'");
        append(variables.apply(s.substring(at + 2, end)));
        return end + 1;
      }
      if (Character.isLetter(next) || next == '_') {
        int j = at + 1;
        while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
        append(variables.apply(s.substring(at + 1, j)));
        return j;
      }
      word.append('$'); // a lone '$' is literal
      return at + 1;
    }

    private void append(String value) {
      if (value != null) word.append(value);
    }
  }
}

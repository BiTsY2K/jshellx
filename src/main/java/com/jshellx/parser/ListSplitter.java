package com.jshellx.parser;

import java.util.ArrayList;
import java.util.List;

import com.jshellx.parser.CommandList.Connector;
import com.jshellx.parser.CommandList.Segment;

/**
 * Splits a line at the unquoted list operators {@code ;}, {@code &&} and {@code ||}. Nothing is
 * expanded here; quoting rules mirror {@link Tokenizer} so {@code 'a;b'} and {@code a\;b} stay intact.
 */
final class ListSplitter {

  private ListSplitter() {}

  static CommandList split(String line, boolean posixEscapes) throws ParseException {
    List<Segment> segments = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    Connector connector = Connector.ALWAYS;
    char quote = 0; // the open quote character, or 0 outside quotes
    int n = line.length();

    for (int i = 0; i < n; i++) {
      char c = line.charAt(i);
      char next = i + 1 < n ? line.charAt(i + 1) : 0;

      if (quote == '\'') { // everything is literal up to the closing quote
        if (c == '\'') quote = 0;
        current.append(c);
      } else if (quote == '"') {
        if (c == '\\' && (next == '"' || (posixEscapes && next == '\\'))) {
          current.append(c).append(next);
          i++;
        } else {
          if (c == '"') quote = 0;
          current.append(c);
        }
      } else if (c == '\'' || c == '"') {
        quote = c;
        current.append(c);
      } else if (c == '\\' && next != 0 && ("'\";&|".indexOf(next) >= 0 || (posixEscapes && next == '\\'))) {
        current.append(c).append(next); // keep the escape: the tokenizer resolves it later
        i++;
      } else if (c == ';' || (c == '&' && next == '&') || (c == '|' && next == '|')) {
        add(segments, connector, current, c == ';' ? ";" : c == '&' ? "&&" : "||");
        connector = c == ';' ? Connector.ALWAYS : c == '&' ? Connector.IF_SUCCESS : Connector.IF_FAILURE;
        current.setLength(0);
        if (c != ';') i++;
      } else if (c == '&') {
        throw new ParseException("syntax error: background jobs ('&') are not supported");
      } else {
        current.append(c);
      }
    }
    if (quote != 0) {
      throw new ParseException(quote == '\'' ? "unterminated single quote" : "unterminated double quote");
    }

    if (!current.toString().isBlank()) {
      segments.add(new Segment(connector, current.toString()));
    } else if (connector != Connector.ALWAYS) { // a trailing ';' is fine, a trailing '&&' / '||' is not
      throw new ParseException(
          "syntax error: missing command after '" + (connector == Connector.IF_SUCCESS ? "&&" : "||") + "'");
    }
    return new CommandList(segments);
  }

  private static void add(List<Segment> segments, Connector connector, StringBuilder text, String op)
      throws ParseException {
    if (text.toString().isBlank()) throw new ParseException("syntax error near '" + op + "'");
    segments.add(new Segment(connector, text.toString()));
  }
}

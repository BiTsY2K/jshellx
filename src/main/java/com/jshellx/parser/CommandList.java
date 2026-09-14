package com.jshellx.parser;

import java.util.List;

/**
 * Pipelines joined by {@code ;}, {@code &&} and {@code ||}. Segments keep their raw text: they are
 * tokenized (and so expanded) only right before they run, so {@code $?} sees the previous status.
 */
public record CommandList(List<Segment> segments) {

  /** How a segment depends on the status of the one before it. */
  public enum Connector {
    ALWAYS, // first segment, or after ';'
    IF_SUCCESS, // after '&&'
    IF_FAILURE // after '||'
  }

  public record Segment(Connector connector, String source) {
  }

  public CommandList {
    segments = List.copyOf(segments);
  }
}

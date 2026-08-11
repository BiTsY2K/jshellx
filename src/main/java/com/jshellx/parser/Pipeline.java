package com.jshellx.parser;

import java.util.List;

/** One or more commands connected with {@code |}. */
public record Pipeline(List<Command> stages) {
  public Pipeline {
    stages = List.copyOf(stages);
  }
}
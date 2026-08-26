package com.jshellx.ui;

import java.util.Locale;

/**
 * Declarative description of a prompt look. Select at runtime with
 * {@code env JSHELLX_THEME=...}.
 */
public record PromptTheme(String name, boolean showUserHost, boolean showGit,
    boolean showExitCode, String symbol) {

  public static final PromptTheme DEFAULT = new PromptTheme("default", true, true, true, ">");
  public static final PromptTheme MINIMAL = new PromptTheme("minimal", false, false, false, "$");
  public static final PromptTheme CLASSIC = new PromptTheme("classic", true, true, false, "$");

  public static PromptTheme byName(String name) {
    return switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
      case "minimal" -> MINIMAL;
      case "classic" -> CLASSIC;
      default -> DEFAULT;
    };
  }
}
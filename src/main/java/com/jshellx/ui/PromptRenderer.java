package com.jshellx.ui;

import static com.jshellx.ui.Ansi.BLUE;
import static com.jshellx.ui.Ansi.BOLD;
import static com.jshellx.ui.Ansi.GREEN;
import static com.jshellx.ui.Ansi.RED;
import static com.jshellx.ui.Ansi.style;

import java.net.InetAddress;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import com.jshellx.context.ShellContext;

/** Builds the colored prompt: {@code user@host ~/path (branch) [status] ❯ }. */
public final class PromptRenderer {
  private final ShellContext ctx;
  private final String user = System.getProperty("user.name", "user");
  private final String host = resolveHostName();

  // @formatter:off
  public PromptRenderer(ShellContext ctx) { this.ctx = ctx; }
  // @formatter:on

  public String render() {
    int status = ctx.lastExitCode();
    StringBuilder sb = new StringBuilder();
    PromptTheme theme = PromptTheme.byName(ctx.getEnv("JSHELLX_THEME"));

    sb.append(style("jshellx", GREEN, BOLD));
    if (theme.showUserHost()) sb.append(style("[" + user + "@" + host + "]", GREEN, BOLD)).append(' ');
    sb.append(style(displayPath(), BLUE, BOLD));

    if (theme.showExitCode() && status != 0) sb.append(' ').append(style("[" + status + "]", RED));
    sb.append(' ').append(style(theme.symbol(), status == 0 ? GREEN : RED, BOLD)).append(' ');
    return sb.toString();
  }

  private static String resolveHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      String fallback = System.getenv("COMPUTERNAME");
      if (fallback == null) fallback = System.getenv("HOSTNAME");
      return fallback == null ? "localhost" : fallback;
    }
  }

  /** Shows the home directory as "~". */
  private String displayPath() {
    Path cwd = ctx.cwd();
    try {
      Path home = Path.of(ctx.home()).toAbsolutePath().normalize();
      if (cwd.startsWith(home)) {
        String rel = home.relativize(cwd).toString().replace('\\', '/');
        return rel.isEmpty() ? "~" : "~/" + rel;
      }
    } catch (InvalidPathException ignored) {
      // fall back to the absolute path
    }
    return cwd.toString();
  }
}

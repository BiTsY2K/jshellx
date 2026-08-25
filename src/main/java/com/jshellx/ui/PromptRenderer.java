package com.jshellx.ui;

import java.net.InetAddress;

import org.jline.jansi.Ansi;
import org.jline.jansi.Ansi.Color;

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
    Ansi a = Ansi.ansi();
    a.fg(Color.GREEN).bold().a("jshellx[").a(user + "@" + host).a("]").reset().a(' ');
    a.fg(Color.BLUE).bold().a(ctx.cwd().toString()).a("> ");
    return a.toString();
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
}

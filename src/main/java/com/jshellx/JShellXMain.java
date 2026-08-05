package com.jshellx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.jshellx.builtin.BuiltinRegistry;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;
import com.jshellx.core.Shell;

public class JShellXMain {

  private static final String VERSION = "1.0.0";

  private JShellXMain() {}

  public static void main(String[] args) throws IOException {
    ShellContext ctx = ShellContext.fromSystem();
    BuiltinRegistry builtins = BuiltinRegistry.withDefaults();
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    IoStreams io = new IoStreams(System.in, System.out, System.err);

    io.out().printf("JShellX %s  -  type 'help' for commands, Ctrl-D to quit.%n", VERSION);
    int code = new Shell(ctx, builtins, in, io).run();
    System.exit(code);
  }
}

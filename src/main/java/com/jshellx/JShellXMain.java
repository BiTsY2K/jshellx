package com.jshellx;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.jshellx.builtin.BuiltinRegistry;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;
import com.jshellx.core.Shell;
import com.jshellx.eval.Evaluator;
import com.jshellx.exec.ExecutableFinder;
import com.jshellx.exec.ProcessLauncher;
import com.jshellx.parser.CommandParser;
import com.jshellx.ui.Ansi;
import com.jshellx.ui.PromptRenderer;

public class JShellXMain {

  private JShellXMain() {}

  public static void main(String[] args) throws IOException {
    Ansi.enableWindowsVt();

    // Always write UTF-8 (the prompt symbol is not ASCII) //
    PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

    ShellContext ctx = ShellContext.fromSystem();
    BuiltinRegistry builtins = BuiltinRegistry.withDefaults();
    CommandParser parser = new CommandParser(ctx);
    ProcessLauncher launcher = new ProcessLauncher(new ExecutableFinder());
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    IoStreams io = new IoStreams(System.in, out, err);

    Evaluator evaluator = new Evaluator(ctx, builtins, parser, launcher, io);
    int code = new Shell(ctx, evaluator, new PromptRenderer(ctx), in, io).run();
    System.exit(code);
  }
}

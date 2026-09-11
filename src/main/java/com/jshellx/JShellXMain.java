package com.jshellx;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.jshellx.builtin.BuiltinRegistry;
import com.jshellx.builtin.IoStreams;
import com.jshellx.context.ShellContext;
import com.jshellx.core.Shell;
import com.jshellx.eval.Evaluator;
import com.jshellx.exec.ExecutableFinder;
import com.jshellx.exec.ProcessLauncher;
import com.jshellx.parser.CommandParser;
import com.jshellx.ui.Ansi;
import com.jshellx.ui.LineEditor;
import com.jshellx.ui.PromptRenderer;
import com.jshellx.ui.RawTerminal;
import com.jshellx.ui.ShellCompleter;

/**
 * Composition root: builds the object graph and starts either the interactive REPL or,
 * with {@code -c "<command line>"}, runs a single command and exits.
 */
public class JShellXMain {

  private JShellXMain() {}

  public static void main(String[] args) {
    Ansi.enableWindowsVt();
    System.exit(run(args)); // pump/signal threads must not keep the JVM alive
  }

  private static int run(String[] args) {

    // Always write UTF-8 (the prompt symbol is not ASCII) //
    PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

    ShellContext ctx = ShellContext.fromSystem();
    BuiltinRegistry builtins = BuiltinRegistry.withDefaults();
    ExecutableFinder execfinder = new ExecutableFinder();
    ProcessLauncher launcher = new ProcessLauncher(execfinder);

    CommandParser parser = new CommandParser(ctx);
    IoStreams io = new IoStreams(System.in, out, err);
    Evaluator evaluator = new Evaluator(ctx, builtins, parser, launcher, io);

    // Non-interactive mode: jshellx -c "echo hi | tr a-z A-Z"
    if (args.length >= 2 && args[0].equals("-c")) {
      return evaluator.evaluate(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
    }

    RawTerminal raw = RawTerminal.open().orElse(null); // null => basic line mode
    ShellCompleter completer = new ShellCompleter(ctx, builtins, execfinder);
    LineEditor editor = new LineEditor(System.in, out, ctx.history(), completer, raw);
    return new Shell(ctx, evaluator, new PromptRenderer(ctx), editor, out).run(); // returns exit status
  }
}

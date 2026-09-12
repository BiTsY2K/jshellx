package com.jshellx.core;

import static com.jshellx.ui.Ansi.BOLD;
import static com.jshellx.ui.Ansi.CYAN;
import static com.jshellx.ui.Ansi.style;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import com.jshellx.context.ShellContext;
import com.jshellx.eval.Evaluator;
import com.jshellx.ui.LineEditor;
import com.jshellx.ui.PromptRenderer;
import com.jshellx.ui.SignalSupport;

public final class Shell {

  private static final String VERSION = "1.0.0";

  private final ShellContext ctx;
  private final Evaluator evaluator;
  private final PromptRenderer prompt;
  private final LineEditor editor;
  private final PrintStream out;

  public Shell(ShellContext ctx, Evaluator evaluator,
      PromptRenderer prompt, LineEditor editor, PrintStream out) {
    this.ctx = ctx;
    this.evaluator = evaluator;
    this.prompt = prompt;
    this.editor = editor;
    this.out = out;
  }

  /** Runs until {@code exit} or Ctrl-D. @return the process exit code */
  public int run() {
    Path historyFile = Path.of(ctx.home(), ".jshellx_history");
    ctx.history().load(historyFile);

    out.printf("%s  -  type 'help' for commands, Ctrl-D to quit.%n", style("JshellX " + VERSION, CYAN, BOLD));
    if (!editor.isRaw()) out.println("(basic line mode: no tab completion or arrow-key history in this terminal)");

    SignalSupport.onInterrupt(evaluator::interrupt);

    try {
      while (ctx.isRunning()) {
        LineEditor.Result result;
        try {
          result = editor.readLine(prompt.render());
        } catch (IOException e) {
          out.printf("jshellx: terminal error: %s", e.getMessage());
          ctx.requestExit(1);
          break;
        }

        // `result` is a sealed type, so this switch must cover every case.
        switch (result) {
          case LineEditor.Eof eof -> ctx.requestExit(ctx.lastExitCode());
          case LineEditor.Interrupted interrupted -> ctx.setLastExitCode(130);
          case LineEditor.Line line -> {
            if (!line.text().isBlank()) {
              ctx.history().add(line.text());
              ctx.setLastExitCode(evaluator.evaluate(line.text()));
            }
          }
        }
      }
    } finally {
      ctx.history().save(historyFile);
    }

    return ctx.exitCode();
  }
}

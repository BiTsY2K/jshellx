package com.jshellx.builtin;

import java.io.InputStream;
import java.io.PrintStream;

/** The three standard streams a command reads from / writes to (may be redirected). */
public record IoStreams(InputStream in, PrintStream out, PrintStream err) {
}

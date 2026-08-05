package com.jshellx.builtin;

import java.io.InputStream;
import java.io.PrintStream;

public record IoStreams(InputStream in, PrintStream out, PrintStream err) {
}

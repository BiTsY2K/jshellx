package com.jshellx;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class JShellXMain {
  private static final String VERSION = "1.0.0";
  private static File currentDirectory = new File(System.getProperty("user.dir"));

  private JShellXMain() {
  }

  public static void main(String[] args) {
    try {
      System.exit(run(args));
    } catch (Exception e) {
    }
  }

  private static int run(String[] args) throws IOException {
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    System.out.printf("JShellX %s  -  type 'help' for commands, Ctrl-D to quit%n", VERSION);
    while (true) {
      System.out.print("jshellx> ");
      System.out.flush();

      String line = in.readLine();
      if (line == null || line.trim().equalsIgnoreCase("exit")) break;
      if (line.isBlank()) continue;

      List<String> tokens = Arrays.stream(line.split("\\s+")).filter(t -> !t.isBlank()).toList();
      if (tokens.isEmpty()) continue;

      try {
        List<String> command = System.getProperty("os.name").toLowerCase().contains("win")
            ? new java.util.ArrayList<>(Stream.concat(Stream.of("cmd.exe", "/c"), tokens.stream()).toList())
            : List.of("sh", "-c", String.join(" ", tokens));

        Process process = new ProcessBuilder(command).inheritIO().start();
        int exitCode = process.waitFor();
        if (exitCode != 0) System.out.printf("[exit %s]%n", exitCode);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.err.printf("jshellx: command execution interrupted");
      } catch (IOException e) {
        System.out.printf("jshellx: command not found: %s%n", tokens.get(0));
      }
    }

    return 0;
  }
}

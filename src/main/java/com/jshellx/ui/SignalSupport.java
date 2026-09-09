package com.jshellx.ui;

import java.lang.reflect.Proxy;

/**
 * Installs a SIGINT (Ctrl-C) handler. Java's only API for this is {@code sun.misc.Signal}
 * (module jdk.unsupported), accessed reflectively so the project compiles on any JDK and
 * degrades gracefully if the class is missing.
 */
public final class SignalSupport {

  private SignalSupport() {}

  /** @return true if the handler was installed */
  public static boolean onInterrupt(Runnable action) {
    try {
      Class<?> signalClass = Class.forName("sun.misc.Signal");
      Class<?> handlerClass = Class.forName("sun.misc.SignalHandler");
      Object signal = signalClass.getConstructor(String.class).newInstance("INT");
      Object handler = Proxy.newProxyInstance(handlerClass.getClassLoader(),
          new Class<?>[] { handlerClass }, (proxy, method, args) -> switch (method.getName()) {
            case "handle" -> {
              action.run();
              yield null;
            }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> "JshellX-SIGINT-handler";
          });
      signalClass.getMethod("handle", signalClass, handlerClass).invoke(null, signal, handler);
      return true;
    } catch (ReflectiveOperationException | RuntimeException e) {
      return false;
    }
  }
}
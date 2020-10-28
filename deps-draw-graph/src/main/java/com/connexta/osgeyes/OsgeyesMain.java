package com.connexta.osgeyes;

import clojure.lang.RT;
import clojure.lang.Symbol;

/**
 * Since the CLI app is just a custom, repackaged version of REPLy / nREPL, bootstrap the app the
 * same way they do but route to our app's Clojure main which wrapps their Clojure main.
 *
 * <p>This also removes a :gen-class requirement for our Clojure main that would result in the AOT
 * compilation of all transitive Clojure dependencies.
 *
 * <p>See: https://github.com/connexta/osg-eyes/issues/1
 */
public class OsgeyesMain {

  private static final String MAIN_NS = "com.connexta.osgeyes.main";

  /**
   * Code adapted from REPLyMain.java
   * https://github.com/trptcolin/reply/blob/master/src/java/reply/ReplyMain.java
   */
  public static void main(String... args) {
    String jlineLog = System.getenv("JLINE_LOGGING");
    if (jlineLog != null) {
      System.setProperty("jline.internal.Log." + jlineLog, "true");
    }
    Symbol ns = Symbol.create(MAIN_NS);
    RT.init();
    RT.var("clojure.core", "require").invoke(ns);
    RT.var(MAIN_NS, "-main").applyTo(RT.seq(args));
  }
}

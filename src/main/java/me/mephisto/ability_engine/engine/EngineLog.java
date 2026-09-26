package me.mephisto.ability_engine.engine;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Replaces the System.out.println calls. Debug output is off by default; toggle with /ae debug. */
public final class EngineLog {

    private final Logger logger;
    private volatile boolean debug;

    public EngineLog(Logger logger) {
        this.logger = logger;
    }

    public void info(String msg) { logger.info(msg); }
    public void warn(String msg) { logger.warning(msg); }
    public void error(String msg, Throwable t) { logger.log(Level.SEVERE, msg, t); }

    /** Supplier so the string is never built when debug is off. */
    public void debug(Supplier<String> msg) {
        if (debug) logger.info("[debug] " + msg.get());
    }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }
}

package me.mephisto.ability_engine.engine.platform;

public interface TaskHandle {
    /** Safe to call more than once, and safe to call after the task already ran. */
    void cancel();
    boolean isCancelled();
}

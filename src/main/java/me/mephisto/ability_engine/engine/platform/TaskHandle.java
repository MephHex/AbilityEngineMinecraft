package me.mephisto.ability_engine.engine.platform;

public interface TaskHandle {
    /** A task that was never scheduled (nothing to cancel). */
    TaskHandle NONE = new TaskHandle() {
        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() { return true; }
    };

    /** Safe to call more than once, and safe to call after the task already ran. */
    void cancel();
    boolean isCancelled();
}

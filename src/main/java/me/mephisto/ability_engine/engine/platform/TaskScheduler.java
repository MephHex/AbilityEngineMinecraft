package me.mephisto.ability_engine.engine.platform;

/**
 * Port for scheduling work. Every callback MUST run on the main game thread;
 * the engine relies on that and has no locking.
 */
public interface TaskScheduler {
    /** Run once after {@code delayTicks} (minimum 1). */
    TaskHandle after(long delayTicks, Runnable task);

    /** Run repeatedly, first after {@code initialDelayTicks}, then every {@code periodTicks}. */
    TaskHandle every(long initialDelayTicks, long periodTicks, Runnable task);
}

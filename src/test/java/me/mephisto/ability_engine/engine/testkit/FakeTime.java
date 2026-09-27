package me.mephisto.ability_engine.engine.testkit;

import me.mephisto.ability_engine.engine.platform.GameClock;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic clock + scheduler. Nothing runs until you call {@link #advance(int)}. */
public final class FakeTime implements GameClock, TaskScheduler {

    private long now = 1000; // non-zero on purpose: catches code that assumes ticks start at 0
    private long seq;
    private final List<Task> tasks = new ArrayList<>();

    private final class Task implements TaskHandle {
        long dueAt;
        final long period;
        final long order = seq++;
        final Runnable body;
        boolean cancelled;

        Task(long dueAt, long period, Runnable body) {
            this.dueAt = dueAt;
            this.period = period;
            this.body = body;
        }

        @Override public void cancel() { cancelled = true; }
        @Override public boolean isCancelled() { return cancelled; }
    }

    @Override public long now() { return now; }

    @Override
    public TaskHandle after(long delayTicks, Runnable task) {
        Task t = new Task(now + Math.max(1, delayTicks), 0, task);
        tasks.add(t);
        return t;
    }

    @Override
    public TaskHandle every(long initialDelayTicks, long periodTicks, Runnable task) {
        Task t = new Task(now + Math.max(1, initialDelayTicks), Math.max(1, periodTicks), task);
        tasks.add(t);
        return t;
    }

    private final List<Runnable> entityTicks = new ArrayList<>();

    /** Runs every tick AFTER the scheduled tasks, like the server ticking entities after the scheduler. */
    public void onEntityTick(Runnable r) { entityTicks.add(r); }

    public void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            now++;
            List<Task> due = tasks.stream()
                    .filter(t -> !t.cancelled && t.dueAt <= now)
                    .sorted(Comparator.comparingLong((Task t) -> t.dueAt).thenComparingLong(t -> t.order))
                    .toList();
            for (Task t : due) {
                if (t.cancelled) continue;
                t.body.run();
                if (t.period > 0) t.dueAt = now + t.period;
                else t.cancelled = true;
            }
            tasks.removeIf(t -> t.cancelled);
            for (Runnable r : List.copyOf(entityTicks)) r.run();
        }
    }

    public int pendingTasks() {
        return (int) tasks.stream().filter(t -> !t.cancelled).count();
    }
}

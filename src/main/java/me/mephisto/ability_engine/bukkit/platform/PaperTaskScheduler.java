package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Main-thread scheduling via the Bukkit scheduler. */
public final class PaperTaskScheduler implements TaskScheduler {

    private final Plugin plugin;

    public PaperTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public TaskHandle after(long delayTicks, Runnable task) {
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, delayTicks)));
    }

    @Override
    public TaskHandle every(long initialDelayTicks, long periodTicks, Runnable task) {
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1, initialDelayTicks), Math.max(1, periodTicks)));
    }

    private static TaskHandle wrap(BukkitTask task) {
        return new TaskHandle() {
            @Override public void cancel() { task.cancel(); }
            @Override public boolean isCancelled() { return task.isCancelled(); }
        };
    }
}

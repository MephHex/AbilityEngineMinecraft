package me.mephisto.ability_engine.bukkit.hud;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shows cast/channel progress on the XP bar (the boss bar stays free for ult timers etc.).
 * Characters with a {@code status_bar} otherwise show that status there: the level number is its
 * stacks, the bar the time it has left (empty and no number while they don't have it); a cast bar
 * still takes over while one is running.
 * The player's real XP is saved when a bar starts and put back when it ends, so nothing is lost.
 * Note: the XP bar is hidden in creative mode.
 */
public final class CastBarHud implements Listener {

    private record SavedXp(float exp, int level) {}

    private final AbilityEngine engine;
    private final Map<UUID, SavedXp> showing = new HashMap<>();
    private TaskHandle task;

    public CastBarHud(AbilityEngine engine) {
        this.engine = engine;
    }

    public void start() {

        task = engine.scheduler().every(1, 1, this::tick);
    }

    /** Stop and give everyone their XP back (plugin disable). */
    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) restore(p);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Optional<Double> fill = engine.instances().castProgress(id);
            Optional<String> statusBar = engine.loadouts().characterOf(id).map(c -> c.statusBar());
            if (fill.isEmpty() && statusBar.isEmpty()) {
                restore(p);
                continue;
            }
            showing.computeIfAbsent(id, k -> new SavedXp(p.getExp(), p.getLevel()));
            if (fill.isPresent()) {
                p.setLevel(0); // hides the level number while the bar is up
                p.setExp((float) Math.min(0.999, fill.get()));
            } else {
                var gauge = engine.statuses().gauge(id, statusBar.get());
                p.setLevel(gauge.map(g -> g.stacks()).orElse(0)); // 0 shows no number
                p.setExp((float) Math.min(0.999, gauge.map(g -> g.fraction()).orElse(0.0)));
            }
        }
    }

    private void restore(Player p) {
        SavedXp saved = showing.remove(p.getUniqueId());
        if (saved == null) return;
        p.setExp(saved.exp());
        p.setLevel(saved.level());
    }

    /** Before the inventory/XP is saved to disk. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        restore(event.getPlayer());
    }
}

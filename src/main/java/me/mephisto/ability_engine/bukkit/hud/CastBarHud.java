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
            Optional<Double> fill = engine.instances().castProgress(p.getUniqueId());
            if (fill.isEmpty()) {
                restore(p);
                continue;
            }
            showing.computeIfAbsent(p.getUniqueId(), id -> new SavedXp(p.getExp(), p.getLevel()));
            p.setLevel(0); // hides the level number while the bar is up
            p.setExp((float) Math.min(0.999, fill.get()));
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

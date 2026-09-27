package me.mephisto.ability_engine.bukkit.hud;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.AbilityInstanceRegistry;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Timers on the boss bar: a {@code delay} with {@code boss_bar: true} (e.g. an ultimate's duration)
 * shows the ability's name and a bar running out, only to the caster.
 */
public final class BossBarHud implements Listener {

    private final AbilityEngine engine;
    private final Map<UUID, BossBar> showing = new HashMap<>();
    private TaskHandle task;

    public BossBarHud(AbilityEngine engine) {
        this.engine = engine;
    }

    public void start() {
        task = engine.scheduler().every(1, 1, this::tick);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) hide(p);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Optional<AbilityInstanceRegistry.Timer> timer = engine.instances().timer(p.getUniqueId());
            if (timer.isEmpty()) {
                hide(p);
                continue;
            }
            Component name = Component.text(timer.get().ability().display().name(), NamedTextColor.GOLD);
            float left = (float) Math.max(0, Math.min(1, timer.get().left()));
            BossBar bar = showing.get(p.getUniqueId());
            if (bar == null) {
                bar = BossBar.bossBar(name, left, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
                showing.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            } else {
                bar.name(name);
                bar.progress(left);
            }
        }
    }

    private void hide(Player p) {
        BossBar bar = showing.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        showing.remove(event.getPlayer().getUniqueId());
    }
}

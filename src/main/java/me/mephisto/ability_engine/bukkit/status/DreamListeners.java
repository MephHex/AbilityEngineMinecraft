package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.tag.Tags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Kills (for refresh_on_kill) and keeping hidden players (state.hidden) hidden from people who join
 * while they're hidden.
 */
public final class DreamListeners implements Listener {

    private final AbilityEngine engine;
    private final Plugin plugin;

    public DreamListeners(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
    }

    /** The killer is whoever dealt the last blow (our ability damage counts: it names the attacker). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (engine.summons().isSummon(event.getEntity().getUniqueId())) { // a destroyed soul: no one's kill, no loot
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        if (killer == null) return;
        engine.notifyKill(killer.getUniqueId(), event.getEntity().getUniqueId(), event.getEntity() instanceof Player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(event.getPlayer()) && engine.tags().has(other.getUniqueId(), Tags.HIDDEN)) {
                event.getPlayer().hideEntity(plugin, other);
            }
        }
    }
}

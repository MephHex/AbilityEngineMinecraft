package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * {@code state.withered} shows vanilla Wither (black hearts), {@code state.poison_hearts} vanilla Poison (green
 * hearts); the ability's own status deals the damage (through the engine: credited, and the same for everyone).
 * This cancels the vanilla effect's damage for entities with the tag, so it isn't dealt twice. Wither or Poison
 * from anything else (a wither skeleton, a cave spider) still hurts.
 */
public final class WitherGuard implements Listener {

    private final AbilityEngine engine;

    public WitherGuard(AbilityEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWither(EntityDamageEvent e) {
        java.util.UUID id = e.getEntity().getUniqueId();
        if (e.getCause() == EntityDamageEvent.DamageCause.WITHER && engine.tags().has(id, "state.withered")) {
            e.setCancelled(true);
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.POISON && engine.tags().has(id, "state.poison_hearts")) {
            e.setCancelled(true);
        }
    }
}

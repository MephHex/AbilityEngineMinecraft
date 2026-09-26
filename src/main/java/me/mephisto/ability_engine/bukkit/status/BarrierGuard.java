package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.effect.DamageEffect;
import me.mephisto.ability_engine.bukkit.platform.Convert;
import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

/**
 * Frontal barriers vs VANILLA hits (mob melee, arrows, tridents...). Our own abilities are checked by
 * the engine; this covers everything Minecraft does by itself.
 */
public final class BarrierGuard implements Listener {

    private final AbilityEngine engine;

    public BarrierGuard(AbilityEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (DamageEffect.isApplying()) return; // ability damage: the engine already decided
        UUID victim = event.getEntity().getUniqueId();
        if (!engine.barriers().has(victim)) return;

        Entity damager = event.getDamager();
        UUID attacker = damager instanceof Projectile p && p.getShooter() instanceof Entity shooter
                ? shooter.getUniqueId() : damager.getUniqueId();
        var from = Convert.vec(damager.getLocation());
        if (!engine.barriers().blocksDirectHit(victim, from, attacker)) return;

        event.setCancelled(true);
        engine.barriers().blocked(event.getEntity().getWorld().getName(), Convert.vec(event.getEntity().getLocation()).add(0, 1, 0));
        if (damager instanceof Projectile) damager.remove(); // absorbed, doesn't bounce off
    }
}

package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.effect.DamageEffect;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.tag.Tags;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * state.frozen and state.flying on Bukkit.
 * <ul>
 *   <li>Frozen: the entity is kept fully frozen (blue hearts, frost overlay, vanilla's powder-snow slow),
 *       but vanilla's own freeze damage is cancelled: the status's ticks deal the ice damage.</li>
 *   <li>Flying: creative-style flight. When it ends, the landing that follows deals no fall damage.</li>
 * </ul>
 */
public final class FrostAndFlight implements Listener {

    /** How long after flight ends the first fall is still forgiven (the drop from wherever you were). */
    private static final int SAFE_LANDING_TICKS = 200;

    /** Player -> server tick until which their next fall does no damage. */
    private static final Map<UUID, Integer> safeLanding = new HashMap<>();

    private final AbilityEngine engine;

    public FrostAndFlight(AbilityEngine engine) {
        this.engine = engine;
    }

    static void freeze(LivingEntity e) {
        e.setFreezeTicks(e.getMaxFreezeTicks());
        e.lockFreezeTicks(true); // stays fully frozen while tagged, even out of powder snow
    }

    static void thaw(LivingEntity e) {
        e.lockFreezeTicks(false);
        e.setFreezeTicks(0);
    }

    static void fly(LivingEntity e) {
        if (!(e instanceof Player p)) return;
        safeLanding.remove(p.getUniqueId());
        p.setAllowFlight(true);
        p.setFlying(true);
    }

    static void land(LivingEntity e) {
        if (!(e instanceof Player p)) return;
        boolean canFly = p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
        if (!canFly) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        p.setFallDistance(0);
        safeLanding.put(p.getUniqueId(), Bukkit.getCurrentTick() + SAFE_LANDING_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE && !DamageEffect.isApplying()
                && engine.tags().has(id, Tags.FROZEN)) {
            event.setCancelled(true); // vanilla freezing damage: the frozen status deals its own
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Integer until = safeLanding.remove(id);
            if (until != null && Bukkit.getCurrentTick() <= until) event.setCancelled(true);
        }
    }
}

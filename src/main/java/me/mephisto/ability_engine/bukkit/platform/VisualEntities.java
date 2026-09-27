package me.mephisto.ability_engine.bukkit.platform;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks entities spawned purely as ability visuals, and protects them. Without this, an end
 * crystal visual explodes the moment anything damages it, and a mob visual could be hit by
 * your own abilities (see BukkitWorldQuery, which skips marked entities).
 */
public final class VisualEntities implements Listener {

    private static final NamespacedKey KEY = new NamespacedKey("ability_engine", "visual");

    public static void mark(Entity e) {
        e.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isVisual(Entity e) {
        return e.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** setInvulnerable doesn't stop creative players, so cancel every damage event instead. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (isVisual(event.getEntity())) event.setCancelled(true);
        // A visual can't hurt anything either (an arrow visual flying through a mob).
        if (event instanceof EntityDamageByEntityEvent byEntity && isVisual(byEntity.getDamager())) event.setCancelled(true);
    }

    /**
     * Arrow projectiles fly through entities: the engine decides what they hit (allies, barriers...), and
     * removes them there. Blocks it doesn't prevent: an arrow that lands tells the engine it landed.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (isVisual(event.getEntity()) && event.getHitEntity() != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(PlayerPickupArrowEvent event) {
        if (isVisual(event.getArrow())) event.setCancelled(true);
    }
}

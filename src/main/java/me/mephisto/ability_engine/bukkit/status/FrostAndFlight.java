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
 *   <li>Flying: creative-style flight. When it ends, the landing that follows deals no fall damage. With
 *       state.slow_flight too, at half the flying speed; with state.level_flight, held at one height (pushed back
 *       to it every tick they drift off it, snapped back if they get far).</li>
 * </ul>
 */
public final class FrostAndFlight implements Listener {

    /** How long after flight ends the first fall is still forgiven (the drop from wherever you were). */
    private static final int SAFE_LANDING_TICKS = 200;

    /** Player -> server tick until which their next fall does no damage. */
    private static final Map<UUID, Integer> safeLanding = new HashMap<>();

    private final AbilityEngine engine;

    /** Flying speed with state.slow_flight (vanilla's is 0.1). */
    private static final float SLOW_FLY_SPEED = 0.05f;
    private static final float DEFAULT_FLY_SPEED = 0.1f;

    /** Players flying slowly right now (their speed is ours to give back). */
    private final java.util.Set<UUID> slowFlyers = new java.util.HashSet<>();

    public FrostAndFlight(AbilityEngine engine) {
        this.engine = engine;
        engine.scheduler().every(1, 1, this::slowFlight);
    }

    /** state.level_flight: the height each one is held at, and where they were last tick (their own drift). */
    private final Map<UUID, Double> levels = new HashMap<>();
    private final Map<UUID, org.bukkit.util.Vector> lastPos = new HashMap<>();
    /** Drifted this far off their height: pushed back; this far: put back. */
    private static final double LEVEL_SLACK = 0.03;
    private static final double LEVEL_SNAP = 0.6;

    private void slowFlight() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            level(p);
            UUID id = p.getUniqueId();
            boolean rooted = engine.tags().has(id, Tags.BLOCK_MOVE);
            if (engine.tags().has(id, Tags.FLYING) && engine.tags().has(id, Tags.SLOW_FLIGHT)) {
                slowFlyers.add(id);
                if (!rooted && Math.abs(p.getFlySpeed() - SLOW_FLY_SPEED) > 1e-4) p.setFlySpeed(SLOW_FLY_SPEED);
            } else if (slowFlyers.remove(id) && !rooted) {
                p.setFlySpeed(DEFAULT_FLY_SPEED);
            }
        }
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

    /** state.level_flight: no flying up or down, the height they had when it started (or were teleported to). */
    private void level(Player p) {
        UUID id = p.getUniqueId();
        org.bukkit.util.Vector here = p.getLocation().toVector();
        org.bukkit.util.Vector before = lastPos.put(id, here);
        if (!engine.tags().has(id, Tags.FLYING) || !engine.tags().has(id, Tags.LEVEL_FLIGHT) || p.isDead()) {
            levels.remove(id);
            return;
        }
        double level = levels.computeIfAbsent(id, k -> here.getY());
        if (p.getAllowFlight() && !p.isFlying()) p.setFlying(true); // (no dropping out of it either)
        double dy = level - here.getY();
        if (Math.abs(dy) <= LEVEL_SLACK) return;
        if (Math.abs(dy) > LEVEL_SNAP) {
            org.bukkit.Location back = p.getLocation();
            back.setY(level);
            p.teleport(back, me.mephisto.ability_engine.bukkit.platform.BukkitMovementControl.KEEP_RIDERS);
            levels.put(id, level); // (the teleport event mustn't move the level)
            return;
        }
        org.bukkit.util.Vector moving = before == null ? new org.bukkit.util.Vector() : here.clone().subtract(before);
        p.setVelocity(new org.bukkit.util.Vector(moving.getX(), Math.max(-1, Math.min(1, dy)), moving.getZ()));
    }

    /** Teleported (a blink) while level-flying: that's the new height. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (levels.containsKey(id) && event.getTo() != null) levels.put(id, event.getTo().getY());
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
            if (engine.tags().has(id, Tags.SAFE_FALL)) {
                event.setCancelled(true); // (e.g. slammed down: the slam was the damage)
                return;
            }
            Integer until = safeLanding.remove(id);
            if (until != null && Bukkit.getCurrentTick() <= until) event.setCancelled(true);
        }
    }
}

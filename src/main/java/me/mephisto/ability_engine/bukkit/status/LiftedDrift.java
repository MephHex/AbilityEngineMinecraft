package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.tag.TagListener;
import me.mephisto.ability_engine.engine.tag.Tags;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code state.lifted} and slows: in the air the game steers everyone at one fixed speed (the movement speed attribute
 * only counts on the ground), so a slow on someone floating up wouldn't slow their drift at all. While they're lifted,
 * their statuses' {@code move_speed} caps how fast they drift sideways instead (0.35: about a third of a normal drift),
 * e.g. enemies caught in Gravitic Collapse crawl toward its edge. Unslowed, nothing changes.
 * <p>
 * Players: the server doesn't know a player's real velocity, so it's read from how far they moved since the last tick,
 * and anything faster than the cap is sent back to them at the cap (same direction, same rise).
 */
public final class LiftedDrift implements TagListener {

    /** How fast anyone drifts sideways in the air at most, unslowed (blocks per tick: vanilla's ~4 blocks a second). */
    private static final double FREE_DRIFT = 0.2;
    /** What the player's own steering adds on top of the speed they're sent, in one tick (vanilla's air control). */
    private static final double STEERING = 0.02;

    private final AbilityEngine engine;
    /** Lifted -> where they were last tick (null: not seen yet). */
    private final Map<UUID, Location> lifted = new HashMap<>();

    public LiftedDrift(AbilityEngine engine) {
        this.engine = engine;
    }

    public void start() {
        engine.scheduler().every(1, 1, this::tick);
    }

    @Override
    public void onTagAdded(UUID entity, String tag) {
        if (tag.equals(Tags.LIFTED)) lifted.put(entity, null);
    }

    @Override
    public void onTagRemoved(UUID entity, String tag) {
        if (tag.equals(Tags.LIFTED)) lifted.remove(entity);
    }

    private void tick() {
        if (lifted.isEmpty()) return;
        var it = lifted.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Entity e = Bukkit.getEntity(entry.getKey());
            if (e == null || !e.isValid()) {
                it.remove();
                continue;
            }
            Location now = e.getLocation();
            Location before = entry.getValue();
            entry.setValue(now);
            double slow = engine.stats().moveSpeedMultiplier(e.getUniqueId());
            if (slow >= 1 || e.isOnGround()) continue;
            if (e instanceof Player) {
                if (before == null || !before.getWorld().equals(now.getWorld())) continue;
                Vector moved = now.toVector().subtract(before.toVector());
                double cap = Math.max(0, FREE_DRIFT * slow - STEERING); // their steering adds STEERING back each tick
                capDrift(e, moved, cap);
            } else {
                capDrift(e, e.getVelocity(), FREE_DRIFT * slow);
            }
        }
    }

    /** Drifting faster than {@code cap} sideways: sent on at the cap, the same way, rising as they were. */
    private static void capDrift(Entity e, Vector velocity, double cap) {
        double flat = Math.hypot(velocity.getX(), velocity.getZ());
        if (flat <= cap || flat < 1e-6) return;
        double k = cap / flat;
        e.setVelocity(new Vector(velocity.getX() * k, velocity.getY(), velocity.getZ() * k));
    }
}

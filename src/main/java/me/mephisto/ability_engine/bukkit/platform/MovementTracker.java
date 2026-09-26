package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Which way players are walking. The server never sees WASD itself (not before 1.21.2's input packet),
 * and a player's server-side velocity doesn't include walking, so this remembers the direction of each
 * player's latest horizontal move. Mobs use their velocity.
 */
public final class MovementTracker implements Listener {

    /** A move older than this means they've stopped. Moving players send a position every tick. */
    private static final long STALE_TICKS = 3;
    /** Horizontal moves shorter than this (squared) are just jitter, or only turning the head. */
    private static final double MIN_STEP_SQ = 1e-4;

    private record Step(long tick, Vec3 direction) {}

    private final Map<UUID, Step> last = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        double dx = e.getTo().getX() - e.getFrom().getX();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        if (dx * dx + dz * dz < MIN_STEP_SQ) return;
        last.put(e.getPlayer().getUniqueId(), new Step(Bukkit.getCurrentTick(), new Vec3(dx, 0, dz).normalize()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { last.remove(e.getPlayer().getUniqueId()); }

    /** Unit horizontal direction the entity is moving, empty if it's standing still. */
    public Optional<Vec3> directionOf(UUID id) {
        Entity e = Bukkit.getEntity(id);
        if (e == null) return Optional.empty();
        if (!(e instanceof Player)) {
            Vector v = e.getVelocity();
            Vec3 flat = new Vec3(v.getX(), 0, v.getZ());
            return flat.lengthSquared() < MIN_STEP_SQ ? Optional.empty() : Optional.of(flat.normalize());
        }
        Step s = last.get(id);
        if (s == null || Bukkit.getCurrentTick() - s.tick() > STALE_TICKS) return Optional.empty();
        return Optional.of(s.direction());
    }
}

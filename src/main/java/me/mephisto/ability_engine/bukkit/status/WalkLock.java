package me.mephisto.ability_engine.bukkit.status;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * {@code block.walk}: held where they stand (no walking, no jumping) without touching their speed. A root's lock
 * (block.move) zeroes the movement speed, and the client zooms the camera in with it; this keeps the view as it is
 * (e.g. the Sylvan planted in the ground). Players: jump strength 0 and every step sideways is undone (they can
 * still look around and fall). Mobs have no camera: they get the normal lock.
 */
public final class WalkLock implements Listener {

    private static final NamespacedKey JUMP_KEY = new NamespacedKey("ability_engine", "walk_jump");
    private static final Set<UUID> held = new HashSet<>();

    static void hold(LivingEntity living) {
        if (!(living instanceof Player p)) {
            MovementLock.apply(living);
            return;
        }
        held.add(p.getUniqueId());
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null && jump.getModifier(JUMP_KEY) == null) {
            jump.addModifier(new AttributeModifier(JUMP_KEY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    static void release(LivingEntity living) {
        if (!(living instanceof Player p)) {
            MovementLock.remove(living);
            return;
        }
        held.remove(p.getUniqueId());
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null && jump.getModifier(JUMP_KEY) != null) jump.removeModifier(JUMP_KEY);
    }

    /** Stepping sideways: back where they were (turning and falling are fine). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!held.contains(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getX() == to.getX() && from.getZ() == to.getZ() && to.getY() <= from.getY()) return;
        Location back = to.clone();
        back.setX(from.getX());
        back.setZ(from.getZ());
        if (back.getY() > from.getY()) back.setY(from.getY()); // no climbing (stairs, slabs) either
        event.setTo(back);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        release(event.getPlayer()); // no jump modifier saved with them
    }
}

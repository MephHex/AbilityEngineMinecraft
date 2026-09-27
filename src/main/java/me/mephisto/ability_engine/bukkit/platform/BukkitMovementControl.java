package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.MovementControl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Moves entities by velocity. For players the client applies it, which is why dashes look smooth;
 * the engine re-sends it every tick while dashing so friction doesn't slow them down.
 */
public final class BukkitMovementControl implements MovementControl {

    @Override
    public void setVelocity(UUID entity, Vec3 velocity) {
        Entity e = Bukkit.getEntity(entity);
        if (e != null && e.isValid()) e.setVelocity(Convert.bukkit(velocity));
    }

    @Override
    public void stop(UUID entity) {
        Entity e = Bukkit.getEntity(entity);
        if (e == null || !e.isValid()) return;
        e.setVelocity(new Vector(0, 0, 0));
        e.setFallDistance(0); // a dash off a ledge shouldn't turn into fall damage
    }

    @Override
    public void teleport(UUID entity, Vec3 center) {
        Entity e = Bukkit.getEntity(entity);
        if (e == null || !e.isValid()) return;
        Location to = e.getLocation(); // keeps world, yaw and pitch
        to.setX(center.x());
        to.setY(center.y() - e.getBoundingBox().getHeight() / 2);
        to.setZ(center.z());
        e.teleport(to);
        e.setFallDistance(0);
    }

    /** Head and body both: a mannequin (no AI) wouldn't turn its body by itself before it moves. */
    @Override
    public void face(UUID entity, Vec3 direction) {
        Entity e = Bukkit.getEntity(entity);
        if (e == null || !e.isValid() || direction.isZero()) return;
        Location look = e.getLocation();
        look.setDirection(Convert.bukkit(direction));
        e.setRotation(look.getYaw(), look.getPitch());
        if (e instanceof LivingEntity living) living.setBodyYaw(look.getYaw());
    }
}

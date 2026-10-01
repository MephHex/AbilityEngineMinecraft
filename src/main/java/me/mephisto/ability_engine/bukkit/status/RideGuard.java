package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.platform.BukkitMovementControl;
import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;

/**
 * Rides (the engine's mount node) on Bukkit: a rider can't hop off by sneaking while their ride lasts; the
 * ability decides when it ends. If the mount itself is gone or dead they're let go (the engine notices and
 * ends the ride).
 */
public final class RideGuard implements Listener {

    private final AbilityEngine engine;

    public RideGuard(AbilityEngine engine) {
        this.engine = engine;
    }

    /** The rider off their seat, or the seat (with the rider on it) off the mount: the ride decides. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        Entity rider = event.getEntity();
        Entity mount = BukkitMovementControl.underSeat(event.getDismounted());
        if (BukkitMovementControl.isSeat(rider)) {
            if (rider.getPassengers().isEmpty()) return;
            rider = rider.getPassengers().get(0);
        }
        if (mount == null) return;
        boolean alive = mount.isValid() && !(mount instanceof LivingEntity l && l.isDead());
        if (alive && engine.rides().holds(rider.getUniqueId(), mount.getUniqueId())) event.setCancelled(true);
    }
}

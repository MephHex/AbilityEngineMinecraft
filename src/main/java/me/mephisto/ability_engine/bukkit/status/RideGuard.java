package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.platform.BukkitMovementControl;
import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.UUID;

/**
 * Rides (the engine's mount node) on Bukkit: nothing else takes a rider off while their ride lasts; the
 * ability decides when it ends. Pressing SHIFT hops them off through the engine, so the ride's "off" branch
 * runs (the ability lands them and starts its cooldown). If the mount itself is gone or dead they're let go
 * (the engine notices and ends the ride). While they ride, their hunger bar shows their mount's hearts.
 */
public final class RideGuard implements Listener {

    private final AbilityEngine engine;

    public RideGuard(AbilityEngine engine) {
        this.engine = engine;
    }

    /** Every tick: each rider's seat wears its mount's health (the hearts in the rider's hunger bar). */
    public void start() {
        engine.scheduler().every(1, 1, () -> {
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                Entity seat = p.getVehicle();
                if (BukkitMovementControl.isSeat(seat)) BukkitMovementControl.showMountHealth(seat);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (event.isSneaking() && engine.rides().mountOf(id).isPresent()) engine.rides().hopOff(id);
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

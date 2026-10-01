package me.mephisto.ability_engine.engine.ride;

import me.mephisto.ability_engine.engine.EngineLog;
import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.platform.MovementControl;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.status.StatusManager;
import me.mephisto.ability_engine.engine.status.StatusRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rides: one entity sitting on another (a fae perched on an ally's head). The platform does the riding
 * ({@link MovementControl#mount}); this keeps track of who rides whom, for which cast, and ends rides:
 * <ul>
 *   <li>by the cast (a dismount node, a new mount, the cast ending however it ends): quietly</li>
 *   <li>by themselves (the mount died or left, the game took the rider off, the rider died): the ride's
 *       "off" branch runs, so the ability can react (land, start its cooldown)</li>
 * </ul>
 * While a ride lasts its statuses are on: {@code status} on the mount (from the rider), {@code selfStatus}
 * on the rider. Both come off when it ends. One ride per rider.
 */
public final class RideManager {

    /**
     * @param status     on the mount while ridden, from the rider (null = none)
     * @param selfStatus on the rider while riding (null = none)
     * @param off        resumed (port "off") if the ride ends by itself
     */
    public record Ride(UUID rider, UUID mount, String status, String selfStatus, AbilityInstance instance, Resumer off) {}

    private final MovementControl movement;
    private final WorldQuery world;
    private final TaskScheduler scheduler;
    private final EngineLog log;
    private final Map<UUID, Ride> byRider = new HashMap<>();
    private StatusManager statuses;
    private StatusRegistry statusDefs;
    private TaskHandle ticker;

    public RideManager(MovementControl movement, WorldQuery world, TaskScheduler scheduler, EngineLog log) {
        this.movement = movement;
        this.world = world;
        this.scheduler = scheduler;
        this.log = log;
    }

    public void attach(StatusManager statuses, StatusRegistry statusDefs) {
        this.statuses = statuses;
        this.statusDefs = statusDefs;
    }

    /**
     * Seat the rider on the mount (ending the rider's current ride quietly first). Returns false if the
     * platform can't (then nothing changed but that old ride).
     */
    public boolean mount(Ride ride) {
        end(ride.rider(), false);
        if (!movement.mount(ride.rider(), ride.mount())) return false;
        byRider.put(ride.rider(), ride);
        if (ride.status() != null) statuses.apply(ride.mount(), statusDefs.require(ride.status()), 0, ride.rider());
        if (ride.selfStatus() != null) statuses.apply(ride.rider(), statusDefs.require(ride.selfStatus()), 0, ride.rider());
        ride.instance().onEnd(() -> { // the cast is over: so is the ride
            Ride now = byRider.get(ride.rider());
            if (now == ride) end(ride.rider(), false);
        });
        log.debug(() -> "ride: " + ride.rider() + " on " + ride.mount());
        if (ticker == null) ticker = scheduler.every(1, 1, this::tick);
        return true;
    }

    /** Get the rider off, quietly (the cast ended it). */
    public void dismount(UUID rider) { end(rider, false); }

    /** Whom the rider is riding right now (rides this manager started). */
    public Optional<UUID> mountOf(UUID rider) {
        return Optional.ofNullable(byRider.get(rider)).map(Ride::mount);
    }

    /** Is this rider held on this mount by a ride (so the platform shouldn't let them hop off)? */
    public boolean holds(UUID rider, UUID mount) {
        Ride r = byRider.get(rider);
        return r != null && r.mount().equals(mount);
    }

    /** Everyone riding this mount right now. */
    public List<UUID> ridersOf(UUID mount) {
        return byRider.values().stream().filter(r -> r.mount().equals(mount)).map(Ride::rider).toList();
    }

    public void shutdown() {
        for (UUID rider : List.copyOf(byRider.keySet())) end(rider, false);
        if (ticker != null) ticker.cancel();
        ticker = null;
    }

    private void tick() {
        for (Ride r : List.copyOf(byRider.values())) {
            boolean holds = world.isAlive(r.rider()) && world.isAlive(r.mount())
                    && movement.vehicleOf(r.rider()).filter(r.mount()::equals).isPresent();
            if (!holds) end(r.rider(), true);
        }
        if (byRider.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    /** @param byItself the ride ended on its own (its "off" branch runs) rather than by its cast */
    private void end(UUID rider, boolean byItself) {
        Ride r = byRider.remove(rider);
        if (r == null) return;
        if (movement.vehicleOf(rider).filter(r.mount()::equals).isPresent()) movement.dismount(rider);
        if (r.status() != null) statuses.remove(r.mount(), r.status());
        if (r.selfStatus() != null) statuses.remove(rider, r.selfStatus());
        log.debug(() -> "ride over: " + rider + (byItself ? " (by itself)" : ""));
        if (byItself && r.instance().isActive()) r.off().resume(Ports.OFF);
        else r.off().abandon();
    }
}

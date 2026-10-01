package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.MovementControl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Moves entities by velocity. For players the client applies it, which is why dashes look smooth;
 * the engine re-sends it every tick while dashing so friction doesn't slow them down.
 * Teleports keep riders on (a fae perched on someone who blinks goes along). Rides are passengers, on an
 * invisible seat that lifts the rider a little (see {@link #SEAT_HEIGHT}).
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
        e.teleport(to, KEEP_RIDERS);
        e.setFallDistance(0);
    }

    /** Teleport flag: whoever rides the entity goes along (without it, an entity with riders can't teleport). */
    public static final io.papermc.paper.entity.TeleportFlag KEEP_RIDERS =
            io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS;

    /** One teleport with the new view (players can't be turned without one). */
    @Override
    public void teleport(UUID entity, Vec3 center, Vec3 look) {
        Entity e = Bukkit.getEntity(entity);
        if (e == null || !e.isValid()) return;
        Location to = e.getLocation();
        to.setX(center.x());
        to.setY(center.y() - e.getBoundingBox().getHeight() / 2);
        to.setZ(center.z());
        if (look != null && !look.isZero()) to.setDirection(Convert.bukkit(look));
        e.teleport(to, KEEP_RIDERS);
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

    // ---- rides: the rider sits on an invisible seat on the vehicle, a bit higher than vanilla -------------

    /**
     * How much higher than vanilla a rider sits: a fae on someone's head would otherwise have her legs in front
     * of their eyes. A passenger sits on top of its vehicle's box, so the seat is one this tall.
     */
    private static final float SEAT_HEIGHT = 0.4f;
    /** A small armor stand's height, before scaling. */
    private static final double SMALL_STAND_HEIGHT = 0.9875 * 0.5;
    private static final org.bukkit.NamespacedKey SEAT = new org.bukkit.NamespacedKey("ability_engine", "seat");

    /**
     * Each rider's seat: an invisible little armor stand. A living one, so the rider's hunger bar shows its
     * hearts (vanilla does that for a living vehicle), and it wears its mount's health: see
     * {@link #showMountHealth}.
     */
    private final java.util.Map<UUID, ArmorStand> seats = new java.util.HashMap<>();

    @Override
    public boolean mount(UUID rider, UUID vehicle) {
        Entity r = Bukkit.getEntity(rider), v = Bukkit.getEntity(vehicle);
        if (r == null || v == null || !r.isValid() || !v.isValid() || r.equals(v) || !r.getWorld().equals(v.getWorld())) {
            return false;
        }
        dismount(rider);
        if (r.isInsideVehicle()) r.leaveVehicle();
        r.setFallDistance(0);
        ArmorStand seat = v.getWorld().spawn(v.getLocation(), ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setSmall(true);
            s.setMarker(false);                 // a marker has no height: the rider would sit on the head again
            s.setBasePlate(false);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setCollidable(false);
            s.setDisabledSlots(org.bukkit.inventory.EquipmentSlot.values()); // nothing to take or put on it
            AttributeInstance scale = s.getAttribute(Attribute.SCALE);
            if (scale != null) scale.setBaseValue(SEAT_HEIGHT / SMALL_STAND_HEIGHT);
            s.setPersistent(false);
            s.getPersistentDataContainer().set(SEAT, PersistentDataType.BYTE, (byte) 1);
            VisualEntities.mark(s);
        });
        if (!v.addPassenger(seat) || !seat.addPassenger(r)) {
            seat.remove();
            return false;
        }
        seats.put(rider, seat);
        showMountHealth(seat);
        return true;
    }

    /**
     * A seat wears its mount's health (max and current), so its rider's hunger bar shows the mount's hearts, as
     * vanilla does riding a horse. Call it every tick for each seat.
     */
    public static void showMountHealth(Entity seat) {
        if (!(seat instanceof LivingEntity s) || !(seat.getVehicle() instanceof LivingEntity mount)) return;
        AttributeInstance from = mount.getAttribute(Attribute.MAX_HEALTH), to = s.getAttribute(Attribute.MAX_HEALTH);
        if (from == null || to == null) return;
        double max = from.getValue();
        if (Math.abs(to.getBaseValue() - max) > 1e-6) to.setBaseValue(max);
        double hp = Math.max(0.01, Math.min(max, mount.getHealth())); // never 0: that would kill the seat
        if (Math.abs(s.getHealth() - hp) > 1e-6) s.setHealth(hp);
    }

    @Override
    public void dismount(UUID rider) {
        Entity r = Bukkit.getEntity(rider);
        if (r != null && r.isInsideVehicle()) {
            r.leaveVehicle();
            r.setFallDistance(0);
        }
        ArmorStand seat = seats.remove(rider);
        if (seat != null) seat.remove();
    }

    /** What they ride (through the seat). A seat that's come apart (its vehicle or rider gone) is cleared up. */
    @Override
    public java.util.Optional<UUID> vehicleOf(UUID rider) {
        Entity r = Bukkit.getEntity(rider);
        ArmorStand seat = seats.get(rider);
        if (seat != null && (r == null || !seat.isValid() || !seat.equals(r.getVehicle()) || seat.getVehicle() == null)) {
            dismount(rider);
            return java.util.Optional.empty();
        }
        Entity v = r == null ? null : underSeat(r.getVehicle());
        return v == null ? java.util.Optional.empty() : java.util.Optional.of(v.getUniqueId());
    }

    /** The entity under a seat (what its rider really rides); anything else as it is. */
    public static Entity underSeat(Entity vehicle) {
        return isSeat(vehicle) ? vehicle.getVehicle() : vehicle;
    }

    public static boolean isSeat(Entity e) {
        return e != null && e.getPersistentDataContainer().has(SEAT, PersistentDataType.BYTE);
    }
}

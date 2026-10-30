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
 * Teleports keep riders on (a fae perched on someone who blinks goes along). Rides are passengers; a ride with a
 * lift puts the rider on an invisible seat that tall. A rider is hidden from the player they ride (sitting on
 * their head, they'd only be in the way), and only from them: everyone else sees them as usual.
 */
public final class BukkitMovementControl implements MovementControl {

    @Override
    public void setVelocity(UUID entity, Vec3 velocity) {
        Entity e = Bukkit.getEntity(entity);
        if (e != null && e.isValid()) e.setVelocity(Convert.bukkit(velocity));
    }

    @Override
    public void kill(UUID entity) {
        if (Bukkit.getEntity(entity) instanceof org.bukkit.entity.LivingEntity living && !living.isDead()) living.setHealth(0);
    }

    @Override
    public void setHealthShare(UUID entity, double share) {
        if (!(Bukkit.getEntity(entity) instanceof org.bukkit.entity.LivingEntity living) || living.isDead()) return;
        var max = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        living.setHealth(Math.max(1, Math.min(cap, cap * share)));
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

    /** A jump: how fast a body leaves the ground (vanilla's 0.42), whatever step up it was asked for. */
    private static final double JUMP_SPEED = 0.42;

    /**
     * A walking step for a body (a mirroring clone): its velocity this tick is the step sideways, and its own up/down
     * (gravity keeps pulling it, it falls off edges), so the game's physics stop it at walls. A step up while it stands
     * on the ground is a jump.
     */
    @Override
    public void stride(UUID entity, Vec3 step) {
        Entity e = Bukkit.getEntity(entity);
        if (e == null || !e.isValid()) return;
        double vy = e.getVelocity().getY();
        if (step.y() > 0.1 && e.isOnGround()) vy = JUMP_SPEED;
        e.setVelocity(new Vector(step.x(), vy, step.z()));
    }

    /** How much of its sideways speed a body keeps each tick: on the ground (block friction) and in the air (drag). */
    private static final double GROUND_KEEP = 0.546, AIR_KEEP = 0.91;

    /**
     * Like walking keys: each tick it's pushed toward {@code move} by just enough that, against the game's own friction,
     * it settles at that speed (on the ground or in the air), so a knockback on it adds to that and wears off as usual.
     */
    @Override
    public void drive(UUID entity, Vec3 move, boolean jump) {
        Entity e = Bukkit.getEntity(entity);
        if (e == null || !e.isValid()) return;
        Vector v = e.getVelocity();
        boolean ground = e.isOnGround();
        double accel = 1 - (ground ? GROUND_KEEP : AIR_KEEP);
        v.setX(v.getX() + move.x() * accel);
        v.setZ(v.getZ() + move.z() * accel);
        if (jump && ground) v.setY(JUMP_SPEED);
        e.setVelocity(v);
    }

    /** The sneaking pose (a mannequin crouches like a player holding SHIFT). */
    @Override
    public void crouch(UUID entity, boolean crouched) {
        Entity e = Bukkit.getEntity(entity);
        if (e == null || !e.isValid()) return;
        e.setSneaking(crouched);
        if (e.getPose() != (crouched ? org.bukkit.entity.Pose.SNEAKING : org.bukkit.entity.Pose.STANDING)) {
            e.setPose(crouched ? org.bukkit.entity.Pose.SNEAKING : org.bukkit.entity.Pose.STANDING, crouched);
        }
    }

    @Override
    public void swing(UUID entity) {
        if (Bukkit.getEntity(entity) instanceof LivingEntity living && living.isValid()) living.swingMainHand();
    }

    /**
     * A player: pushed by exactly this tick's step of the path, so their own client flies them along it smoothly (a
     * teleport every tick jitters their camera); where they land is set exactly by the flight's end. Anything else: a
     * blink (the clients smooth a non-player's moves by themselves).
     */
    @Override
    public void flyStep(UUID entity, Vec3 from, Vec3 to) {
        Entity e = Bukkit.getEntity(entity);
        if (e == null || !e.isValid()) return;
        if (!(e instanceof org.bukkit.entity.Player)) {
            blink(entity, to);
            return;
        }
        e.setVelocity(Convert.bukkit(to.subtract(from)));
        e.setFallDistance(0);
    }

    /** Running: x1.3 their walking speed, like sprinting. Blocks a tick per point of the movement speed attribute. */
    private static final double RUN = 1.3, WALK_PER_SPEED = 4.317 / 20 / 0.1;

    /**
     * Running along {@code direction} (someone confused): a player on the ground is moved at the speed they'd sprint (their
     * own walking is held meanwhile, see InvertedControls; in the air they carry on as they are); anything else is driven.
     */
    @Override
    public void run(UUID entity, Vec3 direction) {
        Entity e = Bukkit.getEntity(entity);
        if (e == null || !e.isValid()) return;
        if (!(e instanceof org.bukkit.entity.Player p)) {
            drive(entity, direction.multiply(0.25), false);
            return;
        }
        if (!p.isOnGround()) return;
        double speed = me.mephisto.ability_engine.bukkit.status.PuppetHold.speedOf(p) * WALK_PER_SPEED * RUN;
        p.setVelocity(new Vector(direction.x() * speed, 0, direction.z() * speed));
    }

    /** Momentum (velocity) and the look both kept: the game moves them on from the new spot as before. */
    private static final io.papermc.paper.entity.TeleportFlag[] KEEP_MOMENTUM = {
            io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_X,
            io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Y,
            io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Z,
            io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_ROTATION,
            KEEP_RIDERS};

    @Override
    public void blink(UUID entity, Vec3 center) {
        Entity e = Bukkit.getEntity(entity);
        if (e == null || !e.isValid()) return;
        Location to = e.getLocation(); // keeps world, yaw and pitch
        to.setX(center.x());
        to.setY(center.y() - e.getBoundingBox().getHeight() / 2);
        to.setZ(center.z());
        Vector velocity = e.getVelocity();
        e.teleport(to, KEEP_MOMENTUM);
        if (!(e instanceof org.bukkit.entity.Player)) e.setVelocity(velocity); // (a player's client keeps its own)
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

    // ---- rides: a passenger, or (with a lift) on an invisible seat that tall on the vehicle ----------------

    /** A small armor stand's height, before scaling (a passenger sits on top of its vehicle's box). */
    private static final double SMALL_STAND_HEIGHT = 0.9875 * 0.5;
    private static final org.bukkit.NamespacedKey SEAT = new org.bukkit.NamespacedKey("ability_engine", "seat");

    /**
     * Each rider's seat: an invisible little armor stand. A living one, so the rider's hunger bar shows its
     * hearts (vanilla does that for a living vehicle), and it wears its mount's health: see
     * {@link #showMountHealth}.
     */
    private final java.util.Map<UUID, ArmorStand> seats = new java.util.HashMap<>();

    /** Riders hidden from the player they ride: rider -> that player. */
    private final java.util.Map<UUID, UUID> hiddenFrom = new java.util.HashMap<>();

    @Override
    public boolean mount(UUID rider, UUID vehicle) {
        return mount(rider, vehicle, 0);
    }

    @Override
    public boolean mount(UUID rider, UUID vehicle, double lift) {
        Entity r = Bukkit.getEntity(rider), v = Bukkit.getEntity(vehicle);
        if (r == null || v == null || !r.isValid() || !v.isValid() || r.equals(v) || !r.getWorld().equals(v.getWorld())) {
            return false;
        }
        dismount(rider);
        if (r.isInsideVehicle()) r.leaveVehicle();
        r.setFallDistance(0);
        if (lift <= 0) { // right on them (riding a living mount shows its hearts)
            if (!v.addPassenger(r)) return false;
            hideFromMount(r, v);
            return true;
        }
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
            if (scale != null) scale.setBaseValue(lift / SMALL_STAND_HEIGHT);
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
        hideFromMount(r, v);
        return true;
    }

    private static org.bukkit.plugin.Plugin plugin() {
        return org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(BukkitMovementControl.class);
    }

    /** The player they ride doesn't see them. */
    private void hideFromMount(Entity rider, Entity mount) {
        if (!(mount instanceof org.bukkit.entity.Player viewer)) return;
        viewer.hideEntity(plugin(), rider);
        hiddenFrom.put(rider.getUniqueId(), mount.getUniqueId());
    }

    /** ...and sees them again once the ride's over. */
    private void revealToMount(UUID rider) {
        UUID mount = hiddenFrom.remove(rider);
        if (mount == null) return;
        org.bukkit.entity.Player viewer = Bukkit.getPlayer(mount);
        Entity r = Bukkit.getEntity(rider);
        if (viewer != null && r != null) viewer.showEntity(plugin(), r);
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
        revealToMount(rider);
    }

    /**
     * What they ride (through the seat). A seat that's come apart (its vehicle or rider gone) is cleared up, and
     * a rider who's off the player they were hidden from shows to them again.
     */
    @Override
    public java.util.Optional<UUID> vehicleOf(UUID rider) {
        Entity r = Bukkit.getEntity(rider);
        ArmorStand seat = seats.get(rider);
        if (seat != null && (r == null || !seat.isValid() || !seat.equals(r.getVehicle()) || seat.getVehicle() == null)) {
            dismount(rider);
            return java.util.Optional.empty();
        }
        Entity v = r == null ? null : underSeat(r.getVehicle());
        UUID hiddenOn = hiddenFrom.get(rider);
        if (hiddenOn != null && (v == null || !hiddenOn.equals(v.getUniqueId()))) revealToMount(rider); // off them
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

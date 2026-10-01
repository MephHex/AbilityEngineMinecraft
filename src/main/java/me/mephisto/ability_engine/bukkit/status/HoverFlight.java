package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.platform.VisualEntities;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.tag.Tags;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The hover passive ({@code hover:} in a kit) on Bukkit, three ways:
 * <ul>
 *   <li>{@code height: 0}: no hover, they walk as usual (and take falls as usual); only what they ride is
 *       shown.</li>
 *   <li>{@code fly: false}: always floating {@code height} blocks above the ground, walking on a floor only they
 *       see (see {@link #floatOn}). No flying.</li>
 *   <li>{@code fly: true}: the rest of this comment.</li>
 * </ul>
 * Creative-style flight (double-tap jump), held to at
 * most {@code height} blocks above the ground below. Above that they sink back down (a gentle push, keeping
 * their horizontal speed). The cap holds off during free flight ({@code state.flying}, e.g. an ultimate) and
 * dashes ({@code state.dashing}); when free flight ends they keep flying and sink back under the cap. They
 * can't fly while they can't move (stunned, rooted: they drop), never take fall damage, and fly at
 * {@code speed} x vanilla flight, scaled by their walking speed (slows and the character's speed count).
 * What they ride ({@code visual}) is at their feet: a block's real model turned upside down (a spore blossom
 * opens upward, a cup they sit in), or an item lying flat under them. Perched on someone, it's on that someone's
 * head (see {@link #drawRide}).
 */
public final class HoverFlight implements Listener {

    /** Vanilla walking speed (the movement attribute's base for players). */
    private static final double BASE_WALK = 0.1;
    /** Vanilla (creative) flying speed, what Player.setFlySpeed calls 1x. */
    private static final double VANILLA_FLY = 0.1;
    /** How far below the cap we look for ground: farther down counts as "way too high". */
    private static final double GROUND_LOOK_EXTRA = 8;

    private final AbilityEngine engine;
    /** Players whose flight we manage (so we only take away flight we gave). */
    private final Set<UUID> hovering = new HashSet<>();
    /** Players in free flight last tick: when it ends they keep flying. */
    private final Set<UUID> wasFree = new HashSet<>();
    /** Each player's position last tick: the server doesn't know a player's own velocity. */
    private final Map<UUID, Vector> lastPos = new HashMap<>();
    /** What each one rides, under their feet. */
    private final Map<UUID, Display> visuals = new HashMap<>();

    public HoverFlight(AbilityEngine engine) {
        this.engine = engine;
    }

    public void start() {
        engine.scheduler().every(1, 1, this::tick);
    }

    private void tick() {
        ticks++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            CharacterDef.Hover hover = engine.loadouts().characterOf(id).map(CharacterDef::hover).orElse(null);
            if (hover == null || p.isDead()) {
                if (hovering.remove(id)) release(p);
                continue;
            }
            hovering.add(id);
            Vector here = p.getLocation().toVector();
            Vector before = lastPos.put(id, here);
            drawRide(p, hover);
            if (p.isInsideVehicle() || creative(p)) {
                clearFloor(p);
                continue;
            }
            if (hover.height() <= 0) { // just the ride: no floor, no flying (unless free flight gives it)
                clearFloor(p);
                if (p.getAllowFlight() && !engine.tags().has(id, Tags.FLYING)) {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                }
                continue;
            }
            if (!hover.fly()) {
                floatOn(p, hover);
                continue;
            }

            boolean free = engine.tags().has(id, Tags.FLYING);
            if (engine.tags().has(id, Tags.BLOCK_MOVE)) { // stunned, rooted: no flying
                if (p.isFlying()) p.setFlying(false);
                if (p.getAllowFlight()) p.setAllowFlight(false);
                wasFree.remove(id);
                continue;
            }
            if (!p.getAllowFlight()) p.setAllowFlight(true);
            if (free) wasFree.add(id);
            else if (wasFree.remove(id) && !p.isOnGround()) p.setFlying(true); // free flight over: hover on

            float speed = (float) Math.max(0, Math.min(1, VANILLA_FLY * (free ? 1.0 : hover.speed()) * walkSpeed(p)));
            if (Math.abs(p.getFlySpeed() - speed) > 1e-4) p.setFlySpeed(speed);

            if (!free && p.isFlying() && !engine.tags().has(id, Tags.DASHING)) cap(p, hover.height(), before, here);
        }
    }

    // ---- floating (fly: false): a floor only they see, a fixed height above the ground ----------------------

    /** Columns around them that get a floor, each way (ahead of a sprint, with some ping). */
    private static final int FLOOR_REACH = 3;
    /** How far down a column is searched for ground; deeper, no floor there (they drop until there is). */
    private static final int FLOOR_DEPTH = 16;
    /** Every so often the whole floor is sent again (the server may have overwritten some of it). */
    private static final int FLOOR_RESEND_TICKS = 10;
    private static final org.bukkit.block.data.BlockData FLOOR = Material.BARRIER.createBlockData();

    private record Cell(int x, int y, int z) {}

    /** The floor each floating player has been sent, and in which world. */
    private record Floor(String world, Set<Cell> cells) {}

    private final Map<UUID, Floor> floors = new HashMap<>();
    /** Players whose whole floor should be sent again now (they clicked something: the server re-sent blocks). */
    private final Set<UUID> resend = new HashSet<>();
    private int ticks;

    /**
     * Float {@code height} blocks above the ground: invisible blocks only they see, that high above the real
     * ground in every column around them, so they walk (and jump, and drop off ledges) one level up, over
     * water too. Their own client does the collision, so it's as smooth as walking. No flying: they're allowed
     * to (the server sees them standing on air) but toggling it is refused. Free flight (state.flying) lifts
     * the floor while it lasts.
     */
    private void floatOn(Player p, CharacterDef.Hover hover) {
        UUID id = p.getUniqueId();
        if (engine.tags().has(id, Tags.FLYING)) { // free flight: no floor in the way
            clearFloor(p);
            wasFree.add(id);
            float speed = (float) Math.max(0, Math.min(1, VANILLA_FLY * walkSpeed(p)));
            if (Math.abs(p.getFlySpeed() - speed) > 1e-4) p.setFlySpeed(speed);
            return;
        }
        if (wasFree.remove(id) || p.isFlying()) p.setFlying(false); // down onto the floor
        if (!p.getAllowFlight()) p.setAllowFlight(true);             // or the server kicks them for "flying"
        int height = Math.max(1, (int) Math.round(hover.height()));

        Location feet = p.getLocation();
        org.bukkit.World w = p.getWorld();
        int fx = feet.getBlockX(), fz = feet.getBlockZ(), fy = (int) Math.floor(feet.getY() + 1e-3);
        Set<Cell> want = new HashSet<>();
        Cell under = null;
        for (int dx = -FLOOR_REACH; dx <= FLOOR_REACH; dx++) {
            for (int dz = -FLOOR_REACH; dz <= FLOOR_REACH; dz++) {
                Integer ground = groundAt(w, fx + dx, fy, fz + dz);
                if (ground == null) continue;
                int y = ground + height;
                org.bukkit.block.Block cell = w.getBlockAt(fx + dx, y, fz + dz);
                if (!cell.isPassable() || cell.isLiquid()) continue; // something real is there
                Cell c = new Cell(fx + dx, y, fz + dz);
                want.add(c);
                if (dx == 0 && dz == 0) under = c;
            }
        }

        Floor had = floors.get(id);
        Set<Cell> before = had != null && had.world().equals(w.getName()) ? had.cells() : Set.of();
        boolean all = resend.remove(id) || ticks % FLOOR_RESEND_TICKS == 0;
        Map<io.papermc.paper.math.Position, org.bukkit.block.data.BlockData> send = new HashMap<>();
        for (Cell c : want) {
            if (all || !before.contains(c)) send.put(io.papermc.paper.math.Position.block(c.x(), c.y(), c.z()), FLOOR);
        }
        for (Cell c : before) {
            if (!want.contains(c)) send.put(io.papermc.paper.math.Position.block(c.x(), c.y(), c.z()),
                    w.getBlockAt(c.x(), c.y(), c.z()).getBlockData());
        }
        if (!send.isEmpty()) p.sendMultiBlockChange(send);
        floors.put(id, new Floor(w.getName(), want));

        // Below their floor, on the real ground or between it and the floor (just arrived, a dismount): up onto
        // it. Their client can't climb out of a block it's inside, or up through one over its head.
        if (under != null && feet.getY() >= under.y() - height + 1 - 1e-3 && feet.getY() < under.y() + 1 - 1e-3) {
            Location up = feet.clone();
            up.setY(under.y() + 1);
            p.teleport(up, me.mephisto.ability_engine.bukkit.platform.BukkitMovementControl.KEEP_RIDERS,
                    io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_X,
                    io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_Z);
        }
    }

    /** The ground in a column: the first solid block or liquid at or below {@code fromY}; null if none close. */
    private static Integer groundAt(org.bukkit.World w, int x, int fromY, int z) {
        int min = Math.max(w.getMinHeight(), fromY - FLOOR_DEPTH);
        for (int y = Math.min(fromY, w.getMaxHeight() - 1); y >= min; y--) {
            org.bukkit.block.Block b = w.getBlockAt(x, y, z);
            if (!b.isPassable() || b.isLiquid()) return y;
        }
        return null;
    }

    /** Take their floor away (what's really there is sent back). */
    private void clearFloor(Player p) {
        Floor had = floors.remove(p.getUniqueId());
        if (had == null || !had.world().equals(p.getWorld().getName())) return;
        Map<io.papermc.paper.math.Position, org.bukkit.block.data.BlockData> send = new HashMap<>();
        for (Cell c : had.cells()) {
            send.put(io.papermc.paper.math.Position.block(c.x(), c.y(), c.z()),
                    p.getWorld().getBlockAt(c.x(), c.y(), c.z()).getBlockData());
        }
        if (!send.isEmpty()) p.sendMultiBlockChange(send);
    }

    /** A floating player can't take off (they're allowed to fly only so the server doesn't kick them). */
    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        if (!event.isFlying() || creative(p) || engine.tags().has(p.getUniqueId(), Tags.FLYING)) return;
        boolean floating = engine.loadouts().characterOf(p.getUniqueId()).map(CharacterDef::hover)
                .filter(h -> !h.fly()).isPresent();
        if (floating) event.setCancelled(true);
    }

    /** A click near the floor makes the server re-send those blocks (as air): put the floor back next tick. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (floors.containsKey(event.getPlayer().getUniqueId())) resend.add(event.getPlayer().getUniqueId());
    }

    /** Above the cap: sink back down, keeping the horizontal speed they have. */
    private void cap(Player p, double height, Vector before, Vector here) {
        double above = heightAboveGround(p, height + GROUND_LOOK_EXTRA);
        double excess = above - height;
        if (excess <= 0.02) return;
        double down = -Math.min(0.8, 0.08 + 0.3 * excess);
        Vector moving = before == null ? new Vector() : here.clone().subtract(before);
        p.setVelocity(new Vector(moving.getX(), down, moving.getZ()));
    }

    /** Feet to the ground (or water) below; {@code maxLook} if there's none that close. */
    private static double heightAboveGround(Player p, double maxLook) {
        Location feet = p.getLocation();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(feet, new Vector(0, -1, 0), maxLook, FluidCollisionMode.ALWAYS, true);
        return hit == null ? maxLook : feet.getY() - hit.getHitPosition().getY();
    }

    /** The movement attribute over vanilla walking: the character's speed, slows and haste. */
    private static double walkSpeed(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        return attr == null ? 1 : attr.getValue() / BASE_WALK;
    }

    private static boolean creative(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    /** No longer a hover character: their flight goes back to what their game mode allows. */
    private void release(Player p) {
        UUID id = p.getUniqueId();
        wasFree.remove(id);
        lastPos.remove(id);
        removeRide(id);
        clearFloor(p);
        resend.remove(id);
        if (!creative(p) && !engine.tags().has(id, Tags.FLYING)) {
            p.setFlying(false);
            p.setAllowFlight(false);
            p.setFallDistance(0);
        }
        p.setFlySpeed((float) VANILLA_FLY);
    }

    // ---- what they ride -------------------------------------------------------------------------------

    /** Whose head each one's ride is on (absent: at their own feet). */
    private final Map<UUID, UUID> perchedOn = new HashMap<>();

    /**
     * What they ride, at their feet. Perched on someone (a ride, e.g. the Fae's Perch, sitting in it), it sits on
     * top of that someone's head instead, riding along with them, and shows even if they're hidden. Perching, hopping off or over to someone else, the old one goes and a new
     * one appears where it belongs: nothing is left behind on anyone's head.
     */
    private void drawRide(Player p, CharacterDef.Hover hover) {
        UUID id = p.getUniqueId();
        Material material = hover.visual() == null ? null : Material.matchMaterial(hover.visual());
        Entity mount = p.isInsideVehicle() ? engine.rides().mountOf(id).map(Bukkit::getEntity).orElse(null) : null;
        boolean shown = material != null && material.isItem() && p.getGameMode() != GameMode.SPECTATOR
                && (mount != null || (!p.isInvisible() && !engine.tags().has(id, Tags.HIDDEN)));
        if (!shown) {
            removeRide(id);
            return;
        }
        UUID on = mount == null ? null : mount.getUniqueId();
        Display display = visuals.get(id);
        if (display == null || !display.isValid() || !display.getWorld().equals(p.getWorld())
                || !java.util.Objects.equals(on, perchedOn.get(id))) { // new, or perched / off since: a new one
            removeRide(id);
            float size = (float) (p.getBoundingBox().getWidthX() / 0.6); // follows the character's scale (1 block at 1.0)
            Location at = mount != null ? onHead(mount) : feet(p);
            display = material.isBlock() ? uprightBlock(at, material, SEAT_SCALE * size) : flatItem(at, material, 0.9f * size);
            visuals.put(id, display);
            if (mount != null) {
                perchedOn.put(id, on);
                mount.addPassenger(display); // on their head: it rides them (a passenger sits on top), no lag
            }
            return;
        }
        if (mount == null) display.teleport(feet(p));
        else if (display.getVehicle() == null) display.teleport(onHead(mount)); // couldn't ride them: follow
    }

    private static Location feet(Player p) {
        Location at = p.getLocation();
        at.setPitch(0);
        return at;
    }

    /** On top of someone's head. */
    private static Location onHead(Entity mount) {
        Location at = mount.getLocation();
        at.setY(mount.getBoundingBox().getMaxY());
        at.setPitch(0);
        return at;
    }

    /** The ride sits a hair above where it's put: not to flicker with the ground (or the head) under it. */
    private static final float GAP = 0.03f;

    /**
     * A block ride's size over the character's (1 block at scale 1.0), so they sit in it. A spore blossom turned
     * over is a cup: its base leaves at the bottom, four petals flaring up and out from the middle to about 0.4 of
     * its height (they droop 22.5 degrees from the top in the vanilla model). At 1.0 the rim is about at the knees
     * (legs are 0.375 of a player's height) and the petals spread about 1.75x their width.
     */
    private static final float SEAT_SCALE = 1.0f;

    /**
     * A block's model turned upside down (a hanging spore blossom opens upward), centred on the spot: its top
     * (where it would hang from) at the bottom, at the spot's height, everything else above it.
     */
    private static Display uprightBlock(Location at, Material material, float s) {
        return at.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(material.createBlockData());
            setUp(d);
            // rotateX(pi): (x, y, z) -> (x, -y, -z); then shift so x and z are centred and y runs 0..s (+ the gap)
            d.setTransformation(new Transformation(new Vector3f(-s / 2, s + GAP, s / 2),
                    new Quaternionf().rotateX((float) Math.PI), new Vector3f(s, s, s), new Quaternionf()));
        });
    }

    /** An item lying flat, like a lily pad. */
    private static Display flatItem(Location at, Material material, float s) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(material));
            setUp(d);
            d.setTransformation(new Transformation(new Vector3f(0, GAP, 0),
                    new Quaternionf().rotateX((float) (Math.PI / 2)), new Vector3f(s, s, s), new Quaternionf()));
        });
    }

    private static void setUp(Display d) {
        d.setPersistent(false);
        d.setTeleportDuration(2); // the client glides it between our moves
        VisualEntities.mark(d);
    }

    /** Take it away: off whoever's head it was on first, then gone. */
    private void removeRide(UUID id) {
        perchedOn.remove(id);
        Display d = visuals.remove(id);
        if (d == null) return;
        Entity vehicle = d.getVehicle();
        if (vehicle != null) vehicle.removePassenger(d);
        d.remove();
    }

    // ---- events ----------------------------------------------------------------------------------------

    /** A hovering fae never takes fall damage: it would have caught itself. (Just riding the visual: it does.) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !hovering.contains(id)) return;
        boolean hovers = engine.loadouts().characterOf(id).map(CharacterDef::hover).filter(h -> h.height() > 0).isPresent();
        if (hovers) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (hovering.remove(id)) release(event.getPlayer());
    }

    /** Plugin disable: take the visuals away (they aren't saved anyway). */
    public void stop() {
        for (UUID id : Set.copyOf(visuals.keySet())) removeRide(id);
    }
}

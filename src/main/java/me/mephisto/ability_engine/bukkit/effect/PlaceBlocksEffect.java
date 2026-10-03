package me.mephisto.ability_engine.bukkit.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Effect id "place_blocks": real blocks for a while (an ice wall, an ice tomb), then the terrain exactly as it was.
 * Params:
 * <ul>
 *   <li>{@code shape: wall} (on a spot, e.g. the aim): a wall {@code width} blocks long and {@code height} tall,
 *       standing on the spot, across the line from the caster to it (facing the caster)</li>
 *   <li>{@code shape: tomb} (on an entity): a pillar around them, 3x3 and {@code height} tall with a lid, leaving
 *       only the column they stand in empty</li>
 *   <li>{@code block} (default PACKED_ICE), {@code duration} ticks (required)</li>
 * </ul>
 * Only air and replaceable blocks (grass, snow layers...) are replaced. Anyone standing where it goes is shoved out of
 * the way first (a wall: to whichever side of it they're on; a tomb: out of the pillar, away from whoever's buried), so
 * it goes up whole. Placed blocks can't be broken, blown up, pushed or melted; they're all put back when the plugin
 * stops.
 */
public final class PlaceBlocksEffect implements Effect {

    /** Every block we placed: where, and what was there before. */
    private static final Map<Location, BlockData> PLACED = new HashMap<>();

    @Override
    public void apply(EffectContext ctx) {
        Params p = ctx.params();
        Material material = material(p);
        int duration = p.requireInt("duration");
        int height = p.getInt("height", 3);
        List<Block> blocks = new ArrayList<>();
        String shape = p.getString("shape", "wall").toLowerCase(Locale.ROOT);
        if (shape.equals("tomb")) {
            if (!(ctx.target() instanceof EntityTarget t)) return;
            Entity e = Bukkit.getEntity(t.id());
            if (e == null) return;
            Block feet = e.getLocation().getBlock();
            for (int dy = 0; dy <= height; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean column = dx == 0 && dz == 0;
                        if (column && dy < height) continue; // the space they're in (the lid closes it above)
                        blocks.add(feet.getRelative(dx, dy, dz));
                    }
                }
            }
            Location center = feet.getLocation().add(0.5, 0, 0.5);
            shoveOut(blocks, e, other -> { // out of the pillar, straight away from its middle
                Vector away = other.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (away.lengthSquared() < 1e-6) away = new Vector(1, 0, 0);
                return center.clone().add(away.normalize().multiply(2.3));
            });
            place(blocks, material, duration, e);
            return;
        }
        // wall
        Location spot = spot(ctx);
        Entity caster = Bukkit.getEntity(ctx.caster());
        if (spot == null || caster == null || !spot.getWorld().equals(caster.getWorld())) return;
        Vector forward = spot.toVector().subtract(caster.getLocation().toVector()).setY(0);
        if (forward.lengthSquared() < 1e-6) forward = caster.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1e-6) forward = new Vector(1, 0, 0);
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        double width = p.getDouble("width", 7);
        int baseY = (int) Math.floor(spot.getY() + 0.01);
        java.util.Set<Block> seen = new java.util.LinkedHashSet<>();
        for (double s = -width / 2 + 0.5; s <= width / 2 - 0.5 + 1e-6; s += 0.5) { // half steps: no gaps on a diagonal
            Vector at = spot.toVector().add(right.clone().multiply(s));
            for (int dy = 0; dy < height; dy++) {
                seen.add(spot.getWorld().getBlockAt(at.getBlockX(), baseY + dy, at.getBlockZ()));
            }
        }
        blocks.addAll(seen);
        Vector plane = forward.clone();
        shoveOut(blocks, null, other -> { // to whichever side of the wall they're on (on it: away from the caster)
            Vector from = other.getLocation().toVector().subtract(spot.toVector());
            double side = from.dot(plane);
            double sign = side < -1e-3 ? -1 : 1;
            return other.getLocation().add(plane.clone().multiply(sign * 1.3 - side));
        });
        place(blocks, material, duration, null);
    }

    private static Location spot(EffectContext ctx) {
        if (ctx.target() instanceof EntityTarget t) {
            Entity e = Bukkit.getEntity(t.id());
            return e == null ? null : e.getLocation();
        }
        if (ctx.target() instanceof PointTarget pt) {
            World w = Bukkit.getWorld(pt.world());
            return w == null ? null : new Location(w, pt.position().x(), pt.position().y(), pt.position().z());
        }
        return null;
    }

    /** Place them (where there's room), and take them away again after {@code duration} ticks. */
    private static void place(List<Block> blocks, Material material, int duration, Entity inside) {
        List<Location> mine = new ArrayList<>();
        for (Block b : blocks) {
            Location key = b.getLocation();
            if (PLACED.containsKey(key)) continue;                       // already one of ours
            if (!b.getType().isAir() && !b.isReplaceable()) continue;   // terrain stays as it is
            if (occupied(b, inside)) continue;                           // never inside someone
            PLACED.put(key, b.getBlockData().clone());
            b.setType(material, false);
            mine.add(key);
        }
        if (mine.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(PlaceBlocksEffect.class), () -> {
            for (Location key : mine) restore(key, material);
        }, Math.max(1, duration));
    }

    /**
     * Everyone (but {@code except}) standing where these blocks go is moved to {@code to.apply(them)}, facing the same
     * way, before they're placed: the blocks go up whole and nobody ends up inside them.
     */
    private static void shoveOut(List<Block> blocks, Entity except, java.util.function.Function<Entity, Location> to) {
        java.util.Set<Entity> inTheWay = new java.util.LinkedHashSet<>();
        for (Block b : blocks) {
            if (!b.getType().isAir() && !b.isReplaceable()) continue;
            BoundingBox box = BoundingBox.of(b);
            for (Entity e : b.getWorld().getNearbyEntities(box)) {
                if (e instanceof LivingEntity && e != except && e.getBoundingBox().overlaps(box)) inTheWay.add(e);
            }
        }
        for (Entity e : inTheWay) {
            Location dest = to.apply(e);
            dest.setYaw(e.getLocation().getYaw());
            dest.setPitch(e.getLocation().getPitch());
            e.teleport(dest);
            e.setVelocity(e.getVelocity().setX(0).setZ(0));
        }
    }

    private static boolean occupied(Block b, Entity except) {
        BoundingBox box = BoundingBox.of(b);
        for (Entity e : b.getWorld().getNearbyEntities(box)) {
            if (e instanceof LivingEntity && e != except && e.getBoundingBox().overlaps(box)) return true;
        }
        return false;
    }

    private static void restore(Location key, Material material) {
        BlockData before = PLACED.remove(key);
        if (before == null) return;
        Block b = key.getBlock();
        if (b.getType() == material) b.setBlockData(before, false);
    }

    /** Everything placed goes back now (the plugin is stopping). */
    public static void restoreAll() {
        for (Map.Entry<Location, BlockData> e : new ArrayList<>(PLACED.entrySet())) {
            e.getKey().getBlock().setBlockData(e.getValue(), false);
        }
        PLACED.clear();
    }

    public static boolean isPlaced(Block b) { return PLACED.containsKey(b.getLocation()); }

    private static Material material(Params p) {
        String name = p.getString("block", "PACKED_ICE");
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isBlock()) throw p.error("block", "not a block: " + name);
        return m;
    }

    @Override
    public void validate(Params params) {
        params.requireInt("duration");
        material(params);
        String shape = params.getString("shape", "wall").toLowerCase(Locale.ROOT);
        if (!shape.equals("wall") && !shape.equals("tomb")) throw params.error("shape", "expected wall or tomb");
    }

    /** Placed blocks can't be broken, blown up, pushed or melted away early. */
    public static final class Protection implements Listener {
        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) { if (isPlaced(event.getBlock())) event.setCancelled(true); }

        @EventHandler(ignoreCancelled = true)
        public void onFade(BlockFadeEvent event) { if (isPlaced(event.getBlock())) event.setCancelled(true); }

        @EventHandler(ignoreCancelled = true)
        public void onExplode(EntityExplodeEvent event) { event.blockList().removeIf(PlaceBlocksEffect::isPlaced); }

        @EventHandler(ignoreCancelled = true)
        public void onBlockExplode(BlockExplodeEvent event) { event.blockList().removeIf(PlaceBlocksEffect::isPlaced); }

        @EventHandler(ignoreCancelled = true)
        public void onPistonExtend(BlockPistonExtendEvent event) {
            if (event.getBlocks().stream().anyMatch(PlaceBlocksEffect::isPlaced)) event.setCancelled(true);
        }

        @EventHandler(ignoreCancelled = true)
        public void onPistonRetract(BlockPistonRetractEvent event) {
            if (event.getBlocks().stream().anyMatch(PlaceBlocksEffect::isPlaced)) event.setCancelled(true);
        }
    }
}

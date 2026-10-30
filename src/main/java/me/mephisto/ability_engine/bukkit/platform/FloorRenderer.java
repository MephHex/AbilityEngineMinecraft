package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.floor.Floors;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Draws the floors (the Amethyst's crystal floor, see Floors): a flat disc of amethyst a hair above the ground, made of
 * four thin squares turned 22.5 degrees from each other (together a 16-sided disc within 2% of round), and a light
 * shimmer over it a few times a second. No real blocks. {@link #spawnFloor} is the one place that builds the look (a
 * model can replace it later). A square one (the Fae Trickster's Trick Room) is drawn as a glowing pink outline instead
 * ({@link #spawnRoom}), with motes rising off its edges and fainter guide outlines a block above and below its floor.
 */
public final class FloorRenderer implements Floors.Listener {

    private static final int SHIMMER_EVERY = 5;
    /** How thick the disc is (blocks), and how far above the ground. */
    private static final float THICKNESS = 0.03f;
    private static final double LIFT = 0.01;
    private static final Particle.DustOptions PALE = new Particle.DustOptions(Color.fromRGB(220, 180, 255), 0.7f);
    private static final Particle.DustOptions VIOLET = new Particle.DustOptions(Color.fromRGB(170, 90, 230), 0.9f);

    private final AbilityEngine engine;
    private final Plugin plugin;
    private final Map<Floors.Patch, List<Display>> shown = new HashMap<>();

    public FloorRenderer(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
    }

    /** The renderer running now (for cues that light up a floor). */
    private static FloorRenderer current;

    public void start() {
        current = this;
        engine.floors().addListener(this);
        Bukkit.getScheduler().runTaskTimer(plugin, this::shimmer, SHIMMER_EVERY, SHIMMER_EVERY);
        Bukkit.getScheduler().runTaskTimer(plugin, this::showWalls, WALLS_EVERY, WALLS_EVERY);
    }

    @Override
    public void added(Floors.Patch patch) {
        if (!patch.drawn()) return; // (not drawn: something else shows it)
        shown.put(patch, patch.lit() ? spawnLit(patch) : patch.square() ? spawnRoom(patch) : spawnFloor(patch));
    }

    /**
     * A room's floor: a checkerboard of glass on the ground at the bottom of it, magenta and purple; light blue and white
     * while it's lit (Topsy-Turvy). Never glowing: that would show the room to everyone through walls.
     */
    private static final Material ROOM_DARK = Material.MAGENTA_STAINED_GLASS, ROOM_LIGHT = Material.PURPLE_STAINED_GLASS;
    private static final Material LIT_DARK = Material.LIGHT_BLUE_STAINED_GLASS, LIT_LIGHT = Material.WHITE_STAINED_GLASS;
    private static final float PANE = 0.02f;
    private static final Particle.DustOptions LIT_BLUE = new Particle.DustOptions(Color.fromRGB(120, 205, 255), 0.8f);
    private static final Particle.DustOptions LIT_WHITE = new Particle.DustOptions(Color.fromRGB(225, 245, 255), 0.7f);

    /** A checkerboard floor: its panes on the one colour's squares and on the other's. */
    private record Checkers(List<BlockDisplay> dark, List<BlockDisplay> light) {
        List<Display> all() {
            List<Display> out = new ArrayList<>(dark);
            out.addAll(light);
            return out;
        }

        void recolor(Material darkBlock, Material lightBlock, Color glow) {
            FloorRenderer.recolor(dark, darkBlock, glow);
            FloorRenderer.recolor(light, lightBlock, glow);
        }
    }

    /** Each drawn room's floor (to turn light blue while a lit floor lies over it). */
    private final Map<Floors.Patch, Checkers> roomFloors = new HashMap<>();
    /** Each lit floor drawn by turning the room it's laid over light blue: lit -> that room. */
    private final Map<Floors.Patch, Floors.Patch> litOver = new HashMap<>();

    /**
     * A lit floor (a room while Topsy-Turvy holds it). Laid over a drawn room: that room's floor turns light blue
     * for as long as it's there (nothing of its own). Otherwise its own light blue floor, on the ground in it.
     */
    private List<Display> spawnLit(Floors.Patch patch) {
        World w = Bukkit.getWorld(patch.world());
        if (w == null) return List.of();
        if (patch.over() != null) {
            for (Floors.Patch room : engine.floors().of(patch.owner())) {
                Checkers floor = roomFloors.get(room);
                if (floor == null || !patch.over().equals(room.name())) continue;
                floor.recolor(LIT_DARK, LIT_LIGHT, null); // (no glow: it would show the room through walls)
                litOver.put(patch, room);
                return List.of();
            }
        }
        return groundPanes(w, patch, LIT_DARK, LIT_LIGHT, null).all();
    }

    private static void recolor(List<BlockDisplay> panes, Material block, Color glow) {
        for (BlockDisplay d : panes) {
            if (!d.isValid()) continue;
            d.setBlock(block.createBlockData());
            d.setGlowing(glow != null);
            if (glow != null) d.setGlowColorOverride(glow);
        }
    }

    /**
     * A room's floor: in every block column of it, a thin glass pane on the ground there within its reach (the first solid
     * top going down from its {@code up} above its floor to its {@code down} below), so it shows at the bottom of the
     * whole room wherever the ground is: put down on a block a step above the ground around it, the floor shows down on
     * that ground. A column that's a wall all the way up, or has no ground in reach, has none. The columns alternate
     * between the two colours like a checkerboard (by the block they're on, so it lines up with the world's grid).
     */
    private Checkers groundPanes(World w, Floors.Patch patch, Material dark, Material light, Color glow) {
        var audience = engine.audienceOf(patch.owner());
        double base = patch.center().y();
        double top = base + (Double.isNaN(patch.up()) ? 1 : patch.up());
        double bottom = base - (Double.isNaN(patch.down()) ? 0.1 : patch.down());
        int x0 = (int) Math.round(patch.center().x() - patch.radius());
        int z0 = (int) Math.round(patch.center().z() - patch.radius());
        int n = (int) Math.round(patch.radius() * 2);
        Checkers out = new Checkers(new ArrayList<>(), new ArrayList<>());
        for (int dz = 0; dz < n; dz++) {
            for (int dx = 0; dx < n; dx++) {
                int x = x0 + dx, z = z0 + dz;
                Double ground = groundIn(w, x, z, top, bottom);
                if (ground == null) continue;
                boolean isDark = Math.floorMod(x + z, 2) == 0;
                Material block = isDark ? dark : light;
                Location at = new Location(w, x, ground + 0.005, z);
                (isDark ? out.dark() : out.light()).add(w.spawn(at, BlockDisplay.class, d -> {
                    d.setBlock(block.createBlockData());
                    d.setPersistent(false);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setGlowing(glow != null);
                    if (glow != null) d.setGlowColorOverride(glow);
                    VisualEntities.mark(d);
                    if (!audience.isEmpty()) VisualEntities.restrict(d, audience);
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                            new Vector3f(1, PANE, 1), new Quaternionf()));
                }));
            }
        }
        return out;
    }

    /** A room's walls, and who sees them right now (whoever is inside it). */
    private record Walls(List<BlockDisplay> panes, java.util.Set<java.util.UUID> shownTo) {}

    private final Map<Floors.Patch, Walls> roomWalls = new HashMap<>();
    /** How often who's inside a room (and sees its walls) is looked at (ticks). */
    private static final int WALLS_EVERY = 4;
    /** A room's walls run from this far below its floor... */
    private static final int WALL_BELOW = 1;
    /** ...to this far above the top of its reach (a head's height). */
    private static final int WALL_ABOVE = 2;

    /**
     * A room's four walls along its edges, a checkerboard of thin glass panes a block each (the same colours as its floor),
     * from a block below its floor to two above the top of its reach. Nobody sees them until they're inside (showWalls).
     */
    private List<BlockDisplay> wallPanes(World w, Floors.Patch patch) {
        int x0 = (int) Math.round(patch.center().x() - patch.radius());
        int z0 = (int) Math.round(patch.center().z() - patch.radius());
        int n = (int) Math.round(patch.radius() * 2);
        double up = Double.isNaN(patch.up()) ? 1 : patch.up();
        int y0 = (int) Math.floor(patch.center().y()) - WALL_BELOW;
        int rows = (int) Math.ceil(up) + WALL_BELOW + WALL_ABOVE;
        List<BlockDisplay> out = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            int y = y0 + row;
            for (int i = 0; i < n; i++) {
                // (x, z, size x, size z) of each wall's cell: north and south run along x, west and east along z
                out.add(wallCell(w, x0 + i, y, z0, 1, PANE, x0 + i + y));
                out.add(wallCell(w, x0 + i, y, z0 + n - PANE, 1, PANE, x0 + i + y));
                out.add(wallCell(w, x0, y, z0 + i, PANE, 1, z0 + i + y));
                out.add(wallCell(w, x0 + n - PANE, y, z0 + i, PANE, 1, z0 + i + y));
            }
        }
        return out;
    }

    private BlockDisplay wallCell(World w, double x, int y, double z, float sizeX, float sizeZ, int parity) {
        Material block = Math.floorMod(parity, 2) == 0 ? ROOM_DARK : ROOM_LIGHT;
        return w.spawn(new Location(w, x, y, z), BlockDisplay.class, d -> {
            d.setVisibleByDefault(false); // (only those inside see it)
            d.setBlock(block.createBlockData());
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            VisualEntities.mark(d);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sizeX, 1, sizeZ),
                    new Quaternionf()));
        });
    }

    /** Every few ticks: each room's walls shown to the players inside it (who may see the room) and hidden from the rest. */
    private void showWalls() {
        for (var e : roomWalls.entrySet()) {
            Floors.Patch room = e.getKey();
            Walls walls = e.getValue();
            World w = Bukkit.getWorld(room.world());
            if (w == null) continue;
            var audience = engine.audienceOf(room.owner());
            java.util.Set<java.util.UUID> inside = new java.util.HashSet<>();
            for (org.bukkit.entity.Player pl : w.getPlayers()) {
                if (!audience.isEmpty() && !audience.contains(pl.getUniqueId())) continue;
                Location at = pl.getLocation();
                var middle = new me.mephisto.ability_engine.engine.math.Vec3(at.getX(), at.getY() + pl.getHeight() / 2, at.getZ());
                if (room.contains(room.world(), middle)) inside.add(pl.getUniqueId());
            }
            for (java.util.UUID id : inside) {
                if (!walls.shownTo().add(id)) continue; // (already sees them)
                org.bukkit.entity.Player pl = Bukkit.getPlayer(id);
                if (pl != null) for (BlockDisplay d : walls.panes()) if (d.isValid()) pl.showEntity(plugin, d);
            }
            for (java.util.UUID id : List.copyOf(walls.shownTo())) {
                if (inside.contains(id)) continue;
                walls.shownTo().remove(id);
                org.bukkit.entity.Player pl = Bukkit.getPlayer(id);
                if (pl != null) for (BlockDisplay d : walls.panes()) if (d.isValid()) pl.hideEntity(plugin, d);
            }
        }
    }

    /**
     * The top of the ground in this column between {@code top} and {@code bottom}: the highest solid top there with room
     * to stand above it, so a roof or a ceiling over the room is passed by (null: solid all the way, a wall; or no ground).
     */
    private static Double groundIn(World w, int x, int z, double top, double bottom) {
        boolean openAbove = w.getBlockAt(x, (int) Math.floor(top) + 1, z).isPassable();
        for (int y = (int) Math.floor(top); y >= (int) Math.floor(bottom) - 1; y--) {
            var b = w.getBlockAt(x, y, z);
            if (b.isPassable()) {
                openAbove = true;
                continue;
            }
            var box = b.getBoundingBox();
            double surface = box.getVolume() > 0 ? box.getMaxY() : y + 1;
            boolean ground = openAbove && surface <= top + 1e-6; // (solid above it: a ceiling or the inside of a wall)
            openAbove = false;
            if (!ground) continue;
            return surface < bottom - 1e-6 ? null : surface;
        }
        return null;
    }

    /** A room's edge: how wide each glowing strip is, and the colours over it. */
    private static final float EDGE = 0.12f;
    private static final float GUIDE_EDGE = 0.06f;
    private static final Particle.DustOptions ROOM_PINK = new Particle.DustOptions(Color.fromRGB(255, 150, 230), 0.8f);
    private static final Particle.DustOptions ROOM_LILAC = new Particle.DustOptions(Color.fromRGB(190, 140, 255), 0.8f);

    /**
     * The heights of a room's guide rings: one each block up to {@link #RINGS_UP} above its floor (so it shows from
     * outside), and below it every whole block short of its {@code down}.
     */
    private static List<Double> guideLayers(Floors.Patch patch) {
        List<Double> out = new ArrayList<>();
        double down = Double.isNaN(patch.down()) ? 2 : patch.down();
        for (int dy = 1; dy <= RINGS_UP; dy++) out.add((double) dy);
        for (int dy = 1; dy < down; dy++) out.add((double) -dy);
        return out;
    }

    /** A room's outline rings stack this many blocks up from its floor (one each block), whatever its reach. */
    private static final int RINGS_UP = 3;

    /** One pink strip along a room's edge ({x, z, size x, size z} from its centre); {@code glowing}: with the glow. */
    private BlockDisplay edgeStrip(World w, Location at, float[] strip, boolean glowing, java.util.Set<java.util.UUID> audience) {
        return w.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(Material.PINK_CONCRETE.createBlockData());
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            if (glowing) { // (the owner's alone: nobody else is ever sent it)
                d.setVisibleByDefault(false);
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(255, 120, 220));
            } else if (!audience.isEmpty()) {
                VisualEntities.restrict(d, audience);
            }
            VisualEntities.mark(d);
            d.setTransformation(new Transformation(new Vector3f(strip[0], 0, strip[1]), new Quaternionf(),
                    new Vector3f(strip[2], THICKNESS, strip[3]), new Quaternionf()));
        });
    }

    /**
     * A square floor's look (Trick Room): its outline on the ground, four thin pink strips along its edges (glowing for
     * its owner only). No real blocks.
     */
    private List<Display> spawnRoom(Floors.Patch patch) {
        World w = Bukkit.getWorld(patch.world());
        if (w == null) return List.of();
        var audience = engine.audienceOf(patch.owner());
        float half = (float) patch.radius();
        float side = half * 2;
        List<Display> parts = new ArrayList<>();
        // (x offset, z offset, size x, size z) of each strip, from the room's centre
        float[][] strips = {
                {-half, -half, side, EDGE}, {-half, half - EDGE, side, EDGE},
                {-half, -half, EDGE, side}, {half - EDGE, -half, EDGE, side}};
        Location at = new Location(w, patch.center().x(), patch.center().y() + LIFT, patch.center().z());
        // The edges glow (through walls) for the room's owner only: Minecraft has no per-viewer glow, so the owner sees
        // a glowing twin of each strip and everyone else a plain one (no giving the room away through a wall)
        org.bukkit.entity.Player owner = Bukkit.getPlayer(patch.owner());
        for (float[] strip : strips) {
            BlockDisplay plain = edgeStrip(w, at, strip, false, audience);
            parts.add(plain);
            if (owner == null) continue;
            BlockDisplay glowing = edgeStrip(w, at, strip, true, audience);
            owner.showEntity(plugin, glowing);
            owner.hideEntity(plugin, plain);
            parts.add(glowing);
        }
        // Guide layers above and below, one at each whole block of its reach (its up and down) short of where it ends: the
        // same outline, thinner, see-through and without the glow
        float g = GUIDE_EDGE;
        float[][] guides = {
                {-half, -half, side, g}, {-half, half - g, side, g},
                {-half, -half, g, side}, {half - g, -half, g, side}};
        for (double dy : guideLayers(patch)) {
            Location layer = at.clone().add(0, dy, 0);
            for (float[] strip : guides) {
                parts.add(w.spawn(layer, BlockDisplay.class, d -> {
                    d.setBlock(Material.PINK_STAINED_GLASS.createBlockData());
                    d.setPersistent(false);
                    d.setBrightness(new Display.Brightness(15, 15));
                    VisualEntities.mark(d);
                    if (!audience.isEmpty()) VisualEntities.restrict(d, audience);
                    d.setTransformation(new Transformation(new Vector3f(strip[0], 0, strip[1]), new Quaternionf(),
                            new Vector3f(strip[2], THICKNESS, strip[3]), new Quaternionf()));
                }));
            }
        }
        // Its floor: magenta glass on the ground at the bottom of the room (light blue while Topsy-Turvy lights it)
        Checkers floor = groundPanes(w, patch, ROOM_DARK, ROOM_LIGHT, null);
        roomFloors.put(patch, floor);
        parts.addAll(floor.all());
        // Its walls: checkered too, seen only by whoever is inside it (they never light up)
        List<BlockDisplay> walls = wallPanes(w, patch);
        roomWalls.put(patch, new Walls(walls, new java.util.HashSet<>()));
        parts.addAll(walls);
        w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.4f);
        return parts;
    }

    /**
     * The moment a room (a square floor) that {@code at} is in, or the nearest one around it, lights up: sparkles burst all
     * across it and a chime (Topsy-Turvy cast inside Trick Room; the glow that stays is the lit floor laid over it,
     * {@link #spawnLit}). Nothing if there's none.
     */
    static void lightUp(Location at) {
        FloorRenderer r = current;
        if (r == null || at.getWorld() == null) return;
        Floors.Patch room = null;
        double best = Double.MAX_VALUE;
        for (Floors.Patch p : r.engine.floors().active()) {
            if (!p.square() || !p.world().equals(at.getWorld().getName())) continue;
            double dx = Math.abs(at.getX() - p.center().x()), dz = Math.abs(at.getZ() - p.center().z());
            double dy = Math.abs(at.getY() - p.center().y());
            if (dx > p.radius() + 0.5 || dz > p.radius() + 0.5 || dy > 4) continue;
            double d = dx * dx + dz * dz + dy * dy;
            if (d < best) {
                best = d;
                room = p;
            }
        }
        if (room != null) r.light(room);
    }

    private void light(Floors.Patch room) {
        World w = Bukkit.getWorld(room.world());
        if (w == null) return;
        float half = (float) room.radius();
        float side = half * 2;
        // (the glow itself is the lit floor laid over the room meanwhile: spawnLit)
        Location at = new Location(w, room.center().x(), room.center().y() + LIFT * 2, room.center().z());
        Location mid = at.clone().add(0, 0.3, 0);
        int motes = (int) Math.ceil(side * side * 0.6);
        w.spawnParticle(Particle.DUST, mid, motes, half * 0.6, 0.15, half * 0.6, 0, LIT_BLUE);
        w.spawnParticle(Particle.DUST, mid, motes / 2, half * 0.6, 0.15, half * 0.6, 0, LIT_WHITE);
        w.spawnParticle(Particle.END_ROD, mid, motes / 4, half * 0.6, 0.3, half * 0.6, 0.03);
        w.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
        w.playSound(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.3f);
    }

    @Override
    public void removed(Floors.Patch patch) {
        List<Display> parts = shown.remove(patch);
        if (parts == null) return; // (never drawn)
        parts.forEach(d -> { if (d.isValid()) d.remove(); });
        roomFloors.remove(patch);
        roomWalls.remove(patch);
        Floors.Patch room = litOver.remove(patch);
        if (patch.lit()) { // no longer lit: the room it lay over (if it's still there) is magenta again
            Checkers floor = room == null ? null : roomFloors.get(room);
            if (floor != null) floor.recolor(ROOM_DARK, ROOM_LIGHT, null);
            return;
        }
        World w = Bukkit.getWorld(patch.world());
        if (w == null) return;
        Location c = new Location(w, patch.center().x(), patch.center().y() + 0.1, patch.center().z());
        double r = patch.radius() * 0.6;
        if (patch.square()) { // a room folds away: a sparkle where its edges were
            w.spawnParticle(Particle.DUST, c, (int) Math.ceil(patch.radius() * 10), patch.radius() * 0.7, 0.1,
                    patch.radius() * 0.7, 0, ROOM_PINK);
            w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.8f);
            return;
        }
        w.spawnParticle(Particle.BLOCK, c, (int) Math.ceil(patch.radius() * 8), r, 0.05, r, 0,
                Material.AMETHYST_BLOCK.createBlockData());
        w.playSound(c, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.6f, 1.6f);
    }

    /** The floor's look: four thin amethyst squares turned 22.5 degrees apart (a 16-sided disc), just above the ground. */
    private List<Display> spawnFloor(Floors.Patch patch) {
        World w = Bukkit.getWorld(patch.world());
        if (w == null) return List.of();
        var audience = engine.audienceOf(patch.owner()); // (in a veil: only the two in it)
        // A square's half side: its corners reach 1/cos(11.25 deg) of it, so between them the disc is within 2% of round.
        float half = (float) (patch.radius() / 1.0098);
        List<Display> parts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            float turn = (float) (i * Math.PI / 8);
            // each a hair higher than the last, so the overlapping tops don't flicker
            Location at = new Location(w, patch.center().x(), patch.center().y() + LIFT + i * 0.002, patch.center().z());
            Quaternionf spin = new Quaternionf().rotateY(turn);
            Vector3f offset = spin.transform(new Vector3f(-half, 0, -half)); // centred on the spot, after the turn
            parts.add(w.spawn(at, BlockDisplay.class, d -> {
                d.setBlock(Material.AMETHYST_BLOCK.createBlockData());
                d.setPersistent(false);
                d.setBrightness(new Display.Brightness(12, 12));
                VisualEntities.mark(d);
                if (!audience.isEmpty()) VisualEntities.restrict(d, audience);
                d.setTransformation(new Transformation(offset, spin, new Vector3f(half * 2, THICKNESS, half * 2),
                        new Quaternionf()));
            }));
        }
        return parts;
    }

    /** A few glints over each floor. */
    private void shimmer() {
        var r = ThreadLocalRandom.current();
        for (Floors.Patch patch : shown.keySet()) {
            World w = Bukkit.getWorld(patch.world());
            if (w == null) continue;
            if (patch.lit()) { // lit: motes rising all over it
                double h = patch.radius();
                for (int i = 0; i < 8; i++) {
                    double x = patch.center().x() + r.nextDouble(-h, h), z = patch.center().z() + r.nextDouble(-h, h);
                    w.spawnParticle(Particle.DUST, x, patch.center().y() + 0.15 + r.nextDouble(0.6), z, 1, 0, 0, 0, 0,
                            r.nextBoolean() ? LIT_BLUE : LIT_WHITE);
                }
                continue;
            }
            if (patch.square()) { // a room: motes rising off its edges
                double h = patch.radius();
                for (int i = 0; i < 6; i++) {
                    double t = r.nextDouble(-h, h);
                    boolean alongX = r.nextBoolean();
                    double edge = r.nextBoolean() ? h : -h;
                    double x = patch.center().x() + (alongX ? t : edge), z = patch.center().z() + (alongX ? edge : t);
                    w.spawnParticle(Particle.DUST, x, patch.center().y() + 0.1 + r.nextDouble(0.5), z, 1, 0, 0, 0, 0,
                            r.nextBoolean() ? ROOM_PINK : ROOM_LILAC);
                }
                continue;
            }
            int glints = (int) Math.ceil(patch.radius());
            for (int i = 0; i < glints; i++) {
                double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * patch.radius();
                double x = patch.center().x() + Math.cos(a) * d, z = patch.center().z() + Math.sin(a) * d;
                double y = patch.center().y() + 0.15 + r.nextDouble(0.3);
                w.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, r.nextBoolean() ? PALE : VIOLET);
            }
            if (r.nextInt(4) == 0) { // now and then, a glint at its edge
                double a = r.nextDouble(Math.PI * 2);
                w.spawnParticle(Particle.END_ROD, patch.center().x() + Math.cos(a) * patch.radius(), patch.center().y() + 0.2,
                        patch.center().z() + Math.sin(a) * patch.radius(), 1, 0, 0.05, 0, 0.005);
            }
        }
    }
}

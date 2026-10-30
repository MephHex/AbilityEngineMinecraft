package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.barrier.Screens;
import me.mephisto.ability_engine.engine.math.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Draws the screens (Arcane Screen) for everyone: a thin pane of light blue glass standing where it was put, facing its
 * owner's look, with two squares of glass turned 45 degrees in its middle (diamonds): a big blue one touching the pane's
 * nearer edges and a purple one half its size. Block displays, so it's solid to look at; enchanting glyphs swirl round its
 * edge and a few motes drift across it.
 */
public final class ScreenRenderer {

    /** How thick the pane is, and the two diamonds (each a little thicker, so they show on both sides). */
    private static final float PANE = 0.04f;
    private static final float BIG = 0.07f;
    private static final float SMALL = 0.1f;
    private static final Particle.DustOptions MOTE = new Particle.DustOptions(Color.fromRGB(170, 225, 255), 0.7f);

    private final AbilityEngine engine;
    private final Plugin plugin;
    private final Map<Screens.Screen, List<BlockDisplay>> shown = new IdentityHashMap<>();

    public ScreenRenderer(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void shutdown() {
        shown.values().forEach(l -> l.forEach(d -> { if (d.isValid()) d.remove(); }));
        shown.clear();
    }

    private void tick() {
        List<Screens.Screen> active = engine.screens().active();
        shown.entrySet().removeIf(e -> {
            if (active.stream().anyMatch(s -> s == e.getKey())) return false;
            e.getValue().forEach(d -> { if (d.isValid()) d.remove(); });
            return true;
        });
        for (Screens.Screen s : active) {
            if (!shown.containsKey(s)) shown.put(s, spawn(s));
            if (Bukkit.getCurrentTick() % 4 == 0) motes(s);
            if (Bukkit.getCurrentTick() % 2 == 0) glyphs(s);
        }
    }

    private List<BlockDisplay> spawn(Screens.Screen s) {
        World w = Bukkit.getWorld(s.world());
        List<BlockDisplay> out = new ArrayList<>();
        if (w == null) return out;
        Location at = new Location(w, s.center().x(), s.center().y(), s.center().z(), 0, 0);
        Vec3 z = s.right().cross(s.up()); // (a right-handed frame: right, up, and this)
        Quaternionf facing = new Quaternionf().setFromNormalized(new Matrix3f(
                (float) s.right().x(), (float) s.right().y(), (float) s.right().z(),
                (float) s.up().x(), (float) s.up().y(), (float) s.up().z(),
                (float) z.x(), (float) z.y(), (float) z.z()));
        java.util.Set<java.util.UUID> audience = engine.audienceOf(s.owner()); // a duel: only the two see it
        VisualEntities.withAudience(audience, () -> {
            // the pane
            out.add(piece(at, Material.LIGHT_BLUE_STAINED_GLASS, facing, 0, 0, 0, (float) s.width(), (float) s.height(), PANE, false));
            // the diamonds, glass too, deeper colours (a little thicker each, so they show on both sides): the big one's
            // corners on the middle of the nearer edges, the small one half its size
            float half = (float) Math.min(s.width(), s.height()) / 2;
            float side = (float) (half * Math.sqrt(2));
            out.add(piece(at, Material.BLUE_STAINED_GLASS, facing, 0, 0, (float) Math.PI / 4, side, side, BIG, true));
            out.add(piece(at, Material.PURPLE_STAINED_GLASS, facing, 0, 0, (float) Math.PI / 4, side / 2, side / 2, SMALL, true));
            return null;
        });
        w.playSound(at, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
        return out;
    }

    /**
     * A box {@code sx} x {@code sy} x {@code sz} centred at ({@code x}, {@code y}) on the screen (its own right / up), turned
     * {@code angle} in its plane.
     */
    private static BlockDisplay piece(Location at, Material block, Quaternionf facing, float x, float y, float angle,
                                      float sx, float sy, float sz, boolean lit) {
        Quaternionf rot = new Quaternionf(facing).rotateZ(angle);
        Vector3f corner = rot.transform(new Vector3f(-sx / 2, -sy / 2, -sz / 2));
        Vector3f offset = new Quaternionf(facing).transform(new Vector3f(x, y, 0));
        return at.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(block.createBlockData());
            d.setPersistent(false);
            VisualEntities.mark(d);
            if (lit) d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(offset.add(corner), rot, new Vector3f(sx, sy, sz), new Quaternionf()));
        });
    }

    /** Enchanting glyphs round its edge (a few each time, somewhere along it), drifting in toward it. */
    private static void glyphs(Screens.Screen s) {
        World w = Bukkit.getWorld(s.world());
        if (w == null) return;
        var r = ThreadLocalRandom.current();
        List<Vec3> c = s.corners();
        for (int i = 0; i < 4; i++) {
            int edge = r.nextInt(4);
            Vec3 a = c.get(edge), b = c.get((edge + 1) % 4);
            Vec3 p = a.add(b.subtract(a).multiply(r.nextDouble())).add(s.normal().multiply(r.nextDouble(-0.3, 0.3)));
            w.spawnParticle(Particle.ENCHANT, p.x(), p.y(), p.z(), 1, 0.1, 0.1, 0.1, 0.6);
        }
    }

    private static void motes(Screens.Screen s) {
        World w = Bukkit.getWorld(s.world());
        if (w == null) return;
        var r = ThreadLocalRandom.current();
        Vec3 p = s.center().add(s.right().multiply(r.nextDouble(-0.5, 0.5) * s.width()))
                .add(s.up().multiply(r.nextDouble(-0.5, 0.5) * s.height()));
        w.spawnParticle(Particle.DUST, p.x(), p.y(), p.z(), 1, 0, 0, 0, 0, MOTE);
    }
}

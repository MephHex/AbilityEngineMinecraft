package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.barrier.SlowFields;
import me.mephisto.ability_engine.engine.math.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Draws the slow fields (Graviton's Stasis Field) for everyone: its whole box outlined in solid violet lines (thin bars,
 * no particles: the floor's edge, the top's and the corners standing up), with motes drifting slowly inside. And while
 * its owner stands in one of his own, he falls slowly: lower gravity (the gravity attribute) until he steps out.
 */
public final class SlowFieldRenderer {

    private static final int EVERY = 4;
    /** The outline's bars: how thick (blocks). */
    private static final float BAR = 0.06f;
    private static final Particle.DustOptions MOTE = new Particle.DustOptions(Color.fromRGB(200, 180, 255), 0.7f);
    /** Inside his own field: his gravity x (1 + this). */
    private static final double LOW_GRAVITY = -0.6;

    private final AbilityEngine engine;
    private final Plugin plugin;
    private final org.bukkit.NamespacedKey gravityKey;
    /** Each field drawn, and its bars (by identity: two alike fields are still two). */
    private final java.util.Map<SlowFields.Field, java.util.List<BlockDisplay>> drawn = new java.util.IdentityHashMap<>();
    /** Players falling slowly right now. */
    private final java.util.Set<java.util.UUID> floating = new java.util.HashSet<>();

    public SlowFieldRenderer(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
        this.gravityKey = new org.bukkit.NamespacedKey(plugin, "stasis_low_gravity");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::draw, EVERY, EVERY);
        Bukkit.getScheduler().runTaskTimer(plugin, this::gravity, 2, 2);
    }

    private void draw() {
        java.util.List<SlowFields.Field> active = engine.slowFields().active();
        drawn.entrySet().removeIf(e -> {
            boolean gone = active.stream().noneMatch(f -> f == e.getKey());
            if (gone) e.getValue().forEach(d -> { if (d.isValid()) d.remove(); });
            return gone;
        });
        for (SlowFields.Field f : active) {
            World w = Bukkit.getWorld(f.world());
            if (w == null) continue;
            if (!drawn.containsKey(f)) drawn.put(f, outline(w, f));
            Vec3 along = f.forward().multiply(f.length() / 2), across = f.right().multiply(f.width() / 2);
            Vec3 floor = f.center().add(0, 0.1, 0);
            var r = ThreadLocalRandom.current();
            int motes = (int) Math.ceil(f.length() * f.width() / 6);
            for (int i = 0; i < motes; i++) {
                Vec3 m = floor.add(along.multiply(r.nextDouble(-1, 1))).add(across.multiply(r.nextDouble(-1, 1)))
                        .add(0, r.nextDouble(0, f.height()), 0);
                w.spawnParticle(Particle.DUST, m.x(), m.y(), m.z(), 1, 0, 0, 0, 0, MOTE);
            }
        }
    }

    /** The box's 12 edges, each a thin violet bar. */
    private java.util.List<BlockDisplay> outline(World w, SlowFields.Field f) {
        Vec3 along = f.forward().multiply(f.length() / 2), across = f.right().multiply(f.width() / 2);
        Vec3 floor = f.center().add(0, 0.05, 0);
        Vec3 up = new Vec3(0, f.height(), 0);
        Vec3 a = floor.subtract(along).subtract(across), b = floor.subtract(along).add(across);
        Vec3 c = floor.add(along).add(across), d = floor.add(along).subtract(across);
        Vec3[][] edges = {
                {a, b}, {b, c}, {c, d}, {d, a},
                {a.add(up), b.add(up)}, {b.add(up), c.add(up)}, {c.add(up), d.add(up)}, {d.add(up), a.add(up)},
                {a, a.add(up)}, {b, b.add(up)}, {c, c.add(up)}, {d, d.add(up)}};
        Location at = new Location(w, f.center().x(), f.center().y(), f.center().z());
        var audience = engine.audienceOf(f.owner());
        java.util.List<BlockDisplay> out = new java.util.ArrayList<>();
        for (Vec3[] e : edges) {
            Vec3 from = e[0].subtract(f.center()), dir = e[1].subtract(e[0]);
            float length = (float) dir.length();
            if (length < 1e-3) continue;
            Vec3 n = dir.normalize();
            Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(1, 0, 0), new Vector3f((float) n.x(), (float) n.y(), (float) n.z()));
            Vector3f corner = rot.transform(new Vector3f(-BAR / 2, -BAR / 2, -BAR / 2))
                    .add((float) from.x(), (float) from.y(), (float) from.z());
            out.add(w.spawn(at, BlockDisplay.class, disp -> {
                disp.setBlock(Material.PURPLE_CONCRETE.createBlockData());
                disp.setPersistent(false);
                disp.setBrightness(new Display.Brightness(15, 15));
                VisualEntities.mark(disp);
                if (!audience.isEmpty()) VisualEntities.restrict(disp, audience);
                disp.setTransformation(new Transformation(corner, rot, new Vector3f(length + BAR, BAR, BAR), new Quaternionf()));
            }));
        }
        return out;
    }

    /** Owners standing in one of their own fields fall slowly; out of it (or it's gone), back to normal. */
    private void gravity() {
        java.util.Set<java.util.UUID> now = new java.util.HashSet<>();
        for (SlowFields.Field f : engine.slowFields().active()) {
            Player p = Bukkit.getPlayer(f.owner());
            if (p == null || p.isDead()) continue;
            Location l = p.getLocation();
            if (f.contains(l.getWorld().getName(), new Vec3(l.getX(), l.getY() + 0.1, l.getZ()))) now.add(p.getUniqueId());
        }
        for (java.util.UUID id : now) {
            if (floating.add(id)) setLowGravity(Bukkit.getPlayer(id), true);
        }
        for (java.util.UUID id : java.util.List.copyOf(floating)) {
            if (now.contains(id)) continue;
            floating.remove(id);
            setLowGravity(Bukkit.getPlayer(id), false);
        }
    }

    private void setLowGravity(Player p, boolean on) {
        if (p == null) return;
        AttributeInstance g = p.getAttribute(Attribute.GRAVITY);
        if (g == null) return;
        g.removeModifier(gravityKey);
        if (on) g.addTransientModifier(new AttributeModifier(gravityKey, LOW_GRAVITY, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }
}

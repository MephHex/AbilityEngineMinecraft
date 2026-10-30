package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.barrier.SlowFields;
import me.mephisto.ability_engine.engine.math.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Draws the slow fields (Graviton's Stasis Field) for everyone: the edge of its floor and its corners standing up, in
 * violet, with motes drifting slowly inside. A few times a second, so it's cheap.
 */
public final class SlowFieldRenderer {

    private static final int EVERY = 4;
    private static final double SPACING = 0.5;
    private static final Particle.DustOptions EDGE = new Particle.DustOptions(Color.fromRGB(140, 90, 255), 1.0f);
    private static final Particle.DustOptions MOTE = new Particle.DustOptions(Color.fromRGB(200, 180, 255), 0.7f);

    private final AbilityEngine engine;
    private final Plugin plugin;

    public SlowFieldRenderer(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::draw, EVERY, EVERY);
    }

    private void draw() {
        for (SlowFields.Field f : engine.slowFields().active()) {
            World w = Bukkit.getWorld(f.world());
            if (w == null) continue;
            Vec3 along = f.forward().multiply(f.length() / 2), across = f.right().multiply(f.width() / 2);
            Vec3 floor = f.center().add(0, 0.1, 0);
            Vec3 a = floor.subtract(along).subtract(across), b = floor.subtract(along).add(across);
            Vec3 c = floor.add(along).add(across), d = floor.add(along).subtract(across);
            line(w, a, b);
            line(w, b, c);
            line(w, c, d);
            line(w, d, a);
            Vec3 up = new Vec3(0, f.height(), 0);
            for (Vec3 corner : new Vec3[] {a, b, c, d}) line(w, corner, corner.add(up));
            var r = ThreadLocalRandom.current();
            int motes = (int) Math.ceil(f.length() * f.width() / 6);
            for (int i = 0; i < motes; i++) {
                Vec3 m = floor.add(along.multiply(r.nextDouble(-1, 1))).add(across.multiply(r.nextDouble(-1, 1)))
                        .add(0, r.nextDouble(0, f.height()), 0);
                w.spawnParticle(Particle.DUST, m.x(), m.y(), m.z(), 1, 0, 0, 0, 0, MOTE);
            }
        }
    }

    private static void line(World w, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int n = Math.max(1, (int) Math.ceil(d.length() / SPACING));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.multiply(i / (double) n));
            w.spawnParticle(Particle.DUST, p.x(), p.y(), p.z(), 1, 0, 0, 0, 0, EDGE);
        }
    }
}

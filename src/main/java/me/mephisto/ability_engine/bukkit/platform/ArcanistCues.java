package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The Arcanist's cues: violet arcane bursts for his bolts, light blue glass for his screen, and a warning circle on the
 * ground under his ultimate's geometries.
 */
final class ArcanistCues {

    private static final Particle.DustOptions VIOLET = new Particle.DustOptions(Color.fromRGB(170, 90, 255), 1.0f);
    private static final Particle.DustOptions PALE = new Particle.DustOptions(Color.fromRGB(225, 200, 255), 0.8f);
    private static final Particle.DustOptions GLASS = new Particle.DustOptions(Color.fromRGB(150, 215, 255), 1.0f);
    private static final Particle.DustOptions WARN = new Particle.DustOptions(Color.fromRGB(255, 70, 160), 1.3f);
    private static final Particle.DustOptions WARN_FILL = new Particle.DustOptions(Color.fromRGB(200, 60, 255), 0.9f);

    /** archmage.yml's radii: keep in step. */
    private static final double BOLT_BURST = 1.0;
    private static final double BARRAGE_BURST = 2.5;
    private static final double MISSILE_BLAST = 3;
    private static final double GEOMETRY_RADIUS = 4;
    /** The geometry's fuse (ticks): the warning circle fills up over it. */
    private static final int GEOMETRY_FUSE = 20;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary: Arcane Bolt (and the passive's Barrage mode) ----
        c.register("arcanist_bolt_burst", loc -> burst(loc, BOLT_BURST, 0.9f));
        c.register("arcanist_bolt_burst_big", loc -> {
            burst(loc, BARRAGE_BURST, 0.6f);
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
        });
        c.register("arcanist_bolt_trail", loc -> // a small trail of enchanting glyphs
                loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 2, 0.1, 0.1, 0.1, 0.3));
        c.register("arcanist_barrage_trail", loc -> {
            loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 4, 0.2, 0.2, 0.2, 0.4);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.15, 0.15, 0.15, 0, VIOLET);
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0.05, 0.05, 0.05, 0.01);
        });
        c.registerLoop("arcanist_barrage_mode", e -> { // runes circling him: everyone can tell he's in Barrage mode
            World w = e.getWorld();
            w.playSound(e.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);
            w.playSound(e.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.7f, 1.6f);
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                int t = tick[0]++;
                Location base = e.getLocation();
                double h = e.getHeight();
                for (int i = 0; i < 3; i++) { // three motes chasing each other round him, bobbing
                    double a = t * 0.35 + i * Math.PI * 2 / 3;
                    double y = h * (0.35 + 0.25 * Math.sin(t * 0.2 + i));
                    e.getWorld().spawnParticle(Particle.DUST, base.getX() + Math.cos(a) * 0.9, base.getY() + y,
                            base.getZ() + Math.sin(a) * 0.9, 1, 0, 0, 0, 0, VIOLET);
                }
                if (t % 3 == 0) e.getWorld().spawnParticle(Particle.ENCHANT, base.clone().add(0, h + 0.3, 0), 4, 0.3, 0.1, 0.3, 0.5);
                if (t % 6 == 0) e.getWorld().spawnParticle(Particle.WITCH, base.clone().add(0, h * 0.5, 0), 2, 0.4, 0.4, 0.4, 0);
            }, 0, 1);
            return (CueHandle) task::cancel;
        });

        // ---- 1: Arcane Missile ----
        c.register("arcanist_mark_hit", loc -> {
            loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 20, 0.3, 0.4, 0.3, 0.2);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 1.6f);
        });
        c.registerLoop("arcanist_marked", e -> { // a small violet ring over their head
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location top = e.getLocation().add(0, e.getHeight() + 0.35, 0);
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6 + Bukkit.getCurrentTick() * 0.15;
                    e.getWorld().spawnParticle(Particle.DUST, top.getX() + Math.cos(a) * 0.35, top.getY(),
                            top.getZ() + Math.sin(a) * 0.35, 1, 0, 0, 0, 0, PALE);
                }
            }, 0, 4);
            return (CueHandle) task::cancel;
        });
        c.register("arcanist_missile_sink", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.REVERSE_PORTAL, loc, 40, 0.3, 0.3, 0.3, 0.05);
            w.spawnParticle(Particle.BLOCK, loc, 20, 0.3, 0.3, 0.3, 0, Material.AMETHYST_BLOCK.createBlockData());
            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, 0.6f);
            w.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.6f);
        });
        c.register("arcanist_missile_lodged", loc -> { // humming in the wall, waiting for the recast
            loc.getWorld().spawnParticle(Particle.PORTAL, loc, 6, 0.3, 0.3, 0.3, 0.3);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.35, 0.35, 0.35, 0, VIOLET);
        });
        c.register("arcanist_missile_blast", loc -> { // the empowered missile's explosion: its whole reach shows
            World w = loc.getWorld();
            ring(w, loc, MISSILE_BLAST, VIOLET);
            ring(w, loc.clone().add(0, -0.8, 0), MISSILE_BLAST, VIOLET); // (and on the ground, for a hit on someone)
            ring(w, loc, MISSILE_BLAST * 0.66, PALE);
            for (int i = 0; i < 40; i++) { // a shell of sparks at its edge
                double a = Math.random() * Math.PI * 2, b = Math.acos(2 * Math.random() - 1);
                w.spawnParticle(Particle.END_ROD, loc.getX() + MISSILE_BLAST * Math.sin(b) * Math.cos(a),
                        loc.getY() + MISSILE_BLAST * Math.cos(b), loc.getZ() + MISSILE_BLAST * Math.sin(b) * Math.sin(a),
                        1, 0, 0, 0, 0);
            }
            w.spawnParticle(Particle.EXPLOSION, loc, 2, 0.6, 0.6, 0.6, 0);
            w.spawnParticle(Particle.WITCH, loc, 50, MISSILE_BLAST / 2, MISSILE_BLAST / 2, MISSILE_BLAST / 2, 0.05);
            w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.4f);
            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, 0.6f);
        });
        c.register("arcanist_missile_trail", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.2, 0.2, 0.2, 0, VIOLET);
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0.1, 0.1, 0.1, 0.02);
        });

        // ---- 3: Arcane Screen (the screen itself is drawn by ScreenRenderer) ----
        c.register("arcanist_screen_raise", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 25, 1.2, 0.9, 1.2, 0, GLASS);
            loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_PLACE, 1f, 1.2f);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.8f);
        });
        c.register("arcanist_screen_fade", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 30, 1.0, 0.8, 1.0, 0,
                    Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
            loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f);
        });
        c.register("arcanist_screen_catch", loc -> { // an enemy shot stuck in it
            loc.getWorld().spawnParticle(Particle.DUST, loc, 12, 0.2, 0.2, 0.2, 0, GLASS);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 1f, 0.5f);
        });
        c.register("arcanist_screen_boost", loc -> { // a friendly shot sped through
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 8, 0.1, 0.1, 0.1, 0.12);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.15, 0.15, 0.15, 0, GLASS);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 2f);
        });

        // ---- ULTIMATE: Arcane Ascension ----
        c.register("arcanist_geometry_warn", loc -> { // the circle on the ground, filling in until it goes off
            World w = loc.getWorld();
            w.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 1.8f);
            int[] tick = {0};
            BukkitTask[] task = {null};
            task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                int t = tick[0]++;
                if (t > GEOMETRY_FUSE) {
                    task[0].cancel();
                    return;
                }
                Location ground = loc.clone().add(0, 0.15, 0);
                ring(w, ground, GEOMETRY_RADIUS, WARN);
                ring(w, ground, GEOMETRY_RADIUS * t / GEOMETRY_FUSE, WARN_FILL); // closing in on the time it goes off
                if (t % 5 == 0) w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f + t / (float) GEOMETRY_FUSE);
            }, 0, 2);
        });
        c.register("arcanist_geometry_blast", loc -> {
            World w = loc.getWorld();
            Location mid = loc.clone().add(0, 1.2, 0);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, mid, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.WITCH, mid, 80, GEOMETRY_RADIUS / 2, 0.8, GEOMETRY_RADIUS / 2, 0.1);
            w.spawnParticle(Particle.END_ROD, mid, 40, 0.5, 0.5, 0.5, 0.35);
            ring(w, loc.clone().add(0, 0.15, 0), GEOMETRY_RADIUS, VIOLET);
            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.5f, 0.5f);
            w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
        });
    }

    /** A small arcane burst: a ring at its reach, a puff and a crack. */
    private static void burst(Location loc, double radius, float pitch) {
        World w = loc.getWorld();
        int n = (int) Math.max(8, radius * 10);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * radius, loc.getY(), loc.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0, VIOLET);
        }
        w.spawnParticle(Particle.WITCH, loc, (int) (8 * radius), radius / 3, radius / 3, radius / 3, 0.05);
        w.spawnParticle(Particle.DUST, loc, (int) (6 * radius), radius / 3, radius / 3, radius / 3, 0, PALE);
        w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.4f * pitch);
        w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.6f * pitch);
    }

    /** A ring at {@code radius} round a spot, level with it. */
    private static void ring(World w, Location at, double radius, Particle.DustOptions dust) {
        if (radius <= 0.05) return;
        int n = (int) Math.max(8, radius * 8);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * radius, at.getY(), at.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0, dust);
        }
    }

    private ArcanistCues() {}
}

package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The Lightkeeper's cues: warm lantern light, gold motes, a sunlit guard and a pillar of dawn. */
final class LightkeeperCues {

    private static final Particle.DustOptions WARM = new Particle.DustOptions(Color.fromRGB(255, 214, 120), 1.2f);
    private static final Particle.DustOptions PALE = new Particle.DustOptions(Color.fromRGB(255, 245, 200), 0.9f);

    /** Keep in step with lightkeeper.yml: Light Guard's radius, the beacon's. */
    static final double GUARD_RADIUS = 5, BEACON_RADIUS = 5;

    private LightkeeperCues() {}

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary: Sunspark ----
        c.register("lk_spark_shot", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.8f));
        c.register("lk_spark_trail", loc -> loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.05, 0.05, 0.05, 0, WARM));
        c.register("lk_spark_hit", loc -> {
            loc.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.fromRGB(255, 230, 150));
            loc.getWorld().spawnParticle(Particle.DUST, loc, 10, 0.2, 0.2, 0.2, 0, WARM);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.5f);
        });

        // ---- 1: Waylight ----
        c.register("lk_lantern_throw", loc -> {
            loc.getWorld().playSound(loc, Sound.BLOCK_LANTERN_PLACE, 1f, 1.2f);
            loc.getWorld().playSound(loc, Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.8f);
        });
        c.register("lk_lantern_trail", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 3, 0.15, 0.15, 0.15, 0, WARM);
            w.spawnParticle(Particle.END_ROD, loc, 1, 0.1, 0.1, 0.1, 0.01);
        });
        c.register("lk_lantern_heal", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.3, 0.5, 0.3, 0);
            w.spawnParticle(Particle.DUST, loc, 10, 0.3, 0.5, 0.3, 0, PALE);
            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
        });
        c.register("lk_lantern_sear", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.fromRGB(255, 230, 150));
            w.spawnParticle(Particle.SMALL_FLAME, loc, 12, 0.25, 0.4, 0.25, 0.02);
            w.playSound(loc, Sound.ENTITY_BLAZE_HURT, 0.6f, 1.6f);
        });
        c.register("lk_lantern_land", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 12, 0.3, 0.3, 0.3, 0, WARM);
            loc.getWorld().playSound(loc, Sound.BLOCK_LANTERN_BREAK, 0.8f, 1.2f);
        });

        // ---- 2: Light Guard ----
        c.registerLoop("lk_guard_aura", e -> ring(plugin, e, GUARD_RADIUS, false));
        c.register("lk_guard_on", loc -> {
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.6, 0.8, 0.6, 0.05);
        });
        c.registerLoop("lk_sheltered", e -> motes(plugin, e)); // an ally in the light
        c.registerLoop("lk_sunfire", e -> { // set burning by an ally's hit in the light: gold sparks
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation().add(0, e.getHeight() * 0.6, 0);
                at.getWorld().spawnParticle(Particle.SMALL_FLAME, at, 3, 0.25, 0.4, 0.25, 0.01);
                at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.25, 0.4, 0.25, 0, WARM);
            }, 0, 4);
            return (CueHandle) task::cancel;
        });

        // ---- 3: Illuminate ----
        c.register("lk_illuminate", loc -> {
            World w = loc.getWorld();
            Location ground = loc.clone().add(0, -0.8, 0);
            for (int r = 1; r <= 6; r++) ring(w, ground, r, 10 + r * 6, r % 2 == 0 ? WARM : PALE);
            w.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.fromRGB(255, 240, 190));
            w.spawnParticle(Particle.END_ROD, loc, 40, 2.5, 0.8, 2.5, 0.05);
            w.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);
            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        });
        c.register("lk_revealed", loc -> loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.3, 0.5, 0.3, 0, PALE));

        // ---- ULTIMATE: Beacon of Dawn ----
        c.registerLoop("lk_beacon", e -> beacon(plugin, e));
        c.register("lk_beacon_mend", loc -> {
            loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1, 0), 1, 0.2, 0.2, 0.2, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.3, 0.6, 0.3, 0, PALE);
        });
    }

    private static void ring(World w, Location center, double radius, int points, Particle.DustOptions color) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius), 1, 0, 0, 0, 0, color);
        }
    }

    /** A ring of light on the ground around them (radius), with rising motes inside it, following them. */
    private static CueHandle ring(Plugin plugin, Entity e, double radius, boolean pillar) {
        int[] t = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid()) return;
            Location feet = e.getLocation();
            World w = feet.getWorld();
            if (t[0] % 3 == 0) ring(w, feet, radius, 40, WARM);
            double a = Math.random() * Math.PI * 2, r = Math.sqrt(Math.random()) * radius;
            w.spawnParticle(Particle.END_ROD, feet.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 0, 0, 0.06, 0, 1);
            if (t[0] % 40 == 0) w.playSound(feet, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.6f);
            t[0]++;
        }, 0, 1);
        return () -> {
            task.cancel();
            if (e.isValid()) e.getWorld().playSound(e.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.6f);
        };
    }

    /** Gold motes rising off someone. */
    private static CueHandle motes(Plugin plugin, Entity e) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid()) return;
            Location at = e.getLocation().add(0, 0.2, 0);
            at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.3, 0.6, 0.3, 0, PALE);
        }, 0, 5);
        return task::cancel;
    }

    /**
     * The pillar of dawn: a tall beam of light (glowing, see-through white) on him, a ring of light at its foot, motes
     * streaming up it, while he channels.
     */
    private static CueHandle beacon(Plugin plugin, Entity e) {
        World w = e.getWorld();
        w.playSound(e.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.8f);
        w.playSound(e.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 1.4f);
        float width = 1.6f, height = 40f;
        BlockDisplay beam = w.spawn(e.getLocation(), BlockDisplay.class, d -> {
            d.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(-width / 2, 0, -width / 2), new Quaternionf(),
                    new Vector3f(width, height, width), new Quaternionf()));
            VisualEntities.mark(d);
        });
        BlockDisplay core = w.spawn(e.getLocation(), BlockDisplay.class, d -> {
            d.setBlock(Material.YELLOW_STAINED_GLASS.createBlockData());
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(-0.3f, 0, -0.3f), new Quaternionf(),
                    new Vector3f(0.6f, height, 0.6f), new Quaternionf()));
            VisualEntities.mark(d);
        });
        int[] t = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid()) return;
            Location feet = e.getLocation();
            feet.setYaw(0);
            feet.setPitch(0);
            if (beam.isValid()) beam.teleport(feet);
            if (core.isValid()) core.teleport(feet);
            if (t[0] % 3 == 0) ring(w, feet, BEACON_RADIUS, 48, WARM);
            for (int i = 0; i < 3; i++) {
                double a = Math.random() * Math.PI * 2, r = Math.random() * 0.8;
                w.spawnParticle(Particle.END_ROD, feet.clone().add(Math.cos(a) * r, Math.random() * 6, Math.sin(a) * r),
                        0, 0, 0.25, 0, 1);
            }
            if (t[0] % 30 == 0) w.playSound(feet, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.2f);
            t[0]++;
        }, 0, 1);
        return () -> {
            task.cancel();
            if (beam.isValid()) beam.remove();
            if (core.isValid()) core.remove();
            if (e.isValid()) w.playSound(e.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
        };
    }
}

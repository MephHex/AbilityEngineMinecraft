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
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The Slime's cues: lime green goo - vanilla's slime particles and squelches - and big wet landings. */
final class SlimeCues {

    /** Slime green... */
    private static final Particle.DustOptions LIME = new Particle.DustOptions(Color.fromRGB(120, 200, 80), 1.2f);
    /** ...and its darker core. */
    private static final Particle.DustOptions MOSS = new Particle.DustOptions(Color.fromRGB(70, 140, 50), 1.0f);

    /** Ooze Burst's radius and the landings' (slime.yml): keep in step. */
    private static final double BURST_RADIUS = 4.5;
    private static final double FLOP_RADIUS = 3.5;
    private static final double SLAM_RADIUS = 4;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- passive: Gelatinous ----
        c.register("slime_drop", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_SLIME_SQUISH_SMALL, 1f, 0.8f));
        c.register("slime_blob_idle", loc -> {
            loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc.clone().add(0, 0.2, 0), 2, 0.15, 0.05, 0.15, 0);
        });
        c.register("slime_eat", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.ITEM_SLIME, loc, 20, 0.4, 0.6, 0.4, 0.05);
            w.spawnParticle(Particle.HEART, loc.clone().add(0, 1, 0), 2, 0.3, 0.2, 0.3, 0);
            w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1f, 1.4f);
            w.playSound(loc, Sound.ENTITY_GENERIC_EAT, 0.8f, 0.8f);
        });
        c.register("slime_squash", loc -> {
            loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc, 15, 0.4, 0.1, 0.4, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_SLIME_DEATH_SMALL, 1f, 0.8f);
        });
        c.register("slime_dry", loc -> loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc.clone().add(0, 0.2, 0), 6, 0.2, 0.1, 0.2, 0));

        // ---- primary: Slime Slap ----
        c.register("slime_slap", loc -> {
            loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc.clone().add(loc.getDirection().multiply(1.5)), 8, 0.4, 0.3, 0.4, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_SLIME_ATTACK, 1f, 0.7f);
        });

        // ---- 1: Ooze Burst ----
        c.register("slime_too_weak", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_SLIME_SQUISH_SMALL, 0.6f, 1.8f));
        c.register("slime_burst", loc -> {
            World w = loc.getWorld();
            ring(w, loc, BURST_RADIUS, LIME);
            w.spawnParticle(Particle.ITEM_SLIME, loc, 60, BURST_RADIUS / 2, 0.5, BURST_RADIUS / 2, 0.1);
            w.spawnParticle(Particle.DUST, loc, 30, BURST_RADIUS / 2, 0.4, BURST_RADIUS / 2, 0, MOSS);
            w.playSound(loc, Sound.ENTITY_SLIME_JUMP, 1f, 0.5f);
            w.playSound(loc, Sound.BLOCK_SLIME_BLOCK_BREAK, 1f, 0.6f);
        });
        c.registerLoop("slime_slimed", e -> { // dripping with slime while slowed
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                e.getWorld().spawnParticle(Particle.ITEM_SLIME, e.getLocation().add(0, e.getHeight() * 0.5, 0), 2, 0.3, 0.4, 0.3, 0);
            }, 0, 4);
            return (CueHandle) task::cancel;
        });

        // ---- 2: Slime Ball ----
        c.registerLoop("slime_windup", e -> { // gathering slime in her hands
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location hands = e.getLocation().add(0, e.getHeight() * 0.6, 0).add(e.getLocation().getDirection().multiply(0.6));
                e.getWorld().spawnParticle(Particle.ITEM_SLIME, hands, 3, 0.2, 0.2, 0.2, 0);
                e.getWorld().playSound(hands, Sound.BLOCK_SLIME_BLOCK_STEP, 0.6f, 1.2f);
            }, 0, 3);
            return (CueHandle) task::cancel;
        });
        c.register("slime_throw", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.5f));
        c.register("slime_ball_trail", loc -> loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc, 2, 0.2, 0.2, 0.2, 0));
        c.register("slime_splat", loc -> {
            loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc, 25, 0.5, 0.5, 0.5, 0.1);
            loc.getWorld().playSound(loc, Sound.BLOCK_SLIME_BLOCK_FALL, 1f, 0.7f);
        });
        c.register("slime_stick", loc -> {
            loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc, 30, 0.5, 0.6, 0.5, 0.1);
            loc.getWorld().playSound(loc, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_PLACE, 1f, 0.8f);
        });
        c.registerLoop("slime_stuck", e -> { // a block of slime around their torso, following them
            BlockDisplay goo = e.getWorld().spawn(torso(e), BlockDisplay.class, d -> {
                d.setBlock(Material.SLIME_BLOCK.createBlockData());
                d.setPersistent(false);
                d.setTeleportDuration(1);
                float s = (float) Math.max(0.9, e.getWidth() * 1.4);
                d.setTransformation(new Transformation(new Vector3f(-s / 2, -s / 2, -s / 2), new Quaternionf(),
                        new Vector3f(s, s, s), new Quaternionf()));
                VisualEntities.mark(d);
            });
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid() || !goo.isValid()) return;
                goo.teleport(torso(e));
            }, 1, 1);
            return (CueHandle) () -> {
                task.cancel();
                if (goo.isValid()) {
                    goo.getWorld().spawnParticle(Particle.ITEM_SLIME, goo.getLocation(), 15, 0.4, 0.4, 0.4, 0.05);
                    goo.getWorld().playSound(goo.getLocation(), Sound.BLOCK_SLIME_BLOCK_BREAK, 1f, 1f);
                    goo.remove();
                }
            };
        });

        // ---- 3: Belly Flop, and the ultimate's bounces ----
        c.register("slime_launch", loc -> {
            loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc, 20, 0.4, 0.2, 0.4, 0.1);
            loc.getWorld().playSound(loc, Sound.ENTITY_SLIME_JUMP, 1f, 0.6f);
        });
        c.register("slime_flop", loc -> landing(loc, FLOP_RADIUS));
        c.register("slime_slam", loc -> landing(loc, SLAM_RADIUS));
    }

    /** A big wet landing: a splash ring at its reach. */
    private static void landing(Location loc, double radius) {
        World w = loc.getWorld();
        ring(w, loc, radius, LIME);
        w.spawnParticle(Particle.ITEM_SLIME, loc, 50, radius / 2, 0.3, radius / 2, 0.15);
        w.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
        w.playSound(loc, Sound.BLOCK_SLIME_BLOCK_FALL, 1.2f, 0.5f);
        w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1f, 0.5f);
    }

    /** A ring at {@code radius} around a spot, on the ground under it (the spot's an entity's centre). */
    private static void ring(World w, Location at, double radius, Particle.DustOptions dust) {
        int n = (int) (radius * 8);
        double y = at.getY() - 0.8;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * radius, y, at.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, dust);
        }
    }

    /** The middle of their body. */
    private static Location torso(Entity e) {
        return e.getLocation().add(0, e.getHeight() * 0.55, 0);
    }

    private SlimeCues() {}
}

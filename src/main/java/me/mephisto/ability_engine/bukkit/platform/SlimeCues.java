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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

        // ---- ultimate: Bubble Trouble (Mitosis's, mitosis.yml), in her own squishing green slime ----
        // (slime.yml's variant: while she has mitosis.melted, each mitosis_x cue plays as mitosis_x_slime)
        // Her pool: a flat slab of slime block growing over the 2s channel to POOL_MAX each way (what the slow and the
        // root reach), wobbling and squelching. It doesn't go after 2s: it stays under her (she's invisible: the pool
        // is her) until the bubble leaps (the spot picked), or the ult ends.
        c.registerLoop("mitosis_puddle_slime", e -> {
            UUID id = e.getUniqueId();
            BlockDisplay pool = e.getWorld().spawn(level(e.getLocation()), BlockDisplay.class, d -> {
                d.setBlock(Material.SLIME_BLOCK.createBlockData());
                d.setPersistent(false);
                d.setTeleportDuration(1);
                d.setTransformation(slab(POOL_MIN, POOL_HEIGHT));
                VisualEntities.mark(d);
            });
            int[] age = {0};
            BukkitTask[] task = new BukkitTask[1];
            Runnable[] end = new Runnable[1];
            end[0] = () -> {
                if (task[0] != null) task[0].cancel();
                if (pool.isValid()) pool.remove();
                POOLS.remove(id, end[0]);
            };
            Runnable old = POOLS.put(id, end[0]);
            if (old != null) old.run();
            task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid() || !pool.isValid()) {
                    end[0].run();
                    return;
                }
                int t = ++age[0];
                double grown = Math.min(1, (double) t / POOL_GROW_TICKS);
                double half = Math.max(POOL_MIN, POOL_MAX * grown);
                // the squish: it swells flat and wide, then bunches up thick and narrow, over and over
                double squish = Math.sin(t * 0.35);
                double width = half + squish * 0.12;
                float height = (float) (POOL_HEIGHT * (1 - squish * 0.45));
                pool.teleport(level(e.getLocation()));
                pool.setInterpolationDelay(0);
                pool.setInterpolationDuration(2);
                pool.setTransformation(slab(width, height));
                World w = e.getWorld();
                Location feet = e.getLocation();
                if (t <= POOL_GROW_TICKS ? t % 2 == 0 : t % 8 == 0) square(w, feet, half, t % 4 == 0 ? LIME : MOSS); // its reach
                if (t % 3 == 0) { // bubbling up all over it
                    w.spawnParticle(Particle.ITEM_SLIME, feet.clone().add(0, 0.2, 0), (int) Math.ceil(half * 1.5), half * 0.6, 0.05, half * 0.6, 0);
                }
                if (t % 9 == 0) w.playSound(feet, Sound.ENTITY_SLIME_SQUISH, 0.7f, 0.5f + (float) grown * 0.3f);
                if (t <= POOL_GROW_TICKS && t % 5 == 0) w.playSound(feet, Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.6f, 0.5f + t / 80f);
            }, 1, 1);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 0.5f);
            return (CueHandle) () -> end[0].run();
        });
        c.register("mitosis_root_slime", loc -> { // the full pool sets: rooted
            World w = loc.getWorld();
            Location feet = loc.clone().add(0, -0.9, 0);
            square(w, feet, POOL_MAX, MOSS);
            square(w, feet, POOL_MAX - 0.3, LIME);
            w.spawnParticle(Particle.ITEM_SLIME, feet, 50, POOL_MAX / 2, 0.1, POOL_MAX / 2, 0.05);
            w.playSound(loc, Sound.BLOCK_SLIME_BLOCK_PLACE, 1.2f, 0.5f);
            w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.2f, 0.4f);
        });
        // the bubble: a big block of slime round her (and whoever she swallowed), squashing and stretching as it flies
        c.registerLoop("mitosis_bubble_slime", e -> {
            Runnable pool = POOLS.remove(e.getUniqueId()); // the spot is picked: the pool goes up into the bubble
            if (pool != null) pool.run();
            BlockDisplay bubble = e.getWorld().spawn(middle(e), BlockDisplay.class, d -> {
                d.setBlock(Material.SLIME_BLOCK.createBlockData());
                d.setPersistent(false);
                d.setTeleportDuration(1);
                d.setTransformation(blob(BUBBLE, BUBBLE));
                VisualEntities.mark(d);
            });
            int[] age = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid() || !bubble.isValid()) return;
                int t = ++age[0];
                double squish = Math.sin(t * 0.6);
                float width = (float) (BUBBLE * (1 + squish * 0.14));
                float height = (float) (BUBBLE * (1 - squish * 0.18));
                bubble.teleport(middle(e));
                bubble.setInterpolationDelay(0);
                bubble.setInterpolationDuration(2);
                bubble.setTransformation(blob(width, height));
                World w = e.getWorld();
                w.spawnParticle(Particle.ITEM_SLIME, middle(e), 3, 1.0, 1.0, 1.0, 0);
                if (t % 6 == 0) w.playSound(e.getLocation(), Sound.ENTITY_SLIME_SQUISH, 0.6f, 0.7f);
            }, 1, 1);
            return (CueHandle) () -> {
                task.cancel();
                if (bubble.isValid()) bubble.remove();
            };
        });
        c.register("mitosis_land_slime", loc -> {
            World w = loc.getWorld();
            ring(w, loc, LAND_RADIUS, LIME);
            w.spawnParticle(Particle.DUST, loc, 60, LAND_RADIUS / 2, 0.4, LAND_RADIUS / 2, 0, LIME);
            w.spawnParticle(Particle.ITEM_SLIME, loc, 60, LAND_RADIUS / 2, 0.3, LAND_RADIUS / 2, 0.15);
            w.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
            w.playSound(loc, Sound.BLOCK_SLIME_BLOCK_FALL, 1.4f, 0.4f);
            w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.2f, 0.5f);
        });
    }

    /** Bubble Trouble (mitosis.yml): the pool grows to this far from her to each side over the channel (2s)... */
    private static final double POOL_MAX = 4, POOL_MIN = 0.4;
    private static final int POOL_GROW_TICKS = 40;
    /** ...a slab this thick... */
    private static final float POOL_HEIGHT = 0.18f;
    /** ...the bubble this big, and the landing's reach. */
    private static final float BUBBLE = 2.4f;
    private static final double LAND_RADIUS = 4;
    /** Whose pool is still down (to take it away when the bubble leaps). */
    private static final Map<UUID, Runnable> POOLS = new HashMap<>();

    /** The pool's slab, {@code half} from its middle to each side, lying on the ground. */
    private static Transformation slab(double half, float height) {
        float h = (float) half;
        return new Transformation(new Vector3f(-h, 0.01f, -h), new Quaternionf(), new Vector3f(2 * h, height, 2 * h), new Quaternionf());
    }

    /** A block centred on its spot, {@code width} across and {@code height} tall. */
    private static Transformation blob(float width, float height) {
        return new Transformation(new Vector3f(-width / 2, -height / 2, -width / 2), new Quaternionf(),
                new Vector3f(width, height, width), new Quaternionf());
    }

    private static Location middle(Entity e) {
        return e.getLocation().add(0, e.getHeight() / 2, 0);
    }

    /** The spot without its turn (a display spawned there lines up with the block grid, like the square it shows). */
    private static Location level(Location at) {
        Location l = at.clone();
        l.setYaw(0);
        l.setPitch(0);
        return l;
    }

    /** A square's edge, {@code half} from {@code at} (on the ground) to each side, lined up with the block grid. */
    private static void square(World w, Location at, double half, Particle.DustOptions dust) {
        double y = at.getY() + 0.25, x0 = at.getX(), z0 = at.getZ();
        for (double t = -half; t <= half + 1e-6; t += 0.35) {
            w.spawnParticle(Particle.DUST, x0 + t, y, z0 - half, 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, x0 + t, y, z0 + half, 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, x0 - half, y, z0 + t, 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, x0 + half, y, z0 + t, 1, 0, 0, 0, 0, dust);
        }
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

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
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Mitosis's cues: blue slime. Glassy light-blue goo, squelches, and big wet landings. */
final class MitosisCues {

    /** Blue slime... */
    private static final Particle.DustOptions BLUE = new Particle.DustOptions(Color.fromRGB(90, 170, 255), 1.2f);
    /** ...its deep core... */
    private static final Particle.DustOptions DEEP = new Particle.DustOptions(Color.fromRGB(40, 90, 210), 1.0f);
    /** ...and its glossy shine. */
    private static final Particle.DustOptions SHINE = new Particle.DustOptions(Color.fromRGB(190, 230, 255), 0.8f);
    private static final Material GOO = Material.LIGHT_BLUE_STAINED_GLASS;

    /** Radii in mitosis.yml: keep in step. */
    private static final double LAND_RADIUS = 4;
    /** Bubble Trouble's puddle: a square growing to this far from its middle to each side (the slow's and root's reach). */
    private static final double PUDDLE_MAX = 4;
    /** The smallest it's drawn, at the very start. */
    private static final double PUDDLE_MIN = 0.4;
    /** Bounce Back's wall: 1 block ahead, 3 x 3. */
    private static final double WALL_AHEAD = 1, WALL_HALF = 1.5;
    /** The puddle's time (ULT_CHANNEL): its look ends by itself then. */
    private static final int PUDDLE_TICKS = 40;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- passive: Mitosis ----
        c.register("mitosis_bud", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 30, 0.4, 0.6, 0.4, 0, BLUE);
            w.spawnParticle(Particle.ITEM_SLIME, loc, 15, 0.4, 0.5, 0.4, 0.05);
            w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1f, 0.6f);
            w.playSound(loc, Sound.BLOCK_HONEY_BLOCK_BREAK, 1f, 0.7f);
        });
        c.register("mitosis_fuse", loc -> { // a squelch as it melts back in
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 25, 0.3, 0.5, 0.3, 0, BLUE);
            w.spawnParticle(Particle.DUST, loc, 10, 0.3, 0.5, 0.3, 0, SHINE);
            w.playSound(loc, Sound.BLOCK_HONEY_BLOCK_SLIDE, 1f, 0.8f);
            w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1f, 1.3f);
        });
        c.register("mitosis_transfer", MitosisCues::splash); // the dying body splashes apart
        c.register("mitosis_clone_burst", MitosisCues::splash); // the clone bursts
        c.register("mitosis_swap", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 15, 0.3, 0.6, 0.3, 0, SHINE);
            loc.getWorld().playSound(loc, Sound.ENTITY_SLIME_JUMP_SMALL, 1f, 1.4f);
        });
        c.register("mitosis_too_far", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f));

        // ---- primary: Stretch (a slime arm out to the hit and back) ----
        c.registerLine("mitosis_stretch", (w, from, to) -> {
            arm(w, from, to);
            Location end = to.toLocation(w);
            w.spawnParticle(Particle.ITEM_SLIME, end, 4, 0.1, 0.1, 0.1, 0.02);
            w.playSound(from.toLocation(w), Sound.ENTITY_SLIME_ATTACK, 0.9f, 1.3f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> arm(w, from, from.clone().add(to.clone().subtract(from).multiply(0.4))), 2);
        });

        // ---- 1: Sticky Fingers ----
        c.register("mitosis_arm_throw", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_SLIME_SQUISH_SMALL, 1f, 0.7f));
        c.register("mitosis_arm_trail", loc -> loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.08, 0.08, 0.08, 0, BLUE));
        c.register("mitosis_splat", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 12, 0.3, 0.3, 0.3, 0, BLUE);
            loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc, 6, 0.3, 0.3, 0.3, 0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_SLIME_BLOCK_FALL, 0.8f, 1.1f);
        });
        c.register("mitosis_stick", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 18, 0.35, 0.5, 0.35, 0, BLUE);
            loc.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_PLACE, 1f, 0.8f);
        });
        c.registerLine("mitosis_tether", (w, from, to) -> { // a stretched strand of slime, sagging a little
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            for (double t = 0; t <= len; t += 0.45) {
                double s = t / len;
                Vector p = from.clone().add(d.clone().multiply(s)).add(new Vector(0, -0.6 * s * (1 - s) * Math.min(1, len / 6), 0));
                w.spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), 1, 0.02, 0.02, 0.02, 0, BLUE);
            }
        });
        c.register("mitosis_slam", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 30, 0.6, 0.6, 0.6, 0, BLUE);
            w.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
            w.playSound(loc, Sound.BLOCK_SLIME_BLOCK_FALL, 1.2f, 0.5f);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
        });
        c.register("mitosis_wall_slam", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 25, 0.5, 0.7, 0.5, 0, BLUE);
            w.spawnParticle(Particle.CRIT, loc, 10, 0.3, 0.5, 0.3, 0.2);
            w.playSound(loc, Sound.BLOCK_SLIME_BLOCK_HIT, 1.2f, 0.6f);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        });

        // ---- 2: Blob Rush (a blob of slime in their place) ----
        c.registerLoop("mitosis_blob", e -> {
            BlockDisplay blob = goo(e, e.getLocation(), 0.95f, 1.3f);
            int[] age = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid() || !blob.isValid()) return;
                float wobble = (float) Math.sin(++age[0] * 0.9) * 0.08f;
                size(blob, 0.95f + wobble, 1.3f - wobble);
                blob.teleport(e.getLocation());
                if (age[0] % 2 == 0) e.getWorld().spawnParticle(Particle.DUST, e.getLocation().add(0, 0.5, 0), 2, 0.3, 0.3, 0.3, 0, BLUE);
            }, 1, 1);
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_HONEY_BLOCK_SLIDE, 1f, 0.6f);
            return (CueHandle) () -> {
                task.cancel();
                if (blob.isValid()) blob.remove();
            };
        });
        c.register("mitosis_rush", loc -> {
            loc.getWorld().playSound(loc, Sound.ENTITY_SLIME_JUMP, 1f, 0.5f);
            loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc, 10, 0.4, 0.2, 0.4, 0.05);
        });

        // ---- 3: Bounce Back (a 3 x 3 sheet of slime 1 block ahead, turning with them) ----
        c.registerLoop("mitosis_wall", e -> {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location eye = e instanceof org.bukkit.entity.LivingEntity l ? l.getEyeLocation() : e.getLocation();
                Vector ahead = eye.getDirection().setY(0);
                if (ahead.lengthSquared() < 1e-6) return;
                ahead.normalize();
                Vector side = new Vector(-ahead.getZ(), 0, ahead.getX());
                Location mid = e.getLocation().add(0, 1.0, 0).add(ahead.clone().multiply(WALL_AHEAD));
                World w = e.getWorld();
                for (double a = -WALL_HALF; a <= WALL_HALF + 1e-6; a += 0.5) {
                    for (double h = -WALL_HALF + 0.5; h <= WALL_HALF + 0.5 + 1e-6; h += 0.5) {
                        Location p = mid.clone().add(side.clone().multiply(a)).add(0, h - 0.5, 0);
                        w.spawnParticle(Particle.DUST, p, 1, 0.03, 0.03, 0.03, 0, (Math.abs(a) > 1.2 || h > 1.6 || h < -0.6) ? DEEP : BLUE);
                    }
                }
            }, 0, 2);
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.7f);
            return (CueHandle) task::cancel;
        });

        // ---- ultimate: Bubble Trouble ----
        // a flat square of slime at their feet that grows over the 2s channel to PUDDLE_MAX each way (9 x 9): what it covers
        // is what the slow and the root reach (mitosis.yml: square radius queries 0.8 ... 4). Lined up with the block grid.
        c.registerLoop("mitosis_puddle", e -> {
            BlockDisplay puddle = e.getWorld().spawn(level(e.getLocation()), BlockDisplay.class, d -> {
                d.setBlock(GOO.createBlockData());
                d.setPersistent(false);
                d.setTeleportDuration(1);
                d.setTransformation(puddle(PUDDLE_MIN));
                VisualEntities.mark(d);
            });
            int[] age = {0};
            BukkitTask[] task = new BukkitTask[1];
            Runnable end = () -> {
                if (task[0] != null) task[0].cancel();
                if (puddle.isValid()) puddle.remove();
            };
            task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid() || !puddle.isValid() || ++age[0] > PUDDLE_TICKS) {
                    end.run();
                    return;
                }
                double half = Math.max(PUDDLE_MIN, PUDDLE_MAX * age[0] / PUDDLE_TICKS);
                puddle.teleport(level(e.getLocation()));
                puddle.setInterpolationDelay(0);
                puddle.setInterpolationDuration(1); // (grows smoothly)
                puddle.setTransformation(puddle(half));
                World w = e.getWorld();
                if (age[0] % 2 == 0) square(w, e.getLocation(), half, age[0] % 4 == 0 ? BLUE : SHINE); // its edge: the reach
                if (age[0] % 5 == 0) w.playSound(e.getLocation(), Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.6f, 0.5f + age[0] / 80f);
            }, 1, 1);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 0.5f);
            return (CueHandle) end::run;
        });
        c.register("mitosis_root", loc -> { // the full square sets: rooted
            World w = loc.getWorld();
            square(w, feet(loc), PUDDLE_MAX, DEEP);
            square(w, feet(loc), PUDDLE_MAX - 0.3, BLUE);
            w.spawnParticle(Particle.ITEM_SLIME, feet(loc), 40, PUDDLE_MAX / 2, 0.1, PUDDLE_MAX / 2, 0.05);
            w.playSound(loc, Sound.BLOCK_SLIME_BLOCK_PLACE, 1.2f, 0.5f);
            w.playSound(loc, Sound.BLOCK_HONEY_BLOCK_PLACE, 1.2f, 0.5f);
        });
        c.registerLoop("mitosis_bubble", e -> { // a big bubble of slime round them, enemies inside
            BlockDisplay bubble = goo(e, center(e), 2.4f, 2.4f);
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid() || !bubble.isValid()) return;
                bubble.teleport(center(e));
                e.getWorld().spawnParticle(Particle.DUST, center(e), 3, 1.0, 1.0, 1.0, 0, SHINE);
            }, 1, 1);
            return (CueHandle) () -> {
                task.cancel();
                if (bubble.isValid()) bubble.remove();
            };
        });
        c.registerLoop("mitosis_swallowed", e -> {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                e.getWorld().spawnParticle(Particle.DUST, e.getLocation().add(0, e.getHeight() / 2, 0), 2, 0.3, 0.5, 0.3, 0, BLUE);
            }, 0, 3);
            return (CueHandle) task::cancel;
        });
        c.register("mitosis_bubble_leap", loc -> {
            loc.getWorld().playSound(loc, Sound.ENTITY_SLIME_JUMP, 1.2f, 0.4f);
            loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc, 20, 0.6, 0.3, 0.6, 0.1);
        });
        c.register("mitosis_land", loc -> {
            World w = loc.getWorld();
            ring(w, feet(loc), LAND_RADIUS, BLUE);
            w.spawnParticle(Particle.DUST, loc, 60, LAND_RADIUS / 2, 0.4, LAND_RADIUS / 2, 0, BLUE);
            w.spawnParticle(Particle.ITEM_SLIME, loc, 40, LAND_RADIUS / 2, 0.3, LAND_RADIUS / 2, 0.15);
            w.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
            w.playSound(loc, Sound.BLOCK_SLIME_BLOCK_FALL, 1.4f, 0.4f);
            w.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1.2f, 0.5f);
        });
    }

    /** A body bursting into slime. */
    private static void splash(Location loc) {
        World w = loc.getWorld();
        w.spawnParticle(Particle.DUST, loc, 50, 0.6, 0.8, 0.6, 0, BLUE);
        w.spawnParticle(Particle.DUST, loc, 20, 0.6, 0.8, 0.6, 0, DEEP);
        w.spawnParticle(Particle.ITEM_SLIME, loc, 30, 0.6, 0.8, 0.6, 0.15);
        w.playSound(loc, Sound.ENTITY_SLIME_DEATH, 1f, 0.6f);
        w.playSound(loc, Sound.BLOCK_SLIME_BLOCK_BREAK, 1f, 0.6f);
    }

    /** A slime arm along a line. */
    private static void arm(World w, Vector from, Vector to) {
        Vector d = to.clone().subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        for (double t = 0; t <= len; t += 0.3) {
            Vector p = from.clone().add(d.clone().multiply(t / len));
            w.spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), 1, 0.03, 0.03, 0.03, 0, t > len - 0.5 ? DEEP : BLUE);
        }
    }

    /** A glassy goo block centred on {@code at} (feet: on them), {@code width} across and {@code height} tall. */
    private static BlockDisplay goo(Entity e, Location at, float width, float height) {
        return e.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(GOO.createBlockData());
            d.setPersistent(false);
            d.setTeleportDuration(1);
            boolean centred = height == width;
            d.setTransformation(new Transformation(new Vector3f(-width / 2, centred ? -height / 2 : 0, -width / 2),
                    new Quaternionf(), new Vector3f(width, height, width), new Quaternionf()));
            VisualEntities.mark(d);
        });
    }

    /** A blob resized (standing on its feet). */
    private static void size(BlockDisplay d, float width, float height) {
        d.setTransformation(new Transformation(new Vector3f(-width / 2, 0, -width / 2), new Quaternionf(),
                new Vector3f(width, height, width), new Quaternionf()));
    }

    /** A cue's spot is a body's middle: its feet are about this much lower. */
    private static Location feet(Location middle) {
        return middle.clone().add(0, -0.9, 0);
    }

    private static Location center(Entity e) {
        return e.getLocation().add(0, e.getHeight() / 2, 0);
    }

    /** The puddle's flat slab, {@code half} from its middle to each side. */
    private static Transformation puddle(double half) {
        float h = (float) half;
        return new Transformation(new Vector3f(-h, 0.01f, -h), new Quaternionf(), new Vector3f(2 * h, 0.12f, 2 * h), new Quaternionf());
    }

    /** The spot without its turn (a display spawned there lines up with the block grid, like the square it shows). */
    private static Location level(Location at) {
        Location l = at.clone();
        l.setYaw(0);
        l.setPitch(0);
        return l;
    }

    /** A square's edge, {@code half} from {@code at} to each side, lined up with the block grid, on the ground under it. */
    private static void square(World w, Location at, double half, Particle.DustOptions dust) {
        double y = at.getY() + 0.2, x0 = at.getX(), z0 = at.getZ();
        double step = 0.35;
        for (double t = -half; t <= half + 1e-6; t += step) {
            w.spawnParticle(Particle.DUST, x0 + t, y, z0 - half, 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, x0 + t, y, z0 + half, 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, x0 - half, y, z0 + t, 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, x0 + half, y, z0 + t, 1, 0, 0, 0, 0, dust);
        }
    }

    /** A ring at {@code radius} around a spot, on the ground under it. */
    private static void ring(World w, Location at, double radius, Particle.DustOptions dust) {
        int n = Math.max(8, (int) (radius * 8));
        double y = at.getY() + 0.1;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * radius, y, at.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, dust);
        }
    }

    private MitosisCues() {}
}

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
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Pyromancer's cues. Each comes in orange flames ({@code pyro_x}) and blue ones ({@code pyro_x_blue}, Hellfire
 * Inferno: her character's variant plays those while it lasts).
 */
final class PyroCues {

    /** One colour of fire: its flame particle, its embers, and dust for a solid core and bright sparks. */
    private record Fire(String suffix, Particle flame, Particle ember, Particle.DustOptions core, Particle.DustOptions spark,
                        Material block) {}

    private static final Fire ORANGE = new Fire("", Particle.FLAME, Particle.LAVA,
            new Particle.DustOptions(Color.fromRGB(255, 110, 20), 1.3f), new Particle.DustOptions(Color.fromRGB(255, 210, 70), 0.9f),
            Material.FIRE);
    private static final Fire BLUE = new Fire("_blue", Particle.SOUL_FIRE_FLAME, Particle.SOUL,
            new Particle.DustOptions(Color.fromRGB(40, 170, 255), 1.3f), new Particle.DustOptions(Color.fromRGB(170, 240, 255), 0.9f),
            Material.SOUL_FIRE);

    /** Hot Coals' scorched patch: how long its fire stays (keep it in step with the ability: 8 x 0.5s) and how wide. */
    static final int COAL_FIRE_TICKS = 80;
    static final double COAL_FIRE_RADIUS = 1.8;

    /** Scorching Judgment's area: keep it in step with the ability's radius. */
    static final double JUDGMENT_RADIUS = 6;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        for (Fire f : List.of(ORANGE, BLUE)) register(c, f, plugin);
        // Overheat: always blue (it's what turns her flames blue). Starts and ends with her overheating.
        c.registerLoop("pyro_overheat", e -> {
            e.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, e.getLocation().add(0, 1, 0), 60, 0.5, 0.9, 0.5, 0.08);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1f, 0.6f);
            e.getWorld().playSound(e.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.5f);
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Vector at = e.getLocation().toVector();
                World w = e.getWorld();
                for (int i = 0; i < 3; i++) { // soul flames spiralling up around her
                    double a = tick[0] * 0.45 + Math.PI * 2 * i / 3;
                    double y = (tick[0] * 0.12 + i * 0.6) % 2.0;
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, at.getX() + Math.cos(a) * 0.6, at.getY() + y,
                            at.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0.01);
                }
                if (tick[0] % 4 == 0) w.spawnParticle(Particle.DUST, at.getX(), at.getY() + 1, at.getZ(), 2, 0.3, 0.5, 0.3, 0, BLUE.core());
                if (tick[0] % 20 == 0) w.playSound(e.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.8f, 0.7f);
                tick[0]++;
            }, 0, 2);
            return (CueHandle) () -> {
                task.cancel();
                if (!e.isValid()) return;
                e.getWorld().spawnParticle(Particle.LARGE_SMOKE, e.getLocation().add(0, 1, 0), 15, 0.4, 0.8, 0.4, 0.02);
                e.getWorld().playSound(e.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.8f);
            };
        });
        for (Fire f : List.of(ORANGE, BLUE)) {
            // Hot Coals: a few glowing coals tossed from one hand to the other, in an arc in front of her
            c.registerLoop("pyro_coals" + f.suffix(), e -> {
                e.getWorld().playSound(e.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1f, 1.4f);
                e.getWorld().playSound(e.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.6f);
                int[] tick = {0};
                BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!e.isValid()) return;
                    Vector look = e.getLocation().getDirection().setY(0);
                    if (look.lengthSquared() < 1e-6) look = new Vector(1, 0, 0);
                    look.normalize();
                    Vector side = new Vector(-look.getZ(), 0, look.getX());
                    Vector chest = e.getLocation().toVector().add(new Vector(0, 1.0, 0)).add(look.clone().multiply(0.45));
                    World w = e.getWorld();
                    for (int i = 0; i < 2; i++) { // two coals, half a toss apart: left to right and back
                        double phase = ((tick[0] + i * 6) % 12) / 12.0;            // 0..1 along one toss
                        double across = Math.cos(Math.PI * phase) * (((tick[0] + i * 6) / 12) % 2 == 0 ? 1 : -1);
                        double up = Math.sin(Math.PI * phase) * 0.45;
                        Vector q = chest.clone().add(side.clone().multiply(across * 0.4)).add(new Vector(0, up, 0));
                        w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, f.core());
                        w.spawnParticle(f.flame(), q.getX(), q.getY(), q.getZ(), 1, 0.02, 0.02, 0.02, 0.003);
                    }
                    if (tick[0] % 12 == 0) w.playSound(e.getLocation(), Sound.BLOCK_LAVA_POP, 0.4f, 1.6f);
                    tick[0]++;
                }, 0, 1);
                return (CueHandle) task::cancel;
            });
        }
    }

    private static void register(BukkitCuePlayer c, Fire f, Plugin plugin) {
        String s = f.suffix();
        // ---- Fire Bolt ----
        c.register("pyro_bolt_cast" + s, loc -> loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.7f));
        c.register("pyro_bolt_trail" + s, loc -> {
            loc.getWorld().spawnParticle(f.flame(), loc, 2, 0.05, 0.05, 0.05, 0.005);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.05, 0.05, 0.05, 0, f.spark());
        });
        c.register("pyro_bolt_hit" + s, loc -> {
            loc.getWorld().spawnParticle(f.flame(), loc, 14, 0.25, 0.3, 0.25, 0.05);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.3, 0.3, 0.3, 0, f.core());
            loc.getWorld().playSound(loc, Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.6f);
        });
        c.register("pyro_bolt_fizzle" + s, loc -> {
            loc.getWorld().spawnParticle(f.flame(), loc, 5, 0.1, 0.1, 0.1, 0.02);
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 4, 0.1, 0.1, 0.1, 0.01);
            loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.8f);
        });
        // ---- Hot Coals ----
        c.register("pyro_coal_scorch" + s, loc -> { // she lands: the coals set the ground under her on fire
            groundFire(plugin, loc, f.block());
            loc.getWorld().spawnParticle(f.flame(), loc.clone().add(0, 0.1, 0), 30, 0.9, 0.05, 0.9, 0.03);
            loc.getWorld().spawnParticle(f.ember(), loc, 4, 0.6, 0.1, 0.6, 0);
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 4, 0.6, 0.1, 0.6, 0.01);
            loc.getWorld().playSound(loc, Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.2f);
            loc.getWorld().playSound(loc, Sound.BLOCK_LAVA_EXTINGUISH, 0.4f, 1.5f);
        });
        c.register("pyro_coal_patch" + s, loc -> { // the scorched patch smouldering (every 0.5s, 1.8 blocks)
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 7; i++) {
                double a = rng.nextDouble(Math.PI * 2), r = Math.sqrt(rng.nextDouble()) * 1.8;
                Location at = loc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                if (i % 3 == 0) loc.getWorld().spawnParticle(Particle.DUST, at, 1, 0.05, 0.05, 0.05, 0, f.core());
                else loc.getWorld().spawnParticle(f.flame(), at, 1, 0.05, 0.05, 0.05, 0.005);
            }
            if (rng.nextDouble() < 0.4) loc.getWorld().spawnParticle(Particle.SMOKE, loc, 2, 0.8, 0.1, 0.8, 0.01);
        });
        // ---- Fireball ----
        c.register("pyro_fireball_cast" + s, loc -> loc.getWorld().playSound(loc, Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.1f));
        c.register("pyro_fireball_trail" + s, loc -> {
            loc.getWorld().spawnParticle(f.flame(), loc, 6, 0.22, 0.22, 0.22, 0.01);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.2, 0.2, 0.2, 0, f.core());
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0.1, 0.1, 0.1, 0.01);
            if (Math.random() < 0.2) loc.getWorld().spawnParticle(f.ember(), loc, 1, 0.1, 0.1, 0.1, 0);
        });
        c.register("pyro_fireball_explode" + s, loc -> {
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.8, 0.5, 0.8, 0);
            loc.getWorld().spawnParticle(f.flame(), loc, 90, 1.6, 0.8, 1.6, 0.12);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 30, 1.6, 0.8, 1.6, 0, f.core());
            loc.getWorld().spawnParticle(f.ember(), loc, 10, 1.2, 0.6, 1.2, 0);
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 15, 1.2, 0.6, 1.2, 0.03);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.2f);
            loc.getWorld().playSound(loc, Sound.ITEM_FIRECHARGE_USE, 1f, 0.7f);
        });
        // ---- Hunting Wisp ----
        c.register("pyro_wisp_cast" + s, loc -> {
            loc.getWorld().playSound(loc, Sound.ENTITY_ALLAY_ITEM_THROWN, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_AMBIENT, 0.5f, 1.8f);
        });
        c.register("pyro_wisp_trail" + s, loc -> { // a flickering wisp (also while it waits)
            loc.getWorld().spawnParticle(f.flame(), loc, 1, 0.08, 0.08, 0.08, 0.005);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.12, 0.12, 0.12, 0, f.spark());
            if (Math.random() < 0.05) loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.4f, 1.8f);
        });
        c.register("pyro_wisp_explode" + s, loc -> {
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
            loc.getWorld().spawnParticle(f.flame(), loc, 60, 1.2, 0.6, 1.2, 0.1);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 20, 1.2, 0.6, 1.2, 0, f.spark());
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
            loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_HURT, 0.7f, 1.6f);
        });
        c.register("pyro_wisp_fade" + s, loc -> {
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 10, 0.15, 0.15, 0.15, 0.02);
            loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.4f);
        });
        // ---- Scorching Judgment ----
        c.register("pyro_judgment_cast" + s, loc -> {
            loc.getWorld().playSound(loc, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.5f);
        });
        c.register("pyro_judgment_ring" + s, loc -> { // the marked area's edge on the ground, for everyone
            double y = loc.getY() + 0.15;
            for (int i = 0; i < 56; i++) {
                double a = Math.PI * 2 * i / 56;
                loc.getWorld().spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * JUDGMENT_RADIUS, y,
                        loc.getZ() + Math.sin(a) * JUDGMENT_RADIUS, 1, 0, 0, 0, 0, f.core());
            }
            for (int i = 0; i < 4; i++) { // and a flame here and there inside
                double a = Math.random() * Math.PI * 2, r = Math.random() * JUDGMENT_RADIUS;
                loc.getWorld().spawnParticle(f.flame(), loc.getX() + Math.cos(a) * r, y, loc.getZ() + Math.sin(a) * r, 1, 0, 0.05, 0, 0.005);
            }
        });
        c.register("pyro_meteor_fall" + s, loc -> { // heard where it'll land
            loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.5f);
            loc.getWorld().playSound(loc, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.5f);
        });
        c.register("pyro_meteor_trail" + s, loc -> {
            loc.getWorld().spawnParticle(f.flame(), loc, 20, 0.7, 0.7, 0.7, 0.03);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.8, 0.8, 0.8, 0, f.core());
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 4, 0.6, 0.6, 0.6, 0.02);
            loc.getWorld().spawnParticle(f.ember(), loc, 2, 0.5, 0.5, 0.5, 0);
        });
        c.register("pyro_meteor_impact" + s, loc -> {
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 1.5, 0.3, 1.5, 0);
            loc.getWorld().spawnParticle(f.flame(), loc, 250, JUDGMENT_RADIUS / 2, 0.8, JUDGMENT_RADIUS / 2, 0.2);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 60, JUDGMENT_RADIUS / 2, 0.6, JUDGMENT_RADIUS / 2, 0, f.core());
            loc.getWorld().spawnParticle(f.ember(), loc, 40, JUDGMENT_RADIUS / 2, 0.5, JUDGMENT_RADIUS / 2, 0);
            loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 20, 2, 0.3, 2, 0.04);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.5f, 0.6f);
        });
        c.register("pyro_scorched" + s, loc -> { // the scorched ground smouldering (every 0.5s)
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 18; i++) {
                double a = rng.nextDouble(Math.PI * 2), r = Math.sqrt(rng.nextDouble()) * JUDGMENT_RADIUS;
                Location at = loc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                loc.getWorld().spawnParticle(f.flame(), at, 1, 0.05, 0.1, 0.05, 0.01);
                if (i % 3 == 0) loc.getWorld().spawnParticle(Particle.SMOKE, at, 1, 0.1, 0.2, 0.1, 0.01);
            }
            if (rng.nextDouble() < 0.4) loc.getWorld().spawnParticle(f.ember(), loc, 2, 2.5, 0.1, 2.5, 0);
            if (rng.nextDouble() < 0.3) loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 1f, 0.8f);
        });
    }

    /**
     * Fire on the ground around {@code loc} for {@link #COAL_FIRE_TICKS}: block displays of a fire block (only a look:
     * nothing burns, spreads or hurts), one in the middle and a ring around it, each on the ground where it stands
     * (a step up or down at most). They die down at the end.
     */
    private static void groundFire(Plugin plugin, Location loc, Material block) {
        World w = loc.getWorld();
        var rng = ThreadLocalRandom.current();
        java.util.List<BlockDisplay> flames = new java.util.ArrayList<>();
        int ring = 7;
        for (int i = 0; i <= ring; i++) {
            double a = Math.PI * 2 * i / ring + rng.nextDouble(-0.3, 0.3);
            double r = i == ring ? 0 : COAL_FIRE_RADIUS * rng.nextDouble(0.55, 0.75);
            Location at = ground(loc.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            if (at == null) continue;
            float size = (float) rng.nextDouble(0.75, 1.0);
            flames.add(w.spawn(at, BlockDisplay.class, d -> {
                d.setBlock(block.createBlockData());
                d.setPersistent(false);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15)); // fire glows, even in the dark
                d.setTransformation(new Transformation(new Vector3f(-size / 2, 0, -size / 2), new Quaternionf(),
                        new Vector3f(size, size, size), new Quaternionf()));
                VisualEntities.mark(d);
            }));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> flames.forEach(d -> { // dying down over the last 0.5s
            if (!d.isValid()) return;
            Transformation t = d.getTransformation();
            float size = t.getScale().x();
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(10);
            d.setTransformation(new Transformation(new Vector3f(-size / 2, 0, -size / 2), new Quaternionf(),
                    new Vector3f(size, 0.05f, size), new Quaternionf()));
        }), COAL_FIRE_TICKS - 10);
        Bukkit.getScheduler().runTaskLater(plugin, () -> flames.forEach(d -> {
            if (d.isValid()) d.remove();
        }), COAL_FIRE_TICKS);
    }

    /** The spot on the ground at {@code at}'s x/z, a block up or down at most: free space with something solid under it. */
    private static Location ground(Location at) {
        int y = (int) Math.floor(at.getY() + 0.01);
        for (int dy : new int[]{0, 1, -1}) {
            var space = at.getWorld().getBlockAt(at.getBlockX(), y + dy, at.getBlockZ());
            if (space.isPassable() && !space.isLiquid() && space.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid()) {
                return new Location(at.getWorld(), at.getX(), y + dy, at.getZ());
            }
        }
        return null;
    }

    private PyroCues() {}
}

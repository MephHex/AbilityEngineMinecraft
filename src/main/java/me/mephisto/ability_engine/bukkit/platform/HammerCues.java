package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.function.BiPredicate;

/** The Hammer's cues: crumbling ground, floating stones, a growing slam circle, an erupting column, a shockwave. */
final class HammerCues {

    /** Keep in step with hammer.yml: Earthshatter's circle (radius, tap to full) and charge time. */
    static final double RING_FROM = 2, RING_TO = 4;
    static final int RING_TICKS = 30;
    /** Earthshatter's charge runs while the caster has this tag (it's dropped at the slam). */
    static final String CHARGING = "hammer.charging";
    /** Rock Launch's landing radius. */
    static final double LAND_RADIUS = 3;

    private static final Particle.DustOptions EARTH = new Particle.DustOptions(Color.fromRGB(140, 120, 95), 1.3f);
    private static final Particle.DustOptions RING = new Particle.DustOptions(Color.fromRGB(230, 170, 70), 1.1f);
    private static final Particle.DustOptions RING_FULL = new Particle.DustOptions(Color.fromRGB(235, 70, 40), 1.3f);
    private static final BlockData COBBLE = Material.COBBLESTONE.createBlockData();

    /** Does this entity have that tag? (the engine's, set by the plugin; until then: yes) */
    private static BiPredicate<UUID, String> hasTag = (id, tag) -> true;

    static void setTagCheck(BiPredicate<UUID, String> check) { hasTag = check; }

    private HammerCues() {}

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary ----
        c.registerLine("hammer_swing", (w, from, to) -> swing(w, from, to, false));
        c.registerLine("hammer_heavy", (w, from, to) -> swing(w, from, to, true));

        // ---- passive: the stones ----
        c.register("hammer_stone_rise", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.2, 0), 14, 0.3, 0.1, 0.3, 0, ground(loc));
            w.playSound(loc, Sound.BLOCK_STONE_BREAK, 0.8f, 0.7f);
        });
        c.register("hammer_stone_idle", loc -> loc.getWorld().spawnParticle(Particle.FALLING_DUST,
                loc.clone().add(0, -0.35, 0), 2, 0.15, 0, 0.15, 0, Material.GRAVEL.createBlockData()));
        c.register("hammer_stone_fall", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc, 16, 0.25, 0.25, 0.25, 0, COBBLE);
            w.playSound(loc, Sound.BLOCK_STONE_BREAK, 1f, 0.9f);
        });
        c.register("hammer_stone_launch", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.CRIT, loc, 10, 0.2, 0.2, 0.2, 0.3);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
            w.playSound(loc, Sound.BLOCK_STONE_HIT, 1f, 0.6f);
        });
        c.register("hammer_stone_trail", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 2, 0.15, 0.15, 0.15, 0, COBBLE);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.1, 0.1, 0.1, 0, EARTH);
        });
        c.register("hammer_stone_hit", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc, 20, 0.3, 0.4, 0.3, 0, COBBLE);
            w.spawnParticle(Particle.CRIT, loc, 12, 0.3, 0.4, 0.3, 0.2);
            w.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.7f);
            w.playSound(loc, Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
        });

        // ---- 1: Earthshatter ----
        c.registerLoop("hammer_charge_ring", e -> chargeRing(plugin, e));
        c.registerLine("hammer_slam", HammerCues::slam);

        // ---- 2: Rock Launch ----
        c.register("hammer_erupt", loc -> erupt(plugin, loc));
        c.register("hammer_land", loc -> {
            World w = loc.getWorld();
            Location feet = loc.clone().add(0, -0.9, 0);
            ringOfDebris(w, feet, LAND_RADIUS, 28);
            w.spawnParticle(Particle.EXPLOSION, feet.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
            w.playSound(feet, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.8f);
            w.playSound(feet, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.6f);
        });

        // ---- 3: Stonebreaker ----
        c.register("hammer_empower", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.ENCHANT, loc, 30, 0.4, 0.6, 0.4, 0.6);
            w.spawnParticle(Particle.BLOCK, loc, 10, 0.3, 0.4, 0.3, 0, COBBLE);
            w.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.7f, 0.8f);
            w.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);
        });
        c.registerLoop("hammer_empowered", e -> {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location hand = e.getLocation().add(0, 1.0, 0)
                        .add(right(e.getLocation().getDirection()).multiply(0.4));
                e.getWorld().spawnParticle(Particle.DUST, hand, 2, 0.15, 0.2, 0.15, 0, EARTH);
                e.getWorld().spawnParticle(Particle.ENCHANTED_HIT, hand, 1, 0.15, 0.2, 0.15, 0);
            }, 0, 4);
            return task::cancel;
        });

        // ---- F: Tectonic Slam ----
        c.registerLoop("hammer_ult_charge", e -> {
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 0.5f);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 0.6f, 0.6f);
            int[] t = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location feet = e.getLocation();
                double r = 1.2 + 0.05 * t[0]++;
                for (int i = 0; i < 6; i++) { // the ground round his feet trembles, bits jumping up
                    double a = Math.random() * Math.PI * 2;
                    Location at = feet.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                    e.getWorld().spawnParticle(Particle.BLOCK, at, 2, 0.05, 0.05, 0.05, 0, ground(at));
                }
                if (t[0] % 4 == 0) e.getWorld().playSound(feet, Sound.BLOCK_STONE_HIT, 0.6f, 0.5f);
            }, 0, 2);
            return task::cancel;
        });
        c.registerLine("hammer_shockwave", (w, from, to) -> shockwave(plugin, w, from, to));
    }

    // ---- primary ----

    /** A wide arc of sweeps in front of him (chest height); a heavy swing: dust and a deeper thud too. */
    private static void swing(World w, Vector from, Vector to, boolean heavy) {
        Vector f = flat(to.clone().subtract(from));
        Vector r = right(f);
        Vector chest = from.clone().add(new Vector(0, 0.2, 0));
        for (int i = -2; i <= 2; i++) {
            double a = Math.toRadians(i * 25);
            Vector way = f.clone().multiply(Math.cos(a)).add(r.clone().multiply(Math.sin(a)));
            Vector p = chest.clone().add(way.multiply(2.4));
            w.spawnParticle(Particle.SWEEP_ATTACK, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
            if (heavy) w.spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), 4, 0.2, 0.2, 0.2, 0, EARTH);
        }
        Location at = chest.toLocation(w);
        w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.55f);
        w.playSound(at, Sound.ITEM_MACE_SMASH_AIR, 0.6f, 0.8f);
        if (heavy) {
            w.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.8f);
            w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        }
    }

    // ---- 1: Earthshatter ----

    /**
     * The slam's circle on the ground in front of him, growing from {@link #RING_FROM} to {@link #RING_TO} blocks over
     * {@link #RING_TICKS} (the charge), red once full; gone when the charge ends (the tag's dropped).
     */
    private static CueHandle chargeRing(Plugin plugin, Entity e) {
        int[] t = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid() || (t[0] > 2 && !hasTag.test(e.getUniqueId(), CHARGING))) {
                task[0].cancel();
                return;
            }
            double grow = Math.min(1, t[0] / (double) RING_TICKS);
            double r = RING_FROM + (RING_TO - RING_FROM) * grow;
            Location feet = e.getLocation();
            Vector f = flat(feet.getDirection());
            Location mid = feet.clone().add(f.multiply(r)).add(0, 0.15, 0);
            int points = (int) (r * 9);
            Particle.DustOptions dust = grow >= 1 ? RING_FULL : RING;
            for (int i = 0; i < points; i++) {
                double a = 2 * Math.PI * i / points;
                e.getWorld().spawnParticle(Particle.DUST, mid.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0, dust);
            }
            if (t[0] % 6 == 0) e.getWorld().spawnParticle(Particle.BLOCK, mid, 4, r * 0.4, 0.05, r * 0.4, 0, ground(mid));
            if (t[0] == RING_TICKS) e.getWorld().playSound(feet, Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.6f); // full
            t[0] += 2;
        }, 0, 2);
        return () -> task[0].cancel();
    }

    /** The slam: the circle (its middle at {@code to}, its radius as far as that is from him) bursts into rubble. */
    private static void slam(World w, Vector from, Vector to) {
        double r = Math.max(1, Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()));
        Location mid = to.toLocation(w).add(0, 0.1, 0);
        ringOfDebris(w, mid, r, (int) (r * 8));
        w.spawnParticle(Particle.BLOCK, mid, (int) (r * 18), r * 0.5, 0.2, r * 0.5, 0, ground(mid));
        w.spawnParticle(Particle.EXPLOSION, mid.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.DUST, mid, (int) (r * 10), r * 0.5, 0.3, r * 0.5, 0, EARTH);
        w.playSound(mid, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.7f);
        w.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.7f);
    }

    // ---- 2: Rock Launch ----

    /** A column of stone, 2 blocks tall, bursting out of the ground under him, then sinking back. */
    private static void erupt(Plugin plugin, Location at) {
        World w = at.getWorld();
        Location feet = at.clone().add(0, -0.9, 0);
        feet.setYaw(0);
        feet.setPitch(0);
        BlockDisplay column = w.spawn(feet, BlockDisplay.class, d -> {
            d.setBlock(Material.COBBLESTONE.createBlockData());
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(-0.45f, -2.2f, -0.45f), new Quaternionf(),
                    new Vector3f(0.9f, 2f, 0.9f), new Quaternionf()));
            VisualEntities.mark(d);
        });
        w.spawnParticle(Particle.BLOCK, feet.clone().add(0, 0.2, 0), 30, 0.5, 0.2, 0.5, 0, ground(feet));
        w.playSound(feet, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1f, 0.5f);
        w.playSound(feet, Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
        w.playSound(feet, Sound.ENTITY_EVOKER_FANGS_ATTACK, 0.8f, 0.6f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> move(column, -0.2f, 3), 1);   // up: out of the ground
        Bukkit.getScheduler().runTaskLater(plugin, () -> move(column, -2.2f, 10), 20); // back down
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (column.isValid()) column.remove();
        }, 32);
    }

    private static void move(BlockDisplay d, float y, int ticks) {
        if (!d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(new Transformation(new Vector3f(-0.45f, y, -0.45f), new Quaternionf(),
                new Vector3f(0.9f, 2f, 0.9f), new Quaternionf()));
    }

    // ---- F: Tectonic Slam ----

    /** A thin wedge of breaking ground running out from him (30 degrees), over 7 ticks. */
    private static void shockwave(Plugin plugin, World w, Vector from, Vector to) {
        Vector f = flat(to.clone().subtract(from));
        Vector r = right(f);
        double length = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        Vector feet = from.clone().add(new Vector(0, -0.8, 0));
        w.playSound(feet.toLocation(w), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.5f);
        w.playSound(feet.toLocation(w), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        int steps = 7;
        int[] step = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double d0 = length * step[0] / steps, d1 = length * (step[0] + 1) / steps;
            for (double d = Math.max(0.5, d0); d < d1; d += 0.5) {
                double half = d * Math.tan(Math.toRadians(15));
                for (double s = -half; s <= half; s += 0.6) {
                    Location p = feet.clone().add(f.clone().multiply(d)).add(r.clone().multiply(s)).toLocation(w);
                    w.spawnParticle(Particle.BLOCK, p, 3, 0.15, 0.3, 0.15, 0, ground(p));
                }
                w.spawnParticle(Particle.DUST, feet.clone().add(f.clone().multiply(d)).toLocation(w), 3, half * 0.5, 0.3, half * 0.5, 0, EARTH);
            }
            Location front = feet.clone().add(f.clone().multiply(d1)).toLocation(w);
            w.spawnParticle(Particle.EXPLOSION, front, 1, 0, 0, 0, 0);
            if (step[0] % 2 == 0) w.playSound(front, Sound.BLOCK_STONE_BREAK, 1f, 0.5f);
            if (++step[0] >= steps) task[0].cancel();
        }, 0, 1);
    }

    // ---- helpers ----

    /** Chunks of the ground jumping up along a circle. */
    private static void ringOfDebris(World w, Location mid, double r, int points) {
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location p = mid.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
            w.spawnParticle(Particle.BLOCK, p, 3, 0.1, 0.2, 0.1, 0, ground(p));
        }
    }

    /** The block under a spot (its particles), or cobblestone over air. */
    private static BlockData ground(Location at) {
        var block = at.clone().add(0, -0.5, 0).getBlock();
        return block.getType().isSolid() ? block.getBlockData() : COBBLE;
    }

    private static Vector flat(Vector v) {
        Vector f = new Vector(v.getX(), 0, v.getZ());
        return f.lengthSquared() < 1e-6 ? new Vector(1, 0, 0) : f.normalize();
    }

    /** Right of a direction, on the ground. */
    private static Vector right(Vector f) {
        Vector flat = flat(f);
        return new Vector(-flat.getZ(), 0, flat.getX());
    }
}

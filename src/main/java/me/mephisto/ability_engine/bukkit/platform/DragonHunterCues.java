package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** The Dragon Hunter's cues: bone and iron, dark red, venom green. */
final class DragonHunterCues {

    private static final Particle.DustOptions BONE = new Particle.DustOptions(Color.fromRGB(230, 225, 205), 1.1f);
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(140, 20, 20), 1.0f);
    private static final Particle.DustOptions VENOM = new Particle.DustOptions(Color.fromRGB(90, 200, 60), 1.0f);
    private static final Particle.DustOptions ROPE = new Particle.DustOptions(Color.fromRGB(120, 85, 50), 0.8f);

    /** Keep these in step with the abilities' reach: Dragonbone Slam's cone, Venom Dagger's circle. */
    static final double SLAM_RANGE = 4.5;
    static final double SLAM_ANGLE = 70;
    static final int WINDUP_TICKS = 40;
    static final double DAGGER_RADIUS = 3.5;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary: Zweihander Swing ----
        c.register("dh_swing", loc -> { // a heavy hit
            loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0, BLOOD);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.7f);
        });
        c.register("dh_bash", loc -> { // the lunge's empowered swing: a stunning blow
            World w = loc.getWorld();
            w.spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.3);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            w.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
        });
        c.register("dh_whiff", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 0.7f));

        // ---- 1: Hunter's Lunge ----
        c.register("dh_lunge", loc -> {
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.3, 0.1, 0.3, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 0.8f);
        });

        // ---- 2: Dragonbone Slam ----
        c.register("dh_windup", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 2, 0), 10, 0.3, 0.3, 0.3, 0, BONE);
            loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ATTACK, 0.6f, 0.7f);
        });
        // Line cues (at: caster, to: aim): from his body's centre toward where he aims, so the cone faces his way.
        c.registerLine("dh_slam", (w, from, to) -> slam(w, from, to, false));
        c.registerLine("dh_slam_full", (w, from, to) -> slam(w, from, to, true));
        c.registerLoop("dh_slam_charge", e -> slamCharge(plugin, e));

        // ---- 3: Venom Dagger ----
        c.register("dh_dagger_spin", loc -> { // a green ring all around (3.5 blocks), low, slashes along it
            World w = loc.getWorld();
            double y = loc.getY() - 0.4;     // (played at his body's centre: about knee height)
            for (int i = 0; i < 32; i++) {
                double a = Math.PI * 2 * i / 32;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * DAGGER_RADIUS, y,
                        loc.getZ() + Math.sin(a) * DAGGER_RADIUS, 1, 0, 0, 0, 0, VENOM);
            }
            for (int i = 0; i < 8; i++) { // the slash itself, all the way round
                double a = Math.PI * 2 * i / 8;
                w.spawnParticle(Particle.SWEEP_ATTACK, loc.getX() + Math.cos(a) * 2, y + 0.2,
                        loc.getZ() + Math.sin(a) * 2, 1, 0, 0, 0, 0);
            }
            w.spawnParticle(Particle.ITEM_SLIME, loc.getX(), y, loc.getZ(), 15, 1.5, 0.2, 1.5, 0.05);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
            w.playSound(loc, Sound.ENTITY_SPIDER_HURT, 0.6f, 1.4f);
        });

        // ---- ultimate: Dragon Harpoon ----
        c.register("dh_harpoon_shot", loc -> {
            loc.getWorld().playSound(loc, Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 1f, 0.7f);
        });
        c.register("dh_harpoon_hit", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0, BLOOD);
            w.spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.2);
            w.playSound(loc, Sound.ITEM_TRIDENT_HIT, 1f, 0.7f);
            w.playSound(loc, Sound.BLOCK_CHAIN_PLACE, 1f, 0.6f);
        });
        c.registerLine("dh_harpoon_rope", (w, from, to) -> { // a rope from him to the harpoon (redrawn every 2 ticks)
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            for (double t = 0; t <= len; t += 0.4) {
                Vector q = from.clone().add(d.clone().multiply(t / len));
                w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, ROPE);
            }
        });
        c.register("dh_not_yet", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_CHAIN_HIT, 0.8f, 1.6f));
        c.register("dh_blink", loc -> {
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
        });
        c.register("dh_execute", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 40, 0.4, 0.7, 0.4, 0, BLOOD);
            w.spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 1, 0), 3, 0.4, 0.4, 0.4, 0);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f);
            w.playSound(loc, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.5f, 1.2f);
        });
    }

    /** The zweihander hits the ground in front of him: cracks along a cone (4.5 blocks). Full: bigger, and red. */
    /**
     * Dragonbone Slam winding up: its cone on the ground in front of him (where he faces, 4.5 blocks, 70 degrees),
     * outlined, and filling up from him outward as he winds up (full after 2s, when it slams by itself). Runs until the
     * cast ends. Keep WINDUP_TICKS, SLAM_RANGE and SLAM_ANGLE in step with the ability.
     */
    private static CueHandle slamCharge(Plugin plugin, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return CueHandle.NONE;
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!living.isValid()) return;
            Vector look = living.getEyeLocation().getDirection().setY(0);
            if (look.lengthSquared() < 1e-6) return;
            look.normalize();
            double base = Math.atan2(look.getZ(), look.getX());
            double half = Math.toRadians(SLAM_ANGLE / 2);
            Location at = living.getLocation();
            double y = at.getY() + 0.1;
            World w = living.getWorld();
            double filled = Math.min(1, (double) tick[0] / WINDUP_TICKS) * SLAM_RANGE;
            for (int i = 0; i <= 8; i++) { // the outline: the far edge...
                double a = base - half + 2 * half * i / 8;
                w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * SLAM_RANGE, y, at.getZ() + Math.sin(a) * SLAM_RANGE,
                        1, 0, 0, 0, 0, BONE);
            }
            for (double r = 0.5; r <= SLAM_RANGE; r += 0.5) { // ...and both sides
                for (double a : new double[]{base - half, base + half}) {
                    w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * r, y, at.getZ() + Math.sin(a) * r,
                            1, 0, 0, 0, 0, BONE);
                }
            }
            for (double r = 0.5; r <= filled; r += 0.5) { // the fill: how far he's wound up
                int across = Math.max(2, (int) Math.round(r * 2));
                for (int i = 0; i <= across; i++) {
                    double a = base - half + 2 * half * i / across;
                    w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * r, y, at.getZ() + Math.sin(a) * r,
                            1, 0, 0, 0, 0, BLOOD);
                }
            }
            tick[0] += 2;
        }, 0, 2);
        return (CueHandle) task::cancel;
    }

    private static void slam(World w, Vector from, Vector to, boolean full) {
        Location loc = new Location(w, from.getX(), from.getY(), from.getZ());
        Vector f = to.clone().subtract(from).setY(0);
        if (f.lengthSquared() < 1e-6) f = new Vector(1, 0, 0);
        f.normalize();
        double base = Math.atan2(f.getZ(), f.getX());
        double ground = from.getY() - 0.8;
        for (int i = -3; i <= 3; i++) {
            double a = base + Math.toRadians(10 * i);
            for (double r = 1; r <= SLAM_RANGE; r += 0.5) {
                double x = from.getX() + Math.cos(a) * r;
                double z = from.getZ() + Math.sin(a) * r;
                w.spawnParticle(Particle.BLOCK, x, ground + 0.1, z, 2, 0.1, 0.05, 0.1, 0,
                        Material.BONE_BLOCK.createBlockData());
                if (full) w.spawnParticle(Particle.DUST, x, ground + 0.3, z, 1, 0, 0, 0, 0, BLOOD);
            }
        }
        for (int i = -1; i <= 1; i++) { // the blade coming down
            double a = base + Math.toRadians(20 * i);
            w.spawnParticle(Particle.SWEEP_ATTACK, from.getX() + Math.cos(a) * 2, ground + 0.6,
                    from.getZ() + Math.sin(a) * 2, 1, 0, 0, 0, 0);
        }
        w.spawnParticle(Particle.EXPLOSION, from.getX() + f.getX() * 2, ground + 0.3, from.getZ() + f.getZ() * 2,
                full ? 3 : 1, 0.5, 0, 0.5, 0);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, full ? 1f : 0.6f, full ? 0.6f : 0.9f);
        w.playSound(loc, Sound.BLOCK_BONE_BLOCK_BREAK, 1f, 0.6f);
    }

    private DragonHunterCues() {}
}

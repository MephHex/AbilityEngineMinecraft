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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** The Iceman's cues: ice blue, snow, frost. */
final class IcemanCues {

    private static final Particle.DustOptions ICE = new Particle.DustOptions(Color.fromRGB(150, 210, 255), 1.1f);
    private static final Particle.DustOptions FROST = new Particle.DustOptions(Color.fromRGB(225, 245, 255), 0.9f);
    private static final BlockData ICE_BLOCK = Material.PACKED_ICE.createBlockData();

    /** Keep these in step with the abilities' reach: the shockwave's cone, the breath's cone, the storm. */
    static final double WAVE_RANGE = 7;
    static final double WAVE_ANGLE = 70;
    static final double BREATH_RANGE = 6;
    static final double BREATH_ANGLE = 50;
    static final double STORM_RADIUS = 5;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary: Ice Mace, Ice Prison ----
        c.register("iceman_mace", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc, 15, 0.3, 0.4, 0.3, 0.1, ICE_BLOCK);
            w.spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.6f);
            w.playSound(loc, Sound.BLOCK_GLASS_HIT, 0.8f, 0.8f);
        });
        c.register("iceman_whiff", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 0.6f));
        c.register("iceman_prison", loc -> { // the 3rd hit: frozen solid
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc, 40, 0.4, 0.7, 0.4, 0.1, ICE_BLOCK);
            w.spawnParticle(Particle.SNOWFLAKE, loc, 30, 0.4, 0.7, 0.4, 0.05);
            w.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.7f);
            w.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
        });
        // Ice Prison's stacks: that many ice shards circling over their head (1, 2; a 3rd freezes)
        for (int n = 1; n <= 3; n++) {
            int shards = n;
            c.registerLoop("iceman_chill_" + n, e -> shardsOverHead(plugin, e, shards));
        }
        // Frozen solid: an ice crust on them
        c.registerLoop("iceman_frozen", e -> {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation().add(0, e.getHeight() / 2, 0);
                e.getWorld().spawnParticle(Particle.BLOCK, at, 6, 0.3, e.getHeight() / 3, 0.3, 0, ICE_BLOCK);
                e.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 3, 0.3, e.getHeight() / 3, 0.3, 0.01);
            }, 0, 3);
            return (CueHandle) task::cancel;
        });

        // ---- 1: Glacial Shockwave (a line cue: at: caster, to: aim) ----
        c.registerLine("iceman_shockwave", (w, from, to) -> {
            Vector f = flat(from, to);
            double base = Math.atan2(f.getZ(), f.getX());
            double half = Math.toRadians(WAVE_ANGLE / 2);
            double ground = from.getY() - 0.8;
            for (double r = 1; r <= WAVE_RANGE; r += 0.75) {
                int across = Math.max(3, (int) Math.round(r * 1.5));
                for (int i = 0; i <= across; i++) {
                    double a = base - half + 2 * half * i / across;
                    double x = from.getX() + Math.cos(a) * r, z = from.getZ() + Math.sin(a) * r;
                    w.spawnParticle(Particle.BLOCK, x, ground + 0.2, z, 2, 0.15, 0.1, 0.15, 0, ICE_BLOCK);
                    if (i % 2 == 0) w.spawnParticle(Particle.DUST, x, ground + 0.3, z, 1, 0, 0, 0, 0, ICE);
                }
            }
            Location at = new Location(w, from.getX(), from.getY(), from.getZ());
            w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
            w.playSound(at, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
        });

        // ---- 2: Frostbreath: frost out of his mouth, where he looks, while he breathes ----
        c.registerLoop("iceman_frostbreath", e -> {
            if (!(e instanceof LivingEntity living)) return CueHandle.NONE;
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!living.isValid()) return;
                Location eye = living.getEyeLocation();
                Vector look = eye.getDirection();
                World w = living.getWorld();
                for (int i = 0; i < 10; i++) {
                    Vector d = spread(look, Math.toRadians(BREATH_ANGLE / 2));
                    w.spawnParticle(Particle.SNOWFLAKE, eye.getX(), eye.getY() - 0.2, eye.getZ(), 0,
                            d.getX(), d.getY(), d.getZ(), 0.45);
                }
                for (int i = 0; i < 4; i++) {
                    Vector d = spread(look, Math.toRadians(BREATH_ANGLE / 2));
                    w.spawnParticle(Particle.CLOUD, eye.getX(), eye.getY() - 0.2, eye.getZ(), 0,
                            d.getX(), d.getY(), d.getZ(), 0.35);
                }
                if (tick[0] % 10 == 0) w.playSound(eye, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.6f, 0.6f);
                if (tick[0] % 20 == 0) w.playSound(eye, Sound.WEATHER_RAIN, 0.4f, 1.8f);
                tick[0] += 2;
            }, 0, 2);
            return (CueHandle) task::cancel;
        });

        // ---- 3: Ice Wall (the blocks are real; this is the frost as it rises) ----
        c.register("iceman_wall", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1.5, 0), 60, 2.5, 1, 2.5, 0.05);
            w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.5, 0), 20, 2.5, 0.3, 2.5, 0.02);
            w.playSound(loc, Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
            w.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.5f);
        });

        // ---- ultimate: Glacial Tomb ----
        c.register("iceman_throw", loc -> {
            loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 1f, 0.5f);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 0.8f);
        });
        c.register("iceman_entomb", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc, 50, 0.6, 1, 0.6, 0.1, ICE_BLOCK);
            w.spawnParticle(Particle.SNOWFLAKE, loc, 40, 0.6, 1, 0.6, 0.1);
            w.playSound(loc, Sound.BLOCK_GLASS_PLACE, 1f, 0.5f);
            w.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.6f);
        });
        c.register("iceman_storm", loc -> { // every 0.25s while it rages: snow swirling in its circle, its edge
            World w = loc.getWorld();
            double y = loc.getY();
            w.spawnParticle(Particle.SNOWFLAKE, loc.getX(), y + 1.5, loc.getZ(), 40, STORM_RADIUS * 0.6, 1.2,
                    STORM_RADIUS * 0.6, 0.08);
            w.spawnParticle(Particle.WHITE_ASH, loc.getX(), y + 1.5, loc.getZ(), 30, STORM_RADIUS * 0.6, 1.2,
                    STORM_RADIUS * 0.6, 0.02);
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * STORM_RADIUS, y + 0.2,
                        loc.getZ() + Math.sin(a) * STORM_RADIUS, 1, 0, 0, 0, 0, FROST);
            }
            if (Bukkit.getCurrentTick() % 20 < 5) w.playSound(loc, Sound.ITEM_ELYTRA_FLYING, 0.3f, 1.6f);
        });
        c.register("iceman_storm_blast", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc.clone().add(0, 1, 0), 120, STORM_RADIUS * 0.5, 1, STORM_RADIUS * 0.5, 0.2, ICE_BLOCK);
            w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 120, STORM_RADIUS * 0.5, 1, STORM_RADIUS * 0.5, 0.3);
            w.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 3, 1, 0.5, 1, 0);
            w.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
            w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
        });
    }

    private static CueHandle shardsOverHead(Plugin plugin, Entity e, int shards) {
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid()) return;
            Location at = e.getLocation();
            double top = e.getBoundingBox().getMaxY() + 0.45;
            World w = e.getWorld();
            for (int i = 0; i < shards; i++) {
                double a = Math.PI * 2 * i / shards + tick[0] * 0.15;
                double x = at.getX() + Math.cos(a) * 0.45, z = at.getZ() + Math.sin(a) * 0.45;
                w.spawnParticle(Particle.DUST, x, top, z, 1, 0, 0, 0, 0, ICE);
                w.spawnParticle(Particle.DUST, x, top + 0.15, z, 1, 0, 0, 0, 0, FROST);
            }
            tick[0]++;
        }, 0, 2);
        return (CueHandle) task::cancel;
    }

    private static Vector flat(Vector from, Vector to) {
        Vector f = to.clone().subtract(from).setY(0);
        if (f.lengthSquared() < 1e-6) f = new Vector(1, 0, 0);
        return f.normalize();
    }

    /** A direction within {@code half} radians of {@code look}, at random. */
    private static Vector spread(Vector look, double half) {
        Vector d = look.clone().normalize();
        Vector r = new Vector(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).multiply(2 * Math.tan(half));
        return d.add(r).normalize();
    }

    private IcemanCues() {}
}

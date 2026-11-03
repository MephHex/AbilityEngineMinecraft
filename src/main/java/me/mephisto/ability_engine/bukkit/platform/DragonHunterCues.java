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
    static final double SLAM_RANGE = 10;
    static final double SLAM_ANGLE = 70;
    static final int WINDUP_TICKS = 40;
    static final double DAGGER_RADIUS = 3.5;
    /** The rework (2026-11-02): Crescent Cleave's half circle, Dragon's Wake's slash and guard, Clean Cut's reach. */
    static final double CRESCENT_RANGE = 4;
    static final double WAKE_RADIUS = 3.5;
    static final double GUARD_RADIUS = 1.6;
    static final double CUT_RADIUS = 3;
    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 140, 40), 1.0f);

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
        registerRework(c, plugin);
        c.register("dh_execute", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 40, 0.4, 0.7, 0.4, 0, BLOOD);
            w.spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 1, 0), 3, 0.4, 0.4, 0.4, 0);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f);
            w.playSound(loc, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.5f, 1.2f);
        });
    }

    /** The rework's cues (Hunter's Rhythm, Crescent Cleave, Dragon's Wake, Rising Fang, Clean Cut). */
    private static void registerRework(BukkitCuePlayer c, Plugin plugin) {
        // ---- passive: Hunter's Rhythm (a chime that climbs with each level) ----
        for (int level = 1; level <= 5; level++) {
            float pitch = 0.8f + 0.2f * level;
            boolean top = level == 5;
            c.register("dh_combo_up_" + level, loc -> {
                World w = loc.getWorld();
                w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, pitch);
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 1.1, 0), top ? 20 : 6, 0.3, 0.2, 0.3, 0, top ? BLOOD : BONE);
                if (top) w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.6f);
            });
        }
        c.register("dh_combo_up", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.2f));

        // ---- 1: Crescent Cleave (line: at caster, to aim; a half circle, 4 blocks) ----
        c.registerLine("dh_crescent", (w, from, to) -> {
            Vector f = to.clone().subtract(from).setY(0);
            if (f.lengthSquared() < 1e-6) f = new Vector(1, 0, 0);
            f.normalize();
            double base = Math.atan2(f.getZ(), f.getX());
            for (int i = 0; i <= 6; i++) { // the blade's sweep, side to side
                double a = base - Math.PI / 2 + Math.PI * i / 6;
                double r = CRESCENT_RANGE * 0.7;
                w.spawnParticle(Particle.SWEEP_ATTACK, from.getX() + Math.cos(a) * r, from.getY() + 0.1,
                        from.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i <= 24; i++) { // its edge: a bone-white crescent
                double a = base - Math.PI / 2 + Math.PI * i / 24;
                w.spawnParticle(Particle.DUST, from.getX() + Math.cos(a) * CRESCENT_RANGE, from.getY() + 0.1,
                        from.getZ() + Math.sin(a) * CRESCENT_RANGE, 1, 0, 0, 0, 0, i % 4 == 0 ? BLOOD : BONE);
            }
            Location at = new Location(w, from.getX(), from.getY(), from.getZ());
            w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
            w.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.6f, 1.3f);
        });

        // ---- 2: Dragon's Wake ----
        c.register("dh_dash", loc -> {
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 12, 0.3, 0.1, 0.3, 0.06);
            loc.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 1f, 0.8f);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
        });
        c.register("dh_wake_slash", loc -> { // all around as he arrives (3.5 blocks)
            World w = loc.getWorld();
            double y = loc.getY() - 0.3;
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10;
                w.spawnParticle(Particle.SWEEP_ATTACK, loc.getX() + Math.cos(a) * WAKE_RADIUS * 0.6, y,
                        loc.getZ() + Math.sin(a) * WAKE_RADIUS * 0.6, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i < 36; i++) {
                double a = Math.PI * 2 * i / 36;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * WAKE_RADIUS, loc.getY() - 0.8,
                        loc.getZ() + Math.sin(a) * WAKE_RADIUS, 1, 0, 0, 0, 0, BONE);
            }
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.9f);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 0.8f);
        });
        // The 1.5s after: the blade whirling close round him, cutting down what flies at him
        c.registerLoop("dh_blade_whirl", e -> {
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation();
                World w = e.getWorld();
                for (int i = 0; i < 2; i++) {
                    double a = tick[0] * 1.3 + Math.PI * i;
                    w.spawnParticle(Particle.SWEEP_ATTACK, at.getX() + Math.cos(a) * GUARD_RADIUS * 0.8, at.getY() + 1.1,
                            at.getZ() + Math.sin(a) * GUARD_RADIUS * 0.8, 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * GUARD_RADIUS, at.getY() + 0.6 + i * 0.8,
                            at.getZ() + Math.sin(a) * GUARD_RADIUS, 1, 0, 0, 0, 0, BONE);
                }
                if (tick[0] % 4 == 0) w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.6f);
                tick[0]++;
            }, 0, 2);
            return (CueHandle) task::cancel;
        });
        c.register("dh_parry", loc -> { // a projectile cut down
            World w = loc.getWorld();
            w.spawnParticle(Particle.CRIT, loc, 15, 0.15, 0.15, 0.15, 0.4);
            w.spawnParticle(Particle.DUST, loc, 6, 0.15, 0.15, 0.15, 0, EMBER);
            w.playSound(loc, Sound.ITEM_SHIELD_BLOCK, 0.9f, 1.5f);
            w.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.3f, 2f);
        });

        // ---- 3: Rising Fang ----
        c.register("dh_fang_cast", loc -> {
            loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_RETURN, 1f, 0.8f);
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.4, 0), 10, 0.3, 0.3, 0.3, 0, BLOOD);
        });
        // While it's ready: a red glint running up the blade
        c.registerLoop("dh_fang_ready", e -> {
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!(e instanceof LivingEntity l) || !l.isValid()) return;
                Location hand = l.getLocation().add(0, 1.0, 0);
                Vector look = l.getLocation().getDirection().setY(0);
                Vector side = look.lengthSquared() > 1e-6
                        ? new Vector(-look.getZ(), 0, look.getX()).normalize().multiply(-0.4) : new Vector();
                double up = (tick[0] % 5) * 0.2;
                l.getWorld().spawnParticle(Particle.DUST, hand.getX() + side.getX(), hand.getY() + up, hand.getZ() + side.getZ(),
                        1, 0, 0, 0, 0, BLOOD);
                tick[0]++;
            }, 0, 2);
            return (CueHandle) task::cancel;
        });
        c.register("dh_fang", loc -> { // the uppercut: thrown up and marked
            World w = loc.getWorld();
            w.spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 0.8, 0), 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.CRIT, loc.clone().add(0, 0.5, 0), 25, 0.3, 0.8, 0.3, 0.4);
            w.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0, BLOOD);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
        });
        // Marked: a red rune turning over their head
        c.registerLoop("dh_marked", e -> {
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location top = e.getLocation().add(0, e.getHeight() + 0.5, 0);
                for (int i = 0; i < 3; i++) {
                    double a = tick[0] * 0.5 + Math.PI * 2 * i / 3;
                    e.getWorld().spawnParticle(Particle.DUST, top.getX() + Math.cos(a) * 0.35, top.getY(),
                            top.getZ() + Math.sin(a) * 0.35, 1, 0, 0, 0, 0, BLOOD);
                }
                tick[0]++;
            }, 0, 3);
            return (CueHandle) task::cancel;
        });
        c.register("dh_bleed_open", loc -> { // the mark set off
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0, BLOOD);
            w.spawnParticle(Particle.DAMAGE_INDICATOR, loc.clone().add(0, 1, 0), 4, 0.3, 0.3, 0.3, 0.1);
            w.playSound(loc, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.7f);
        });
        // Bleeding: drops of blood falling off them
        c.registerLoop("dh_bleeding", e -> {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation().add(0, e.getHeight() * 0.6, 0);
                e.getWorld().spawnParticle(Particle.DUST, at, 3, 0.25, 0.3, 0.25, 0, BLOOD);
                e.getWorld().spawnParticle(Particle.FALLING_DUST, at, 1, 0.2, 0.2, 0.2, 0,
                        Material.REDSTONE_BLOCK.createBlockData());
            }, 0, 4);
            return (CueHandle) task::cancel;
        });

        // ---- ultimate: Clean Cut ----
        c.register("dh_cut_ready", loc -> { // (to him only) pick a spot
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
            loc.getWorld().playSound(loc, Sound.BLOCK_BELL_USE, 0.6f, 0.6f);
        });
        c.register("dh_cut_leap", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.CLOUD, loc.clone().add(0, -0.8, 0), 15, 0.4, 0.05, 0.4, 0.06);
            w.spawnParticle(Particle.DUST, loc, 15, 0.3, 0.6, 0.3, 0, BLOOD);
            w.playSound(loc, Sound.ENTITY_PHANTOM_SWOOP, 1f, 0.7f);
        });
        c.register("dh_clean_cut", loc -> { // one clean stroke all around where he lands (3 blocks)
            World w = loc.getWorld();
            double y = loc.getY() + 0.9;
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                w.spawnParticle(Particle.SWEEP_ATTACK, loc.getX() + Math.cos(a) * CUT_RADIUS * 0.7, y,
                        loc.getZ() + Math.sin(a) * CUT_RADIUS * 0.7, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i < 40; i++) {
                double a = Math.PI * 2 * i / 40;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * CUT_RADIUS, loc.getY() + 0.1,
                        loc.getZ() + Math.sin(a) * CUT_RADIUS, 1, 0, 0, 0, 0, i % 2 == 0 ? BLOOD : BONE);
            }
            w.spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f);
            w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
        });
        c.register("dh_cut_return", loc -> { // a kill: back where he cast it, ready again
            World w = loc.getWorld();
            w.spawnParticle(Particle.LARGE_SMOKE, loc, 15, 0.3, 0.6, 0.3, 0.02);
            w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.2f);
            w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 2f);
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

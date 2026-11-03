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

/** The Warrior's cues: polished steel, white sparks; his charge at full speed burns orange. */
final class WarriorCues {

    private static final Particle.DustOptions STEEL = new Particle.DustOptions(Color.fromRGB(200, 205, 215), 1.1f);
    private static final Particle.DustOptions STEEL_DARK = new Particle.DustOptions(Color.fromRGB(110, 115, 130), 1.0f);
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(240, 190, 70), 1.0f);
    private static final Particle.DustOptions EMBER = new Particle.DustOptions(Color.fromRGB(255, 120, 30), 1.3f);

    /** Keep these in step with warrior.yml: the slash's cone, Leaping Strike's, Whirlwind's circle, the crash's blast. */
    static final double SLASH_RANGE = 3.2, SLASH_ANGLE = 90;
    static final double STRIKE_RANGE = 3.5, STRIKE_ANGLE = 110;
    static final double WHIRL_RADIUS = 3;
    static final double CRASH_RADIUS = 3.5;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary: Sword Slash (line: at caster, to aim) ----
        c.registerLine("warrior_slash", (w, from, to) -> {
            arc(w, from, to, SLASH_RANGE * 0.75, SLASH_ANGLE, 5, STEEL);
            w.playSound(at(w, from), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.1f);
            w.playSound(at(w, from), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 1.0f);
        });
        c.register("warrior_whiff", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1.0f));

        // ---- 1: Leaping Strike ----
        c.register("warrior_leap", loc -> {
            loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, -0.8, 0), 10, 0.3, 0.05, 0.3, 0.04);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.8f);
            loc.getWorld().playSound(loc, Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 0.9f);
        });
        c.registerLine("warrior_leap_strike", (w, from, to) -> { // the blade comes down, the ground cracks in front
            arc(w, from, to, STRIKE_RANGE * 0.7, STRIKE_ANGLE, 6, STEEL);
            Vector f = flat(from, to);
            double ground = from.getY() - 0.8;
            for (double r = 1; r <= STRIKE_RANGE; r += 0.5) {
                w.spawnParticle(Particle.BLOCK, from.getX() + f.getX() * r, ground + 0.1, from.getZ() + f.getZ() * r, 3,
                        0.3, 0.05, 0.3, 0, Material.STONE.createBlockData());
            }
            w.spawnParticle(Particle.CRIT, from.getX() + f.getX() * 1.5, ground + 0.6, from.getZ() + f.getZ() * 1.5, 20,
                    0.6, 0.3, 0.6, 0.3);
            w.playSound(at(w, from), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
            w.playSound(at(w, from), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
        });

        // ---- 2: Iron Will ----
        c.register("warrior_iron_will", loc -> {
            World w = loc.getWorld();
            w.playSound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.9f);
            w.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.5f, 1.4f);
            w.spawnParticle(Particle.DUST, loc, 30, 0.4, 0.7, 0.4, 0, STEEL);
            w.spawnParticle(Particle.DUST, loc, 12, 0.4, 0.7, 0.4, 0, GOLD);
            ring(w, loc, 1.2, loc.getY() - 0.8, STEEL_DARK);
        });
        // While it lasts: steel motes circling him, a glint of gold now and then
        c.registerLoop("warrior_iron_will_aura", e -> every(plugin, e, 3, (w, at, tick) -> {
            for (int i = 0; i < 3; i++) {
                double a = tick * 0.5 + Math.PI * 2 * i / 3;
                w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * 0.7, at.getY() + 0.2 + (tick + i * 3) % 9 * 0.2,
                        at.getZ() + Math.sin(a) * 0.7, 1, 0, 0, 0, 0, i == 0 && tick % 4 == 0 ? GOLD : STEEL);
            }
        }, null));

        // ---- 3: Whirlwind ----
        // The blade circling him (3 blocks) while he spins: two sweeps a step, a ring on the ground, the whoosh.
        c.registerLoop("warrior_whirlwind", e -> every(plugin, e, 2, (w, at, tick) -> {
            for (int i = 0; i < 2; i++) {
                double a = tick * 1.1 + Math.PI * i;
                double r = WHIRL_RADIUS * 0.65;
                w.spawnParticle(Particle.SWEEP_ATTACK, at.getX() + Math.cos(a) * r, at.getY() + 1,
                        at.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * WHIRL_RADIUS, at.getY() + 0.9,
                        at.getZ() + Math.sin(a) * WHIRL_RADIUS, 2, 0.1, 0.1, 0.1, 0, STEEL);
            }
            if (tick % 3 == 0) ring(w, new Location(w, at.getX(), at.getY(), at.getZ()), WHIRL_RADIUS, at.getY() + 0.1, STEEL_DARK);
            if (tick % 5 == 0) w.playSound(new Location(w, at.getX(), at.getY(), at.getZ()), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.3f);
        }, null));
        c.register("warrior_whirlwind_hit", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 1.3f));

        // ---- ultimate: Unstoppable Charge ----
        c.register("warrior_charge_start", loc -> {
            World w = loc.getWorld();
            w.playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 0.9f, 1.3f);
            w.playSound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.7f);
            w.spawnParticle(Particle.CLOUD, loc.clone().add(0, -0.8, 0), 20, 0.5, 0.1, 0.5, 0.05);
        });
        // Running: dust kicked up at his feet, and a heavy footstep now and then (it quickens as he speeds up)
        c.registerLoop("warrior_charge", e -> every(plugin, e, 2, (w, at, tick) -> {
            w.spawnParticle(Particle.BLOCK, at.getX(), at.getY() + 0.1, at.getZ(), 4, 0.3, 0.05, 0.3, 0,
                    Material.DIRT.createBlockData());
            w.spawnParticle(Particle.CLOUD, at.getX(), at.getY() + 0.2, at.getZ(), 1, 0.2, 0.05, 0.2, 0.01);
            if (tick % 3 == 0) w.playSound(new Location(w, at.getX(), at.getY(), at.getZ()), Sound.ENTITY_IRON_GOLEM_STEP, 0.8f, 1.1f);
        }, null));
        c.register("warrior_charge_full_start", loc -> { // full speed: he flares up
            World w = loc.getWorld();
            w.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
            w.playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8f, 0.8f);
            w.spawnParticle(Particle.FLAME, loc, 30, 0.4, 0.6, 0.4, 0.08);
            w.spawnParticle(Particle.DUST, loc, 25, 0.5, 0.7, 0.5, 0, EMBER);
        });
        // At full speed: flames streaming off him, and a burning wedge in front where he'll pin whoever he meets
        c.registerLoop("warrior_charge_full", e -> every(plugin, e, 1, (w, at, tick) -> {
            Vector look = e instanceof LivingEntity l ? l.getEyeLocation().getDirection().setY(0) : new Vector(1, 0, 0);
            if (look.lengthSquared() < 1e-6) look = new Vector(1, 0, 0);
            look.normalize();
            Vector side = new Vector(-look.getZ(), 0, look.getX());
            for (int i = -1; i <= 1; i++) { // the wedge
                double s = i * 0.45;
                w.spawnParticle(Particle.DUST, at.getX() + look.getX() * (1.2 - Math.abs(s)) + side.getX() * s, at.getY() + 1,
                        at.getZ() + look.getZ() * (1.2 - Math.abs(s)) + side.getZ() * s, 1, 0, 0.3, 0, 0, EMBER);
            }
            w.spawnParticle(Particle.FLAME, at.getX() - look.getX() * 0.5, at.getY() + 1, at.getZ() - look.getZ() * 0.5,
                    2, 0.25, 0.5, 0.25, 0.01);
        }, null));
        c.register("warrior_bump", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.CRIT, loc, 15, 0.3, 0.4, 0.3, 0.3);
            w.spawnParticle(Particle.CLOUD, loc.clone().add(0, -0.8, 0), 8, 0.3, 0.05, 0.3, 0.05);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
            w.playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 1.0f);
        });
        c.register("warrior_pin", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 20, 0.3, 0.5, 0.3, 0, EMBER);
            w.spawnParticle(Particle.CRIT, loc, 20, 0.3, 0.5, 0.3, 0.4);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            w.playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
        });
        c.register("warrior_wall_slam", loc -> { // crushed into the wall
            World w = loc.getWorld();
            w.spawnParticle(Particle.EXPLOSION, loc, 2, 0.3, 0.3, 0.3, 0);
            w.spawnParticle(Particle.BLOCK, loc, 30, 0.5, 0.6, 0.5, 0, Material.STONE.createBlockData());
            w.spawnParticle(Particle.DUST, loc, 25, 0.4, 0.6, 0.4, 0, EMBER);
            w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            w.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.6f);
        });
        c.register("warrior_crash", loc -> { // into terrain: the blast all around (3.5 blocks)
            World w = loc.getWorld();
            double y = loc.getY() - 0.8;
            for (double r = 1; r <= CRASH_RADIUS; r += 1.25) ring(w, loc, r, y + 0.1, STEEL_DARK);
            ring(w, loc, CRASH_RADIUS, y + 0.1, STEEL);
            w.spawnParticle(Particle.BLOCK, loc.getX(), y + 0.2, loc.getZ(), 40, CRASH_RADIUS / 2, 0.1, CRASH_RADIUS / 2, 0,
                    Material.STONE.createBlockData());
            w.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
            w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
            w.playSound(loc, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1f, 0.6f);
        });
        c.register("warrior_charge_end", loc -> { // ran out of breath
            loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, -0.8, 0), 12, 0.4, 0.05, 0.4, 0.03);
            loc.getWorld().playSound(loc, Sound.ENTITY_HORSE_BREATHE, 1f, 0.7f);
        });
    }

    /** A sword's arc in front: sweeps along a curve of {@code angle} degrees at {@code radius}, facing from -> to. */
    private static void arc(World w, Vector from, Vector to, double radius, double angle, int sweeps, Particle.DustOptions dust) {
        Vector f = flat(from, to);
        double base = Math.atan2(f.getZ(), f.getX());
        double half = Math.toRadians(angle / 2);
        for (int i = 0; i < sweeps; i++) {
            double a = base - half + 2 * half * i / Math.max(1, sweeps - 1);
            w.spawnParticle(Particle.SWEEP_ATTACK, from.getX() + Math.cos(a) * radius, from.getY() + 0.2,
                    from.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i <= 12; i++) {
            double a = base - half + 2 * half * i / 12;
            w.spawnParticle(Particle.DUST, from.getX() + Math.cos(a) * (radius + 0.4), from.getY() + 0.2,
                    from.getZ() + Math.sin(a) * (radius + 0.4), 1, 0, 0, 0, 0, dust);
        }
    }

    /** The level way from -> to (unit; +x if there's none). */
    private static Vector flat(Vector from, Vector to) {
        Vector f = to.clone().subtract(from).setY(0);
        return f.lengthSquared() < 1e-6 ? new Vector(1, 0, 0) : f.normalize();
    }

    private static Location at(World w, Vector v) { return new Location(w, v.getX(), v.getY(), v.getZ()); }

    /** A ring of dust around {@code center}, at height {@code y}. */
    private static void ring(World w, Location center, double radius, double y, Particle.DustOptions dust) {
        int points = Math.max(12, (int) (radius * 9));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            w.spawnParticle(Particle.DUST, center.getX() + Math.cos(a) * radius, y, center.getZ() + Math.sin(a) * radius,
                    1, 0, 0.05, 0, 0, dust);
        }
    }

    /** One step of a looping cue: the world, where the entity's feet are, and how many steps came before. */
    @FunctionalInterface
    private interface Step {
        void run(World w, Vector at, int tick);
    }

    /** Run {@code step} on the entity every {@code period} ticks until stopped; then {@code end} (null = nothing). */
    private static CueHandle every(Plugin plugin, Entity e, int period, Step step, java.util.function.Consumer<Entity> end) {
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid()) return;
            step.run(e.getWorld(), e.getLocation().toVector(), tick[0]++);
        }, 0, period);
        return () -> {
            task.cancel();
            if (end != null && e.isValid()) end.accept(e);
        };
    }

    private WarriorCues() {}
}

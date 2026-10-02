package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** The Berserker's cues: an axe, blood-red rage, a roar. */
final class BerserkerCues {

    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(170, 10, 10), 1.4f);
    private static final Particle.DustOptions BLOOD_SMALL = new Particle.DustOptions(Color.fromRGB(120, 0, 0), 0.9f);
    private static final Particle.DustOptions RAGE = new Particle.DustOptions(Color.fromRGB(255, 60, 30), 1.2f);

    /** Whirling Leap's spin: keep it in step with the ability's radius. */
    static final double SPIN_RADIUS = 3.2;
    /** Reaping Cleave: the handle's reach and the blade's edge (keep them in step with the ability's radii). */
    static final double CLEAVE_INNER = 2.5;
    static final double CLEAVE_OUTER = 5;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary: Axe Chop ----
        c.register("berserker_chop", loc -> {
            loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0.4, 0.1, 0.4, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.9f, 0.7f);
            loc.getWorld().playSound(loc, Sound.ITEM_AXE_STRIP, 0.6f, 0.8f);
        });

        // ---- 1: Whirling Leap ----
        c.register("berserker_leap", loc -> {
            loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, -0.8, 0), 8, 0.3, 0.05, 0.3, 0.03);
            loc.getWorld().playSound(loc, Sound.ENTITY_GOAT_LONG_JUMP, 1f, 0.8f);
        });
        c.register("berserker_spin", loc -> {
            World w = loc.getWorld();
            double y = loc.getY() - 0.2;
            for (int i = 0; i < 10; i++) { // the axe swung all the way round
                double a = Math.PI * 2 * i / 10;
                double r = SPIN_RADIUS * 0.7;
                w.spawnParticle(Particle.SWEEP_ATTACK, loc.getX() + Math.cos(a) * r, y, loc.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            ring(w, loc, SPIN_RADIUS, loc.getY() - 0.8, BLOOD_SMALL);
            w.spawnParticle(Particle.CRIT, loc, 25, SPIN_RADIUS / 2, 0.3, SPIN_RADIUS / 2, 0.2);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
            w.playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.7f, 1.4f);
        });

        // ---- 2: War Cry ----
        c.register("berserker_war_cry_charge", loc -> { // drawing breath
            loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_STUNNED, 0.8f, 1.2f);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 15, 0.4, 0.6, 0.4, 0, RAGE);
        });
        c.register("berserker_war_cry", loc -> {
            World w = loc.getWorld();
            w.playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 1.1f);
            w.spawnParticle(Particle.ANGRY_VILLAGER, loc.clone().add(0, 0.9, 0), 4, 0.4, 0.2, 0.4, 0);
            for (double r = 1; r <= 3; r += 1) ring(w, loc, r, loc.getY(), RAGE); // the shout rolling out
        });
        // While it lasts: a red haze around him (his axe drinks)
        c.registerLoop("berserker_war_cry_aura", e -> every(plugin, e, 3, (w, at, tick) -> {
            double a = tick * 0.6;
            for (int i = 0; i < 2; i++) {
                double b = a + Math.PI * i;
                w.spawnParticle(Particle.DUST, at.getX() + Math.cos(b) * 0.55, at.getY() + 0.3 + (tick % 6) * 0.25,
                        at.getZ() + Math.sin(b) * 0.55, 1, 0, 0, 0, 0, RAGE);
            }
        }, null));

        // ---- 3: Reaping Cleave ----
        c.register("berserker_cleave_windup", loc -> {
            loc.getWorld().playSound(loc, Sound.ITEM_AXE_SCRAPE, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.6f, 0.7f);
        });
        c.register("berserker_cleave_ring", loc -> { // the telegraph: the blade's band, between the two rings
            World w = loc.getWorld();
            double y = loc.getY() - 0.8;
            ring(w, loc, CLEAVE_OUTER, y, BLOOD);
            ring(w, loc, CLEAVE_INNER, y, BLOOD_SMALL);
        });
        c.register("berserker_cleave", loc -> {
            World w = loc.getWorld();
            double r = (CLEAVE_INNER + CLEAVE_OUTER) / 2;
            for (int i = 0; i < 16; i++) { // the blade's arc, all the way round, where the blade hits
                double a = Math.PI * 2 * i / 16;
                double x = loc.getX() + Math.cos(a) * r, z = loc.getZ() + Math.sin(a) * r;
                w.spawnParticle(Particle.SWEEP_ATTACK, x, loc.getY() - 0.2, z, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.DUST, x, loc.getY() - 0.2, z, 3, 0.4, 0.2, 0.4, 0, BLOOD);
            }
            w.spawnParticle(Particle.DAMAGE_INDICATOR, loc, 12, r / 2, 0.3, r / 2, 0.1);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            w.playSound(loc, Sound.ENTITY_RAVAGER_ATTACK, 0.8f, 0.9f);
        });

        // ---- passive: Bloodlust (low on health) ----
        c.registerLoop("berserker_bloodlust", e -> {
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.2f);
            return every(plugin, e, 4, (w, at, tick) -> {
                w.spawnParticle(Particle.DUST, at.getX(), at.getY() + 1, at.getZ(), 2, 0.3, 0.5, 0.3, 0, BLOOD_SMALL); // dripping
                if (tick % 5 == 0) w.playSound(e.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 1.3f);
            }, null);
        });

        // ---- ultimate: Rampage ----
        c.register("berserker_rampage_start", loc -> {
            World w = loc.getWorld();
            w.playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.7f);
            w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
            w.spawnParticle(Particle.DUST, loc, 60, 0.8, 1, 0.8, 0, BLOOD);
            w.spawnParticle(Particle.ANGRY_VILLAGER, loc.clone().add(0, 1, 0), 6, 0.5, 0.3, 0.5, 0);
            for (double r = 1.5; r <= 4.5; r += 1.5) ring(w, loc, r, loc.getY() - 0.8, BLOOD);
        });
        c.registerLoop("berserker_rampage", e -> every(plugin, e, 2, (w, at, tick) -> {
            for (int i = 0; i < 2; i++) { // red flames licking up around him
                double a = tick * 0.5 + Math.PI * i;
                double y = (tick * 0.15 + i) % 2.0;
                w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * 0.6, at.getY() + y, at.getZ() + Math.sin(a) * 0.6,
                        1, 0, 0, 0, 0, BLOOD);
            }
            if (tick % 10 == 0) w.spawnParticle(Particle.ANGRY_VILLAGER, at.getX(), at.getY() + 2.1, at.getZ(), 1, 0.2, 0, 0.2, 0);
        }, entity -> {
            entity.getWorld().spawnParticle(Particle.LARGE_SMOKE, entity.getLocation().add(0, 1, 0), 15, 0.4, 0.8, 0.4, 0.02);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 0.6f, 0.8f);
        }));
    }

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

    private BerserkerCues() {}
}

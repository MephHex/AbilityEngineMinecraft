package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** The Graviton's cues: deep violet, warping portal motes, low hums and heavy thuds. */
final class GravitonCues {

    private static final Particle.DustOptions INDIGO = new Particle.DustOptions(Color.fromRGB(90, 40, 200), 1.2f);
    private static final Particle.DustOptions VIOLET = new Particle.DustOptions(Color.fromRGB(160, 110, 255), 0.9f);

    /** Gravitic Collapse's circle (its radius in graviton.yml). */
    private static final double WELL_RADIUS = 6;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary ----
        c.register("graviton_orb_shot", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_SHULKER_SHOOT, 0.7f, 0.7f));
        c.register("graviton_orb_burst", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.REVERSE_PORTAL, loc, 30, 0.6, 0.6, 0.6, 0.15);
            w.spawnParticle(Particle.DUST, loc, 20, 1.0, 0.6, 1.0, 0, INDIGO);
            w.playSound(loc, Sound.ENTITY_SHULKER_BULLET_HIT, 1f, 0.6f);
            w.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.4f, 1.6f);
        });

        // ---- 1: Gravity Grasp ----
        c.register("graviton_grasp_throw", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_EYE_LAUNCH, 1f, 0.6f));
        c.register("graviton_grasp_catch", loc -> {
            loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 25, 0.3, 0.5, 0.3, 0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.8f);
        });
        c.registerLine("graviton_grasp_line", (w, from, to) -> { // a taut violet thread, redrawn every other tick
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            for (double k = 0; k <= len; k += 0.35) {
                Vector q = from.clone().add(d.clone().multiply(k / len));
                w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0.02, 0.02, 0.02, 0, VIOLET);
            }
        });
        c.registerLoop("graviton_grasped", e -> { // a ring of motes squeezing around them while they're held
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation().add(0, 0.9, 0);
                at.getWorld().spawnParticle(Particle.PORTAL, at, 6, 0.4, 0.6, 0.4, 0.4);
            }, 0, 3);
            return (CueHandle) task::cancel;
        });
        c.register("graviton_drag", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f));
        c.register("graviton_slam", loc -> { // slammed into each other
            World w = loc.getWorld();
            w.spawnParticle(Particle.EXPLOSION, loc, 2, 0.3, 0.3, 0.3, 0);
            w.spawnParticle(Particle.DUST, loc, 30, 0.8, 0.8, 0.8, 0, INDIGO);
            w.playSound(loc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.8f);
        });

        // ---- 2: Kinetic Sink ----
        c.registerLoop("graviton_sink", e -> { // a well swirling in front of him while he channels
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 0.6f);
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location eye = e instanceof org.bukkit.entity.LivingEntity l ? l.getEyeLocation() : e.getLocation();
                Vector look = eye.getDirection().setY(0);
                if (look.lengthSquared() < 1e-6) return;
                look.normalize();
                Vector right = new Vector(-look.getZ(), 0, look.getX());
                Location center = eye.clone().add(look.clone().multiply(1.0)).add(0, -0.4, 0);
                for (int i = 0; i < 8; i++) {
                    double a = tick[0] * 0.4 + Math.PI * 2 * i / 8;
                    double r = 1.6;
                    Location q = center.clone().add(right.clone().multiply(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0);
                    q.getWorld().spawnParticle(Particle.DUST, q, 1, 0, 0, 0, 0, i % 2 == 0 ? INDIGO : VIOLET);
                }
                center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center, 3, 0.3, 0.3, 0.3, 0.02);
                tick[0]++;
            }, 0, 2);
            return (CueHandle) task::cancel;
        });
        c.register("graviton_absorb", loc -> {
            loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 20, 0.2, 0.2, 0.2, 0.2);
            loc.getWorld().playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.6f, 1.6f);
        });

        // ---- 3: Stasis Field (the field itself is drawn by SlowFieldRenderer) ----
        c.register("graviton_field", loc -> {
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);
            loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 1, 0), 60, 2.5, 1.0, 2.5, 0.05);
        });

        // ---- ULTIMATE: Gravitic Collapse ----
        c.register("graviton_rise", loc -> {
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);
            loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 40, 0.5, 0.3, 0.5, 0.1);
        });
        c.register("graviton_well", loc -> { // the circle on the ground, pulsing while it lifts them
            World w = loc.getWorld();
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * WELL_RADIUS, loc.getY() + 0.15,
                        loc.getZ() + Math.sin(a) * WELL_RADIUS, 1, 0, 0, 0, 0, INDIGO);
            }
            w.spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 0.5, 0), 25, WELL_RADIUS / 2, 0.3, WELL_RADIUS / 2, 0.08);
            w.playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 0.3f, 1.8f);
        });
        c.registerLoop("graviton_lifted", e -> { // motes rising under them as they float
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location feet = e.getLocation();
                feet.getWorld().spawnParticle(Particle.REVERSE_PORTAL, feet, 4, 0.3, 0.1, 0.3, 0.02);
            }, 0, 2);
            return (CueHandle) task::cancel;
        });
        c.register("graviton_collapse", loc -> { // everyone smashed down at once
            World w = loc.getWorld();
            w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.3, 0), 120, WELL_RADIUS / 2, 0.3, WELL_RADIUS / 2, 0, INDIGO);
            w.playSound(loc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.6f);
            w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
        });
    }

    private GravitonCues() {}
}

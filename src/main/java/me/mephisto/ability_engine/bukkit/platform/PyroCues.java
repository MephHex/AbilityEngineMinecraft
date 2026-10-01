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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Pyromancer's cues. Each comes in orange flames ({@code pyro_x}) and blue ones ({@code pyro_x_blue}, Hellfire
 * Inferno: her character's variant plays those while it lasts).
 */
final class PyroCues {

    /** One colour of fire: its flame particle, its embers, and dust for a solid core and bright sparks. */
    private record Fire(String suffix, Particle flame, Particle ember, Particle.DustOptions core, Particle.DustOptions spark) {}

    private static final Fire ORANGE = new Fire("", Particle.FLAME, Particle.LAVA,
            new Particle.DustOptions(Color.fromRGB(255, 110, 20), 1.3f), new Particle.DustOptions(Color.fromRGB(255, 210, 70), 0.9f));
    private static final Fire BLUE = new Fire("_blue", Particle.SOUL_FIRE_FLAME, Particle.SOUL,
            new Particle.DustOptions(Color.fromRGB(40, 170, 255), 1.3f), new Particle.DustOptions(Color.fromRGB(170, 240, 255), 0.9f));

    /** Scorching Judgment's area: keep it in step with the ability's radius. */
    static final double JUDGMENT_RADIUS = 6;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        for (Fire f : List.of(ORANGE, BLUE)) register(c, f);
        // Hellfire Inferno: always blue (it's what turns her flames blue)
        c.registerLoop("pyro_hellfire", e -> {
            e.getWorld().playSound(e.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.5f);
            e.getWorld().playSound(e.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 0.8f);
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
            return (CueHandle) task::cancel;
        });
        c.register("pyro_hellfire_start", loc -> {
            loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 60, 0.5, 0.9, 0.5, 0.08);
            loc.getWorld().spawnParticle(Particle.SOUL, loc, 8, 0.5, 0.8, 0.5, 0.03);
            loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_AMBIENT, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.BLOCK_SOUL_SAND_BREAK, 1f, 0.6f);
        });
        c.register("pyro_hellfire_end", loc -> {
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 15, 0.4, 0.8, 0.4, 0.02);
            loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.8f);
        });
    }

    private static void register(BukkitCuePlayer c, Fire f) {
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
        // ---- Overheat ----
        c.register("pyro_heat_full" + s, loc -> { // the gauge is full: right click is ready
            loc.getWorld().spawnParticle(f.flame(), loc, 25, 0.4, 0.7, 0.4, 0.04);
            loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_AMBIENT, 0.8f, 1.4f);
            loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f);
        });
        c.register("pyro_beam_start" + s, loc -> {
            loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        });
        c.register("pyro_beam_end" + s, loc -> {
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.3, 0.5, 0.3, 0.02);
            loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.9f);
        });
        c.registerLine("pyro_beam" + s, (w, from, to) -> { // one pulse of the beam (every 4 ticks): a column of fire
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            var rng = ThreadLocalRandom.current();
            for (double k = 0.6; k <= len; k += 0.3) {
                Vector q = from.clone().add(d.clone().multiply(k / len));
                w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0.04, 0.04, 0.04, 0, f.spark());
                w.spawnParticle(f.flame(), q.getX(), q.getY(), q.getZ(), 1, 0.12, 0.12, 0.12, 0.01);
                if (rng.nextDouble() < 0.15) w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0.2, 0.2, 0.2, 0, f.core());
            }
            w.spawnParticle(f.flame(), to.getX(), to.getY(), to.getZ(), 10, 0.25, 0.25, 0.25, 0.06);
            if (rng.nextDouble() < 0.3) w.spawnParticle(f.ember(), to.getX(), to.getY(), to.getZ(), 1, 0.2, 0.2, 0.2, 0);
            Location at = new Location(w, from.getX(), from.getY(), from.getZ());
            w.playSound(at, Sound.BLOCK_FIRE_AMBIENT, 0.9f, 1.5f);
            w.playSound(new Location(w, to.getX(), to.getY(), to.getZ()), Sound.ITEM_FIRECHARGE_USE, 0.25f, 1.8f);
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

    private PyroCues() {}
}

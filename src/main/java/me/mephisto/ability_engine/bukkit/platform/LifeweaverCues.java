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

/** The Lifeweaver's cues: oxidized copper - verdigris, cyan-green and teal - and copper's scraped sparkle. */
final class LifeweaverCues {

    /** Oxidized copper: the deep verdigris... */
    private static final Particle.DustOptions VERDIGRIS = new Particle.DustOptions(Color.fromRGB(72, 160, 130), 1.0f);
    /** ...and its lighter cyan-green (weathered copper). */
    private static final Particle.DustOptions PATINA = new Particle.DustOptions(Color.fromRGB(110, 205, 185), 1.0f);
    /** Decaying: patina gone dull and grey. */
    private static final Particle.DustOptions DULL = new Particle.DustOptions(Color.fromRGB(110, 135, 128), 0.9f);
    /** Her spectral form: a pale cyan glow. */
    private static final Particle.DustOptions SPIRIT = new Particle.DustOptions(Color.fromRGB(170, 245, 230), 1.2f);

    /** Keep in step with Apotheosis's aura radius. */
    static final double AURA_RADIUS = 7;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- RMB: Life Drain (a line, from her to them, every 0.2s while held) ----
        c.registerLine("lw_drain", (w, from, to) -> {
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            for (double t = 0; t <= len; t += 0.4) {
                Vector q = from.clone().add(d.clone().multiply(t / len));
                w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0.03, 0.03, 0.03, 0, t / len < 0.5 ? PATINA : VERDIGRIS);
            }
            w.spawnParticle(Particle.DAMAGE_INDICATOR, to.getX(), to.getY(), to.getZ(), 1, 0.2, 0.2, 0.2, 0);
            if (Bukkit.getCurrentTick() % 8 < 4) {
                w.playSound(new Location(w, from.getX(), from.getY(), from.getZ()), Sound.BLOCK_BEACON_AMBIENT, 0.4f, 1.8f);
            }
        });

        // ---- 1: Harmonic Strike ----
        c.register("lw_orb_throw", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f));
        c.register("lw_orb_trail", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.1, 0.1, 0.1, 0, PATINA);
            loc.getWorld().spawnParticle(Particle.SCRAPE, loc, 1, 0.1, 0.1, 0.1, 0);
        });
        c.register("lw_orb_steal", loc -> {
            loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 4, 0.3, 0.4, 0.3, 0.1);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 10, 0.3, 0.4, 0.3, 0, VERDIGRIS);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 1.2f);
        });
        c.register("lw_orb_mend", loc -> {
            loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 0.5, 0), 3, 0.3, 0.3, 0.3, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 10, 0.4, 0.6, 0.4, 0, PATINA);
            loc.getWorld().spawnParticle(Particle.SCRAPE, loc, 6, 0.4, 0.6, 0.4, 0.5);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
        });

        // ---- 2: Beyond Life and Death ----
        c.register("lw_weave", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 25, 0.4, 0.7, 0.4, 0, PATINA);
            loc.getWorld().spawnParticle(Particle.SCRAPE, loc, 20, 0.5, 0.8, 0.5, 1);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
        });
        c.registerLoop("lw_beyond", e -> motes(plugin, e, PATINA, 4));
        c.registerLoop("lw_decaying", e -> motes(plugin, e, DULL, 3));

        // ---- 3: Lifeline (lines, every 2 ticks while it holds; brighter as it charges) ----
        for (int stage = 1; stage <= 3; stage++) {
            float size = 0.6f + 0.25f * stage;
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(60 + 30 * stage, 150 + 30 * stage, 125 + 30 * stage), size);
            c.registerLine("lw_tether_" + stage, (w, from, to) -> {
                Vector d = to.clone().subtract(from);
                double len = d.length();
                if (len < 0.1) return;
                for (double t = 0; t <= len; t += 0.35) {
                    Vector q = from.clone().add(d.clone().multiply(t / len));
                    w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, dust);
                }
            });
        }
        c.register("lw_bond", loc -> {
            loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1, 0), 6, 0.4, 0.4, 0.4, 0);
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.4, 0.7, 0.4, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        });
        c.register("lw_tether_snap", loc -> {
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 8, 0.3, 0.4, 0.3, 0.02);
            loc.getWorld().playSound(loc, Sound.BLOCK_VINE_BREAK, 1f, 1.2f);
        });

        // ---- passive: Lingering Soul ----
        c.registerLoop("lw_remnant", e -> {
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation().add(0, e.getHeight() / 2, 0);
                World w = e.getWorld();
                w.spawnParticle(Particle.DUST, at, 8, 0.3, e.getHeight() / 2.5, 0.3, 0, SPIRIT);
                w.spawnParticle(Particle.SOUL, at, 1, 0.2, 0.4, 0.2, 0.01);
                if (tick[0] % 20 == 0) w.playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 1f, 1.2f);
                tick[0] += 2;
            }, 0, 2);
            return (CueHandle) task::cancel;
        });

        // ---- ultimate: Apotheosis ----
        c.register("lw_revive", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 50, 0.5, 1, 0.5, 0, SPIRIT);
            loc.getWorld().spawnParticle(Particle.SCRAPE, loc, 40, 0.5, 1, 0.5, 1.5);
            loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        });
        c.register("lw_ascend", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 60, 1.5, 1.5, 1.5, 0, VERDIGRIS);
            loc.getWorld().spawnParticle(Particle.SCRAPE, loc, 40, 1.5, 1.5, 1.5, 1);
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 30, 0.5, 1, 0.5, 0.1);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        });
        c.registerLoop("lw_lattice", e -> { // she's a lattice of light: a rotating green-and-pink frame around her
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation();
                World w = e.getWorld();
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6 + tick[0] * 0.08;
                    for (double y = 0; y <= 2; y += 0.4) {
                        w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * 0.7, at.getY() + y, at.getZ() + Math.sin(a) * 0.7,
                                1, 0, 0, 0, 0, i % 2 == 0 ? PATINA : VERDIGRIS);
                    }
                }
                tick[0] += 2;
            }, 0, 2);
            return (CueHandle) task::cancel;
        });
        c.register("lw_aura", loc -> { // every 0.5s: the aura's edge, petals inside
            World w = loc.getWorld();
            double ground = loc.getY() - 0.8;
            for (int i = 0; i < 40; i++) {
                double a = Math.PI * 2 * i / 40;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * AURA_RADIUS, ground + 0.2,
                        loc.getZ() + Math.sin(a) * AURA_RADIUS, 1, 0, 0, 0, 0, i % 2 == 0 ? PATINA : VERDIGRIS);
            }
            w.spawnParticle(Particle.SCRAPE, loc.getX(), ground + 1.5, loc.getZ(), 12, AURA_RADIUS * 0.5, 1,
                    AURA_RADIUS * 0.5, 0.5);
        });
    }

    /** A few motes drifting up around them while it lasts. */
    private static CueHandle motes(Plugin plugin, Entity e, Particle.DustOptions dust, int every) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid()) return;
            Location at = e.getLocation().add(0, e.getHeight() / 2, 0);
            e.getWorld().spawnParticle(Particle.DUST, at, 3, 0.35, e.getHeight() / 3, 0.35, 0, dust);
        }, 0, every);
        return (CueHandle) task::cancel;
    }

    private LifeweaverCues() {}
}

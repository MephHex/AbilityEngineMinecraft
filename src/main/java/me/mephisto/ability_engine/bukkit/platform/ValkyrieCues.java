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

/** The Valkyrie's cues: gold and white light, wings, bells. */
final class ValkyrieCues {

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 205, 60), 1.2f);
    private static final Particle.DustOptions PALE_GOLD = new Particle.DustOptions(Color.fromRGB(255, 240, 170), 0.9f);

    /** Light Arrow's burst: keep it in step with the ability's radius. */
    static final double BURST_RADIUS = 3;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary: Gilded Slash ----
        c.register("valkyrie_slash", loc -> {
            loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0.3, 0.1, 0.3, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.6, 0.2, 0.6, 0, GOLD);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
        });

        // ---- 1: Radiant Thrust (and its dive while gliding) ----
        c.register("valkyrie_thrust", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 8, 0.2, 0.2, 0.2, 0.05);
            loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 1f, 1.3f);
        });
        c.register("valkyrie_dive", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.3, 0.3, 0.08);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 15, 0.5, 0.5, 0.5, 0, GOLD);
            loc.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_SWOOP, 1f, 1.4f);
        });
        c.register("valkyrie_stab", loc -> {
            loc.getWorld().spawnParticle(Particle.CRIT, loc, 12, 0.3, 0.4, 0.3, 0.2);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.3f);
        });
        c.register("valkyrie_smite", loc -> { // a dive that lands: a flash of light, a bell
            World w = loc.getWorld();
            w.spawnParticle(Particle.END_ROD, loc, 40, 0.4, 0.6, 0.4, 0.15);
            w.spawnParticle(Particle.DUST, loc, 30, 0.6, 0.8, 0.6, 0, GOLD);
            w.spawnParticle(Particle.CRIT, loc, 20, 0.4, 0.6, 0.4, 0.3);
            w.playSound(loc, Sound.BLOCK_BELL_USE, 1f, 1.5f);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
        });

        // ---- 2: Guardian's Tether ----
        c.register("valkyrie_flit", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 12, 0.3, 0.5, 0.3, 0.03);
            loc.getWorld().playSound(loc, Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1f, 1.2f);
        });
        c.registerLine("valkyrie_tether_1", (w, from, to) -> { // a thread of golden light, drawn every 2 ticks
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            for (double t = 0; t <= len; t += 0.35) {
                Vector q = from.clone().add(d.clone().multiply(t / len));
                w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, PALE_GOLD);
            }
        });
        c.register("valkyrie_mend", loc -> { // each pulse of healing on the ally
            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 5, 0.4, 0.6, 0.4, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.4, 0.6, 0.4, 0, PALE_GOLD);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
        });
        c.register("valkyrie_valor", loc -> { // held all the way: Strength
            loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 25, 0.4, 0.7, 0.4, 0.2);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        });
        c.register("valkyrie_tether_snap", loc -> {
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 8, 0.3, 0.4, 0.3, 0.02);
            loc.getWorld().playSound(loc, Sound.ENTITY_ALLAY_HURT, 0.8f, 1.2f);
        });

        // ---- 3: Light Arrows ----
        c.register("valkyrie_bow", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 15, 0.4, 0.6, 0.4, 0.03);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
        });
        c.register("valkyrie_arrow_shot", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_SHOOT, 1f, 1.5f));
        c.register("valkyrie_light_burst", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.END_ROD, loc, 40, 0.5, 0.5, 0.5, 0.2);
            w.spawnParticle(Particle.WAX_ON, loc, 15, 1, 0.5, 1, 0.5);
            for (int i = 0; i < 28; i++) { // its reach, on the ground
                double a = Math.PI * 2 * i / 28;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * BURST_RADIUS, loc.getY(),
                        loc.getZ() + Math.sin(a) * BURST_RADIUS, 1, 0, 0.05, 0, 0, GOLD);
            }
            w.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);
            w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.6f);
        });

        // ---- ultimate: Divine Ward ----
        c.register("valkyrie_blessing_open", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 30, 0.6, 1, 0.6, 0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        });
        c.register("valkyrie_blessing", loc -> {
            loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 50, 0.5, 0.9, 0.5, 0.3);
            loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
            loc.getWorld().playSound(loc, Sound.BLOCK_BELL_RESONATE, 0.8f, 1.6f);
        });
        // While warded: a golden halo over their head
        c.registerLoop("valkyrie_blessed", e -> {
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation();
                double top = e.getBoundingBox().getMaxY() + 0.25;
                World w = e.getWorld();
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2 * i / 10 + tick[0] * 0.2;
                    w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * 0.35, top, at.getZ() + Math.sin(a) * 0.35,
                            1, 0, 0, 0, 0, GOLD);
                }
                if (tick[0] % 3 == 0) w.spawnParticle(Particle.END_ROD, at.getX(), at.getY() + 1, at.getZ(), 1, 0.3, 0.5, 0.3, 0.01);
                tick[0]++;
            }, 0, 3);
            return (CueHandle) task::cancel;
        });
    }

    private ValkyrieCues() {}
}

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

    /** Keep these in step with the abilities' reach: Valkyrie's Leap's slashes and dive, War Cry. */
    static final double CLEAVE_RADIUS = 4;
    static final double SMITE_RADIUS = 4;
    static final double CRY_RADIUS = 8;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary: Gilded Slash ----
        c.register("valkyrie_slash", loc -> {
            loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0.3, 0.1, 0.3, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.6, 0.2, 0.6, 0, GOLD);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
        });

        // ---- 1: Valkyrie's Leap (two leaping slashes, then the dive) ----
        c.register("valkyrie_flit", loc -> { // a leap or a dash: a rush of wings (low, around her legs)
            loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, -0.5, 0), 12, 0.3, 0.3, 0.3, 0.03);
            loc.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 1f, 1.4f);
        });
        // A golden half circle in front of her (4 blocks), low, with sword sweeps along it. A line cue (at: caster,
        // to: aim): from her body's centre toward where she aims, so it faces her way.
        c.registerLine("valkyrie_cleave", (w, from, to) -> {
            Vector f = to.clone().subtract(from).setY(0);
            if (f.lengthSquared() < 1e-6) f = new Vector(1, 0, 0);
            f.normalize();
            double base = Math.atan2(f.getZ(), f.getX());
            double y = from.getY() - 0.5;    // about knee height
            for (int i = 0; i <= 18; i++) {
                double a = base - Math.PI / 2 + Math.PI * i / 18;
                for (double r = 2.0; r <= CLEAVE_RADIUS; r += 1.0) {
                    w.spawnParticle(Particle.DUST, from.getX() + Math.cos(a) * r, y, from.getZ() + Math.sin(a) * r,
                            1, 0, 0, 0, 0, r >= CLEAVE_RADIUS ? GOLD : PALE_GOLD);
                }
            }
            for (int i = 0; i < 5; i++) { // the slash itself: sweeps along the arc
                double a = base - Math.PI / 2 + Math.PI * (i + 0.5) / 5;
                w.spawnParticle(Particle.SWEEP_ATTACK, from.getX() + Math.cos(a) * 2.2, y + 0.2,
                        from.getZ() + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
            }
            w.spawnParticle(Particle.CRIT, from.getX() + f.getX() * 2, y + 0.2, from.getZ() + f.getZ() * 2,
                    12, 1.2, 0.1, 1.2, 0.15);
            Location at = new Location(w, from.getX(), from.getY(), from.getZ());
            w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.1f);
            w.playSound(at, Sound.ITEM_TRIDENT_RETURN, 0.6f, 1.6f);
        });
        c.register("valkyrie_rise", loc -> { // soaring up for the dive
            World w = loc.getWorld();
            w.spawnParticle(Particle.CLOUD, loc.clone().add(0, -0.8, 0), 20, 0.5, 0.1, 0.5, 0.05);
            w.spawnParticle(Particle.END_ROD, loc.clone().add(0, -0.5, 0), 25, 0.3, 0.6, 0.3, 0.08);
            w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.5f);
        });
        c.register("valkyrie_dive", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.3, 0.3, 0.08);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 15, 0.5, 0.5, 0.5, 0, GOLD);
            loc.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_SWOOP, 1f, 1.4f);
        });
        c.register("valkyrie_smite", loc -> { // the dive lands: a flash of light, a bell (played at her body's centre)
            World w = loc.getWorld();
            Location low = loc.clone().add(0, -0.6, 0);
            double ground = loc.getY() - 0.8;
            w.spawnParticle(Particle.END_ROD, low, 40, 0.4, 0.3, 0.4, 0.15);
            w.spawnParticle(Particle.DUST, low, 30, 0.6, 0.3, 0.6, 0, GOLD);
            w.spawnParticle(Particle.CRIT, low, 20, 0.4, 0.3, 0.4, 0.3);
            for (int i = 0; i < 28; i++) { // its reach, on the ground
                double a = Math.PI * 2 * i / 28;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * SMITE_RADIUS, ground + 0.1,
                        loc.getZ() + Math.sin(a) * SMITE_RADIUS, 1, 0, 0, 0, 0, GOLD);
            }
            for (int i = 0; i < 8; i++) { // slashes all around
                double a = Math.PI * 2 * i / 8;
                w.spawnParticle(Particle.SWEEP_ATTACK, loc.getX() + Math.cos(a) * 2, ground + 0.5,
                        loc.getZ() + Math.sin(a) * 2, 1, 0, 0, 0, 0);
            }
            w.playSound(loc, Sound.BLOCK_BELL_USE, 1f, 1.5f);
            w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
        });

        // ---- 2: Valkyrie's Charge ----
        c.register("valkyrie_stab", loc -> { // she reaches them
            loc.getWorld().spawnParticle(Particle.CRIT, loc, 12, 0.3, 0.4, 0.3, 0.2);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.3f);
        });
        c.register("valkyrie_crash", loc -> { // carried into terrain
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc, 30, 0.4, 0.5, 0.4, 0.1, org.bukkit.Material.STONE.createBlockData());
            w.spawnParticle(Particle.CRIT, loc, 20, 0.4, 0.5, 0.4, 0.3);
            w.playSound(loc, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1f, 0.8f);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
        });

        // ---- 3: War Cry ----
        c.register("valkyrie_cry", loc -> { // a ring of gold going out to its reach (8 blocks)
            World w = loc.getWorld();
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * CRY_RADIUS, loc.getY() + 0.2,
                        loc.getZ() + Math.sin(a) * CRY_RADIUS, 1, 0, 0, 0, 0, GOLD);
            }
            w.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.1);
            w.playSound(loc, Sound.EVENT_RAID_HORN, 1f, 1.4f);
            w.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
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

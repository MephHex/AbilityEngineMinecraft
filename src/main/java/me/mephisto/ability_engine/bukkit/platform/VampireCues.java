package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** The Vampire's cues: blood - dark crimson and bright scarlet - wet, heavy sounds, bats and a charm's pink hearts. */
final class VampireCues {

    /** Dark, old blood... */
    private static final Particle.DustOptions CRIMSON = new Particle.DustOptions(Color.fromRGB(120, 0, 12), 1.1f);
    /** ...and fresh, bright blood. */
    private static final Particle.DustOptions SCARLET = new Particle.DustOptions(Color.fromRGB(205, 20, 35), 0.9f);
    /** The charm: rose pink. */
    private static final Particle.DustOptions ROSE = new Particle.DustOptions(Color.fromRGB(255, 120, 170), 0.9f);
    private static final BlockData BLOOD = Material.REDSTONE_BLOCK.createBlockData();

    /** Sanguine Tether's reach (its radius in vampire.yml). */
    private static final double TETHER_RADIUS = 8;
    /** How long a drop of blood takes from them to her (keep in step with vampire_ab1's flight delay). */
    private static final int DROP_TICKS = 6;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary: Blood Missile (the ultimate's missiles too) ----
        c.register("vampire_missile_shot", loc -> {
            loc.getWorld().playSound(loc, Sound.ENTITY_SNOWBALL_THROW, 0.7f, 0.5f);
            loc.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.6f, 1.4f);
        });
        c.register("vampire_missile_trail", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.06, 0.06, 0.06, 0, SCARLET);
            loc.getWorld().spawnParticle(Particle.FALLING_DUST, loc, 1, 0.05, 0.05, 0.05, 0, BLOOD);
        });
        c.register("vampire_missile_hit", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 14, 0.3, 0.4, 0.3, 0, CRIMSON);
            w.spawnParticle(Particle.DAMAGE_INDICATOR, loc, 2, 0.2, 0.2, 0.2, 0.1);
            w.playSound(loc, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 0.8f);
        });
        c.register("vampire_missile_fizzle", loc -> {
            loc.getWorld().spawnParticle(Particle.FALLING_DUST, loc, 8, 0.2, 0.1, 0.2, 0, BLOOD);
            loc.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_BREAK, 0.6f, 1.2f);
        });

        // ---- passive: Blood Feast ----
        c.registerLine("vampire_essence", (w, from, to) -> // their essence streams into her
                travel(plugin, w, from, to, 10, (at, k) -> {
                    w.spawnParticle(Particle.DUST, at.getX(), at.getY(), at.getZ(), 4, 0.15, 0.15, 0.15, 0, k % 2 == 0 ? SCARLET : CRIMSON);
                    w.spawnParticle(Particle.SOUL, at.getX(), at.getY(), at.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
                }, at -> {
                    w.spawnParticle(Particle.DUST, at.getX(), at.getY(), at.getZ(), 30, 0.4, 0.7, 0.4, 0, SCARLET);
                    w.playSound(new Location(w, at.getX(), at.getY(), at.getZ()), Sound.ENTITY_GENERIC_DRINK, 1f, 0.6f);
                    w.playSound(new Location(w, at.getX(), at.getY(), at.getZ()), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.2f);
                }));
        c.registerLoop("vampire_feast", e -> {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation().add(0, 0.2, 0);
                at.getWorld().spawnParticle(Particle.DUST, at, 3, 0.3, 0.1, 0.3, 0, SCARLET);
                at.getWorld().spawnParticle(Particle.CRIMSON_SPORE, at, 2, 0.3, 0.3, 0.3, 0);
            }, 0, 3);
            return (CueHandle) task::cancel;
        });

        // ---- 1: Sanguine Tether ----
        c.register("vampire_tether_cast", loc -> { // a ring of blood at her reach
            World w = loc.getWorld();
            double y = loc.getY() - 0.8;
            for (int i = 0; i < 64; i++) {
                double a = Math.PI * 2 * i / 64;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * TETHER_RADIUS, y, loc.getZ() + Math.sin(a) * TETHER_RADIUS,
                        1, 0, 0, 0, 0, CRIMSON);
            }
            w.playSound(loc, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.8f, 0.7f);
            w.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);
        });
        c.register("vampire_tether_none", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.6f));
        for (int stage = 1; stage <= 3; stage++) { // a vein that swells and brightens as it holds
            float size = 0.6f + 0.25f * stage;
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(110 + 40 * stage, 5 * stage, 10 + 5 * stage), size);
            c.registerLine("vampire_tether_" + stage, (w, from, to) -> {
                Vector d = to.clone().subtract(from);
                double len = d.length();
                if (len < 0.1) return;
                for (double t = 0; t <= len; t += 0.35) {
                    Vector q = from.clone().add(d.clone().multiply(t / len));
                    w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, dust);
                }
            });
        }
        c.registerLine("vampire_siphon", (w, from, to) -> // a drop of their blood, flying to her
                travel(plugin, w, from, to, DROP_TICKS, (at, k) -> {
                    w.spawnParticle(Particle.DUST, at.getX(), at.getY(), at.getZ(), 3, 0.05, 0.05, 0.05, 0, SCARLET);
                    w.spawnParticle(Particle.FALLING_DUST, at.getX(), at.getY(), at.getZ(), 1, 0.05, 0.05, 0.05, 0, BLOOD);
                }, at -> { }));
        c.register("vampire_siphon_heal", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 8, 0.3, 0.5, 0.3, 0, SCARLET);
            loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 0.8, 0), 1, 0.2, 0.1, 0.2, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_DRINK, 0.5f, 1.3f);
        });
        c.register("vampire_bleed", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 25, 0.4, 0.6, 0.4, 0, CRIMSON);
            loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 4, 0.3, 0.3, 0.3, 0.1);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.6f);
        });
        c.registerLoop("vampire_bleeding", e -> {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation().add(0, e.getHeight() * 0.6, 0);
                at.getWorld().spawnParticle(Particle.FALLING_DUST, at, 3, 0.25, 0.4, 0.25, 0, BLOOD);
            }, 0, 4);
            return (CueHandle) task::cancel;
        });
        c.register("vampire_tether_snap", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 8, 0.3, 0.3, 0.3, 0, CRIMSON);
            loc.getWorld().playSound(loc, Sound.BLOCK_VINE_BREAK, 1f, 0.8f);
        });

        // ---- 2: Bat Form ----
        c.register("vampire_bat_burst", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.LARGE_SMOKE, loc, 15, 0.3, 0.6, 0.3, 0.02);
            w.spawnParticle(Particle.DUST, loc, 15, 0.4, 0.7, 0.4, 0, CRIMSON);
            w.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 1f, 0.8f);
        });
        c.registerLoop("vampire_bat", e -> { // she's a bat: a big one in her place (she doesn't see it herself)
            Location start = e.getLocation().add(0, 0.4, 0);
            Bat bat = start.getWorld().spawn(start, Bat.class, b -> {
                VisualEntities.mark(b);
                b.setAI(false);
                b.setAwake(true);
                b.setSilent(true);
                b.setInvulnerable(true);
                b.setPersistent(false);
                b.setGravity(false);
                b.setCollidable(false);
                var scale = b.getAttribute(Attribute.SCALE);
                if (scale != null) scale.setBaseValue(1.8);
            });
            if (e instanceof Player owner) owner.hideEntity(plugin, bat);
            int[] tick = {0};
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid() || !bat.isValid()) return;
                bat.teleport(e.getLocation().add(0, 0.4, 0));
                if (tick[0]++ % 8 == 0) e.getWorld().playSound(e.getLocation(), Sound.ENTITY_BAT_AMBIENT, 0.6f, 0.9f);
            }, 1, 1);
            return (CueHandle) () -> {
                task.cancel();
                if (bat.isValid()) {
                    bat.getWorld().spawnParticle(Particle.LARGE_SMOKE, bat.getLocation().add(0, 0.5, 0), 10, 0.3, 0.4, 0.3, 0.02);
                    bat.remove();
                }
            };
        });

        // ---- 3: Charm ----
        c.register("vampire_charm_shot", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_ALLAY_ITEM_THROWN, 1f, 0.7f));
        c.register("vampire_charm_trail", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.08, 0.08, 0.08, 0, ROSE);
            if (Bukkit.getCurrentTick() % 3 == 0) loc.getWorld().spawnParticle(Particle.HEART, loc, 1, 0.05, 0.05, 0.05, 0);
        });
        c.register("vampire_charm_hit", loc -> {
            loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 0.6, 0), 6, 0.4, 0.4, 0.4, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 15, 0.4, 0.6, 0.4, 0, ROSE);
            loc.getWorld().playSound(loc, Sound.ENTITY_ALLAY_ITEM_GIVEN, 1f, 0.6f);
        });
        c.registerLoop("vampire_charmed", e -> { // hearts over their head while they walk to her
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, e.getHeight() + 0.4, 0), 1, 0.25, 0.1, 0.25, 0);
            }, 0, 6);
            return (CueHandle) task::cancel;
        });
        c.registerLine("vampire_charm_line", (w, from, to) -> { // a thin rose thread from her to them
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            for (double t = 0; t <= len; t += 0.5) {
                Vector q = from.clone().add(d.clone().multiply(t / len));
                w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, ROSE);
            }
        });

        // ---- ultimate: Crimson Rain ----
        c.register("vampire_rain_rise", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 40, 0.6, 1.0, 0.6, 0, CRIMSON);
            w.spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.4, 0.6, 0.4, 0.03);
            w.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 1f, 0.6f);
            w.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.8f);
        });
        c.register("vampire_rain_shot", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_SNOWBALL_THROW, 0.6f, 0.4f));
        c.register("vampire_rain_burst", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 20, 0.9, 0.5, 0.9, 0, SCARLET);
            w.spawnParticle(Particle.FALLING_DUST, loc.clone().add(0, 0.5, 0), 10, 0.8, 0.3, 0.8, 0, BLOOD);
            w.playSound(loc, Sound.BLOCK_HONEY_BLOCK_BREAK, 0.8f, 0.7f);
        });
    }

    /** What a {@link #travel} step draws: where it is now, and which step this is. */
    private interface Step {
        void at(Vector where, int step);
    }

    /** Something flying from {@code from} to {@code to} over {@code ticks}: each tick drawn where it is, then {@code arrive}. */
    private static void travel(Plugin plugin, World w, Vector from, Vector to, int ticks, Step step,
                               java.util.function.Consumer<Vector> arrive) {
        int[] k = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            k[0]++;
            Vector at = from.clone().add(to.clone().subtract(from).multiply(Math.min(1.0, (double) k[0] / ticks)));
            step.at(at, k[0]);
            if (k[0] >= ticks) {
                task[0].cancel();
                arrive.accept(at);
            }
        }, 0, 1);
    }

    private VampireCues() {}
}

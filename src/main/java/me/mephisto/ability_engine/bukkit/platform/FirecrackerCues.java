package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The Firecracker's cues: real fireworks. Every burst is a vanilla firework going off in random festive colours (a
 * visual: its blast hurts nobody, see VisualEntities), and the trails are colourful sparks.
 */
final class FirecrackerCues {

    private static final Color[] FESTIVE = {
            Color.fromRGB(235, 50, 50), Color.fromRGB(255, 200, 40), Color.fromRGB(60, 140, 255), Color.fromRGB(255, 255, 255),
            Color.fromRGB(80, 230, 90), Color.fromRGB(200, 80, 255), Color.fromRGB(255, 120, 200), Color.fromRGB(40, 230, 230)};

    /** The radii in firecracker.yml: keep in step. */
    private static final double ROCKET_RADIUS = 3;
    private static final double BOMB_RADIUS = 4;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary: Firecracker (Double Tap) ----
        c.register("firecracker_shot", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 0.6f, 1.6f));
        c.register("firecracker_spark", loc -> sparks(loc, 2, 0.6f));
        c.register("firecracker_hit", loc -> { // a bullet's hit: just a crackle of sparks (no firework: too busy at that rate)
            sparks(loc, 4, 0.7f);
            loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.35f, 2f);
        });
        c.register("firecracker_pop", loc -> firework(loc, FireworkEffect.Type.BALL, 1, false)); // a tiny firework

        // ---- the ultimate's basic attack: Grand Finale ----
        c.register("firecracker_finale_shot", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f));
        c.register("firecracker_trail", loc -> {
            sparks(loc, 4, 0.9f);
            loc.getWorld().spawnParticle(Particle.SMALL_FLAME, loc, 1, 0.03, 0.03, 0.03, 0.01);
        });
        c.register("firecracker_finale_burst", loc -> firework(loc, FireworkEffect.Type.BALL_LARGE, 3, true));
        c.register("firecracker_finale_start", loc -> { // a show over her head
            firework(loc.clone().add(0, 2.5, 0), FireworkEffect.Type.STAR, 3, true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> firework(loc.clone().add(1.5, 3.5, 0), FireworkEffect.Type.BALL_LARGE, 2, true), 4);
            Bukkit.getScheduler().runTaskLater(plugin, () -> firework(loc.clone().add(-1.5, 3, 1), FireworkEffect.Type.BURST, 2, true), 8);
        });
        c.registerLoop("firecracker_finale_aura", e -> { // colourful sparks around her while the finale lasts
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                sparks(e.getLocation().add(0, e.getHeight() * 0.6, 0), 3, 0.8f);
            }, 0, 3);
            return (CueHandle) task::cancel;
        });

        // ---- passive: a kill (line: from the victim to her) ----
        c.registerLine("firecracker_kill", (w, from, to) ->
                firework(new Location(w, from.getX(), from.getY() + 0.5, from.getZ()), FireworkEffect.Type.STAR, 2, true));

        // ---- 1: Rocket ----
        c.register("firecracker_launch", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.8f));
        c.register("firecracker_rocket_blast", loc -> {
            firework(loc, FireworkEffect.Type.BALL_LARGE, 3, true);
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 20, ROCKET_RADIUS / 3, 0.4, ROCKET_RADIUS / 3, 0.03);
        });
        c.registerLoop("firecracker_flashed", e -> { // a white flash in their eyes, only they see it (then just Blindness)
            int[] tick = {0};
            BukkitTask[] task = new BukkitTask[1];
            task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid() || !(e instanceof Player p) || tick[0]++ >= 4) {
                    task[0].cancel();
                    return;
                }
                Location eye = p.getEyeLocation();
                p.spawnParticle(Particle.FLASH, eye.clone().add(eye.getDirection().multiply(0.6)), 1, 0, 0, 0, 0, Color.WHITE);
                if (tick[0] == 1) p.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 2f);
            }, 0, 2);
            return (CueHandle) () -> task[0].cancel();
        });

        // ---- 2: Scatter Fire ----
        c.register("firecracker_scatter", loc -> {
            loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 1f, 1.1f);
            loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 1.4f);
        });

        // ---- 3: Firework Bomb ----
        c.registerLoop("firecracker_windup", e -> {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location hands = e.getLocation().add(0, e.getHeight() * 0.6, 0).add(e.getLocation().getDirection().multiply(0.5));
                sparks(hands, 2, 0.5f);
            }, 0, 2);
            return (CueHandle) task::cancel;
        });
        c.register("firecracker_throw", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_TNT_PRIMED, 0.8f, 1.6f));
        c.register("firecracker_fuse", loc -> { // a hissing, sparking fuse
            loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.5, 0), 2, 0.05, 0.05, 0.05, 0.01);
            sparks(loc.clone().add(0, 0.5, 0), 1, 0.5f);
            if (Bukkit.getCurrentTick() % 9 < 3) loc.getWorld().playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 0.5f, 1.8f);
        });
        c.register("firecracker_bomb_blast", loc -> {
            World w = loc.getWorld();
            firework(loc.clone().add(0, 0.8, 0), FireworkEffect.Type.BURST, 3, true);
            firework(loc.clone().add(0, 1.6, 0), FireworkEffect.Type.BALL_LARGE, 2, false);
            w.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
            ring(w, loc, BOMB_RADIUS);
            w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        });
        c.registerLoop("firecracker_dazed", e -> {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                e.getWorld().spawnParticle(Particle.SMOKE, e.getLocation().add(0, e.getHeight() + 0.2, 0), 2, 0.2, 0.05, 0.2, 0.01);
            }, 0, 5);
            return (CueHandle) task::cancel;
        });
    }

    /**
     * A real firework going off right here (next tick): {@code colours} random festive colours fading to another, a
     * twinkle at random, a trail with {@code trail}. It's a visual: its blast doesn't hurt anyone (VisualEntities).
     */
    private static void firework(Location at, FireworkEffect.Type type, int colours, boolean trail) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Firework fw = at.getWorld().spawn(at, Firework.class, f -> {
            VisualEntities.mark(f);
            FireworkMeta meta = f.getFireworkMeta();
            FireworkEffect.Builder effect = FireworkEffect.builder().with(type).flicker(r.nextBoolean()).trail(trail)
                    .withFade(colour());
            for (int i = 0; i < colours; i++) effect.withColor(colour());
            meta.addEffect(effect.build());
            meta.setPower(0);
            f.setFireworkMeta(meta);
            f.setVelocity(new Vector());
            f.setPersistent(false);
        });
        fw.detonate();
    }

    /** Colourful sparks: festive dust and a firework sparkle. */
    private static void sparks(Location at, int count, float size) {
        for (int i = 0; i < count; i++) {
            at.getWorld().spawnParticle(Particle.DUST, at, 1, 0.06, 0.06, 0.06, 0, dust(size));
        }
        at.getWorld().spawnParticle(Particle.FIREWORK, at, 1, 0.03, 0.03, 0.03, 0.01);
    }

    private static Color colour() { return FESTIVE[ThreadLocalRandom.current().nextInt(FESTIVE.length)]; }

    /** A random festive colour, as dust. */
    private static Particle.DustOptions dust(float size) {
        return new Particle.DustOptions(colour(), size);
    }

    /** A ring of colourful sparks at the blast's reach, on the ground. */
    private static void ring(World w, Location at, double radius) {
        int n = (int) (radius * 8);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * radius, at.getY() + 0.1, at.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0, dust(1f));
        }
    }

    private FirecrackerCues() {}
}

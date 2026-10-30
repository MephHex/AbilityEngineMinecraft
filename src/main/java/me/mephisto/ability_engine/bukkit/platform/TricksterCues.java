package me.mephisto.ability_engine.bukkit.platform;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.util.Vector;

/** The Fae Trickster's cues: pink and lilac fae dust, puffs of smoke, playful chimes. */
final class TricksterCues {

    private static final Particle.DustOptions PINK = new Particle.DustOptions(Color.fromRGB(255, 150, 230), 1.0f);
    private static final Particle.DustOptions LILAC = new Particle.DustOptions(Color.fromRGB(190, 140, 255), 1.0f);
    private static final Particle.DustOptions TEAL = new Particle.DustOptions(Color.fromRGB(80, 210, 200), 0.9f);

    /** Keep these in step with Play Rough's cone (trickster.yml): its full width in degrees, and its reach. */
    static final double PLAY_ROUGH_ANGLE = 90;
    static final double PLAY_ROUGH_RANGE = 3;
    /** How many ticks a slash takes to sweep out to its reach (drawn on the swinger, wherever they go meanwhile). */
    static final int PLAY_ROUGH_TICKS = 4;

    static void register(BukkitCuePlayer c, org.bukkit.plugin.Plugin plugin) {
        // ---- primary: Swipe ----
        c.register("trickster_swipe", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.DUST, loc, 5, 0.3, 0.3, 0.3, 0, PINK);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.5f);
        });
        // Passive (Sucker Punch): a "shh" and a little puff over the silenced one's head.
        c.register("trickster_hush", loc -> {
            World w = loc.getWorld();
            Location head = loc.clone().add(0, 1.3, 0);
            w.spawnParticle(Particle.CLOUD, head, 6, 0.15, 0.08, 0.15, 0.01);
            w.spawnParticle(Particle.DUST, head, 8, 0.25, 0.15, 0.25, 0, TEAL);
            w.playSound(loc, Sound.ENTITY_PUFFER_FISH_BLOW_OUT, 0.9f, 1.8f);
            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.6f);
        });

        // ---- 1: Play Rough ----
        c.registerLoop("trickster_play_rough", e -> slash(plugin, e)); // a slash, drawn on whoever swings it

        // ---- 2: Trick Room ----
        c.register("trickster_room_dash", loc -> { // off to where the room goes
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 12, 0.3, 0.5, 0.3, 0, LILAC);
            w.playSound(loc, Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.5f);
        });
        c.register("trickster_room", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 30, 1.5, 0.3, 1.5, 0, LILAC);
            w.spawnParticle(Particle.END_ROD, loc, 8, 1.0, 0.4, 1.0, 0.02);
            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
            w.playSound(loc, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.7f, 1.4f);
        });
        c.register("trickster_room_shift", loc -> { // 2 again: the room moves to where she dashed
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 20, 1.0, 0.3, 1.0, 0, PINK);
            w.spawnParticle(Particle.END_ROD, loc, 6, 0.8, 0.3, 0.8, 0.02);
            w.playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9f, 1.4f);
        });
        c.register("trickster_blink", loc -> { // where a teleport left from, and where it arrived
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 10, 0.25, 0.45, 0.25, 0, PINK);
            w.spawnParticle(Particle.POOF, loc, 4, 0.2, 0.3, 0.2, 0.01);
            w.playSound(loc, Sound.ENTITY_CHICKEN_EGG, 0.7f, 1.9f);
        });

        // ---- 3: Topsy-Turvy ----
        c.register("trickster_topsy_cast", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 8, 0.2, 0.2, 0.2, 0, PINK);
            w.playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.7f);
            w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 0.7f);
        });
        c.register("trickster_topsy_trail", loc -> { // the slow orb: a pink and teal twist around where it is
            World w = loc.getWorld();
            double a = (w.getGameTime() % 40) * Math.PI / 10;
            for (int i = 0; i < 2; i++) {
                double b = a + i * Math.PI;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(b) * 0.3, loc.getY() + Math.sin(b) * 0.3, loc.getZ(),
                        1, 0, 0, 0, 0, i == 0 ? PINK : TEAL);
            }
            w.spawnParticle(Particle.END_ROD, loc, 1, 0.05, 0.05, 0.05, 0);
        });
        c.register("trickster_topsy_hit", loc -> { // caught by it: a burst of fae dust, a giggle and a wobble
            World w = loc.getWorld();
            Location body = loc.clone().add(0, 0.9, 0);
            w.spawnParticle(Particle.DUST, body, 16, 0.35, 0.5, 0.35, 0, PINK);
            w.spawnParticle(Particle.DUST, body, 10, 0.35, 0.5, 0.35, 0, TEAL);
            w.spawnParticle(Particle.WITCH, body, 10, 0.3, 0.4, 0.3, 0.05);
            w.playSound(loc, Sound.ENTITY_WITCH_CELEBRATE, 0.7f, 1.9f);
            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.6f);
        });
        c.register("trickster_room_flip", FloorRenderer::lightUp); // cast inside Trick Room: the whole room lights up
        c.registerLoop("trickster_topsy_turvy", e -> topsyTurvy(plugin, e)); // on whoever's controls are inverted
        c.registerLoop("trickster_confused", e -> confused(plugin, e));      // Confuse Ray: on whoever's confused

        // ---- (Changeling: no kit uses it now; kept for the clone tech) ----
        c.registerLoop("trickster_betrayed", e -> betrayed(plugin, e)); // broke a Changeling: friendly fire on them
        c.register("trickster_clone_mode", loc -> { // the clone's mode changed (only its owner sees and hears it)
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc.clone().add(0, 1.2, 0), 8, 0.2, 0.2, 0.2, 0, LILAC);
            w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f);
        });
        c.register("trickster_clone_place", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.3, 0.6, 0.3, 0.01);
            w.spawnParticle(Particle.DUST, loc, 12, 0.3, 0.6, 0.3, 0, LILAC);
            w.playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.3f);
        });
        c.register("trickster_clone_poof", loc -> { // broken or gone: a puff of smoke and a giggle
            World w = loc.getWorld();
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 6, 0.25, 0.5, 0.25, 0.01);
            w.spawnParticle(Particle.POOF, loc, 20, 0.3, 0.6, 0.3, 0.03);
            w.spawnParticle(Particle.DUST, loc, 10, 0.3, 0.6, 0.3, 0, PINK);
            w.playSound(loc, Sound.ENTITY_WITCH_CELEBRATE, 0.8f, 1.9f);
        });

        // ---- ultimate: Switcheroo ----
        c.register("trickster_switch", loc -> { // both lift off: a puff of fae dust under each
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 14, 0.3, 0.4, 0.3, 0, LILAC);
            w.spawnParticle(Particle.POOF, loc, 6, 0.25, 0.2, 0.25, 0.02);
            w.playSound(loc, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.9f, 1.6f);
        });
        c.registerLoop("trickster_switch_trail", e -> switchTrail(plugin, e)); // sparkles behind each of them as they fly
        c.register("trickster_switch_land", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 12, 0.35, 0.2, 0.35, 0, PINK);
            w.spawnParticle(Particle.END_ROD, loc, 5, 0.3, 0.3, 0.3, 0.02);
            w.playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9f, 1.5f);
        });
        c.register("trickster_switch_fail", loc -> { // nobody to swap with (only the Fae sees it): the charge is kept
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 10, 0.2, 0.3, 0.2, 0.01);
            loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.8f);
        });

        // ---- (Stolen Fable: no kit uses it now; kept for the steal tech) ----
        c.registerLine("trickster_snatch", TricksterCues::snatch); // a ribbon from the one robbed to the Fae
        c.register("trickster_steal_fail", loc -> {
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 10, 0.2, 0.3, 0.2, 0.01);
            loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.8f);
        });
    }

    /**
     * Topsy-Turvy on them: two rings turning opposite ways, pink round their feet and teal over their head, and now and
     * then a mote falling from over their head, for everyone to see.
     */
    private static me.mephisto.ability_engine.engine.platform.CueHandle topsyTurvy(org.bukkit.plugin.Plugin plugin,
                                                                                 org.bukkit.entity.Entity entity) {
        int[] tick = {0};
        org.bukkit.scheduler.BukkitTask task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid()) return;
            Location feet = entity.getLocation().add(0, 0.15, 0);
            Location head = entity.getLocation().add(0, entity.getHeight() + 0.3, 0);
            double r = Math.max(0.45, entity.getWidth() * 0.8);
            World w = entity.getWorld();
            for (int i = 0; i < 3; i++) {
                double a = tick[0] * 0.4 + i * Math.PI * 2 / 3;
                w.spawnParticle(Particle.DUST, feet.getX() + Math.cos(a) * r, feet.getY(), feet.getZ() + Math.sin(a) * r,
                        1, 0, 0, 0, 0, PINK);
                w.spawnParticle(Particle.DUST, head.getX() + Math.cos(-a) * r * 0.8, head.getY(),
                        head.getZ() + Math.sin(-a) * r * 0.8, 1, 0, 0, 0, 0, TEAL);
            }
            if (tick[0] % 4 == 0) w.spawnParticle(Particle.FALLING_DUST, head, 1, r * 0.5, 0, r * 0.5, 0,
                    org.bukkit.Material.PINK_CONCRETE_POWDER.createBlockData());
            tick[0]++;
        }, 0, 2);
        return task::cancel;
    }

    /**
     * Confused (Confuse Ray): lilac and pink motes spinning fast in a small loop over their head, and now and then a swirl
     * of witchy sparks, for everyone to see.
     */
    private static me.mephisto.ability_engine.engine.platform.CueHandle confused(org.bukkit.plugin.Plugin plugin,
                                                                              org.bukkit.entity.Entity entity) {
        int[] tick = {0};
        org.bukkit.scheduler.BukkitTask task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid()) return;
            Location head = entity.getLocation().add(0, entity.getHeight() + 0.4, 0);
            World w = entity.getWorld();
            for (int i = 0; i < 2; i++) {
                double a = tick[0] * 0.7 + i * Math.PI;
                w.spawnParticle(Particle.DUST, head.getX() + Math.cos(a) * 0.35, head.getY() + Math.sin(a * 2) * 0.08,
                        head.getZ() + Math.sin(a) * 0.35, 1, 0, 0, 0, 0, i == 0 ? LILAC : PINK);
            }
            if (tick[0] % 5 == 0) w.spawnParticle(Particle.WITCH, head, 2, 0.2, 0.1, 0.2, 0.02);
            tick[0]++;
        }, 0, 2);
        return task::cancel;
    }

    /** Switcheroo: a short trail of lilac and pink sparkles behind whoever's flying. */
    private static me.mephisto.ability_engine.engine.platform.CueHandle switchTrail(org.bukkit.plugin.Plugin plugin,
                                                                                  org.bukkit.entity.Entity entity) {
        int[] tick = {0};
        org.bukkit.scheduler.BukkitTask task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid()) return;
            Location body = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
            World w = entity.getWorld();
            w.spawnParticle(Particle.DUST, body, 3, 0.2, 0.4, 0.2, 0, tick[0] % 2 == 0 ? PINK : LILAC);
            if (tick[0] % 3 == 0) w.spawnParticle(Particle.END_ROD, body, 1, 0.1, 0.2, 0.1, 0.01);
            tick[0]++;
        }, 0, 1);
        return task::cancel;
    }

    /** Friendly fire on them: a ring of pink and teal motes circling over their head, for everyone to see. */
    private static me.mephisto.ability_engine.engine.platform.CueHandle betrayed(org.bukkit.plugin.Plugin plugin,
                                                                               org.bukkit.entity.Entity entity) {
        int[] tick = {0};
        org.bukkit.scheduler.BukkitTask task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid()) return;
            Location head = entity.getLocation().add(0, entity.getHeight() + 0.35, 0);
            World w = entity.getWorld();
            for (int i = 0; i < 4; i++) {
                double a = tick[0] * 0.35 + i * Math.PI / 2;
                w.spawnParticle(Particle.DUST, head.getX() + Math.cos(a) * 0.45, head.getY(), head.getZ() + Math.sin(a) * 0.45,
                        1, 0, 0, 0, 0, i % 2 == 0 ? PINK : TEAL);
            }
            tick[0]++;
        }, 0, 2);
        return task::cancel;
    }

    /**
     * Play Rough's slash, on {@code entity} (the Fae or its clone): over a few ticks it sweeps out across the cone in front of
     * them, from where they are and the way they face THAT tick, so it moves with them (nothing hangs where they were to
     * be walked into). Only quick particles: sweeps that are gone in a moment and crits flying outward.
     */
    private static me.mephisto.ability_engine.engine.platform.CueHandle slash(org.bukkit.plugin.Plugin plugin,
                                                                            org.bukkit.entity.Entity entity) {
        int[] tick = {0};
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid() || tick[0] >= PLAY_ROUGH_TICKS) {
                task[0].cancel();
                return;
            }
            Location at = entity.getLocation();
            Vector look = at.getDirection().setY(0);
            if (look.lengthSquared() < 1e-6) look = new Vector(1, 0, 0);
            look.normalize();
            World w = entity.getWorld();
            double base = Math.atan2(look.getZ(), look.getX());
            double half = Math.toRadians(PLAY_ROUGH_ANGLE / 2);
            double r = PLAY_ROUGH_RANGE * (tick[0] + 1) / PLAY_ROUGH_TICKS; // further out each tick
            double y = at.getY() + entity.getHeight() * 0.55;
            int across = Math.max(2, (int) Math.round(r * 1.5));
            for (int i = 0; i <= across; i++) {
                double a = base - half + 2 * half * i / across;
                double cos = Math.cos(a), sin = Math.sin(a);
                double x = at.getX() + cos * r, z = at.getZ() + sin * r;
                if (i % 2 == 0) w.spawnParticle(Particle.SWEEP_ATTACK, x, y, z, 1, 0, 0, 0, 0);
                w.spawnParticle(Particle.CRIT, x, y, z, 0, cos, 0.1, sin, 0.4); // (count 0: flying outward)
            }
            if (tick[0] == 0) {
                w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
                w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.7f);
            }
            tick[0]++;
        }, 0, 1);
        return () -> task[0].cancel();
    }

    /** A pink ribbon from {@code from} (the one robbed) to {@code to} (the Fae), twisting as it goes. */
    private static void snatch(World w, Vector from, Vector to) {
        Vector line = to.clone().subtract(from);
        double len = line.length();
        if (len < 1e-6) return;
        Vector dir = line.clone().multiply(1 / len);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        if (side.lengthSquared() < 1e-6) side = new Vector(1, 0, 0);
        side.normalize();
        Vector up = side.getCrossProduct(dir).normalize();
        for (double d = 0; d <= len; d += 0.25) {
            double a = d * 2.2;
            Vector p = from.clone().add(dir.clone().multiply(d))
                    .add(side.clone().multiply(Math.cos(a) * 0.3)).add(up.clone().multiply(Math.sin(a) * 0.3));
            w.spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0, d % 1 < 0.5 ? PINK : LILAC);
        }
        w.playSound(from.toLocation(w), Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
        w.playSound(to.toLocation(w), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.5f);
    }

    private TricksterCues() {}
}

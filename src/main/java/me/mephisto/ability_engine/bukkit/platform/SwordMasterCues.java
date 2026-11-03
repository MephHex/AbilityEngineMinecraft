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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The SwordMaster's cues: his two floating blades (netherite on his left, gold on his right, pointing where he looks),
 * their stabs and returns,
 * the twin thrust, the crossed-blades guard, a blade dropped from the sky, and the thousand cuts.
 */
final class SwordMasterCues {

    static final Material NETH = Material.NETHERITE_SWORD;
    static final Material GOLD = Material.GOLDEN_SWORD;

    /** The floating blades: how far out to his sides, back and up from his eyes, and how big. */
    private static final double SIDE = 0.75, BACK = 0.2, UP = 0.05;
    private static final float BLADE_SIZE = 0.85f;
    /** A dropped blade stays planted this long (keep in step with swordmaster.yml's sm_*_planted statuses). */
    static final int PLANTED_TICKS = 40;
    /** Ticks a dropped blade takes to fall (keep the *_fall delays in swordmaster.yml in step). */
    static final int FALL_TICKS = 10;

    private static final Particle.DustOptions DARK = new Particle.DustOptions(Color.fromRGB(70, 60, 70), 1.1f);
    private static final Particle.DustOptions GILT = new Particle.DustOptions(Color.fromRGB(250, 200, 60), 1.1f);
    private static final Particle.DustOptions STEEL = new Particle.DustOptions(Color.fromRGB(210, 210, 225), 0.9f);

    /** Each wearer's floating blades. */
    private static final Map<UUID, BukkitTask> blades = new HashMap<>();
    /** How many reasons each blade is away (stabbing, planted, guarding): [netherite, gold]; shown again at 0. */
    private static final Map<UUID, int[]> away = new HashMap<>();

    private SwordMasterCues() {}

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- the passive: two blades floating by his head ----
        c.registerLoop("sm_blades", e -> blades(plugin, e));
        c.registerLoop("sm_neth_away", e -> away(e, 0));
        c.registerLoop("sm_gold_away", e -> away(e, 1));

        // ---- primary: a blade stabs, then flies back to his side ----
        c.register("sm_neth_shot", loc -> loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 0.8f, 1.4f));
        c.register("sm_gold_shot", loc -> loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 0.8f, 1.8f));
        c.register("sm_neth_hit", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 10, 0.25, 0.3, 0.25, 0, DARK);
            w.spawnParticle(Particle.CRIT, loc, 8, 0.2, 0.3, 0.2, 0.2);
            w.playSound(loc, Sound.ITEM_TRIDENT_HIT, 0.9f, 0.8f);
        });
        c.register("sm_gold_hit", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 10, 0.25, 0.3, 0.25, 0, GILT);
            w.spawnParticle(Particle.ENCHANTED_HIT, loc, 8, 0.2, 0.3, 0.2, 0.2);
            w.playSound(loc, Sound.ITEM_TRIDENT_HIT, 0.9f, 1.3f);
        });
        c.register("sm_heal", loc -> loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1.2, 0), 2, 0.3, 0.2, 0.3, 0));
        c.registerLine("sm_neth_return", (w, from, to) -> back(plugin, w, NETH, from, to));
        c.registerLine("sm_gold_return", (w, from, to) -> back(plugin, w, GOLD, from, to));

        // ---- 1: Twin Thrust ----
        c.registerLine("sm_twin_thrust", (w, from, to) -> twinThrust(plugin, w, from, to));
        c.register("sm_thrust_hit", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.DUST, loc, 8, 0.25, 0.3, 0.25, 0, DARK);
            w.spawnParticle(Particle.DUST, loc, 8, 0.25, 0.3, 0.25, 0, GILT);
            w.spawnParticle(Particle.CRIT, loc, 10, 0.25, 0.3, 0.25, 0.3);
            w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.1f);
        });

        // ---- 2: Blade Guard ----
        c.registerLoop("sm_guard", e -> guard(plugin, e));
        c.register("sm_dash", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.SWEEP_ATTACK, loc, 3, 0.4, 0.3, 0.4, 0);
            w.spawnParticle(Particle.DUST, loc, 12, 0.4, 0.5, 0.4, 0, STEEL);
            w.playSound(loc, Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.3f);
            w.playSound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f, 1.4f);
        });

        // ---- 3: Falling Blade ----
        c.register("sm_neth_drop", loc -> drop(plugin, loc, NETH));
        c.register("sm_gold_drop", loc -> drop(plugin, loc, GOLD));
        c.register("sm_neth_impact", loc -> impact(loc, DARK, Particle.CRIT));
        c.register("sm_gold_impact", loc -> impact(loc, GILT, Particle.ENCHANTED_HIT));

        // ---- ULTIMATE: Death by a Thousand Cuts ----
        c.register("sm_ult_start", loc -> {
            World w = loc.getWorld();
            ring(w, loc, 5, 48, STEEL);
            w.playSound(loc, Sound.ITEM_TRIDENT_RETURN, 1f, 0.7f);
            w.playSound(loc, Sound.BLOCK_BELL_RESONATE, 0.6f, 1.6f);
        });
        c.register("sm_zone", loc -> {
            ring(loc.getWorld(), loc, 5, 32, STEEL);
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.2, 0), 6, 3, 0.1, 3, 0, GILT);
        });
        c.registerLoop("sm_cut", e -> cut(plugin, e));
    }

    // ---- the floating blades -----------------------------------------------------------------------------------

    /** Facing yaw (degrees) -> the way they face, flat. */
    private static Vector facing(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    /** Their right, flat (facing south: west). */
    private static Vector right(Vector facing) { return new Vector(-facing.getZ(), 0, facing.getX()); }

    private static CueHandle blades(Plugin plugin, Entity e) {
        UUID id = e.getUniqueId();
        BukkitTask old = blades.remove(id);
        if (old != null) old.cancel();
        ItemDisplay neth = blade(e.getLocation(), NETH, BLADE_SIZE), gold = blade(e.getLocation(), GOLD, BLADE_SIZE);
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid()) return;
            int[] gone = away.getOrDefault(id, new int[2]);
            Location eye = e instanceof org.bukkit.entity.LivingEntity l ? l.getEyeLocation() : e.getLocation().add(0, 1.6, 0);
            Vector f = facing(eye.getYaw()), r = right(f);
            double bob = Math.sin(tick[0] * 0.12) * 0.06;
            place(neth, eye, f, r.clone().multiply(-SIDE), UP + bob, gone[0] > 0, NETH, eye.getDirection());
            place(gold, eye, f, r.clone().multiply(SIDE), UP - bob, gone[1] > 0, GOLD, eye.getDirection());
            tick[0]++;
        }, 0, 1);
        blades.put(id, task);
        return () -> {
            task.cancel();
            blades.remove(id, task);
            if (neth.isValid()) neth.remove();
            if (gold.isValid()) gold.remove();
        };
    }

    private static void place(ItemDisplay d, Location eye, Vector f, Vector side, double up, boolean hidden, Material m,
                              Vector look) {
        if (!d.isValid()) return;
        Location at = eye.clone().add(side).subtract(f.clone().multiply(BACK)).add(0, up, 0);
        at.setYaw(0);
        at.setPitch(0);
        d.teleport(at);
        boolean shown = d.getItemStack() != null && !d.getItemStack().getType().isAir();
        if (hidden == shown) d.setItemStack(hidden ? null : new ItemStack(m));
        // its tip where he looks (up, down and around), ready to stab
        d.setTransformation(new Transformation(new Vector3f(), tipAlong(look), new Vector3f(BLADE_SIZE), new Quaternionf()));
    }

    private static CueHandle away(Entity e, int which) {
        int[] gone = away.computeIfAbsent(e.getUniqueId(), k -> new int[2]);
        gone[which]++;
        boolean[] done = {false};
        return () -> {
            if (done[0]) return;
            done[0] = true;
            gone[which] = Math.max(0, gone[which] - 1);
            if (gone[0] == 0 && gone[1] == 0) away.remove(e.getUniqueId(), gone);
        };
    }

    // ---- flying blades ------------------------------------------------------------------------------------------

    private static ItemDisplay blade(Location at, Material m, float size) {
        Location spot = at.clone();
        spot.setYaw(0);
        spot.setPitch(0);
        return spot.getWorld().spawn(spot, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(m));
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            VisualEntities.mark(d);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size), new Quaternionf()));
        });
    }

    /** A sword's sprite points up-right: turned so its tip points along {@code dir} (blade upright). */
    private static Quaternionf tipAlong(Vector dir) {
        Vector d = dir.clone();
        if (d.lengthSquared() < 1e-9) d = new Vector(0, 1, 0);
        d.normalize();
        double flat = Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ());
        float heading = (float) Math.atan2(-d.getZ(), d.getX());
        float pitch = (float) Math.atan2(d.getY(), flat);
        return new Quaternionf().rotateY(heading).rotateZ(pitch - (float) Math.toRadians(45));
    }

    private static void aim(ItemDisplay d, Vector dir, float size) {
        d.setTransformation(new Transformation(new Vector3f(), tipAlong(dir), new Vector3f(size), new Quaternionf()));
    }

    /**
     * A blade moving from {@code from} to {@code to} in {@code ticks}, its tip along {@code tip}, then gone (or kept
     * where it ended: the caller removes it).
     */
    private static ItemDisplay flight(Plugin plugin, World w, Material m, Vector from, Vector to, Vector tip, int ticks,
                                      float size, Runnable done) {
        return flight(plugin, w, m, from, to, tip, ticks, size, false, done);
    }

    /** {@code falling}: speeding up as it goes (k squared), like something dropped. */
    private static ItemDisplay flight(Plugin plugin, World w, Material m, Vector from, Vector to, Vector tip, int ticks,
                                      float size, boolean falling, Runnable done) {
        ItemDisplay d = blade(from.toLocation(w), m, size);
        aim(d, tip, size);
        int[] t = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            t[0]++;
            if (!d.isValid()) {
                task[0].cancel();
                return;
            }
            double k = Math.min(1, t[0] / (double) ticks);
            if (falling) k = k * k;
            Location at = from.clone().add(to.clone().subtract(from).multiply(k)).toLocation(w);
            d.teleport(at);
            if (k >= 1) {
                task[0].cancel();
                if (done != null) done.run();
                else d.remove();
            }
        }, 1, 1);
        return d;
    }

    /** Back to his side after a stab: from where it struck to him (just above his middle), hilt first. */
    private static void back(Plugin plugin, World w, Material m, Vector from, Vector to) {
        Vector home = to.clone().add(new Vector(0, 0.75, 0));
        double blocks = home.distance(from);
        int ticks = (int) Math.max(2, Math.min(5, Math.ceil(blocks / 3.5)));
        flight(plugin, w, m, from, home, from.clone().subtract(home), ticks, BLADE_SIZE, null);
    }

    /** 1: both blades shoot out from his sides 4.5 blocks along his aim and back. */
    private static void twinThrust(Plugin plugin, World w, Vector from, Vector to) {
        Vector dir = to.clone().subtract(from);
        if (dir.lengthSquared() < 1e-6) return;
        dir.normalize();
        Vector flat = new Vector(dir.getX(), 0, dir.getZ());
        Vector r = flat.lengthSquared() < 1e-6 ? new Vector(1, 0, 0) : right(flat.normalize());
        for (int i = 0; i < 2; i++) {
            Material m = i == 0 ? NETH : GOLD;
            Vector start = from.clone().add(new Vector(0, 0.75, 0)).add(r.clone().multiply(i == 0 ? -0.6 : 0.6));
            Vector end = start.clone().add(dir.clone().multiply(4.5));
            ItemDisplay[] out = new ItemDisplay[1];
            out[0] = flight(plugin, w, m, start, end, dir, 3, 1.1f, () -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (out[0].isValid()) out[0].remove();
                flight(plugin, w, m, end, start, dir, 5, 1.1f, null);
            }, 2));
        }
        Location at = from.toLocation(w);
        w.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.5f);
        w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
    }

    /** 2: the blades crossed in front of him (an X, big), following him while he channels. */
    private static CueHandle guard(Plugin plugin, Entity e) {
        e.getWorld().playSound(e.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.8f);
        e.getWorld().playSound(e.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.4f, 1.8f);
        float size = 1.5f;
        ItemDisplay neth = blade(e.getLocation(), NETH, size), gold = blade(e.getLocation(), GOLD, size);
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid()) return;
            Location eye = e instanceof org.bukkit.entity.LivingEntity l ? l.getEyeLocation() : e.getLocation().add(0, 1.6, 0);
            float yaw = eye.getYaw();
            Location at = eye.clone().add(facing(yaw).multiply(0.9)).add(0, -0.45, 0);
            at.setYaw(0);
            at.setPitch(0);
            Quaternionf turn = new Quaternionf().rotateY((float) Math.toRadians(-yaw));
            for (ItemDisplay d : new ItemDisplay[]{neth, gold}) {
                if (!d.isValid()) continue;
                d.teleport(at);
                Quaternionf q = new Quaternionf(turn).rotateZ(d == neth ? (float) (Math.PI / 2) : 0f); // tips up-left / up-right
                d.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(size), new Quaternionf()));
            }
            if (tick[0] % 4 == 0) at.getWorld().spawnParticle(Particle.DUST, at, 4, 0.5, 0.5, 0.5, 0, STEEL);
            tick[0]++;
        }, 0, 1);
        return () -> {
            task.cancel();
            if (neth.isValid()) neth.remove();
            if (gold.isValid()) gold.remove();
        };
    }

    /** 3: a big blade falls from the sky tip first, plants itself in the ground, stays, then fades. */
    private static void drop(Plugin plugin, Location ground, Material m) {
        World w = ground.getWorld();
        float size = 2.4f;
        Vector planted = ground.toVector().add(new Vector(0, 0.75, 0));
        Vector top = planted.clone().add(new Vector(0, 10, 0));
        w.playSound(ground, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8f, 1.6f);
        ItemDisplay[] d = new ItemDisplay[1];
        // pointing straight down the whole way, falling faster and faster
        d[0] = flight(plugin, w, m, top, planted, new Vector(0, -1, 0), FALL_TICKS, size, true, () -> {
            int[] t = {0};
            BukkitTask[] stay = new BukkitTask[1];
            stay[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                t[0]++;
                if (!d[0].isValid()) {
                    stay[0].cancel();
                    return;
                }
                if (t[0] % 10 == 0) w.spawnParticle(Particle.DUST, planted.toLocation(w), 3, 0.3, 0.6, 0.3, 0,
                        m == GOLD ? GILT : DARK);
                if (t[0] >= PLANTED_TICKS - FALL_TICKS - 4) { // the last moments: it pulls itself out
                    d[0].teleport(d[0].getLocation().add(0, 0.25, 0));
                }
                if (t[0] >= PLANTED_TICKS - FALL_TICKS) { // (its fall counts: planted from when it was dropped)
                    stay[0].cancel();
                    w.spawnParticle(Particle.DUST, d[0].getLocation(), 12, 0.3, 0.6, 0.3, 0, m == GOLD ? GILT : DARK);
                    d[0].remove();
                }
            }, 1, 1);
        });
    }

    private static void impact(Location loc, Particle.DustOptions color, Particle sparks) {
        World w = loc.getWorld();
        ring(w, loc, 3, 36, color);
        w.spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.2, 0), 40, 1.2, 0.2, 1.2, 0,
                loc.clone().add(0, -0.6, 0).getBlock().getBlockData().getMaterial().isAir()
                        ? Material.STONE.createBlockData() : loc.clone().add(0, -0.6, 0).getBlock().getBlockData());
        w.spawnParticle(sparks, loc.clone().add(0, 0.5, 0), 25, 1.2, 0.4, 1.2, 0.2);
        w.playSound(loc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.9f);
        w.playSound(loc, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 0.7f);
    }

    private static void ring(World w, Location center, double radius, int points, Particle.DustOptions color) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius), 1, 0, 0, 0, 0, color);
        }
    }

    /**
     * ULTIMATE: a pair of blades (netherite, gold) on either side of someone, a little above, pointing at them: they
     * draw back, stab into them and pull out again, following them as they move (10 ticks: one cut).
     */
    private static CueHandle cut(Plugin plugin, Entity victim) {
        World w = victim.getWorld();
        double a = Math.random() * Math.PI * 2;
        ItemDisplay neth = blade(victim.getLocation(), NETH, 1.0f), gold = blade(victim.getLocation(), GOLD, 1.0f);
        int[] t = {0};
        BukkitTask[] task = new BukkitTask[1];
        Runnable end = () -> {
            task[0].cancel();
            if (neth.isValid()) neth.remove();
            if (gold.isValid()) gold.remove();
        };
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!victim.isValid() || t[0] >= 10) {
                end.run();
                return;
            }
            // how far out: drawn back (0-3), the stab (4-5), pulled out (6-9)
            int k = t[0];
            double out = k < 4 ? 1.6 + k * 0.1 : k < 6 ? 1.9 - (k - 3) * 0.8 : 0.3 + (k - 5) * 0.3;
            Vector center = victim.getBoundingBox().getCenter();
            for (int i = 0; i < 2; i++) {
                ItemDisplay d = i == 0 ? neth : gold;
                if (!d.isValid()) continue;
                double side = a + i * Math.PI;
                Vector from = new Vector(Math.cos(side), 0.55, Math.sin(side)).normalize().multiply(out);
                d.teleport(center.clone().add(from).toLocation(w));
                aim(d, from.clone().multiply(-1), 1.0f); // its tip at them
            }
            if (k == 5) {
                Location c = center.toLocation(w);
                w.spawnParticle(Particle.SWEEP_ATTACK, c, 1, 0.2, 0.2, 0.2, 0);
                w.spawnParticle(Particle.CRIT, c, 6, 0.2, 0.3, 0.2, 0.2);
                w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
            }
            t[0]++;
        }, 0, 1);
        return end::run;
    }
}

package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.CuePlayer;
import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/** Cue id -> particles/sounds. Register more with {@link #register}; unknown ids warn once. */
public final class BukkitCuePlayer implements CuePlayer {

    private final Map<String, Consumer<Location>> cues = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final Logger logger;

    public BukkitCuePlayer(Logger logger) {
        this.logger = logger;
    }

    public static BukkitCuePlayer withDefaults(Plugin plugin, Logger logger) {
        BukkitCuePlayer c = new BukkitCuePlayer(logger);
        c.registerLoop("riptide", BukkitCuePlayer::riptide);
        c.registerLoop("parasol", e -> parasol(plugin, e));
        c.registerLoop("guard_pose", e -> guardPose(plugin, e));
        c.registerLoop("geyser_charge", e -> geyserCharge(plugin, e));
        c.registerLine("geyser_beam", (world, from, to) -> geyserBeam(plugin, world, from, to));

        // ---- Dreamer ----
        c.register("crit", loc -> {
            loc.getWorld().spawnParticle(Particle.CRIT, loc, 18, 0.3, 0.4, 0.3, 0.3);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        });
        c.register("dream_enter", loc -> {
            loc.getWorld().spawnParticle(Particle.PORTAL, loc, 40, 0.3, 0.6, 0.3, 0.6);
            loc.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
        });
        c.register("dream_echo", loc -> {
            loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 30, 0.3, 0.6, 0.3, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.3f);
        });
        c.register("echo_swap", loc -> {
            loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 40, 0.3, 0.6, 0.3, 0.1);
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
        });
        c.register("dream_tempest", loc -> { // a few slashes somewhere in the 5-block circle
            var rng = java.util.concurrent.ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                double angle = rng.nextDouble(Math.PI * 2), r = Math.sqrt(rng.nextDouble()) * 5;
                Location at = loc.clone().add(Math.cos(angle) * r, rng.nextDouble(-0.4, 0.6), Math.sin(angle) * r);
                loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0.2, 0.1, 0.2, 0);
                loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at, 6, 0.3, 0.3, 0.3, 0.05);
            }
            if (rng.nextBoolean()) loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, rng.nextFloat(0.8f, 1.4f));
        });
        c.registerLine("crescent", (w, from, to) -> {
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            for (double t = 0; t <= len; t += 0.5) {
                Vector p = from.clone().add(d.clone().multiply(t / len));
                w.spawnParticle(Particle.SWEEP_ATTACK, p.getX(), p.getY(), p.getZ(), 1, 0.1, 0.1, 0.1, 0);
                w.spawnParticle(Particle.END_ROD, p.getX(), p.getY(), p.getZ(), 1, 0.15, 0.15, 0.15, 0.01);
            }
            w.playSound(new Location(w, to.getX(), to.getY(), to.getZ()), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        });

        // ---- Essence Reaver ----
        c.register("sweep", loc -> {
            loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0.3, 0.1, 0.3, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.1f);
        });
        c.register("reaver_cleave", loc -> {
            loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 6, 1.4, 0.2, 1.4, 0);
            loc.getWorld().spawnParticle(Particle.SOUL, loc, 25, 1.5, 0.4, 1.5, 0.02);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
        });
        c.register("spirit_echo", loc -> {
            loc.getWorld().spawnParticle(Particle.SOUL, loc, 20, 0.3, 0.6, 0.3, 0.02);
            loc.getWorld().playSound(loc, Sound.PARTICLE_SOUL_ESCAPE, 1f, 1.2f);
        });
        c.registerLine("essence_recall", (w, from, to) -> soulStreak(plugin, w, from, to, 1));
        c.registerLine("echo_path", (w, from, to) -> soulStreak(plugin, w, from, to, 6));
        c.registerLoop("essence_mark", e -> particlesOn(plugin, e, Particle.SOUL_FIRE_FLAME, 3, 4));
        c.registerLoop("meditation", e -> {
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
            return particlesOn(plugin, e, Particle.HAPPY_VILLAGER, 4, 5);
        });
        c.register("barrier_block", loc -> {
            loc.getWorld().spawnParticle(Particle.SPLASH, loc, 25, 0.3, 0.3, 0.3, 0.1);
            loc.getWorld().spawnParticle(Particle.FALLING_WATER, loc, 8, 0.3, 0.3, 0.3, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 1.4f);
        });
        // ---- Hunter ----
        c.register("crossbow_shot", loc -> loc.getWorld().playSound(loc, Sound.ITEM_CROSSBOW_SHOOT, 1f, 1f));
        c.register("dry_fire", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.6f));
        c.register("hunter_dash", loc -> {
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.3, 0.1, 0.3, 0.02);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.6f);
        });
        c.register("vanish", loc -> {
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 20, 0.3, 0.6, 0.3, 0.02);
            loc.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
        });
        c.register("flask_throw", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_SPLASH_POTION_THROW, 1f, 0.8f));
        c.register("flask_burst", loc -> {
            loc.getWorld().spawnParticle(Particle.WITCH, loc, 60, 1.6, 0.6, 1.6, 0.1);
            loc.getWorld().spawnParticle(Particle.SPLASH, loc, 40, 1.4, 0.3, 1.4, 0.1);
            loc.getWorld().playSound(loc, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.8f);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
        });
        c.register("overdrive_start", loc -> {
            loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 30, 0.4, 0.8, 0.4, 0.1);
            loc.getWorld().playSound(loc, Sound.ITEM_CROSSBOW_QUICK_CHARGE_3, 1f, 0.8f);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
        });
        c.registerLoop("overdrive", e -> overdrive(plugin, e));
        c.register("trap_set", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 0.8f));
        c.register("trap_spring", loc -> {
            loc.getWorld().spawnParticle(Particle.CRIT, loc, 25, 0.4, 0.2, 0.4, 0.2);
            loc.getWorld().playSound(loc, Sound.BLOCK_TRIPWIRE_DETACH, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 1.4f);
        });
        c.register("mark_pop", loc -> {
            loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 20, 0.3, 0.4, 0.3, 0.2);
            loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 0.6f);
        });
        // ---- Arcanist: Arcane Barrage ----
        c.register("barrage_rise", loc -> {
            loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 50, 0.4, 0.2, 0.4, 0.2);
            loc.getWorld().playSound(loc, Sound.ENTITY_BREEZE_JUMP, 1f, 0.8f);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        });
        c.register("barrage_shot", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 12, 0.1, 0.1, 0.1, 0.08);
            loc.getWorld().playSound(loc, Sound.ENTITY_BREEZE_SHOOT, 1f, 1.4f);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
        });
        c.register("barrage_hit", loc -> {
            loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 30, 0.3, 0.4, 0.3, 0.3);
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 15, 0.2, 0.3, 0.2, 0.1);
            loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 0.5f);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.7f);
        });
        c.register("barrage_blast", loc -> {
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 0.5, 0.3, 0.5, 0);
            loc.getWorld().spawnParticle(Particle.WITCH, loc, 40, 1.6, 0.6, 1.6, 0.1);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
        });
        c.register("barrage_reload", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f));

        // ---- Umbrella ----
        c.register("thrust_windup", loc -> loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_RETURN, 1f, 1.4f));
        c.registerLine("umbrella_thrust", (w, from, to) -> {
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            for (double t = 0; t <= len; t += 0.4) {
                Vector p = from.clone().add(d.clone().multiply(t / len));
                w.spawnParticle(Particle.CRIT, p.getX(), p.getY(), p.getZ(), 1, 0.05, 0.05, 0.05, 0);
                w.spawnParticle(Particle.BUBBLE_POP, p.getX(), p.getY(), p.getZ(), 1, 0.1, 0.1, 0.1, 0.02);
            }
            w.playSound(new Location(w, from.getX(), from.getY(), from.getZ()), Sound.ITEM_TRIDENT_THROW, 1f, 1.2f);
        });

        // ---- Essence Reaver: Soul Rend ----
        c.register("soul_rend_ready", loc -> {
            loc.getWorld().spawnParticle(Particle.SOUL, loc, 25, 0.4, 0.8, 0.4, 0.03);
            loc.getWorld().playSound(loc, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.6f);
        });
        c.register("soul_rend", loc -> {
            loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, 30, 0.3, 0.7, 0.3, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.6f);
        });
        c.registerLine("soul_tether", (w, from, to) -> {
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            for (double t = 0; t <= len; t += 0.7) {
                Vector p = from.clone().add(d.clone().multiply(t / len));
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getX(), p.getY(), p.getZ(), 1, 0.02, 0.02, 0.02, 0);
            }
        });
        c.registerLine("soul_return", (w, from, to) -> {
            soulStreak(plugin, w, from, to, 4);
            w.playSound(new Location(w, to.getX(), to.getY(), to.getZ()), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.4f);
        });

        // ---- Vanguard ----
        c.register("leap_off", loc -> {
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 15, 0.4, 0.1, 0.4, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_GOAT_LONG_JUMP, 1f, 0.8f);
        });
        c.register("leap_slam", loc -> {
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 1.2, 0.2, 1.2, 0);
            loc.getWorld().spawnParticle(Particle.CRIT, loc, 40, 2.5, 0.3, 2.5, 0.3);
            loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        });
        c.register("radiant_bond", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 25, 0.4, 0.7, 0.4, 0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        });
        c.registerLine("radiant_tether", (w, from, to) -> {
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            var gold = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 90), 0.8f);
            for (double t = 0; t <= len; t += 0.6) {
                Vector p = from.clone().add(d.clone().multiply(t / len));
                w.spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), 1, 0.02, 0.02, 0.02, 0, gold);
            }
        });
        c.register("tether_break", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 12, 0.3, 0.5, 0.3, 0.08);
            loc.getWorld().playSound(loc, Sound.BLOCK_CHAIN_BREAK, 1f, 1.2f);
        });
        c.registerLoop("bulwark", e -> shieldUp(plugin, e));
        c.register("heroic_launch", loc -> {
            loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 30, 0.4, 0.2, 0.4, 0.15);
            loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.7f);
        });
        c.register("heroic_impact", loc -> {
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 60, 3, 0.4, 3, 0.1);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
            loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.6f);
        });

        c.register("hit", loc -> {
            loc.getWorld().spawnParticle(Particle.CRIT, loc, 12, 0.2, 0.2, 0.2, 0.2);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
        });
        c.register("impact", loc -> {
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 10, 0.1, 0.1, 0.1, 0.02);
            loc.getWorld().playSound(loc, Sound.BLOCK_STONE_HIT, 1f, 1f);
        });
        c.register("cast", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.4f));
        c.register("spark", loc -> {
            loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 8, 0.2, 0.2, 0.2, 0.1);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.4f);
        });
        c.register("shatter", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 30, 0.6, 0.6, 0.6, 0.15);
            loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 25, 1.5, 0.8, 1.5, 0.2);
            loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.8f);
        });
        c.register("shatter_stun", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 60, 0.8, 0.8, 0.8, 0.25);
            loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 60, 2.0, 1.0, 2.0, 0.3);
            loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
            loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 1.2f);
        });
        c.register("fizzle", loc -> {
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 12, 0.3, 0.3, 0.3, 0.02);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.6f, 1.5f);
        });
        c.register("teleport", loc -> {
            loc.getWorld().spawnParticle(Particle.PORTAL, loc, 40, 0.3, 0.8, 0.3, 0.5);
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        });
        return c;
    }

    public void register(String cueId, Consumer<Location> action) { cues.put(cueId, action); }

    // ---- two-point cues ---------------------------------------------------------------------------

    /** Draws something between two points (a beam, a tether). */
    @FunctionalInterface
    public interface LineCue {
        void play(World world, Vector from, Vector to);
    }

    private final Map<String, LineCue> lines = new HashMap<>();

    public void registerLine(String cueId, LineCue cue) { lines.put(cueId, cue); }

    @Override
    public void playLine(String cueId, String world, Vec3 from, Vec3 to) {
        LineCue cue = lines.get(cueId);
        if (cue == null) {
            if (warned.add(cueId)) logger.warning("Unknown line cue '" + cueId + "' (known: " + lines.keySet() + ")");
            return;
        }
        World w = Bukkit.getWorld(world);
        if (w != null) cue.play(w, Convert.bukkit(from), Convert.bukkit(to));
    }

    /** A thick water beam that lingers for ~0.6s, with a burst where it ends. */
    private static void geyserBeam(Plugin plugin, World w, Vector from, Vector to) {
        Vector d = to.clone().subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        Vector step = d.clone().multiply(0.4 / len);
        w.playSound(new Location(w, from.getX(), from.getY(), from.getZ()), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 0.8f);
        w.playSound(new Location(w, to.getX(), to.getY(), to.getZ()), Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.6f);
        int[] runs = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (++runs[0] > 6) {
                task[0].cancel();
                return;
            }
            Vector p = from.clone();
            for (double t = 0; t < len; t += 0.4) {
                w.spawnParticle(Particle.SPLASH, p.getX(), p.getY(), p.getZ(), 3, 0.35, 0.35, 0.35, 0);
                w.spawnParticle(Particle.FALLING_WATER, p.getX(), p.getY(), p.getZ(), 1, 0.4, 0.4, 0.4, 0);
                p.add(step);
            }
            w.spawnParticle(Particle.SPLASH, to.getX(), to.getY(), to.getZ(), 40, 0.8, 0.8, 0.8, 0.2);
        }, 0, 2);
    }

    // ---- looping cues ---------------------------------------------------------------------------

    private final Map<String, Function<Entity, CueHandle>> loops = new HashMap<>();

    /**
     * Register a looping cue: start it on an entity, return how to stop it. This is the extension
     * point for animations from other plugins (BetterModel, ModelEngine...): register a loop that
     * plays their animation and stops it in the handle. Ability YAML just says  start_cue: <id>.
     */
    public void registerLoop(String cueId, Function<Entity, CueHandle> starter) { loops.put(cueId, starter); }

    @Override
    public CueHandle start(String cueId, UUID entityId) {
        Function<Entity, CueHandle> starter = loops.get(cueId);
        if (starter == null) {
            if (warned.add(cueId)) logger.warning("Unknown looping cue '" + cueId + "' (known: " + loops.keySet() + ")");
            return CueHandle.NONE;
        }
        Entity entity = Bukkit.getEntity(entityId);
        return entity == null || !entity.isValid() ? CueHandle.NONE : starter.apply(entity);
    }

    /**
     * Parasol Guard: a ring of falling water in front of the guard, matching the engine's barrier
     * (1 block in front of the body's centre, radius 1.3, facing where they look). Everyone sees it,
     * so the stance is readable.
     */
    private static CueHandle parasol(Plugin plugin, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return CueHandle.NONE;
        living.getWorld().playSound(living.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1f, 1.2f);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!living.isValid()) return;
            Vector look = living.getEyeLocation().getDirection();
            Vector facing = new Vector(look.getX(), 0, look.getZ());
            if (facing.lengthSquared() < 1e-6) return;
            facing.normalize();
            Vector right = new Vector(-facing.getZ(), 0, facing.getX());
            Vector center = living.getBoundingBox().getCenter().add(facing.clone().multiply(1.0));
            World w = living.getWorld();
            double r = 1.3;
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16;
                Vector p = center.clone().add(right.clone().multiply(Math.cos(a) * r)).add(new Vector(0, Math.sin(a) * r, 0));
                w.spawnParticle(Particle.FALLING_WATER, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
            }
            w.spawnParticle(Particle.SPLASH, center.getX(), center.getY(), center.getZ(), 6, 0.5, 0.6, 0.5, 0);
        }, 0, 3);
        return task::cancel;
    }

    /** A soul streak from one point to another, drawn over {@code steps} ticks (1 = all at once). */
    private static void soulStreak(Plugin plugin, World w, Vector from, Vector to, int steps) {
        Vector d = to.clone().subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        w.playSound(new Location(w, to.getX(), to.getY(), to.getZ()), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.9f);
        int[] step = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double t0 = (double) step[0] / steps, t1 = (double) (step[0] + 1) / steps;
            for (double t = t0; t <= t1; t += 0.35 / len) {
                Vector p = from.clone().add(d.clone().multiply(t));
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getX(), p.getY(), p.getZ(), 1, 0.05, 0.05, 0.05, 0);
                w.spawnParticle(Particle.SOUL, p.getX(), p.getY(), p.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
            }
            if (++step[0] >= steps) task[0].cancel();
        }, 0, 1);
    }

    /** Particles around an entity every {@code period} ticks while the cue runs. */
    /** Sparks around the Hunter during Overdrive, but none while invisible (they'd give them away). */
    private static CueHandle overdrive(Plugin plugin, Entity entity) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid() || (entity instanceof LivingEntity l && l.hasPotionEffect(PotionEffectType.INVISIBILITY))) return;
            Vector c = entity.getBoundingBox().getCenter();
            entity.getWorld().spawnParticle(Particle.CRIT, c.getX(), c.getY(), c.getZ(), 3, 0.35, 0.5, 0.35, 0.01);
        }, 0, 3);
        return task::cancel;
    }

    private static CueHandle particlesOn(Plugin plugin, Entity entity, Particle particle, int count, int period) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid()) return;
            Vector c = entity.getBoundingBox().getCenter();
            entity.getWorld().spawnParticle(particle, c.getX(), c.getY(), c.getZ(), count, 0.35, 0.5, 0.35, 0.01);
        }, 0, period);
        return task::cancel;
    }

    /** Water gathering around the caster while the Geyser charges. */
    private static CueHandle geyserCharge(Plugin plugin, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return CueHandle.NONE;
        living.getWorld().playSound(living.getLocation(), Sound.ITEM_BUCKET_FILL, 1f, 0.7f);
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!living.isValid()) return;
            Vector c = living.getBoundingBox().getCenter();
            World w = living.getWorld();
            double r = Math.max(0.4, 1.6 - tick[0] * 0.08); // spirals inward as it charges
            for (int i = 0; i < 6; i++) {
                double a = tick[0] * 0.5 + Math.PI * 2 * i / 6;
                w.spawnParticle(Particle.SPLASH, c.getX() + Math.cos(a) * r, c.getY() + Math.sin(tick[0] * 0.3 + i) * 0.6,
                        c.getZ() + Math.sin(a) * r, 2, 0, 0, 0, 0);
            }
            tick[0]++;
        }, 0, 2);
        return task::cancel;
    }

    /**
     * Guard stance prop: a copy of the held weapon floating in front of the chest, angled across the
     * body like a guard, turning with the player. Everyone sees it. (A true blocking pose is client-side
     * only; with a resource pack this can become an open-parasol model.)
     */
    private static CueHandle guardPose(Plugin plugin, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return CueHandle.NONE;
        living.swingMainHand();
        ItemStack held = living.getEquipment() == null ? null : living.getEquipment().getItemInMainHand().clone();
        ItemStack shown = held == null || held.getType().isAir() ? new ItemStack(Material.IRON_SWORD) : held;
        return heldProp(plugin, living, shown, 50);
    }

    /** Bulwark: a shield held up in front of you. */
    private static CueHandle shieldUp(Plugin plugin, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return CueHandle.NONE;
        living.getWorld().playSound(living.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
        return heldProp(plugin, living, new ItemStack(Material.SHIELD), 0);
    }

    /** An item shown in front of the entity, following where it faces, until stopped. */
    private static CueHandle heldProp(Plugin plugin, LivingEntity living, ItemStack shown, float tiltDegrees) {
        Location start = living.getLocation();
        ItemDisplay prop = living.getWorld().spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(shown);
            d.setPersistent(false);
            d.setTeleportDuration(1); // glides with the player
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ((float) Math.toRadians(tiltDegrees)),
                    new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
            VisualEntities.mark(d);
        });
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!living.isValid() || !prop.isValid()) return;
            Vector look = living.getEyeLocation().getDirection();
            Vector facing = new Vector(look.getX(), 0, look.getZ());
            if (facing.lengthSquared() < 1e-6) return;
            facing.normalize();
            Vector pos = living.getBoundingBox().getCenter().add(facing.clone().multiply(0.55)).add(new Vector(0, 0.25, 0));
            Location loc = new Location(living.getWorld(), pos.getX(), pos.getY(), pos.getZ());
            loc.setDirection(facing);
            prop.teleport(loc);
        }, 0, 1);
        return () -> {
            task.cancel();
            if (prop.isValid()) prop.remove();
        };
    }

    /** Trident riptide spin POSE only: no vanilla riptide damage or movement (the dash does the moving). */
    private static CueHandle riptide(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return CueHandle.NONE;
        living.setRiptiding(true);
        living.getWorld().playSound(living.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1f);
        return () -> {
            if (living.isValid()) living.setRiptiding(false);
        };
    }

    @Override
    public void play(String cueId, String world, Vec3 position) {
        Consumer<Location> action = cues.get(cueId);
        if (action == null) {
            if (warned.add(cueId)) logger.warning("Unknown cue '" + cueId + "' (known: " + cues.keySet() + ")");
            return;
        }
        Location loc = Convert.location(world, position);
        if (loc != null) action.accept(loc);
    }
}

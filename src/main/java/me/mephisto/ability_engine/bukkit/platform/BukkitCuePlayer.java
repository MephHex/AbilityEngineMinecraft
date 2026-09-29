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

    /** Anti-magic colour coding (silence): bright teal fading to deep cyan. */
    public static final org.bukkit.Color ANTI_MAGIC = org.bukkit.Color.fromRGB(40, 240, 210);
    public static final org.bukkit.Color ANTI_MAGIC_DEEP = org.bukkit.Color.fromRGB(0, 110, 130);
    /** Near-black teal, for particles that fade out dark (the silenced wisps). */
    public static final org.bukkit.Color ANTI_MAGIC_DARK = org.bukkit.Color.fromRGB(8, 40, 38);


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
        c.register("dream_tempest_ring", loc -> { // Dream Tempest's edge (5 blocks), on the ground, for everyone
            var dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(170, 90, 255), 1.3f);
            double y = loc.getY() - 0.8; // the spot is a body's centre: its feet are a little lower
            for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48;
                loc.getWorld().spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * 5, y, loc.getZ() + Math.sin(a) * 5,
                        1, 0, 0, 0, 0, dust);
            }
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

        // ---- Gunner ----
        // Silence reads as anti-magic: teal / cyan, never the green of heals or the purple of spells.
        c.register("null_burst", loc -> {
            var teal = new Particle.DustTransition(ANTI_MAGIC, ANTI_MAGIC_DEEP, 1.4f);
            loc.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 70, 1.6, 0.5, 1.6, 0, teal);
            loc.getWorld().spawnParticle(Particle.GLOW, loc, 25, 1.4, 0.4, 1.4, 0.05);
            loc.getWorld().spawnParticle(Particle.NAUTILUS, loc.clone().add(0, 0.8, 0), 40, 0.3, 0.3, 0.3, 1.2);
            loc.getWorld().playSound(loc, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.8f, 1.4f);
        });
        c.register("null_pool", loc -> { // the pool's edge (3 blocks), on the ground
            var edge = new Particle.DustOptions(ANTI_MAGIC, 1.2f);
            for (int i = 0; i < 32; i++) {
                double a = Math.PI * 2 * i / 32;
                loc.getWorld().spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * 3, loc.getY() + 0.1,
                        loc.getZ() + Math.sin(a) * 3, 1, 0, 0, 0, 0, edge);
            }
            var inside = new Particle.DustTransition(ANTI_MAGIC, ANTI_MAGIC_DEEP, 1.0f);
            loc.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, loc.clone().add(0, 0.15, 0), 14, 1.4, 0.05, 1.4, 0, inside);
            loc.getWorld().spawnParticle(Particle.GLOW, loc.clone().add(0, 0.2, 0), 3, 1.4, 0.1, 1.4, 0);
        });
        c.register("rounds_loaded", loc -> { // magic rounds loaded into the guns
            loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 40, 0.4, 0.8, 0.4, 0.5);
            loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.4f);
            loc.getWorld().playSound(loc, Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.2f);
        });
        c.register("spell_blocked", loc -> { // Counterspell ate a spell: a teal flash
            loc.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 40, 0.7, 0.9, 0.7, 0,
                    new Particle.DustTransition(ANTI_MAGIC, ANTI_MAGIC_DEEP, 1.4f));
            loc.getWorld().spawnParticle(Particle.GLOW, loc, 12, 0.6, 0.8, 0.6, 0.1);
            loc.getWorld().playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
            loc.getWorld().playSound(loc, Sound.BLOCK_CONDUIT_ATTACK_TARGET, 0.8f, 1.6f);
        });
        // Guns: from = the shooter (their centre), to = where they aimed.
        c.registerLine("shotgun_blast", (w, from, to) -> gunCone(w, from, to, 7, 22.5, 1f));
        c.registerLine("buckshot_blast", (w, from, to) -> {
            gunCone(w, from, to, 9, 35, 1.4f);
            w.playSound(new Location(w, from.getX(), from.getY(), from.getZ()), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
        });
        c.registerLine("revolver_shot", (w, from, to) -> {
            Vector start = from.clone().add(new Vector(0, 0.55, 0)); // about eye height
            Vector d = to.clone().subtract(start);
            double len = Math.min(30, d.length());
            if (len < 0.1) return;
            d.normalize();
            var brass = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 225, 140), 0.6f);
            for (double t = 0.8; t <= len; t += 0.5) {
                Vector q = start.clone().add(d.clone().multiply(t));
                w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, brass);
            }
            Vector muzzle = start.clone().add(d.clone().multiply(0.8));
            w.spawnParticle(Particle.SMOKE, muzzle.getX(), muzzle.getY(), muzzle.getZ(), 3, 0.05, 0.05, 0.05, 0.01);
            Location at = new Location(w, start.getX(), start.getY(), start.getZ());
            w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 1.7f);
            w.playSound(at, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.5f, 1.8f);
        });
        // ---- Powder Keg ----
        c.register("keg_throw", loc -> {
            loc.getWorld().playSound(loc, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_TNT_PRIMED, 0.8f, 1.2f);
        });
        c.register("keg_blast", loc -> {
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 80, 2.5, 0.8, 2.5, 0.1);
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 40, 2, 0.8, 2, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        });
        c.registerLoop("spellshield", e -> spellShield(plugin, e));
        c.register("spellshield_absorb", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 16, 0.6, 0.8, 0.6, 0,
                    new Particle.DustTransition(ANTI_MAGIC, ANTI_MAGIC_DEEP, 1.1f));
            loc.getWorld().spawnParticle(Particle.GLOW, loc, 4, 0.5, 0.7, 0.5, 0);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
        });
        c.register("spellshield_blast", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 90, 2.4, 0.8, 2.4, 0,
                    new Particle.DustTransition(ANTI_MAGIC, ANTI_MAGIC_DEEP, 1.5f));
            loc.getWorld().spawnParticle(Particle.GLOW, loc, 30, 2.2, 0.8, 2.2, 0.1);
            loc.getWorld().spawnParticle(Particle.NAUTILUS, loc.clone().add(0, 1, 0), 50, 0.3, 0.3, 0.3, 2);
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 1, 0.3, 1, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.8f);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.5f);
        });
        c.register("spellshield_purge", loc -> { // enemy buffs stripped: dark cyan motes and smoke
            loc.getWorld().spawnParticle(Particle.DUST, loc, 50, 2.5, 0.6, 2.5, 0,
                    new Particle.DustOptions(ANTI_MAGIC_DEEP, 1.3f));
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 30, 2.5, 0.6, 2.5, 0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.2f);
        });
        c.register("ward_block", loc -> { // Null Ward shrugs a debuff off: a teal flash
            loc.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, loc, 25, 0.5, 0.7, 0.5, 0,
                    new Particle.DustTransition(ANTI_MAGIC, ANTI_MAGIC_DEEP, 1.2f));
            loc.getWorld().spawnParticle(Particle.GLOW, loc, 8, 0.4, 0.6, 0.4, 0.05);
            loc.getWorld().playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1f, 1.4f);
        });
        c.register("ward_ready", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.8f));

        // ---- Copper Golem ----
        c.register("barrier_break", loc -> { // Cuprous Might: the copper barrier takes a hit and cracks
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 40, 0.5, 0.7, 0.5, 0.1,
                    org.bukkit.Material.COPPER_BLOCK.createBlockData());
            loc.getWorld().spawnParticle(Particle.WAX_OFF, loc, 15, 0.5, 0.7, 0.5, 0.2);
            loc.getWorld().playSound(loc, Sound.BLOCK_COPPER_BREAK, 1f, 0.7f);
            loc.getWorld().playSound(loc, Sound.ITEM_SHIELD_BREAK, 0.7f, 1.3f);
        });
        c.register("golem_swing", loc -> {
            loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 0.3, 0), 2, 0.6, 0.1, 0.6, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 1.2f);
        });
        c.register("anchor_throw", loc -> {
            loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.BLOCK_CHAIN_PLACE, 0.8f, 0.8f);
        });
        c.registerLine("anchor_chain", (w, from, to) -> { // the chain, grey links from you to the pick
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            var iron = new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 150, 160), 0.9f);
            for (double t = 0; t <= len; t += 0.4) {
                Vector q = from.clone().add(d.clone().multiply(t / len));
                w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, iron);
            }
            w.playSound(new Location(w, to.getX(), to.getY(), to.getZ()), Sound.BLOCK_CHAIN_HIT, 1f, 0.8f);
        });
        c.register("anchor_land", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 25, 0.5, 0.2, 0.5, 0.1,
                    org.bukkit.Material.COPPER_BLOCK.createBlockData());
            loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.7f);
        });
        c.register("rust_step", loc -> { // a layer of rust: verdigris flakes
            var verdigris = new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 160, 130), 1.1f);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 12, 0.4, 0.6, 0.4, 0, verdigris);
            loc.getWorld().spawnParticle(Particle.SCRAPE, loc, 4, 0.4, 0.6, 0.4, 0);
            loc.getWorld().playSound(loc, Sound.BLOCK_COPPER_STEP, 0.8f, 0.6f);
        });
        c.register("rust_burst", loc -> { // bursting out of the rust
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 80, 2.2, 0.8, 2.2, 0.2,
                    org.bukkit.Material.OXIDIZED_COPPER.createBlockData());
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 1.5, 0.4, 1.5, 0);
            loc.getWorld().playSound(loc, Sound.BLOCK_COPPER_BREAK, 1f, 0.5f);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        });
        c.register("conduction_slam", loc -> {
            loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 30, 0.6, 0.2, 0.6, 0.3);
            loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1f, 0.6f);
            loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
        });
        for (int r = 2; r <= 8; r += 2) { // Conduction Field's rings, on the ground
            final double radius = r;
            c.register("shockwave_" + r, loc -> {
                int points = (int) (radius * 10);
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    double x = loc.getX() + Math.cos(a) * radius, z = loc.getZ() + Math.sin(a) * radius;
                    loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, x, loc.getY() + 0.2, z, 1, 0, 0.1, 0, 0.02);
                    if (i % 3 == 0) {
                        loc.getWorld().spawnParticle(Particle.BLOCK, x, loc.getY() + 0.1, z, 2, 0.1, 0.1, 0.1, 0,
                                org.bukkit.Material.COPPER_BLOCK.createBlockData());
                    }
                }
                loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.35f, 1.6f);
            });
        }
        c.register("rod_charge", loc -> {
            loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1.5, 0), 40, 0.3, 1.2, 0.3, 0.2);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);
        });
        c.register("lightning_strike", loc -> {
            loc.getWorld().strikeLightningEffect(loc); // the look and sound only: the ability does the damage
            loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 80, 2.5, 0.5, 2.5, 0.4);
        });
        c.register("electric_field", loc -> { // Lightning Rod's field: its 5-block edge
            for (int i = 0; i < 36; i++) {
                double a = Math.PI * 2 * i / 36;
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.getX() + Math.cos(a) * 5, loc.getY() - 0.8,
                        loc.getZ() + Math.sin(a) * 5, 1, 0, 0.15, 0, 0.02);
            }
            loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 10, 2.5, 0.2, 2.5, 0.1);
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

    /** A gun's blast: a cone of sparks and smoke from about eye height toward {@code to}. */
    private static void gunCone(World w, Vector from, Vector to, double range, double halfAngleDeg, float volume) {
        Vector start = from.clone().add(new Vector(0, 0.55, 0));
        Vector dir = to.clone().subtract(start);
        if (dir.lengthSquared() < 1e-6) return;
        dir.normalize();
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        double spread = Math.tan(Math.toRadians(halfAngleDeg));
        for (int i = 0; i < 45; i++) {
            Vector jitter = new Vector(rng.nextDouble(-1, 1), rng.nextDouble(-1, 1), rng.nextDouble(-1, 1)).multiply(spread);
            Vector ray = dir.clone().add(jitter).normalize();
            double t = rng.nextDouble(0.8, range);
            Vector q = start.clone().add(ray.multiply(t));
            w.spawnParticle(i % 3 == 0 ? Particle.SMOKE : Particle.CRIT, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0);
        }
        Vector muzzle = start.clone().add(dir.clone().multiply(0.9));
        w.spawnParticle(Particle.FLAME, muzzle.getX(), muzzle.getY(), muzzle.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
        w.spawnParticle(Particle.LARGE_SMOKE, muzzle.getX(), muzzle.getY(), muzzle.getZ(), 4, 0.15, 0.15, 0.15, 0.02);
        Location at = new Location(w, start.getX(), start.getY(), start.getZ());
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.5f * volume, 1.9f);
        w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, volume, 0.7f);
    }

    /** Counterspell: a shimmering teal sphere around the caster (anti-magic colours). */
    private static CueHandle spellShield(Plugin plugin, Entity entity) {
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.6f, 1.6f);
        var teal = new Particle.DustTransition(ANTI_MAGIC, ANTI_MAGIC_DEEP, 1.0f);
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!entity.isValid()) return;
            Vector c = entity.getBoundingBox().getCenter();
            World w = entity.getWorld();
            for (int i = 0; i < 10; i++) { // a slowly turning band of points on a 1.3-block sphere
                double theta = (tick[0] * 0.2) + i * Math.PI * 2 / 10;
                double phi = Math.PI * (0.25 + 0.5 * ((i + tick[0]) % 5) / 4.0);
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.getX() + 1.3 * Math.sin(phi) * Math.cos(theta),
                        c.getY() + 1.3 * Math.cos(phi), c.getZ() + 1.3 * Math.sin(phi) * Math.sin(theta), 1, 0, 0, 0, 0, teal);
            }
            if (tick[0] % 3 == 0) { // a glint somewhere on the sphere
                double theta = Math.random() * Math.PI * 2, phi = Math.random() * Math.PI;
                w.spawnParticle(Particle.GLOW, c.getX() + 1.3 * Math.sin(phi) * Math.cos(theta),
                        c.getY() + 1.3 * Math.cos(phi), c.getZ() + 1.3 * Math.sin(phi) * Math.sin(theta), 1, 0, 0, 0, 0);
            }
            tick[0]++;
        }, 0, 2);
        return task::cancel;
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

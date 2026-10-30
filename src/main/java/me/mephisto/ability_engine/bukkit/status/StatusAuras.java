package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.platform.BukkitCuePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Particles on an entity for as long as it has a tag, so everyone can read its state at a glance:
 * silenced (teal wisps fading to near-black: teal / cyan is the anti-magic colour), poisoned (green poison
 * swirls, like the vanilla effect), paralyzed (yellow sparks crackling over the body), stunned (dizzy stars circling
 * over the head), rooted (a ring of roots and crumbling earth at the feet).
 */
final class StatusAuras {

    private static final Color POISON = Color.fromRGB(0x87A363);   // vanilla Poison's colour
    private static final Color SPARK_YELLOW = Color.fromRGB(0xF4E04D);
    private static final Color STAR = Color.fromRGB(0xFFF6D5);     // pale cream: not the paralyzed yellow
    private static final Color ROOT_BROWN = Color.fromRGB(0x6B4A2B);
    private static final Color ROOT_GREEN = Color.fromRGB(0x4F7A28);
    private static final org.bukkit.block.data.BlockData ROOT_BLOCK = org.bukkit.Material.ROOTED_DIRT.createBlockData();

    /** Running loops, per entity and tag. */
    private static final Map<UUID, Map<String, BukkitTask>> RUNNING = new HashMap<>();

    private StatusAuras() {}

    /** Bind the auras. */
    static void bindAll(TagBindings b) {
        bind(b, me.mephisto.ability_engine.engine.tag.Tags.SILENCED, 4, StatusAuras::silenced);
        bind(b, me.mephisto.ability_engine.engine.tag.Tags.POISONED, 4, StatusAuras::poisoned);
        bind(b, me.mephisto.ability_engine.engine.tag.Tags.PARALYZED, 3, StatusAuras::paralyzed);
        bind(b, me.mephisto.ability_engine.engine.tag.Tags.STUNNED, 2, StatusAuras::stunned);
        bind(b, me.mephisto.ability_engine.engine.tag.Tags.ROOTED, 4, StatusAuras::rooted);
    }

    private static void bind(TagBindings b, String tag, long period, BiConsumer<LivingEntity, Integer> frame) {
        b.bind(tag, e -> start(e, tag, period, frame), e -> stop(e.getUniqueId(), tag));
    }

    private static void start(LivingEntity e, String tag, long period, BiConsumer<LivingEntity, Integer> frame) {
        stop(e.getUniqueId(), tag);
        int[] step = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(JavaPlugin.getProvidingPlugin(StatusAuras.class), () -> {
            if (!e.isValid()) {
                stop(e.getUniqueId(), tag);
                return;
            }
            frame.accept(e, step[0]++);
        }, 0L, period);
        RUNNING.computeIfAbsent(e.getUniqueId(), k -> new HashMap<>()).put(tag, task);
    }

    private static void stop(UUID entity, String tag) {
        Map<String, BukkitTask> tasks = RUNNING.get(entity);
        if (tasks == null) return;
        BukkitTask task = tasks.remove(tag);
        if (task != null) task.cancel();
        if (tasks.isEmpty()) RUNNING.remove(entity);
    }

    /** Teal wisps fading to near-black cyan, drifting up off the body, like the Null Flask's burst. */
    private static void silenced(LivingEntity e, int step) {
        var teal = new Particle.DustTransition(BukkitCuePlayer.ANTI_MAGIC, BukkitCuePlayer.ANTI_MAGIC_DARK, 0.9f);
        Location body = e.getLocation().add(0, e.getHeight() * 0.5, 0);
        double w = e.getWidth() * 0.5;
        e.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, body, 4, w, e.getHeight() * 0.4, w, 0, teal);
        if (step % 4 == 0) e.getWorld().spawnParticle(Particle.GLOW, body, 1, w, e.getHeight() * 0.3, w, 0);
    }

    /** Vanilla-style poison swirls rising off the body, and now and then a green drip. */
    private static void poisoned(LivingEntity e, int step) {
        Location body = e.getLocation().add(0, e.getHeight() * 0.5, 0);
        double w = e.getWidth() * 0.5;
        e.getWorld().spawnParticle(Particle.ENTITY_EFFECT, body, 3, w, e.getHeight() * 0.35, w, 1, POISON);
        if (step % 3 == 0) {
            e.getWorld().spawnParticle(Particle.ITEM_SLIME, body, 1, w, e.getHeight() * 0.3, w, 0);
        }
    }

    /** Yellow sparks and short crackles over the body. */
    private static void paralyzed(LivingEntity e, int step) { // step unused: every frame looks alike
        Location body = e.getLocation().add(0, e.getHeight() * 0.5, 0);
        double w = e.getWidth() * 0.6;
        e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, body, 4, w, e.getHeight() * 0.4, w, 0.05);
        e.getWorld().spawnParticle(Particle.DUST, body, 2, w, e.getHeight() * 0.4, w, 0,
                new Particle.DustOptions(SPARK_YELLOW, 0.8f));
    }

    /** Three stars circling just over the head, leaving a faint ring; now and then a glint. */
    private static void stunned(LivingEntity e, int step) {
        Location head = e.getLocation().add(0, e.getHeight() + 0.25, 0);
        double r = Math.max(0.35, e.getWidth() * 0.6);
        var star = new Particle.DustOptions(STAR, 0.9f);
        for (int i = 0; i < 3; i++) {
            double a = step * 0.45 + i * (Math.PI * 2 / 3);
            Location at = head.clone().add(Math.cos(a) * r, Math.sin(step * 0.3 + i) * 0.05, Math.sin(a) * r);
            e.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, star);
        }
        if (step % 5 == 0) e.getWorld().spawnParticle(Particle.WAX_OFF, head, 1, r * 0.6, 0.05, r * 0.6, 0);
    }

    /** A ring of brown and green roots around the feet, and crumbling earth. */
    private static void rooted(LivingEntity e, int step) {
        Location feet = e.getLocation().add(0, 0.1, 0);
        double r = e.getWidth() * 0.5 + 0.25;
        var brown = new Particle.DustOptions(ROOT_BROWN, 1.1f);
        var green = new Particle.DustOptions(ROOT_GREEN, 0.9f);
        for (int i = 0; i < 6; i++) {
            double a = i * (Math.PI / 3) + step * 0.1;
            Location at = feet.clone().add(Math.cos(a) * r, (i % 2) * 0.15, Math.sin(a) * r);
            e.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, i % 2 == 0 ? brown : green);
        }
        if (step % 2 == 0) e.getWorld().spawnParticle(Particle.BLOCK, feet, 3, r * 0.6, 0.05, r * 0.6, 0, ROOT_BLOCK);
    }
}

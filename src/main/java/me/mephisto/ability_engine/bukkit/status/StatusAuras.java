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
 * silenced (a teal ring over the head: teal / cyan is the anti-magic colour), poisoned (green poison
 * swirls, like the vanilla effect), paralyzed (yellow sparks crackling over the body).
 */
final class StatusAuras {

    private static final Color POISON = Color.fromRGB(0x87A363);   // vanilla Poison's colour
    private static final Color SPARK_YELLOW = Color.fromRGB(0xF4E04D);

    /** Running loops, per entity and tag. */
    private static final Map<UUID, Map<String, BukkitTask>> RUNNING = new HashMap<>();

    private StatusAuras() {}

    /** Bind the auras. */
    static void bindAll(TagBindings b) {
        bind(b, me.mephisto.ability_engine.engine.tag.Tags.SILENCED, 3, StatusAuras::silenced);
        bind(b, me.mephisto.ability_engine.engine.tag.Tags.POISONED, 4, StatusAuras::poisoned);
        bind(b, me.mephisto.ability_engine.engine.tag.Tags.PARALYZED, 3, StatusAuras::paralyzed);
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

    /** A small spinning teal ring over the head. */
    private static void silenced(LivingEntity e, int step) {
        var teal = new Particle.DustTransition(BukkitCuePlayer.ANTI_MAGIC, BukkitCuePlayer.ANTI_MAGIC_DEEP, 0.7f);
        Location top = e.getLocation().add(0, e.getHeight() + 0.35, 0);
        double spin = step * 0.35;
        for (int i = 0; i < 6; i++) {
            double a = spin + Math.PI * 2 * i / 6;
            e.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, top.getX() + Math.cos(a) * 0.45, top.getY(),
                    top.getZ() + Math.sin(a) * 0.45, 1, 0, 0, 0, 0, teal);
        }
        if (step % 5 == 0) e.getWorld().spawnParticle(Particle.GLOW, top, 1, 0.2, 0.05, 0.2, 0);
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
}

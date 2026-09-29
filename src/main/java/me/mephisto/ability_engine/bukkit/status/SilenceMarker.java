package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.platform.BukkitCuePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * While an entity is silenced, a small teal ring turns above its head, so everyone can see who
 * can't cast. Teal / cyan is the anti-magic colour (see {@link BukkitCuePlayer#ANTI_MAGIC}).
 */
final class SilenceMarker {

    private static final Map<UUID, BukkitTask> RUNNING = new HashMap<>();

    private SilenceMarker() {}

    static void apply(LivingEntity e) {
        remove(e);
        var teal = new Particle.DustTransition(BukkitCuePlayer.ANTI_MAGIC, BukkitCuePlayer.ANTI_MAGIC_DEEP, 0.7f);
        int[] step = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(JavaPlugin.getProvidingPlugin(SilenceMarker.class), () -> {
            if (!e.isValid()) {
                remove(e);
                return;
            }
            Location top = e.getLocation().add(0, e.getHeight() + 0.35, 0);
            double spin = step[0]++ * 0.35;
            for (int i = 0; i < 6; i++) {
                double a = spin + Math.PI * 2 * i / 6;
                e.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, top.getX() + Math.cos(a) * 0.45, top.getY(),
                        top.getZ() + Math.sin(a) * 0.45, 1, 0, 0, 0, 0, teal);
            }
            if (step[0] % 5 == 0) e.getWorld().spawnParticle(Particle.GLOW, top, 1, 0.2, 0.05, 0.2, 0);
        }, 0L, 3L);
        RUNNING.put(e.getUniqueId(), task);
    }

    static void remove(LivingEntity e) {
        BukkitTask task = RUNNING.remove(e.getUniqueId());
        if (task != null) task.cancel();
    }
}

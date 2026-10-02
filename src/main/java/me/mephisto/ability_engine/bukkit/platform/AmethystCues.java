package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** The Amethyst's cues: violet crystal, chimes and shattering glass. */
final class AmethystCues {

    private static final Particle.DustOptions VIOLET = new Particle.DustOptions(Color.fromRGB(170, 90, 230), 1.1f);
    private static final Particle.DustOptions PALE = new Particle.DustOptions(Color.fromRGB(220, 180, 255), 0.8f);
    private static final BlockData AMETHYST = Material.AMETHYST_BLOCK.createBlockData();

    /** Shardfall's rain: keep it in step with the ability's radius. */
    static final double RAIN_RADIUS = 4;

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- primary and Recall ----
        c.register("amethyst_shard_shot", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.6f));
        c.register("amethyst_shard_hit", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 10, 0.2, 0.2, 0.2, 0, AMETHYST);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 1.4f);
        });
        c.register("amethyst_shard_linger", loc -> loc.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.15, 0.15, 0.15, 0, PALE));
        c.register("amethyst_shard_fade", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.2, 0.2, 0.2, 0, VIOLET);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.4f, 1.8f);
        });
        c.register("amethyst_recall", loc -> {
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 15, 0.4, 0.6, 0.4, 0, VIOLET);
        });
        c.register("amethyst_recall_hit", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 12, 0.25, 0.3, 0.25, 0, AMETHYST);
            loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, 2, 0.2, 0.2, 0.2, 0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.1f);
        });
        c.register("amethyst_shard_caught", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f));

        // ---- 1: Shard Volley ----
        c.register("amethyst_gather", loc -> {
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0), 6, 0.3, 0.3, 0.3, 0, VIOLET);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        });
        c.register("amethyst_volley_shot", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.9f));
        c.register("amethyst_burst", loc -> { // the shard bursting behind someone
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc, 30, 0.8, 0.4, 0.8, 0, AMETHYST);
            w.spawnParticle(Particle.DUST, loc, 15, 0.8, 0.4, 0.8, 0, PALE);
            w.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.0f);
        });

        // ---- 2: Crystal Ward ----
        c.registerLoop("amethyst_ward", e -> { // crystal motes in front of her, where the barrier stands
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, 1.0f);
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location front = e.getLocation().add(e.getLocation().getDirection().setY(0).normalize().multiply(1.0)).add(0, 1, 0);
                e.getWorld().spawnParticle(Particle.DUST, front, 10, 0.6, 0.7, 0.6, 0, VIOLET);
            }, 0, 3);
            return (CueHandle) () -> {
                task.cancel();
                if (e.isValid()) e.getWorld().playSound(e.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.2f);
            };
        });
        c.register("amethyst_reflect", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 8, 0.2, 0.2, 0.2, 0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
        });

        // ---- 3: Shardfall ----
        c.register("amethyst_shardfall_cast", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.6f));
        c.register("amethyst_shardfall_impact", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc, 80, 1.5, 0.5, 1.5, 0, AMETHYST);
            w.spawnParticle(Particle.EXPLOSION, loc, 2, 0.5, 0.2, 0.5, 0);
            w.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
            w.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.6f);
        });
        c.register("amethyst_shardfall_rain", loc -> { // crystal bits falling all over the area, and its edge
            World w = loc.getWorld();
            for (int i = 0; i < 12; i++) {
                double a = Math.random() * Math.PI * 2, r = Math.sqrt(Math.random()) * RAIN_RADIUS;
                w.spawnParticle(Particle.FALLING_DUST, loc.getX() + Math.cos(a) * r, loc.getY() + 3, loc.getZ() + Math.sin(a) * r,
                        1, 0, 0, 0, 0, AMETHYST);
            }
            for (int i = 0; i < 28; i++) {
                double a = Math.PI * 2 * i / 28;
                w.spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * RAIN_RADIUS, loc.getY() + 0.1,
                        loc.getZ() + Math.sin(a) * RAIN_RADIUS, 1, 0, 0, 0, 0, VIOLET);
            }
            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.8f, 1.5f);
        });

        // ---- ultimate: Crystallize ----
        c.register("amethyst_choose", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.9f));
        c.register("amethyst_encase", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 40, 0.4, 0.8, 0.4, 0, AMETHYST);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1.2f, 0.7f);
        });
        c.registerLoop("amethyst_crystal", e -> geode(plugin, e)); // encased: an amethyst geode around them
        c.register("amethyst_shatter", loc -> {
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc, 120, 2, 0.8, 2, 0, AMETHYST);
            w.spawnParticle(Particle.DUST, loc, 40, 2, 0.8, 2, 0, VIOLET);
            w.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.8f);
            w.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.5f, 0.5f);
        });
    }

    /** A geode piece: a block display, {@code size} blocks wide on x/z and {@code height} tall, its bottom centre at {@code offset}. */
    private record Piece(Material block, float x, float y, float z, float size, float height, float tilt) {}

    /**
     * Encased: an amethyst geode around them, just big enough to hide them (by their size): a shell of amethyst with
     * clusters bristling out of it, a calcite rim at its foot. It follows them (they can't move anyway) until it ends.
     */
    private static CueHandle geode(Plugin plugin, org.bukkit.entity.Entity e) {
        var box = e.getBoundingBox();
        float w = (float) Math.max(0.8, box.getWidthX() * 1.6), h = (float) Math.max(1.0, box.getHeight() * 1.12);
        java.util.List<Piece> pieces = java.util.List.of(
                new Piece(Material.AMETHYST_BLOCK, 0, 0, 0, w, h, 0),                                    // the shell
                new Piece(Material.CALCITE, 0, -0.02f, 0, w + 0.2f, 0.25f, 0),                          // its rim
                new Piece(Material.AMETHYST_CLUSTER, w * 0.42f, h * 0.55f, 0, w * 0.45f, w * 0.45f, -0.6f),
                new Piece(Material.AMETHYST_CLUSTER, -w * 0.42f, h * 0.35f, w * 0.1f, w * 0.4f, w * 0.4f, 0.6f),
                new Piece(Material.AMETHYST_CLUSTER, 0, h * 0.97f, 0, w * 0.5f, w * 0.5f, 0),
                new Piece(Material.LARGE_AMETHYST_BUD, w * 0.1f, h * 0.7f, -w * 0.45f, w * 0.35f, w * 0.35f, 0.5f));
        Location at = e.getLocation();
        at.setPitch(0);
        at.setYaw(0);
        java.util.List<org.bukkit.entity.BlockDisplay> shown = new java.util.ArrayList<>();
        for (Piece piece : pieces) {
            shown.add(at.getWorld().spawn(at, org.bukkit.entity.BlockDisplay.class, d -> {
                d.setBlock(piece.block().createBlockData());
                d.setPersistent(false);
                d.setTeleportDuration(2);
                VisualEntities.mark(d);
                // centred on x/z at the offset, bottom at its y, tipped outward by tilt (radians, about z)
                d.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(piece.x() - piece.size() / 2, piece.y(), piece.z() - piece.size() / 2),
                        new org.joml.Quaternionf().rotateZ(piece.tilt()),
                        new org.joml.Vector3f(piece.size(), piece.height(), piece.size()), new org.joml.Quaternionf()));
            }));
        }
        e.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, h / 2, 0), 40, w / 3, h / 3, w / 3, 0, AMETHYST);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid()) return;
            Location now = e.getLocation();
            now.setPitch(0);
            now.setYaw(0);
            for (var d : shown) if (d.isValid()) d.teleport(now);
            if (Math.random() < 0.3) e.getWorld().spawnParticle(Particle.DUST, now.clone().add(0, h, 0), 2, w / 3, 0.2, w / 3, 0, PALE);
        }, 2, 2);
        return () -> {
            task.cancel();
            for (var d : shown) d.remove();
        };
    }

    private AmethystCues() {}
}

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

    /** The Shard Volley shards circling each caster right now, oldest first (each new one takes the next place round). */
    private static final java.util.Map<java.util.UUID, java.util.Deque<Orbiter>> gathered = new java.util.HashMap<>();

    /** Crystal Ward's shards: how far out they circle (the barrier itself reaches 1.6), and how big they are. */
    static final double WARD_RADIUS = 1.0;
    static final float WARD_SHARD_SIZE = 1.3f;

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
        // A shell of 4 large shards circling her, slightly tilted, where the barrier stands
        c.registerLoop("amethyst_ward", e -> {
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, 1.0f);
            java.util.List<CueHandle> shards = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                shards.add(orbiter(plugin, e, i * Math.PI / 2, WARD_RADIUS, 0.5, WARD_SHARD_SIZE, 0.35f, 0.18, true));
            }
            return () -> {
                shards.forEach(CueHandle::stop);
                if (e.isValid()) e.getWorld().playSound(e.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.2f);
            };
        });
        // Shard Volley: one more shard circling her for every one gathered (spread evenly around her, up to 6)
        c.registerLoop("amethyst_orbit", e -> {
            var mine = gathered.computeIfAbsent(e.getUniqueId(), k -> new java.util.ArrayDeque<>());
            Orbiter shard = orbiter(plugin, e, mine.size() * Math.PI * 2 / 6, 0.9, 1.1, 0.55f, 0f, 0.22, false);
            mine.addLast(shard);
            return () -> { // the channel's over: whatever wasn't fired goes
                shard.stop();
                mine.remove(shard);
                if (mine.isEmpty()) gathered.remove(e.getUniqueId(), mine);
            };
        });
        // Each shot of the volley: the oldest circling shard is the one that flies (it leaves its place in a flash)
        c.register("amethyst_orbit_fire", loc -> {
            java.util.UUID owner = null;
            double best = 4;
            for (var id : gathered.keySet()) { // whose shards: the caster this cue plays on (the nearest one with some)
                org.bukkit.entity.Entity who = Bukkit.getEntity(id);
                if (who == null || !who.getWorld().equals(loc.getWorld())) continue;
                double d = who.getBoundingBox().getCenter().toLocation(loc.getWorld()).distance(loc);
                if (d < best) {
                    best = d;
                    owner = id;
                }
            }
            var mine = owner == null ? null : gathered.get(owner);
            Orbiter shard = mine == null ? null : mine.pollFirst();
            if (shard == null) return;
            Location at = shard.where();
            if (at != null) at.getWorld().spawnParticle(Particle.DUST, at, 6, 0.1, 0.1, 0.1, 0, PALE);
            shard.stop();
        });
        // ---- 2: Gem Rush ----
        c.register("amethyst_gem_rush", loc -> { // she bursts away in a spray of gems
            World w = loc.getWorld();
            w.spawnParticle(Particle.BLOCK, loc, 25, 0.4, 0.5, 0.4, 0.15, Material.AMETHYST_BLOCK.createBlockData());
            w.spawnParticle(Particle.DUST, loc, 15, 0.4, 0.5, 0.4, 0, VIOLET);
            w.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.4f);
            w.playSound(loc, Sound.ENTITY_BREEZE_JUMP, 0.7f, 1.4f);
        });
        c.registerLoop("amethyst_gem_trail", e -> { // gems glinting behind her while she dashes
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation().add(0, 1, 0);
                at.getWorld().spawnParticle(Particle.DUST, at, 4, 0.25, 0.4, 0.25, 0, PALE);
                at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.25, 0.4, 0.25, 0, VIOLET);
                at.getWorld().spawnParticle(Particle.END_ROD, at, 1, 0.2, 0.3, 0.2, 0.01);
            }, 0, 1);
            return (CueHandle) task::cancel;
        });

        // ---- 2: Crystal Volley ----
        // Charging: the volley's shards circling her, evenly round (one loop per count: a hit taken adds one)
        for (int n = 1; n <= 10; n++) {
            int count = n;
            c.registerLoop("amethyst_volley_ring_" + n, e -> {
                java.util.List<CueHandle> ring = new java.util.ArrayList<>();
                for (int i = 0; i < count; i++) {
                    ring.add(orbiter(plugin, e, i * Math.PI * 2 / count, 1.0, 1.1, 0.5f, 0f, 0.2, false));
                }
                return () -> ring.forEach(CueHandle::stop);
            });
        }

        // ---- ultimate: Prismatic Burst ----
        c.registerLoop("amethyst_prism_charge", e -> { // the prism gathering light in front of her
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!e.isValid()) return;
                Location at = e.getLocation().add(0, e.getHeight() * 0.75, 0);
                at.add(at.getDirection().multiply(0.9));
                at.getWorld().spawnParticle(Particle.DUST, at, 4, 0.15, 0.15, 0.15, 0, VIOLET);
                at.getWorld().spawnParticle(Particle.END_ROD, at, 1, 0.1, 0.1, 0.1, 0.01);
            }, 0, 2);
            return (CueHandle) task::cancel;
        });
        c.register("amethyst_prism_shot", loc -> {
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.2f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.2f);
        });

        c.register("amethyst_reflect", loc -> {
            loc.getWorld().spawnParticle(Particle.END_ROD, loc, 8, 0.2, 0.2, 0.2, 0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
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

    /** A shard circling someone: where it is right now, and how to take it away. */
    private interface Orbiter extends CueHandle {
        Location where();
    }

    /**
     * An amethyst shard circling {@code e}: starting at angle {@code start}, {@code radius} blocks out, {@code height}
     * above their feet, {@code size} big, standing upright but tipped {@code tilt} radians, going round {@code speed}
     * radians a tick (bobbing a little). {@code faceThem}: its face turned to them (else to whoever looks). Until stopped.
     */
    private static Orbiter orbiter(Plugin plugin, org.bukkit.entity.Entity e, double start, double radius, double height,
                                   float size, float tilt, double speed, boolean faceThem) {
        Location at = e.getLocation();
        org.bukkit.entity.ItemDisplay d = at.getWorld().spawn(at, org.bukkit.entity.ItemDisplay.class, x -> {
            x.setItemStack(new org.bukkit.inventory.ItemStack(Material.AMETHYST_SHARD));
            x.setPersistent(false);
            x.setTeleportDuration(1);
            x.setBillboard(faceThem ? org.bukkit.entity.Display.Billboard.FIXED : org.bukkit.entity.Display.Billboard.VERTICAL);
            VisualEntities.mark(x);
            x.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                    new org.joml.Quaternionf().rotateZ((float) (-Math.PI / 4) + tilt),
                    new org.joml.Vector3f(size, size, size), new org.joml.Quaternionf()));
        });
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid() || !d.isValid()) return;
            double a = start + tick[0] * speed;
            Location c = e.getLocation();
            c.add(Math.cos(a) * radius, height * e.getHeight() / 1.8 + Math.sin(tick[0] * 0.15 + start) * 0.06,
                    Math.sin(a) * radius);
            // facing them: looking back at their middle (its flat face toward them); otherwise the billboard turns it
            if (faceThem) c.setDirection(new org.bukkit.util.Vector(-Math.cos(a), 0, -Math.sin(a)));
            d.teleport(c);
            tick[0]++;
        }, 0, 1);
        return new Orbiter() {
            @Override
            public Location where() { return d.isValid() ? d.getLocation() : null; }

            @Override
            public void stop() {
                task.cancel();
                if (d.isValid()) d.remove();
            }
        };
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

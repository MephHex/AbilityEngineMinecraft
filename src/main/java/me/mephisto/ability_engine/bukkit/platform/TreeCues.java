package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The Sylvan's cues. Each form has a looping cue (its status's {@code cue}): for now a block "crown" on her head
 * (a sapling, an azalea, a big crown of leaves) and the trees' domain ring. That's where a model (BetterModel,
 * ModelEngine) can go later: register a loop under the same id that plays it, and the content doesn't change.
 */
final class TreeCues {

    /** The domains' radius: keep these in step with far_damage_taken.beyond in sylvan.yml. */
    static final double TREE_DOMAIN = 8;
    static final double LARGE_DOMAIN = 14;
    /** How far a Windfall fruit reaches (its trigger and burst): keep it in step with sylvan.yml. */
    static final double FRUIT_REACH = 3;
    private static final Particle.DustOptions FRUIT_RING = new Particle.DustOptions(Color.fromRGB(230, 60, 60), 1.0f);

    private static final Particle.DustOptions LEAF = new Particle.DustOptions(Color.fromRGB(70, 160, 50), 1.2f);
    private static final Particle.DustOptions LEAF_BRIGHT = new Particle.DustOptions(Color.fromRGB(120, 220, 80), 1.6f);
    private static final Particle.DustOptions SAP = new Particle.DustOptions(Color.fromRGB(230, 150, 30), 1.2f);
    private static final Particle.DustOptions BARK = new Particle.DustOptions(Color.fromRGB(100, 70, 40), 1.3f);

    static void register(BukkitCuePlayer c, Plugin plugin) {
        // ---- forms (looping, on her while she's in that form) ----
        c.registerLoop("sylvan_buried", e -> { // dirt stirring where she's buried
            e.getWorld().playSound(e.getLocation(), Sound.BLOCK_ROOTED_DIRT_PLACE, 1f, 0.8f);
            return every(plugin, 4, () -> {
                Location at = e.getLocation();
                e.getWorld().spawnParticle(Particle.BLOCK, at, 4, 0.3, 0.05, 0.3, 0, Material.ROOTED_DIRT.createBlockData());
            });
        });
        c.registerLoop("sylvan_sapling", e -> {
            Location at = e.getLocation();
            e.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.5, 0), 20, 0.4, 0.4, 0.4, 0);
            e.getWorld().playSound(at, Sound.BLOCK_AZALEA_PLACE, 1f, 1.4f);
            return crown(plugin, e, Material.OAK_SAPLING, 0.7f, -0.1, null, 0);
        });
        c.registerLoop("sylvan_tree", e -> {
            Location at = e.getLocation();
            e.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0);
            e.getWorld().playSound(at, Sound.BLOCK_AZALEA_LEAVES_PLACE, 1.2f, 0.8f);
            return crown(plugin, e, Material.FLOWERING_AZALEA_LEAVES, 1.2f, -0.3, LEAF, TREE_DOMAIN);
        });
        c.registerLoop("sylvan_large_tree", e -> {
            Location at = e.getLocation();
            e.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1.5, 0), 80, 1.2, 1.2, 1.2, 0);
            e.getWorld().playSound(at, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.5f);
            e.getWorld().playSound(at, Sound.BLOCK_AZALEA_LEAVES_PLACE, 1.5f, 0.5f);
            return crown(plugin, e, Material.OAK_LEAVES, 2.6f, -0.6, LEAF_BRIGHT, LARGE_DOMAIN);
        });

        // ---- seed ----
        c.register("sylvan_seed_shot", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_CHICKEN_EGG, 0.6f, 1.6f));
        c.register("sylvan_seed_hit", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 8, 0.15, 0.15, 0.15, 0, Material.HAY_BLOCK.createBlockData());
            loc.getWorld().playSound(loc, Sound.BLOCK_CROP_BREAK, 0.8f, 1.4f);
        });
        c.register("sylvan_blink", loc -> {
            loc.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 0.4, 0), 15, 0.2, 0.3, 0.2, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.4, 0), 10, 0.2, 0.3, 0.2, 0, LEAF);
            loc.getWorld().playSound(loc, Sound.ENTITY_ALLAY_ITEM_THROWN, 1f, 1.8f);
        });
        c.register("sylvan_burrow", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 30, 0.4, 0.1, 0.4, 0, Material.DIRT.createBlockData());
            loc.getWorld().playSound(loc, Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 1f);
            loc.getWorld().playSound(loc, Sound.ITEM_HOE_TILL, 1f, 0.8f);
        });
        // ---- sapling ----
        c.register("sylvan_uproot", loc -> { // out of the ground, a seed again
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 30, 0.4, 0.2, 0.4, 0, Material.DIRT.createBlockData());
            loc.getWorld().playSound(loc, Sound.BLOCK_ROOTED_DIRT_BREAK, 1f, 1.4f);
        });
        c.register("sylvan_thorn_shot", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 0.8f, 1.6f));
        c.register("sylvan_thorn_hit", loc -> {
            loc.getWorld().spawnParticle(Particle.CRIT, loc, 8, 0.15, 0.15, 0.15, 0.1);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.15, 0.15, 0.15, 0, LEAF);
            loc.getWorld().playSound(loc, Sound.ENCHANT_THORNS_HIT, 0.8f, 1.2f);
        });
        c.register("sylvan_root_shot", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_HANGING_ROOTS_PLACE, 1f, 0.8f));
        c.register("sylvan_rooted", loc -> { // roots gripping someone's feet
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10;
                loc.getWorld().spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * 0.5, loc.getY() - 0.7,
                        loc.getZ() + Math.sin(a) * 0.5, 2, 0.05, 0.2, 0.05, 0, BARK);
            }
            loc.getWorld().playSound(loc, Sound.BLOCK_HANGING_ROOTS_BREAK, 1f, 0.7f);
        });
        c.register("sylvan_scatter", loc -> { // a 3.5-block burst that flings everyone out
            for (int i = 0; i < 40; i++) {
                double a = Math.PI * 2 * i / 40;
                loc.getWorld().spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * 3.5, loc.getY() + 0.15,
                        loc.getZ() + Math.sin(a) * 3.5, 1, 0, 0, 0, 0, LEAF_BRIGHT);
            }
            loc.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 0.5, 0), 40, 1.8, 0.5, 1.8, 0.05);
            loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.3, 0), 20, 1.5, 0.2, 1.5, 0.05);
            loc.getWorld().playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.8f);
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
        });
        // ---- tree ----
        c.register("sylvan_sap_shot", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_SLIDE, 0.8f, 1.4f));
        c.register("sylvan_sap_hit", loc -> {
            loc.getWorld().spawnParticle(Particle.DRIPPING_HONEY, loc, 10, 0.3, 0.3, 0.3, 0);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 8, 0.25, 0.25, 0.25, 0, SAP);
            loc.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_BREAK, 0.8f, 1.2f);
        });
        c.register("sylvan_fruit_fall", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.2f, 0.7f));
        c.register("sylvan_fruit_bomb", loc -> {
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 0.5, 0.3, 0.5, 0);
            loc.getWorld().spawnParticle(Particle.ITEM, loc.clone().add(0, 0.5, 0), 30, 1.2, 0.5, 1.2, 0.15,
                    new org.bukkit.inventory.ItemStack(Material.APPLE));
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.4, 0), 25, 1.4, 0.4, 1.4, 0, LEAF_BRIGHT);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
            loc.getWorld().playSound(loc, Sound.BLOCK_SLIME_BLOCK_BREAK, 1f, 0.8f);
        });
        c.register("sylvan_lash_throw", loc -> loc.getWorld().playSound(loc, Sound.ENTITY_FISHING_BOBBER_THROW, 1f, 0.6f));
        c.registerLine("sylvan_root_line", (w, from, to) -> { // the root holding them, redrawn every other tick
            Vector d = to.clone().subtract(from);
            double len = d.length();
            if (len < 0.1) return;
            for (double k = 0; k <= len; k += 0.3) {
                Vector q = from.clone().add(d.clone().multiply(k / len));
                double sag = Math.sin(Math.PI * k / len) * 0.4; // hangs a little
                w.spawnParticle(Particle.DUST, q.getX(), q.getY() - sag, q.getZ(), 1, 0.02, 0.02, 0.02, 0, BARK);
            }
        });
        c.register("sylvan_drag", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 15, 0.3, 0.3, 0.3, 0, Material.ROOTED_DIRT.createBlockData());
            loc.getWorld().playSound(loc, Sound.ITEM_LEAD_BREAK, 1f, 0.7f);
            loc.getWorld().playSound(loc, Sound.BLOCK_ROOTS_BREAK, 1f, 0.6f);
        });
        c.register("sylvan_strength_sap", loc -> { // sap welling up across the domain (8 blocks)
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 60; i++) {
                double a = rng.nextDouble(Math.PI * 2), r = Math.sqrt(rng.nextDouble()) * TREE_DOMAIN;
                loc.getWorld().spawnParticle(Particle.DRIPPING_HONEY, loc.getX() + Math.cos(a) * r, loc.getY() + 1.2,
                        loc.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                if (i % 3 == 0) loc.getWorld().spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * r, loc.getY() + 0.2,
                        loc.getZ() + Math.sin(a) * r, 1, 0, 0.1, 0, 0, SAP);
            }
            loc.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_PLACE, 1.2f, 0.8f);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
        });
        c.register("sylvan_vigor", loc -> loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.3, 0.5, 0.3, 0, SAP));
        // ---- large tree ----
        c.register("sylvan_fruit_drop", loc -> loc.getWorld().playSound(loc, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1f, 0.9f));
        c.register("sylvan_fruit_idle", loc -> { // a ripe fruit lying there, its reach on the ground (every 0.5s)
            if (Math.random() < 0.5) loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 0.4, 0), 1, 0.2, 0.1, 0.2, 0);
            ring(loc.clone().subtract(0, 0.2, 0), FRUIT_REACH, FRUIT_RING);
        });
        c.register("sylvan_fruit_burst", loc -> {
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 30, 0.8, 0.4, 0.8, 0, Material.MELON.createBlockData());
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
            loc.getWorld().playSound(loc, Sound.BLOCK_WOOD_BREAK, 1f, 0.6f);
        });
        c.register("sylvan_fruit_eaten", loc -> {
            loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EAT, 1f, 1.2f);
        });
        c.register("sylvan_fruit_compost", loc -> {
            loc.getWorld().spawnParticle(Particle.COMPOSTER, loc, 12, 0.3, 0.2, 0.3, 0);
            loc.getWorld().playSound(loc, Sound.BLOCK_COMPOSTER_FILL_SUCCESS, 1f, 1f);
        });
        c.register("sylvan_fruit_smashed", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 20, 0.3, 0.2, 0.3, 0, Material.MELON.createBlockData());
            loc.getWorld().playSound(loc, Sound.BLOCK_WOOD_BREAK, 1f, 1.2f);
        });
        c.register("sylvan_deep_roots", loc -> { // roots bursting up across the whole domain (14 blocks)
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 120; i++) {
                double a = rng.nextDouble(Math.PI * 2), r = Math.sqrt(rng.nextDouble()) * LARGE_DOMAIN;
                loc.getWorld().spawnParticle(Particle.BLOCK, loc.getX() + Math.cos(a) * r, loc.getY() + 0.2,
                        loc.getZ() + Math.sin(a) * r, 2, 0.1, 0.3, 0.1, 0, Material.ROOTED_DIRT.createBlockData());
            }
            loc.getWorld().playSound(loc, Sound.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.5f);
            loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_DIG, 1f, 1.2f);
        });
        c.register("sylvan_screech", loc -> {
            for (double r = 2; r <= LARGE_DOMAIN; r += 3) { // sparse rings of booms rolling out over the domain
                int n = (int) (r * 1.5);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    loc.getWorld().spawnParticle(Particle.SONIC_BOOM, loc.getX() + Math.cos(a) * r, loc.getY() + 1,
                            loc.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            loc.getWorld().playSound(loc, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 2f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.8f);
        });
        c.register("sylvan_walk", loc -> { // tearing its roots out of the ground
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 60, 1.2, 0.3, 1.2, 0, Material.ROOTED_DIRT.createBlockData());
            loc.getWorld().playSound(loc, Sound.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.4f);
            loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_STEP, 2f, 0.5f);
        });
        c.register("sylvan_drain", loc -> { // every 0.5s while it walks: life pulled in from around it
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 25; i++) {
                double a = rng.nextDouble(Math.PI * 2), r = rng.nextDouble(2, LARGE_DOMAIN);
                loc.getWorld().spawnParticle(Particle.DUST, loc.getX() + Math.cos(a) * r, loc.getY() + 0.3,
                        loc.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, LEAF_BRIGHT);
            }
            if (rng.nextDouble() < 0.5) loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_STEP, 1f, 0.6f);
        });
        c.register("sylvan_take_root", loc -> {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 60, 1.2, 0.2, 1.2, 0, Material.ROOTED_DIRT.createBlockData());
            loc.getWorld().playSound(loc, Sound.BLOCK_ROOTED_DIRT_PLACE, 2f, 0.5f);
        });
    }

    private static CueHandle every(Plugin plugin, int period, Runnable r) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, r, 0, period);
        return task::cancel;
    }

    /**
     * A block shown on top of her head (her form, until there are models), following her every tick; with a
     * {@code domain} above 0, its ring drawn on the ground around her twice a second, for everyone.
     */
    private static CueHandle crown(Plugin plugin, Entity e, Material block, float size, double sink,
                                   Particle.DustOptions ring, double domain) {
        BlockDisplay crown = e.getWorld().spawn(top(e, sink), BlockDisplay.class, d -> {
            d.setBlock(block.createBlockData());
            d.setPersistent(false);
            d.setTeleportDuration(1); // glides along with her
            d.setTransformation(new Transformation(new Vector3f(-size / 2, 0, -size / 2), new Quaternionf(),
                    new Vector3f(size, size, size), new Quaternionf()));
            VisualEntities.mark(d);
        });
        int[] tick = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!e.isValid() || !crown.isValid()) return;
            crown.teleport(top(e, sink));
            if (domain > 0 && tick[0] % 10 == 0) ring(e.getLocation(), domain, ring);
            tick[0]++;
        }, 1, 1);
        return () -> {
            task.cancel();
            if (crown.isValid()) crown.remove();
        };
    }

    /** Just above her head ({@code sink} lower: a crown sits down over it a bit). */
    private static Location top(Entity e, double sink) {
        Location at = e.getLocation();
        return new Location(at.getWorld(), at.getX(), at.getY() + e.getHeight() + sink, at.getZ());
    }

    /** A ring on the ground around {@code at}: her domain's edge. */
    private static void ring(Location at, double radius, Particle.DustOptions dust) {
        World w = at.getWorld();
        int n = (int) (radius * 7);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * radius, at.getY() + 0.15, at.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0, dust);
        }
    }

    private TreeCues() {}
}

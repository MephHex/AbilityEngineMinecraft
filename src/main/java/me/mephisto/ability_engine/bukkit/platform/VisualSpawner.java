package me.mephisto.ability_engine.bukkit.platform;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Spawns what a {@code visual:} string describes, for projectiles and constructs alike:
 * an item Material on an ItemDisplay (scaled by size), or "entity:<EntityType>" (a real, frozen,
 * protected entity). Entities are shifted so their middle sits on the given point.
 */
final class VisualSpawner {

    private static final String ENTITY_PREFIX = "entity:";
    private static final String BLOCK_PREFIX = "block:";

    record Spawned(Entity entity, double yOffset) {}

    static Spawned spawn(Location loc, String visual, float size, Logger log) {
        if (visual != null && visual.regionMatches(true, 0, ENTITY_PREFIX, 0, ENTITY_PREFIX.length())) {
            Spawned s = entity(loc, visual.substring(ENTITY_PREFIX.length()), log);
            if (s != null) return s;
            visual = null;
        }
        if (visual != null && visual.regionMatches(true, 0, BLOCK_PREFIX, 0, BLOCK_PREFIX.length())) {
            Spawned s = block(loc, visual.substring(BLOCK_PREFIX.length()), size, log);
            if (s != null) return s;
            visual = null;
        }
        return item(loc, visual, size);
    }

    /**
     * A real block model (e.g. a closed trapdoor, which is already flat). Block displays grow from their
     * corner, so it's shifted to be centred on the point horizontally, with its BOTTOM on the point.
     */
    private static Spawned block(Location loc, String name, float s, Logger log) {
        Material material = Material.matchMaterial(name.trim());
        if (material == null || !material.isBlock()) {
            log.warning("Unknown visual block '" + name + "' (use a block Material, e.g. EXPOSED_COPPER_TRAPDOOR)");
            return null;
        }
        BlockData data = material.createBlockData(); // defaults: a trapdoor is closed and bottom-half = flat
        BlockDisplay display = loc.getWorld().spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setPersistent(false);          // never saved to disk: no orphans after a crash
            d.setInterpolationDuration(0);
            d.setTeleportDuration(0);
            d.setTransformation(new Transformation(new Vector3f(-s / 2, 0, -s / 2), new Quaternionf(),
                    new Vector3f(s, s, s), new Quaternionf()));
            VisualEntities.mark(d);
        });
        return new Spawned(display, 0);
    }

    private static Spawned item(Location loc, String visual, float s) {
        Material material = visual == null ? null : Material.matchMaterial(visual);
        ItemStack item = new ItemStack(material != null && material.isItem() ? material : Material.DIAMOND_BLOCK);
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setPersistent(false);          // never saved to disk: no orphans after a crash
            d.setInterpolationDuration(0);
            d.setTeleportDuration(0);        // crisp position: redirects and bounces snap instantly
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            VisualEntities.mark(d);
        });
        return new Spawned(display, 0);
    }

    private static Spawned entity(Location loc, String typeName, Logger log) {
        EntityType type;
        try {
            type = EntityType.valueOf(typeName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Unknown visual entity '" + typeName + "' (use Bukkit EntityType names, e.g. END_CRYSTAL)");
            return null;
        }
        Class<? extends Entity> cls = type.getEntityClass();
        if (cls == null || !type.isSpawnable()) {
            log.warning("Visual entity '" + typeName + "' can't be spawned");
            return null;
        }
        Entity entity = loc.getWorld().spawn(loc, cls, VisualSpawner::freeze);
        // Entities are positioned by their feet; shift down so the MIDDLE of the entity sits on the point.
        double halfHeight = entity.getBoundingBox().getHeight() / 2;
        entity.teleport(loc.clone().subtract(0, halfHeight, 0));
        return new Spawned(entity, halfHeight);
    }

    private static void freeze(Entity e) {
        VisualEntities.mark(e);
        e.setPersistent(false);
        e.setGravity(false);
        e.setSilent(true);
        e.setInvulnerable(true);
        if (e instanceof EnderCrystal crystal) {
            crystal.setShowingBottom(false); // no bedrock base plate
            crystal.setBeamTarget(null);
        }
        if (e instanceof LivingEntity living) living.setCollidable(false);
        if (e instanceof Mob mob) mob.setAI(false);
    }

    private VisualSpawner() {}
}

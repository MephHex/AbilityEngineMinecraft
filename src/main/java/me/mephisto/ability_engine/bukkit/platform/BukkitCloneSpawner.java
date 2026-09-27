package me.mephisto.ability_engine.bukkit.platform;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.CloneSpawner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Clones are Mannequins (Minecraft 1.21.9+) wearing the owner's skin and holding their weapon. Marked
 * as ability visuals, so abilities can't target them, and invulnerable, never saved, silent.
 */
public final class BukkitCloneSpawner implements CloneSpawner {

    @Override
    public Optional<UUID> spawnClone(UUID of, String worldName, Vec3 center, Vec3 facing) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return Optional.empty();
        Entity owner = Bukkit.getEntity(of);
        double halfHeight = owner != null ? owner.getBoundingBox().getHeight() / 2 : 0.9;
        Location feet = new Location(world, center.x(), center.y() - halfHeight, center.z());
        if (facing != null && !facing.isZero()) {
            feet.setDirection(Convert.bukkit(facing)); // e.g. toward its owner
        } else if (owner != null) {
            feet.setYaw(owner.getLocation().getYaw());
            feet.setPitch(owner.getLocation().getPitch());
        }
        Mannequin clone = world.spawn(feet, Mannequin.class, m -> {
            if (owner instanceof Player player) m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            if (owner instanceof LivingEntity living && living.getEquipment() != null && m.getEquipment() != null) {
                m.getEquipment().setItemInMainHand(living.getEquipment().getItemInMainHand().clone());
            }
            m.setInvulnerable(true);
            m.setPersistent(false);
            m.setSilent(true);
            m.setCollidable(false);
            VisualEntities.mark(m);
        });
        return Optional.of(clone.getUniqueId());
    }

    @Override
    public void despawn(UUID clone) {
        Entity e = Bukkit.getEntity(clone);
        if (e != null) e.remove();
    }
}

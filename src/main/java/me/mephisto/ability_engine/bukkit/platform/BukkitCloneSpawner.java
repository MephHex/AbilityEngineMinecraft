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

    /** Design HP per Minecraft health point (config damage-scale), for vulnerable clones' health. */
    private java.util.function.DoubleSupplier damageScale = () -> 10;

    public void setDamageScale(java.util.function.DoubleSupplier damageScale) { this.damageScale = damageScale; }

    @Override
    public Optional<UUID> spawnClone(UUID of, String worldName, Vec3 center, Vec3 facing) {
        return spawnClone(of, worldName, center, facing, Options.DECOY);
    }

    /**
     * Vulnerable clones (e.g. a torn-out soul) are real targets: not invulnerable, not an ability visual,
     * with their own health, optionally glowing, on the team of whoever they copy. They drop nothing.
     */
    @Override
    public Optional<UUID> spawnClone(UUID of, String worldName, Vec3 center, Vec3 facing, Options options) {
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
            m.setPersistent(false);
            m.setCollidable(false);
            if (!options.vulnerable()) {
                m.setInvulnerable(true);
                m.setSilent(true);
                VisualEntities.mark(m);
                return;
            }
            double health = Math.max(1, options.health() / damageScale.getAsDouble());
            var maxHealth = m.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxHealth != null) maxHealth.setBaseValue(health);
            m.setHealth(health);
            m.setGlowing(options.glowing());
            if (m.getEquipment() != null) m.getEquipment().setItemInMainHandDropChance(0f);
            if (options.teamOf() != null) {
                Entity copied = Bukkit.getEntity(options.teamOf());
                org.bukkit.scoreboard.Team team = copied == null ? null
                        : Bukkit.getScoreboardManager().getMainScoreboard().getEntityTeam(copied);
                if (team != null) team.addEntity(m);
            }
        });
        return Optional.of(clone.getUniqueId());
    }

    @Override
    public void despawn(UUID clone) {
        Entity e = Bukkit.getEntity(clone);
        if (e != null) e.remove();
    }
}

package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.ProjectileRenderer;
import me.mephisto.ability_engine.engine.platform.ProjectileVisual;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.logging.Logger;

/**
 * Draws projectiles. {@code visual:} in YAML is an item Material or {@code "entity:<EntityType>"}
 * (see VisualSpawner). Purely cosmetic: collision is the engine's ray sweep.
 */
public final class BukkitProjectileRenderer implements ProjectileRenderer {

    private final Logger log;

    public BukkitProjectileRenderer(Logger log) {
        this.log = log;
    }

    @Override
    public ProjectileVisual spawn(String world, Vec3 position, ProjectileSpec spec) {
        Location loc = Convert.location(world, position);
        if (loc == null) return NONE;
        VisualSpawner.Spawned s = VisualSpawner.spawn(loc, spec.visual(), (float) spec.size(), log);
        Entity entity = s.entity();
        double yOffset = s.yOffset();
        return new ProjectileVisual() {
            @Override
            public void moveTo(Vec3 p) {
                if (entity.isValid()) entity.teleport(new Location(entity.getWorld(), p.x(), p.y() - yOffset, p.z()));
            }

            @Override
            public void remove() {
                if (entity.isValid()) entity.remove();
            }
        };
    }

    private static final ProjectileVisual NONE = new ProjectileVisual() {
        @Override public void moveTo(Vec3 position) {}
        @Override public void remove() {}
    };
}

package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;

/** Port for drawing projectiles. Purely cosmetic: collision is done by the engine via WorldQuery. */
public interface ProjectileRenderer {
    ProjectileVisual spawn(String world, Vec3 position, ProjectileSpec spec);

    /**
     * @param velocity its launch velocity (blocks per tick), for visuals that fly along it (arrows)
     * @param tint     "#RRGGBB" to color it (e.g. an infused bolt), or null. Visuals may ignore it.
     */
    default ProjectileVisual spawn(String world, Vec3 position, Vec3 velocity, ProjectileSpec spec, String tint) {
        return spawn(world, position, spec);
    }
}

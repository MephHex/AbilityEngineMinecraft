package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;

/** Port for drawing projectiles. Purely cosmetic: collision is done by the engine via WorldQuery. */
public interface ProjectileRenderer {
    ProjectileVisual spawn(String world, Vec3 position, ProjectileSpec spec);
}

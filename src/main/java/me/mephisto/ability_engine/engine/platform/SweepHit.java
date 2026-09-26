package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.Target;

/**
 * First thing a swept ray touched.
 *
 * @param target   EntityTarget for an entity hit, PointTarget for a block hit
 * @param position exact impact position
 * @param normal   surface normal for block hits, null for entity hits
 */
public record SweepHit(Target target, Vec3 position, Vec3 normal) {
    public boolean isBlock() { return normal != null; }
}

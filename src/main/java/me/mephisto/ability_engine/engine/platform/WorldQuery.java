package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Port for reading the game world. The engine does geometry (cones, sorting, homing);
 * the platform only answers raw questions.
 */
public interface WorldQuery {

    Optional<Aim> aimOf(UUID entity);

    /** Entity -> hitbox center. Point -> itself. Empty if the entity is gone. */
    Optional<PointTarget> positionOf(Target target);

    /** Living, targetable entities whose hitbox center is roughly within {@code radius}. */
    List<EntitySnapshot> livingEntitiesNear(PointTarget center, double radius);

    /**
     * Cast a ray of thickness {@code radius} from {@code from} to {@code to}, returning whichever
     * of (block, living entity) is hit first. Entities for which {@code passThrough} is true are
     * never hit (the caster and their allies; or every entity, for a blocks-only ray).
     */
    Optional<SweepHit> sweep(String world, Vec3 from, Vec3 to, double radius, Predicate<UUID> passThrough);

    /** The entity's team, if it has one (on Bukkit: the main scoreboard). */
    Optional<String> teamOf(UUID entity);

    boolean isLoaded(String world, Vec3 position);

    /**
     * The standing position on the first solid surface at or below {@code from}, at most
     * {@code maxDrop} blocks down. Empty if there's nothing to stand on in that range, or if
     * {@code from} is inside a solid block.
     */
    Optional<Vec3> groundBelow(String world, Vec3 from, double maxDrop);

    boolean isAlive(UUID entity);
}

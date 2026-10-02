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

    /** Max health in design HP (like damage), for entities without a character. Empty = unknown. */
    default java.util.OptionalDouble maxHealth(UUID entity) { return java.util.OptionalDouble.empty(); }

    /** Current health as a share of max health (0..1), e.g. "below 40%". Empty = unknown. */
    default java.util.OptionalDouble healthFraction(UUID entity) { return java.util.OptionalDouble.empty(); }

    /** A player (not a mob, a summon or a projectile's body). Default: unknown (false). */
    default boolean isPlayer(UUID entity) { return false; }

    /**
     * Which way the entity is moving right now, horizontally (walking, strafing, backpedalling),
     * as a unit vector. Empty when standing still. Default: unknown (always empty).
     */
    default Optional<Vec3> movementOf(UUID entity) { return Optional.empty(); }
}

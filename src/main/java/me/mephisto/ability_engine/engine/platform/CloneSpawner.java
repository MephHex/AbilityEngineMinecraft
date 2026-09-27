package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.Optional;
import java.util.UUID;

/** Port for look-alike stand-ins of an entity (e.g. a mannequin wearing a player's skin). */
public interface CloneSpawner {

    /** Spawn a look-alike of {@code of}, its centre at {@code center}, looking where {@code of} looks. */
    default Optional<UUID> spawnClone(UUID of, String world, Vec3 center) {
        return spawnClone(of, world, center, null);
    }

    /**
     * Spawn a look-alike of {@code of}, its centre at {@code center}. Untargetable by abilities.
     *
     * @param facing the (horizontal) way it looks; null = the way {@code of} looks
     */
    Optional<UUID> spawnClone(UUID of, String world, Vec3 center, Vec3 facing);

    /**
     * How a clone behaves.
     *
     * @param health  0: a decoy, invulnerable and untargetable (a Dream Echo). Above 0: it can be hit and
     *                killed, with this much health (design HP, like damage), e.g. a torn-out soul
     * @param glowing outlined through walls
     * @param teamOf  put it on this entity's team (null = none), so its allies can't hit it
     */
    record Options(double health, boolean glowing, UUID teamOf) {
        public static final Options DECOY = new Options(0, false, null);

        public boolean vulnerable() { return health > 0; }
    }

    /** Spawn a clone with {@link Options}. Platforms that don't support them spawn a decoy. */
    default Optional<UUID> spawnClone(UUID of, String world, Vec3 center, Vec3 facing, Options options) {
        return spawnClone(of, world, center, facing);
    }

    void despawn(UUID clone);
}

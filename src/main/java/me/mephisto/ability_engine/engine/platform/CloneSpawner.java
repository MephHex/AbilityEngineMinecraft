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

    void despawn(UUID clone);
}

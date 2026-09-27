package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.Optional;
import java.util.UUID;

/** Port for look-alike stand-ins of an entity (e.g. a mannequin wearing a player's skin). */
public interface CloneSpawner {

    /** Spawn a look-alike of {@code of}, its centre at {@code center}. Untargetable by abilities. */
    Optional<UUID> spawnClone(UUID of, String world, Vec3 center);

    void despawn(UUID clone);
}

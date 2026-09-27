package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.Optional;

/**
 * A projectile visual that flies ITSELF with the game's own physics (on Bukkit: a real arrow), so it
 * looks exactly like a vanilla shot. The engine never moves it. Each tick, before the game moves it,
 * the engine reads where it is and how fast it's going and checks the stretch it's about to fly: entities,
 * allies, blocks, barriers and constructs all work as for engine-flown projectiles. On a hit the engine
 * removes it before the game moves it through the target.
 */
public interface FlyingVisual extends ProjectileVisual {

    /** Where it is now; empty if it's gone. */
    Optional<Vec3> position();

    /** How far it will move during the game's next tick (blocks per tick). */
    Vec3 velocity();

    /** Steer it (redirect_projectile, steer_projectile). */
    void setVelocity(Vec3 velocity);

    /** It came to rest by itself (stuck in a block the engine didn't see coming). */
    boolean landed();

    /** It moves itself: the engine's position updates are ignored. */
    @Override
    default void moveTo(Vec3 position) {}

    @Override
    default void moveTo(Vec3 position, Vec3 velocity) {}
}

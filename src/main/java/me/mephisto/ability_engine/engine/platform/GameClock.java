package me.mephisto.ability_engine.engine.platform;

/**
 * Monotonic game-tick counter. On Paper this is {@code Bukkit.getCurrentTick()} —
 * NOT world time, which /time set and the daylight gamerule can move or freeze.
 */
public interface GameClock {
    long now();
}

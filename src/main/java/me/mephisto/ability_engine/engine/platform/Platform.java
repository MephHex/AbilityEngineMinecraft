package me.mephisto.ability_engine.engine.platform;

import java.util.logging.Logger;

/** Everything the engine needs from the outside world. Bukkit implements these; tests fake them. */
public record Platform(
        GameClock clock,
        TaskScheduler scheduler,
        WorldQuery world,
        MovementControl movement,
        ProjectileRenderer projectileRenderer,
        CuePlayer cues,
        IndicatorRenderer indicators,
        ConstructRenderer constructRenderer,
        Logger logger
) {}

package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.platform.GameClock;
import org.bukkit.Bukkit;

/**
 * Server tick counter. Unlike world.getFullTime() it can't be frozen by the daylight gamerule
 * or rewound by /time set — that's what broke cooldowns before.
 */
public final class PaperClock implements GameClock {
    @Override
    public long now() { return Bukkit.getCurrentTick(); }
}

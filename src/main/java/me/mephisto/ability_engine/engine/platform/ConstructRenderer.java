package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.construct.ConstructHandle;
import me.mephisto.ability_engine.engine.construct.ConstructVisual;

/**
 * Port for drawing constructs. The platform may also give them a hitbox players can punch;
 * it reports those hits back with {@code engine.constructs().strike(handle, Strike.melee(player))}.
 */
public interface ConstructRenderer {
    ConstructVisual spawn(ConstructHandle construct, String visual);
}

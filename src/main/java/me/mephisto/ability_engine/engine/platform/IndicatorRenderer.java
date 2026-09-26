package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.targeting.Targeting;

import java.util.UUID;

/** Port for targeting previews. Draw ONLY for the viewer: enemies shouldn't see where you're aiming. */
public interface IndicatorRenderer {

    /**
     * Called every tick while {@code viewer} is aiming {@code abilityName}.
     * {@code valid} = false: can't be cast there (e.g. no ground); draw it as such (red).
     */
    void draw(UUID viewer, String abilityName, Targeting targeting, Aim from, PointTarget at, boolean valid);

    /** Aiming ended. {@code reason}: "confirmed", "cancelled", "timeout", "blocked", "switched"... */
    void clear(UUID viewer, String reason);
}

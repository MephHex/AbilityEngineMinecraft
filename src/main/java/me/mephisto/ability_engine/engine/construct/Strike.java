package me.mephisto.ability_engine.engine.construct;

import java.util.UUID;

/**
 * What hit a construct: who did it, with which ability, and that shot's "phase" (so an
 * empowered/recast projectile is recognisable). Melee hits have no ability.
 */
public record Strike(UUID attacker, String abilityId, String phase) {

    public static final String MELEE = "melee";

    public static Strike melee(UUID attacker) { return new Strike(attacker, MELEE, "none"); }

    public boolean isMelee() { return MELEE.equals(abilityId); }
}

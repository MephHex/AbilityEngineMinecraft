package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;

/**
 * An instant thing that happens to a target (damage, apply a status, knockback...).
 * Effects are stateless singletons shared by all abilities; per-use values come from params.
 * Anything that lasts over time is a Status, not an Effect.
 */
public interface Effect {

    void apply(EffectContext ctx);

    /** Throw a DataException if params are wrong. Called once at load time, not every cast. */
    default void validate(Params params) {}
}

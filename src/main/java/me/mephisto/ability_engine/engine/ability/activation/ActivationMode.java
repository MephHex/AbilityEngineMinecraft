package me.mephisto.ability_engine.engine.ability.activation;

import me.mephisto.ability_engine.engine.ability.AbilityInstance;

/** How a cast unfolds over time: instantly, channeled, (later: charged, toggled...). Was CastExecutionStrategy. */
public interface ActivationMode {

    void start(AbilityInstance instance);

    /** True if a caster may only run one instance of this ability at a time. */
    default boolean exclusive() { return false; }
}

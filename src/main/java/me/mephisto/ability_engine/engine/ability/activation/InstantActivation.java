package me.mephisto.ability_engine.engine.ability.activation;

import me.mephisto.ability_engine.engine.ability.AbilityInstance;

/** Run the graph once. Delays/projectiles inside it still keep the instance alive. */
public final class InstantActivation implements ActivationMode {

    public static final InstantActivation INSTANCE = new InstantActivation();

    private InstantActivation() {}

    @Override
    public void start(AbilityInstance instance) {
        instance.engine().runner().start(instance.newBranch());
    }
}

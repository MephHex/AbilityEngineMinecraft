package me.mephisto.ability_engine.engine.ability.activation;

import me.mephisto.ability_engine.engine.ability.AbilityInstance;

/**
 * Runs the graph every {@code periodTicks} for as long as the input is held. The platform keeps
 * it alive by re-activating the ability (holding right click repeats about every 4 ticks); if no
 * keep-alive arrives for {@code releaseTicks}, the button was released and the hold ends.
 *
 * @param ammoResource    optional resource spent per pulse; the hold ends when it runs out
 * @param whileProjectile optional ability id: the hold only runs while the caster's latest
 *                        projectile from that ability is still flying (nothing to steer = no drain)
 */
public record HoldActivation(int periodTicks, int releaseTicks, String ammoResource, double ammoPerPulse,
                             String whileProjectile) implements ActivationMode {

    public HoldActivation {
        if (periodTicks < 1) throw new IllegalArgumentException("periodTicks must be >= 1");
        if (releaseTicks < 1) throw new IllegalArgumentException("releaseTicks must be >= 1");
    }

    @Override
    public void start(AbilityInstance instance) {
        new ActiveHold(this, instance).begin();
    }

    @Override
    public boolean exclusive() { return true; }
}

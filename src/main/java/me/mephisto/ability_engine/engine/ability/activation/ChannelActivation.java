package me.mephisto.ability_engine.engine.ability.activation;

import me.mephisto.ability_engine.engine.ability.AbilityInstance;

/**
 * Re-runs the graph every {@code periodTicks} for up to {@code durationTicks} (was ChannelCastStrategy
 * + ChannelConfig). Everything is in ticks now; the old version mixed milliseconds and ticks.
 *
 * @param ammoResource  optional resource consumed per pulse; the channel ends when it runs out
 */
public record ChannelActivation(int periodTicks, int durationTicks, String ammoResource, int ammoPerPulse)
        implements ActivationMode {

    public ChannelActivation {
        if (periodTicks < 1) throw new IllegalArgumentException("periodTicks must be >= 1");
    }

    @Override
    public void start(AbilityInstance instance) {
        new ActiveChannel(this, instance).begin();
    }

    @Override
    public boolean exclusive() { return true; }
}

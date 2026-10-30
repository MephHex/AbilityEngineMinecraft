package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.List;

/**
 * Effect id "reduce_cooldowns": takes {@code ticks} off the target's ability cooldowns (1, 2, 3, and the ultimate when
 * ultimates have cooldowns: a charging one isn't affected). Running cooldowns only. E.g. "your critical strikes take 1s
 * off your abilities" ({@code self: true} in an on-hit).
 */
public final class ReduceCooldownsEffect implements Effect {

    private static final List<String> ABILITIES = List.of(Slots.ABILITY_1, Slots.ABILITY_2, Slots.ABILITY_3, Slots.ULTIMATE);

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target)) return;
        int ticks = ctx.params().requireInt("ticks");
        for (String slot : ABILITIES) {
            ctx.engine().loadouts().abilityIn(target.id(), slot)
                    .ifPresent(id -> ctx.engine().cooldowns().reduce(target.id(), id, ticks));
        }
    }

    @Override
    public void validate(Params params) {
        if (params.requireInt("ticks") <= 0) throw params.error("ticks", "how many ticks to take off, above 0 (20 = 1s)");
    }
}

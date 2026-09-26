package me.mephisto.ability_engine.engine.status;

import me.mephisto.ability_engine.engine.effect.EffectConfig;

import java.util.List;
import java.util.Set;

/**
 * A lasting condition, defined as data (GAS "duration GameplayEffect", LoL "buff", Dota "modifier").
 *
 * @param defaultDurationTicks duration if the applier doesn't specify one; <= 0 means infinite
 * @param grantedTags          what it means: tags the holder has while it lasts
 * @param onHit                BUFF: effects the holder's on-hit abilities also apply to whoever they hit
 * @param tickEvery            damage/heal over time: run {@code tickEffects} on the holder every N ticks (0 = never),
 *                             credited to whoever applied the status
 */
public record StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags,
                        List<EffectConfig> onHit, int tickEvery, List<EffectConfig> tickEffects) {

    public StatusDef {
        grantedTags = Set.copyOf(grantedTags);
        onHit = List.copyOf(onHit);
        tickEffects = List.copyOf(tickEffects);
        if (maxStacks < 1) maxStacks = 1;
    }

    /** A plain status: tags only. */
    public StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags) {
        this(id, defaultDurationTicks, stacking, maxStacks, grantedTags, List.of(), 0, List.of());
    }
}

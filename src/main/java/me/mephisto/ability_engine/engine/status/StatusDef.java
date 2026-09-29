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
 * @param breakOnDamage        ends as soon as the holder deals damage (e.g. stealth)
 * @param once                 a buff used up by the first hit that applies its on_hit effects
 * @param tickEvery            damage/heal over time: run {@code tickEffects} on the holder every N ticks (0 = never),
 *                             credited to whoever applied the status
 * @param attackSpeed          the holder's basic attacks (primary / melee) come this much faster: their cooldown
 *                             is divided by it, a crossbow's draw takes longer (Paralysis: 0.6 = 40% slower)
 */
public record StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags,
                        List<EffectConfig> onHit, int tickEvery, List<EffectConfig> tickEffects,
                        boolean breakOnDamage, boolean once,
                        boolean positive, double damageDealt, double damageTaken, double attackSpeed) {

    public StatusDef {
        grantedTags = Set.copyOf(grantedTags);
        onHit = List.copyOf(onHit);
        tickEffects = List.copyOf(tickEffects);
        if (maxStacks < 1) maxStacks = 1;
    }

    /**
     * @param positive    a boon (copied to a tethered ally, see LinkManager)
     * @param damageDealt the holder's damage is multiplied by this (Strength: 1.2)
     * @param damageTaken damage to the holder is multiplied by this (0.8 = 20% less)
     */
    public StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags,
                     List<EffectConfig> onHit, int tickEvery, List<EffectConfig> tickEffects,
                     boolean breakOnDamage, boolean once, boolean positive, double damageDealt, double damageTaken) {
        this(id, defaultDurationTicks, stacking, maxStacks, grantedTags, onHit, tickEvery, tickEffects, breakOnDamage, once,
                positive, damageDealt, damageTaken, 1);
    }

    /** Without positive / damage / attack speed modifiers. */
    public StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags,
                     List<EffectConfig> onHit, int tickEvery, List<EffectConfig> tickEffects,
                     boolean breakOnDamage, boolean once) {
        this(id, defaultDurationTicks, stacking, maxStacks, grantedTags, onHit, tickEvery, tickEffects, breakOnDamage, once,
                false, 1, 1, 1);
    }

    /** Without break_on_damage / once. */
    public StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags,
                     List<EffectConfig> onHit, int tickEvery, List<EffectConfig> tickEffects) {
        this(id, defaultDurationTicks, stacking, maxStacks, grantedTags, onHit, tickEvery, tickEffects, false, false);
    }

    /** A plain status: tags only. */
    public StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags) {
        this(id, defaultDurationTicks, stacking, maxStacks, grantedTags, List.of(), 0, List.of());
    }
}

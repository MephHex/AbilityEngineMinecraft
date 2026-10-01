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
 * @param moveSpeed            the holder's movement speed is multiplied by this PER STACK (0.9 with 5 stacks =
 *                             x0.59), e.g. a slow that builds up
 * @param decayEvery           above 0: once its duration is up it doesn't end at once, it loses a stack every this
 *                             many ticks (a gauge cooling down); applying it again stops that and starts the duration over
 * @param links                how it hangs together with other statuses, and its looping cue
 * @param farDamage            damage from attackers farther than {@code beyond} blocks from the holder is multiplied by
 *                             {@code multiplier} (a domain: 0.5 = half from outside it, 0 = immune); null = none
 */
public record StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags,
                        List<EffectConfig> onHit, int tickEvery, List<EffectConfig> tickEffects,
                        boolean breakOnDamage, boolean once,
                        boolean positive, double damageDealt, double damageTaken, double attackSpeed,
                        double moveSpeed, int decayEvery, Links links, FarDamage farDamage) {

    /** Damage from attackers more than {@code beyond} blocks away is multiplied by {@code multiplier}. */
    public record FarDamage(double beyond, double multiplier) {}

    /**
     * @param atMax    reaching its max_stacks puts this status on the holder too (null = none), e.g. a full gauge
     *                 sets off a state
     * @param requires it only lasts while the holder has this status (null = no such tie): it ends with it
     * @param cue      a looping cue on the holder while it lasts (null = none)
     * @param then     when its time runs out (not when it's removed early), this status goes on the holder (null =
     *                 none), e.g. one stage growing into the next
     */
    public record Links(String atMax, String requires, String cue, String then) {
        public static final Links NONE = new Links(null, null, null, null);

        public Links(String atMax, String requires, String cue) { this(atMax, requires, cue, null); }
    }

    public StatusDef {
        if (links == null) links = Links.NONE;
        grantedTags = Set.copyOf(grantedTags);
        onHit = List.copyOf(onHit);
        tickEffects = List.copyOf(tickEffects);
        if (maxStacks < 1) maxStacks = 1;
    }

    /** Without far damage. */
    public StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags,
                     List<EffectConfig> onHit, int tickEvery, List<EffectConfig> tickEffects,
                     boolean breakOnDamage, boolean once, boolean positive, double damageDealt, double damageTaken,
                     double attackSpeed, double moveSpeed, int decayEvery, Links links) {
        this(id, defaultDurationTicks, stacking, maxStacks, grantedTags, onHit, tickEvery, tickEffects, breakOnDamage, once,
                positive, damageDealt, damageTaken, attackSpeed, moveSpeed, decayEvery, links, null);
    }

    /** Without links. */
    public StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags,
                     List<EffectConfig> onHit, int tickEvery, List<EffectConfig> tickEffects,
                     boolean breakOnDamage, boolean once, boolean positive, double damageDealt, double damageTaken,
                     double attackSpeed, double moveSpeed, int decayEvery) {
        this(id, defaultDurationTicks, stacking, maxStacks, grantedTags, onHit, tickEvery, tickEffects, breakOnDamage, once,
                positive, damageDealt, damageTaken, attackSpeed, moveSpeed, decayEvery, Links.NONE);
    }

    /** Without decay. */
    public StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags,
                     List<EffectConfig> onHit, int tickEvery, List<EffectConfig> tickEffects,
                     boolean breakOnDamage, boolean once, boolean positive, double damageDealt, double damageTaken,
                     double attackSpeed, double moveSpeed) {
        this(id, defaultDurationTicks, stacking, maxStacks, grantedTags, onHit, tickEvery, tickEffects, breakOnDamage, once,
                positive, damageDealt, damageTaken, attackSpeed, moveSpeed, 0);
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
                positive, damageDealt, damageTaken, 1, 1);
    }

    public StatusDef(String id, int defaultDurationTicks, StackPolicy stacking, int maxStacks, Set<String> grantedTags,
                     List<EffectConfig> onHit, int tickEvery, List<EffectConfig> tickEffects,
                     boolean breakOnDamage, boolean once, boolean positive, double damageDealt, double damageTaken,
                     double attackSpeed) {
        this(id, defaultDurationTicks, stacking, maxStacks, grantedTags, onHit, tickEvery, tickEffects, breakOnDamage, once,
                positive, damageDealt, damageTaken, attackSpeed, 1);
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

    /** The same, with {@code decay}. */
    public StatusDef withDecay(int every) {
        return new StatusDef(id, defaultDurationTicks, stacking, maxStacks, grantedTags, onHit, tickEvery, tickEffects,
                breakOnDamage, once, positive, damageDealt, damageTaken, attackSpeed, moveSpeed, every, links, farDamage);
    }

    /** The same, with these links. */
    public StatusDef withLinks(Links other) {
        return new StatusDef(id, defaultDurationTicks, stacking, maxStacks, grantedTags, onHit, tickEvery, tickEffects,
                breakOnDamage, once, positive, damageDealt, damageTaken, attackSpeed, moveSpeed, decayEvery, other, farDamage);
    }
}

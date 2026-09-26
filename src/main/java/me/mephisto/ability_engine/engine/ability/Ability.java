package me.mephisto.ability_engine.engine.ability;

import me.mephisto.ability_engine.engine.ability.activation.ActivationMode;
import me.mephisto.ability_engine.engine.ability.activation.InstantActivation;
import me.mephisto.ability_engine.engine.graph.AbilityGraph;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.targeting.Targeting;

import java.util.Map;
import java.util.Set;

/**
 * Immutable ability definition. One per ability id, shared by every cast.
 * A running cast is an {@link AbilityInstance}.
 *
 * @param blockedBy     can't activate while the caster has any of these tags
 * @param interruptedBy running instances are cancelled when the caster gains any of these
 * @param activeTags    granted to the caster while an instance runs (e.g. state.channeling)
 * @param display       name/icon/description for the HUD; not used by gameplay
 * @param targeting     aiming preview + confirm step; null = casts instantly at the crosshair
 * @param cooldownAfterRecast the cooldown starts when the recast window closes (recast used, timed out,
 *                      or the cast ended) instead of on the first cast
 * @param aura          a looping cue on the caster for the whole cast (null = none)
 * @param cancelOnRepress pressing the ability's key again while it runs ends it early
 */
public record Ability(
        String id,
        AbilityGraph graph,
        int cooldownTicks,
        Map<String, Integer> costs,
        ActivationMode mode,
        Set<String> blockedBy,
        Set<String> interruptedBy,
        Set<String> activeTags,
        AbilityDisplay display,
        Targeting targeting,
        boolean cooldownAfterRecast,
        String aura,
        boolean cancelOnRepress
) {
    public Ability {
        costs = Map.copyOf(costs);
        blockedBy = Set.copyOf(blockedBy);
        interruptedBy = Set.copyOf(interruptedBy);
        activeTags = Set.copyOf(activeTags);
        if (display == null) display = AbilityDisplay.of(id);
    }

    public static Builder builder(String id, AbilityGraph graph) { return new Builder(id, graph); }

    public static final class Builder {
        private final String id;
        private final AbilityGraph graph;
        private int cooldownTicks;
        private Map<String, Integer> costs = Map.of();
        private ActivationMode mode = InstantActivation.INSTANCE;
        private Set<String> blockedBy = Set.of(Tags.BLOCK_ABILITY);
        private Set<String> interruptedBy;
        private Set<String> activeTags;
        private AbilityDisplay display;
        private Targeting targeting;
        private boolean cooldownAfterRecast;
        private String aura;
        private boolean cancelOnRepress;

        private Builder(String id, AbilityGraph graph) {
            this.id = id;
            this.graph = graph;
        }

        public Builder cooldown(int ticks) { this.cooldownTicks = ticks; return this; }
        public Builder costs(Map<String, Integer> costs) { this.costs = costs; return this; }
        public Builder mode(ActivationMode mode) { this.mode = mode; return this; }
        public Builder blockedBy(Set<String> tags) { this.blockedBy = tags; return this; }
        public Builder interruptedBy(Set<String> tags) { this.interruptedBy = tags; return this; }
        public Builder activeTags(Set<String> tags) { this.activeTags = tags; return this; }
        public Builder display(AbilityDisplay display) { this.display = display; return this; }
        public Builder targeting(Targeting targeting) { this.targeting = targeting; return this; }
        public Builder cooldownAfterRecast(boolean v) { this.cooldownAfterRecast = v; return this; }
        public Builder aura(String cueId) { this.aura = cueId; return this; }
        public Builder cancelOnRepress(boolean v) { this.cancelOnRepress = v; return this; }

        public Ability build() {
            // Channels are interruptible and mark the caster as channeling by default; instants aren't.
            Set<String> interrupts = interruptedBy != null ? interruptedBy
                    : mode.exclusive() ? Set.of(Tags.BLOCK_ABILITY) : Set.of();
            Set<String> active = activeTags != null ? activeTags
                    : mode.exclusive() ? Set.of(Tags.CHANNELING) : Set.of();
            return new Ability(id, graph, cooldownTicks, costs, mode, blockedBy, interrupts, active, display, targeting,
                    cooldownAfterRecast, aura, cancelOnRepress);
        }
    }
}

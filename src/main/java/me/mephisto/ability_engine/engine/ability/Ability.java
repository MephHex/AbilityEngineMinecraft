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
 * @param survivesDeath   the caster dying doesn't cancel its casts (e.g. traps stay armed); logging out
 *                        or changing character still does
 * @param movement        it moves the caster (dash, blink): can't be used while they can't move
 *                        ({@code block.move}: rooted, stunned), and a cast in progress stops if they lose
 *                        the ability to move. Already folded into blockedBy / interruptedBy.
 * @param charges         uses stored up (default 1). With 2+, each use spends one and they come back one
 *                        at a time, {@code cooldownTicks} each; it's only "on cooldown" with none left.
 * @param recastMovement  its RECAST moves the caster (a swap, a charge): refused while rooted
 *                        ({@code block.move}), the window staying open; the first cast isn't affected
 * @param passiveWhile    while the caster has this tag the ability is passive: it's already in effect (e.g. an
 *                        ultimate keeps it up), so pressing its key does nothing and its icon glints (null = never)
 * @param needsConstructs it can only be used while the caster has constructs from this ability (or these, comma-separated:
 *                        a YAML list) standing (e.g. a recall
 *                        of shards left lying around); its icon counts them, greyed out with none (null = always)
 * @param alsoFlying      with needsConstructs: the projectiles that ability's node of this name has flying count too
 *                        (e.g. shards still on their way, that a recall stops and calls back); null = only constructs
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
        boolean cancelOnRepress,
        boolean survivesDeath,
        boolean movement,
        String refreshOnKill,
        boolean manualCooldown,
        int charges,
        boolean recastMovement,
        String passiveWhile,
        String needsConstructs,
        String alsoFlying
) {
    public Ability {
        costs = Map.copyOf(costs);
        blockedBy = Set.copyOf(blockedBy);
        interruptedBy = Set.copyOf(interruptedBy);
        activeTags = Set.copyOf(activeTags);
        if (display == null) display = AbilityDisplay.of(id);
        charges = Math.max(1, charges);
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
        private boolean survivesDeath;
        private boolean movement;
        private String refreshOnKill = "none";
        private boolean manualCooldown;
        private int charges = 1;
        private boolean recastMovement;
        private String passiveWhile;
        private String needsConstructs;
        private String alsoFlying;

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
        public Builder survivesDeath(boolean v) { this.survivesDeath = v; return this; }
        /** "none", "players" (reset the cooldown on player kills) or "all" (any kill). */
        public Builder refreshOnKill(String v) { this.refreshOnKill = v; return this; }
        public Builder movement(boolean v) { this.movement = v; return this; }
        /** The cooldown only starts when a start_cooldown node runs (e.g. only if the cast succeeded). */
        public Builder manualCooldown(boolean v) { this.manualCooldown = v; return this; }
        public Builder charges(int n) { this.charges = n; return this; }
        public Builder recastMovement(boolean v) { this.recastMovement = v; return this; }
        /** While the caster has this tag, pressing it does nothing (it's in effect anyway) and its icon glints. */
        public Builder passiveWhile(String tag) { this.passiveWhile = tag; return this; }
        /** Only usable while the caster has constructs from this ability standing. */
        public Builder needsConstructs(String abilityId) { this.needsConstructs = abilityId; return this; }
        /** With needsConstructs: that ability's projectiles from this node, still flying, count too. */
        public Builder alsoFlying(String node) { this.alsoFlying = node; return this; }

        public Ability build() {
            // Channels are interruptible and mark the caster as channeling by default; instants aren't.
            Set<String> interrupts = interruptedBy != null ? interruptedBy
                    : mode.exclusive() ? Set.of(Tags.BLOCK_ABILITY) : Set.of();
            Set<String> active = activeTags != null ? activeTags
                    : mode.exclusive() ? Set.of(Tags.CHANNELING) : Set.of();
            Set<String> blocked = blockedBy;
            if (movement) { // rooted (or stunned): no dashing or blinking, and a dash in progress stops
                blocked = with(blocked, Tags.BLOCK_MOVE);
                interrupts = with(interrupts, Tags.BLOCK_MOVE);
            }
            return new Ability(id, graph, cooldownTicks, costs, mode, blocked, interrupts, active, display, targeting,
                    cooldownAfterRecast, aura, cancelOnRepress, survivesDeath, movement,
                    refreshOnKill, manualCooldown, charges, recastMovement, passiveWhile, needsConstructs, alsoFlying);
        }

        private static Set<String> with(Set<String> tags, String tag) {
            Set<String> out = new java.util.HashSet<>(tags);
            out.add(tag);
            return out;
        }
    }
}

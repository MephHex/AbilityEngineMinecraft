package me.mephisto.ability_engine.engine.loadout;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What a character's model does by itself, copying its wearer: {@code states} loop while their conditions hold (the
 * first that holds plays: idle, walk, sprint, swim...), {@code on} plays once when something happens (a jump, a swing,
 * a hit taken). Abilities' animate steps play on top of them.
 *
 * @param states in order: the first whose conditions hold plays (none: nothing loops)
 * @param on     event ({@link #EVENTS}) -> the animation it plays once
 */
public record ModelBehavior(List<State> states, Map<String, String> on) {

    public static final ModelBehavior NONE = new ModelBehavior(List.of(), Map.of());

    /** What the platform tells about a wearer, every tick. Any word with a dot in it is an engine tag instead. */
    public static final Set<String> FACTS = Set.of("moving", "sprinting", "sneaking", "swimming", "in_water",
            "on_ground", "airborne", "falling", "rising", "gliding", "flying", "riding", "climbing", "blocking",
            "animating");

    /** What {@code on:} can answer. */
    public static final Set<String> EVENTS = Set.of("jump", "land", "swing", "hurt", "death");

    /** A player's walking speed, blocks per second: what a movement-scaled animation is made for by default. */
    public static final double WALK_SPEED = 4.317;

    public ModelBehavior {
        states = List.copyOf(states);
        on = Map.copyOf(on);
    }

    public boolean isEmpty() { return states.isEmpty() && on.isEmpty(); }

    /** One condition: a fact or a tag, or ({@code !}) its absence. */
    public record Condition(String fact, boolean negated) {
        public boolean isTag() { return fact.contains("."); }

        boolean holds(Set<String> facts, Predicate<String> hasTag) {
            boolean is = isTag() ? hasTag.test(fact) : facts.contains(fact);
            return is != negated;
        }

        @Override
        public String toString() { return (negated ? "!" : "") + fact; }
    }

    /**
     * A looping animation and when it plays.
     *
     * @param when        all must hold (empty: always - a last, fallback state such as idle)
     * @param healthBelow above 0: only while their health is below this share of their max (0.3 = 30%)
     * @param speed       1 = as made
     * @param byMovement  sped up or slowed down with how fast they move ({@code scale_with: movement})
     * @param madeFor     blocks per second the animation was made for (with byMovement): at that speed it plays at
     *                    {@code speed}
     * @param blend       ticks it eases in from the state before, and out to the next (-1: the model's blend)
     */
    public record State(String animation, List<Condition> when, double healthBelow, double speed, boolean byMovement,
                        double madeFor, int blend) {
        public State {
            when = List.copyOf(when);
        }

        public State(String animation, List<Condition> when) { this(animation, when, 0, 1, false, WALK_SPEED, -1); }

        /** @param health their health, a share of their max (0..1) */
        public boolean holds(Set<String> facts, Predicate<String> hasTag, double health) {
            if (healthBelow > 0 && health >= healthBelow) return false;
            for (Condition c : when) if (!c.holds(facts, hasTag)) return false;
            return true;
        }

        /** Its speed while they move at {@code blocksPerSecond}. */
        public double speedAt(double blocksPerSecond) {
            if (!byMovement) return speed;
            return speed * Math.max(0.25, Math.min(3, blocksPerSecond / madeFor));
        }
    }

    /** The state that plays now: the first whose conditions hold (null: none). */
    public State pick(Set<String> facts, Predicate<String> hasTag, double health) {
        for (State s : states) if (s.holds(facts, hasTag, health)) return s;
        return null;
    }

    /** Every animation it plays by itself (an ability's own animation is anything else: the {@code animating} fact). */
    public Set<String> animations() {
        Set<String> all = new LinkedHashSet<>();
        for (State s : states) all.add(s.animation());
        all.addAll(on.values());
        return all;
    }

    /** {@code "not sneaking"} or {@code "!sneaking"} (quoted in YAML) -> sneaking, negated. */
    public static Condition condition(String word) {
        String w = word.trim();
        boolean not = w.startsWith("!") || w.startsWith("not ");
        if (not) w = w.substring(w.startsWith("!") ? 1 : 4).trim();
        return new Condition(w, not);
    }
}

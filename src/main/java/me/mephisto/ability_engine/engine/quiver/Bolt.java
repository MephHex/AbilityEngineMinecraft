package me.mephisto.ability_engine.engine.quiver;

import java.util.ArrayList;
import java.util.List;

/**
 * One bolt in a quiver: plain, or carrying infusions (ids from {@code infusions:} in YAML). A bolt can
 * hold several different infusions; infusing it again with one it already has changes nothing.
 * Immutable: infusing makes a new bolt.
 */
public record Bolt(List<String> infusions) {

    public static final Bolt PLAIN = new Bolt(List.of());

    public Bolt {
        infusions = List.copyOf(infusions);
    }

    public boolean isPlain() { return infusions.isEmpty(); }

    public boolean has(String infusion) { return infusions.contains(infusion); }

    /** This bolt without any of {@code infusions}. */
    public Bolt without(java.util.Collection<String> removed) {
        if (infusions.stream().noneMatch(removed::contains)) return this;
        return new Bolt(infusions.stream().filter(i -> !removed.contains(i)).toList());
    }

    /** This bolt plus {@code infusion} (appended, so the HUD lists them in the order they were added). */
    public Bolt with(String infusion) {
        if (has(infusion)) return this;
        List<String> more = new ArrayList<>(infusions);
        more.add(infusion);
        return new Bolt(more);
    }
}

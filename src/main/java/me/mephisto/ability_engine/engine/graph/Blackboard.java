package me.mephisto.ability_engine.engine.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Scoped key/value store for one execution branch. A forked branch gets a child board:
 * it can read everything the parent wrote, but its own writes stay local — so two
 * projectiles from the same cast can't overwrite each other's "hit".
 */
public final class Blackboard {

    private final Blackboard parent;
    private final Map<String, Object> values = new HashMap<>();

    public Blackboard() { this(null); }

    private Blackboard(Blackboard parent) { this.parent = parent; }

    public Blackboard child() { return new Blackboard(this); }

    public <T> void put(Key<T> key, T value) { values.put(key.name(), value); }

    public <T> T get(Key<T> key) { return key.cast(raw(key.name())); }

    public <T> Optional<T> find(Key<T> key) { return Optional.ofNullable(get(key)); }

    /** Untyped write by name, for data-driven nodes (set, projectile store). */
    public void putRaw(String name, Object value) { values.put(name, value); }

    /** Untyped read by name, walking up to parent scopes. For data-driven nodes. */
    public Object raw(String name) {
        for (Blackboard b = this; b != null; b = b.parent) {
            Object v = b.values.get(name);
            if (v != null) return v;
        }
        return null;
    }
}

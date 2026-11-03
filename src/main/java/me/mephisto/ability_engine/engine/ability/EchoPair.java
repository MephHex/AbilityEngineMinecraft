package me.mephisto.ability_engine.engine.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A cast and its echo (the same ability cast at the same moment by a clone that copies its owner, see Bodies): the two
 * can tell each other things, by name. E.g. the spot the owner picked (the clone's leap goes there too), or where each
 * one's shot landed (two shots landing together pull at once).
 * <p>A value posted under a name stays there; whoever waits for it gets it at once if it's there, else when it's posted.
 * When the owner's cast ends, whoever still waits gets null.
 */
public final class EchoPair {

    private final AbilityInstance owner;
    private final AbilityInstance echo;
    private final Map<String, Object> posted = new HashMap<>();
    private final Map<String, List<Consumer<Object>>> waiting = new HashMap<>();
    private boolean closed;

    EchoPair(AbilityInstance owner, AbilityInstance echo) {
        this.owner = owner;
        this.echo = echo;
        owner.onEnd(this::close);
    }

    /** The owner's own cast. */
    public AbilityInstance owner() { return owner; }

    /** The clone's copy of it. */
    public AbilityInstance echo() { return echo; }

    /** The other cast of the two. */
    public AbilityInstance other(AbilityInstance one) { return one == owner ? echo : owner; }

    public boolean isOwner(AbilityInstance one) { return one == owner; }

    public void post(String name, Object value) {
        posted.put(name, value);
        List<Consumer<Object>> listeners = waiting.remove(name);
        if (listeners != null) for (Consumer<Object> l : listeners) l.accept(value);
    }

    /** What was posted under {@code name} (null = nothing yet). */
    public Object get(String name) { return posted.get(name); }

    /** {@code listener} gets what's posted under {@code name}: now if it's there, else later (null if it never comes). */
    public void await(String name, Consumer<Object> listener) {
        if (posted.containsKey(name)) {
            listener.accept(posted.get(name));
            return;
        }
        if (closed) {
            listener.accept(null);
            return;
        }
        waiting.computeIfAbsent(name, k -> new ArrayList<>()).add(listener);
    }

    private void close() {
        if (closed) return;
        closed = true;
        var all = new ArrayList<>(waiting.values());
        waiting.clear();
        for (List<Consumer<Object>> listeners : all) for (Consumer<Object> l : listeners) l.accept(null);
    }
}

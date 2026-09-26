package me.mephisto.ability_engine.engine.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Effect types by id. An instance now (was static), so plugin reloads start clean. */
public final class EffectRegistry {

    private final Map<String, Effect> effects = new HashMap<>();

    public void register(String id, Effect effect) {
        effects.put(id, effect);
    }

    public boolean has(String id) { return effects.containsKey(id); }

    public Effect require(String id) {
        Effect e = effects.get(id);
        if (e == null) throw new IllegalArgumentException("Unknown effect '" + id + "', known: " + effects.keySet());
        return e;
    }

    public Set<String> ids() { return Set.copyOf(effects.keySet()); }
}

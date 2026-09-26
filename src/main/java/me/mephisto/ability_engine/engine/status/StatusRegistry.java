package me.mephisto.ability_engine.engine.status;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StatusRegistry {

    private final Map<String, StatusDef> defs = new HashMap<>();

    public void define(StatusDef def) { defs.put(def.id(), def); }
    public Optional<StatusDef> find(String id) { return Optional.ofNullable(defs.get(id)); }

    public StatusDef require(String id) {
        StatusDef d = defs.get(id);
        if (d == null) throw new IllegalArgumentException("Unknown status '" + id + "', known: " + defs.keySet());
        return d;
    }

    public Set<String> ids() { return Set.copyOf(defs.keySet()); }
    public void clear() { defs.clear(); }
}

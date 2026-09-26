package me.mephisto.ability_engine.engine.quiver;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class InfusionRegistry {

    private final Map<String, InfusionDef> infusions = new TreeMap<>();

    public void define(InfusionDef infusion) { infusions.put(infusion.id(), infusion); }
    public Optional<InfusionDef> find(String id) { return Optional.ofNullable(infusions.get(id)); }
    public Set<String> ids() { return Set.copyOf(infusions.keySet()); }
    public void clear() { infusions.clear(); }
}

package me.mephisto.ability_engine.engine.ability;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class AbilityRegistry {

    private final Map<String, Ability> abilities = new TreeMap<>();

    public void register(Ability ability) { abilities.put(ability.id(), ability); }
    public Optional<Ability> find(String id) { return Optional.ofNullable(abilities.get(id)); }
    public Set<String> ids() { return Set.copyOf(abilities.keySet()); }
    public void clear() { abilities.clear(); }
}

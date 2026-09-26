package me.mephisto.ability_engine.engine.loadout;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class CharacterRegistry {

    private final Map<String, CharacterDef> characters = new TreeMap<>();

    public void define(CharacterDef character) { characters.put(character.id(), character); }
    public Optional<CharacterDef> find(String id) { return Optional.ofNullable(characters.get(id)); }
    public Set<String> ids() { return Set.copyOf(characters.keySet()); }
    public void clear() { characters.clear(); }
}

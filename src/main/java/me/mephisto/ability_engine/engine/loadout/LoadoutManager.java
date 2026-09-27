package me.mephisto.ability_engine.engine.loadout;

import me.mephisto.ability_engine.engine.ability.activation.AbilityActivator;
import me.mephisto.ability_engine.engine.ability.activation.ActivationResult;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.quiver.QuiverManager;
import me.mephisto.ability_engine.engine.state.ResourceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Which character each player is playing, and "fire slot X" -> "activate ability Y".
 * Stores character ids, not objects, so /ae reload picks up edited kits immediately.
 */
public final class LoadoutManager {

    private final CharacterRegistry characters;
    private final AbilityActivator activator;
    private final ResourceManager resources;
    private final QuiverManager quivers;
    private final me.mephisto.ability_engine.engine.tag.TagManager tags;
    private final Map<UUID, String> assigned = new HashMap<>();

    public LoadoutManager(CharacterRegistry characters, AbilityActivator activator, ResourceManager resources,
                          QuiverManager quivers, me.mephisto.ability_engine.engine.tag.TagManager tags) {
        this.characters = characters;
        this.activator = activator;
        this.resources = resources;
        this.quivers = quivers;
        this.tags = tags;
    }

    public void assign(UUID player, String characterId) {
        if (characters.find(characterId).isEmpty()) {
            throw new IllegalArgumentException("Unknown character '" + characterId + "', known: " + characters.ids());
        }
        assigned.put(player, characterId);
        characters.find(characterId).get().resources().values().forEach(def -> resources.define(player, def));
        quivers.reset(player); // a fresh quiver: plain bolts, nothing loaded
    }

    public void clear(UUID player) {
        assigned.remove(player);
        quivers.clear(player);
    }

    /**
     * The player's character as it is right now: with a form applied while they have its tag (e.g. an
     * ultimate's other primary and weapon). Empty if unassigned, or if their character no longer exists
     * after a reload.
     */
    public Optional<CharacterDef> characterOf(UUID player) {
        String id = assigned.get(player);
        if (id == null) return Optional.empty();
        return characters.find(id).map(c -> {
            for (CharacterDef.Form form : c.forms()) {
                if (tags.has(player, form.tag())) return c.in(form);
            }
            return c;
        });
    }

    public boolean has(UUID player) { return characterOf(player).isPresent(); }

    public Optional<String> abilityIn(UUID player, String slot) {
        return characterOf(player).map(c -> c.abilityIn(slot));
    }

    public ActivationResult activate(UUID player, String slot) {
        return activate(player, slot, true);
    }

    /** @param freshPress false for auto-repeat while a button is held; held input never recasts */
    public ActivationResult activate(UUID player, String slot, boolean freshPress) {
        Optional<CharacterDef> character = characterOf(player);
        if (character.isEmpty()) return ActivationResult.fail("no_character");
        String abilityId = character.get().abilityIn(slot);
        if (abilityId == null) return ActivationResult.fail("empty_slot:" + slot);
        return activator.activate(player, abilityId, freshPress, java.util.Map.of(Keys.SLOT.name(), slot));
    }

    /** Fire a slot at a specific entity (melee clicks). Empty slot: empty_slot:<slot>. */
    public ActivationResult activateOn(UUID player, String slot, me.mephisto.ability_engine.engine.target.Target target) {
        Optional<CharacterDef> character = characterOf(player);
        if (character.isEmpty()) return ActivationResult.fail("no_character");
        String abilityId = character.get().abilityIn(slot);
        if (abilityId == null) return ActivationResult.fail("empty_slot:" + slot);
        return activator.activateOnId(player, abilityId, target, java.util.Map.of(Keys.SLOT.name(), slot));
    }

    public Set<UUID> assignedPlayers() { return Set.copyOf(assigned.keySet()); }
}

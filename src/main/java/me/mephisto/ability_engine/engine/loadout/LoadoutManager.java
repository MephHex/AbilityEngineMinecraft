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
    private final me.mephisto.ability_engine.engine.ability.AbilityRegistry abilities;
    private final me.mephisto.ability_engine.engine.ability.AbilityInstanceRegistry instances;
    private final Map<UUID, String> assigned = new HashMap<>();
    private final java.util.List<java.util.function.Consumer<UUID>> assignListeners = new java.util.ArrayList<>();

    /** Told after a player is given a character. */
    public void onAssign(java.util.function.Consumer<UUID> listener) { assignListeners.add(listener); }

    public LoadoutManager(CharacterRegistry characters, AbilityActivator activator, ResourceManager resources,
                          QuiverManager quivers, me.mephisto.ability_engine.engine.tag.TagManager tags,
                          me.mephisto.ability_engine.engine.ability.AbilityRegistry abilities,
                          me.mephisto.ability_engine.engine.ability.AbilityInstanceRegistry instances) {
        this.abilities = abilities;
        this.instances = instances;
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
        assignListeners.forEach(l -> l.accept(player));
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

    /** The player's character as defined, ignoring forms (its own primary, its own stats). */
    public Optional<CharacterDef> baseCharacterOf(UUID player) {
        String id = assigned.get(player);
        return id == null ? Optional.empty() : characters.find(id);
    }

    public boolean has(UUID player) { return characterOf(player).isPresent(); }

    public Optional<String> abilityIn(UUID player, String slot) {
        return characterOf(player).map(c -> c.abilityIn(slot));
    }

    /** Basic attacks: what silence doesn't stop, and disarm does. */
    public static boolean isBasicAttack(String slot) {
        return Slots.PRIMARY.equals(slot) || Slots.MELEE.equals(slot);
    }

    /**
     * Which crowd control keeps this slot from being used right now, if any (for the HUD, and silence /
     * disarm are enforced here):
     * <ul>
     *   <li>stunned: every slot</li>
     *   <li>disarmed: basic attacks (primary, melee)</li>
     *   <li>silenced: everything but basic attacks</li>
     *   <li>rooted (block.move): movement abilities (dashes, blinks), and recasts that move you while
     *       their window is open (recast_movement)</li>
     * </ul>
     * A passive isn't a slot, so it's never blocked. Empty = free.
     */
    public Optional<String> crowdControl(UUID player, String slot) {
        if (tags.has(player, me.mephisto.ability_engine.engine.tag.Tags.STUNNED)) return Optional.of(me.mephisto.ability_engine.engine.tag.Tags.STUNNED);
        if (isBasicAttack(slot)) {
            if (tags.has(player, me.mephisto.ability_engine.engine.tag.Tags.DISARMED)) return Optional.of(me.mephisto.ability_engine.engine.tag.Tags.DISARMED);
        } else if (tags.has(player, me.mephisto.ability_engine.engine.tag.Tags.SILENCED)) {
            return Optional.of(me.mephisto.ability_engine.engine.tag.Tags.SILENCED);
        }
        if (tags.has(player, me.mephisto.ability_engine.engine.tag.Tags.BLOCK_MOVE)) {
            boolean movement = abilityIn(player, slot).flatMap(abilities::find)
                    .map(a -> a.movement() || (a.recastMovement() && instances.awaitingRecast(player, a.id())))
                    .orElse(false);
            if (movement) return Optional.of(me.mephisto.ability_engine.engine.tag.Tags.ROOTED);
        }
        return Optional.empty();
    }

    /** Silence and disarm, which depend on the slot (stun and root are the abilities' own blocked_by). */
    private Optional<ActivationResult> slotBlocked(UUID player, String slot) {
        boolean basic = isBasicAttack(slot);
        String tag = basic ? me.mephisto.ability_engine.engine.tag.Tags.DISARMED : me.mephisto.ability_engine.engine.tag.Tags.SILENCED;
        return tags.has(player, tag) ? Optional.of(ActivationResult.fail("blocked:" + tag)) : Optional.empty();
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
        var blocked = slotBlocked(player, slot);
        if (blocked.isPresent()) return blocked.get();
        return activator.activate(player, abilityId, freshPress, java.util.Map.of(Keys.SLOT.name(), slot));
    }

    /** Fire a slot at a specific entity (melee clicks). Empty slot: empty_slot:<slot>. */
    public ActivationResult activateOn(UUID player, String slot, me.mephisto.ability_engine.engine.target.Target target) {
        Optional<CharacterDef> character = characterOf(player);
        if (character.isEmpty()) return ActivationResult.fail("no_character");
        String abilityId = character.get().abilityIn(slot);
        if (abilityId == null) return ActivationResult.fail("empty_slot:" + slot);
        var blocked = slotBlocked(player, slot);
        if (blocked.isPresent()) return blocked.get();
        return activator.activateOnId(player, abilityId, target, java.util.Map.of(Keys.SLOT.name(), slot));
    }

    public Set<UUID> assignedPlayers() { return Set.copyOf(assigned.keySet()); }
}

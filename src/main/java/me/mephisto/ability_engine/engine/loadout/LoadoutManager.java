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
    /** Slots a player has filled with something else for now (a stolen ultimate), over their kit and its forms. */
    private final Map<UUID, Map<String, String>> overrides = new HashMap<>();
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
        overrides.remove(player); // a fresh kit: nothing borrowed
        characters.find(characterId).get().resources().values().forEach(def -> resources.define(player, def));
        quivers.reset(player); // a fresh quiver: plain bolts, nothing loaded
        assignListeners.forEach(l -> l.accept(player));
    }

    public void clear(UUID player) {
        assigned.remove(player);
        overrides.remove(player);
        quivers.clear(player);
    }

    /**
     * The player's character as it is right now: with a form applied while they have its tag (e.g. an
     * ultimate's other primary and weapon). Empty if unassigned, or if their character no longer exists
     * after a reload.
     */
    public Optional<CharacterDef> characterOf(UUID player) {
        Map<String, String> over = overrides.get(player);
        Optional<CharacterDef> kit = kitOf(player);
        if (over == null || over.isEmpty()) return kit;
        return kit.map(c -> c.in(new CharacterDef.Form(null, null, over, null, null)));
    }

    /** Their character with its form applied, without any slot overrides (what's really theirs right now). */
    public Optional<CharacterDef> kitOf(UUID player) {
        String id = assigned.get(player);
        if (id == null) return Optional.empty();
        return characters.find(id).map(c -> {
            for (CharacterDef.Form form : c.forms()) {
                if (tags.has(player, form.tag())) return c.in(form);
            }
            return c;
        });
    }

    /**
     * For now, {@code slot} holds {@code abilityId} instead of whatever their kit has there (forms too), e.g. a stolen
     * ultimate, until {@link #clearOverride}. Only with a character.
     */
    public void override(UUID player, String slot, String abilityId) {
        if (!assigned.containsKey(player)) return;
        overrides.computeIfAbsent(player, k -> new java.util.LinkedHashMap<>()).put(slot, abilityId);
    }

    /** The slot holds what their kit has there again. */
    public void clearOverride(UUID player, String slot) {
        Map<String, String> over = overrides.get(player);
        if (over == null) return;
        over.remove(slot);
        if (over.isEmpty()) overrides.remove(player);
    }

    /** What {@code slot} holds for now instead of their kit's (empty = their kit's own). */
    public Optional<String> overrideOf(UUID player, String slot) {
        return Optional.ofNullable(overrides.getOrDefault(player, Map.of()).get(slot));
    }

    /**
     * Whose stat sheet the player has right now: their character, or the form they're in when it has stats of its
     * own (a stage of growth). A form without stats doesn't change the sheet (nor which primary follows attack speed).
     */
    public Optional<CharacterDef> sheetOf(UUID player) {
        String id = assigned.get(player);
        if (id == null) return Optional.empty();
        return characters.find(id).map(c -> {
            for (CharacterDef.Form form : c.forms()) {
                if (tags.has(player, form.tag())) return form.stats() != null ? c.in(form) : c;
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
     *   <li>anything else in the ability's blocked_by (e.g. block.ability while an ultimate runs), unless it's a
     *       running cast its own key can stop (cancel_on_repress)</li>
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
        // Anything else it can't be used with right now (its blocked_by: e.g. an ultimate's block.ability meanwhile).
        // A running cast its key can end (cancel_on_repress) is still usable: that press stops it.
        var ability = abilityIn(player, slot).flatMap(abilities::find);
        if (ability.isPresent()) {
            String blocking = tags.firstMatch(player, ability.get().blockedBy());
            boolean stoppable = ability.get().cancelOnRepress() && instances.isRunning(player, ability.get().id());
            if (blocking != null && !stoppable) return Optional.of(blocking);
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
        return activate(player, slot, freshPress, java.util.Map.of());
    }

    /** @param extra more blackboard values for the cast (e.g. how far a bow was drawn: {@code draw}) */
    public ActivationResult activate(UUID player, String slot, boolean freshPress, java.util.Map<String, Object> extra) {
        Optional<CharacterDef> character = characterOf(player);
        if (character.isEmpty()) return ActivationResult.fail("no_character");
        String abilityId = character.get().abilityIn(slot);
        if (abilityId == null) return ActivationResult.fail("empty_slot:" + slot);
        var blocked = slotBlocked(player, slot);
        if (blocked.isPresent()) return blocked.get();
        java.util.Map<String, Object> presets = new java.util.HashMap<>(extra);
        presets.put(Keys.SLOT.name(), slot);
        return activator.activate(player, abilityId, freshPress, presets);
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

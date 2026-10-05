package me.mephisto.ability_engine.engine.ability.activation;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.Ability;
import me.mephisto.ability_engine.engine.ability.AbilityInstance;

import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.nodes.control.AwaitRecastNode;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.targeting.AimPoint;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The one place that turns "player pressed a button" into a running cast.
 * Replaces CasterPolicy and the check -> commit -> execute code duplicated in the listener and command.
 * Naming follows Unreal GAS: check (CanActivateAbility), commit (CommitAbility), activate.
 */
public final class AbilityActivator {

    /** How long an early recast press is kept for the window to open (0.4s). */
    public static final int RECAST_BUFFER_TICKS = 8;
    /** The failure reason for a press kept for a recast window that isn't open yet: nothing to report. */
    public static final String BUFFERED = "buffered";
    /** The failure reason for a press of an ability that's passive right now (passive_while): nothing to report. */
    public static final String PASSIVE = "passive";
    /** The failure reason for an ability with nothing to use (needs_constructs, none standing): its icon is greyed out. */
    public static final String UNAVAILABLE = "unavailable";
    /** The failure reason prefix for an ultimate still charging up ("ult_charging:63%"). */
    public static final String ULT_CHARGING = "ult_charging";

    private final AbilityEngine engine;

    public AbilityActivator(AbilityEngine engine) {
        this.engine = engine;
    }

    /** Is the ability passive for {@code caster} right now (passive_while): in effect anyway, its key does nothing? */
    public boolean isPassive(UUID caster, Ability ability) {
        return ability.passiveWhile() != null && engine.tags().has(caster, ability.passiveWhile());
    }

    /**
     * How many constructs it can use right now (needs_constructs: the caster's standing constructs from that ability),
     * or -1 if it doesn't need any.
     */
    public int constructsFor(UUID caster, Ability ability) {
        if (ability.needsConstructs() == null) return -1;
        int total = 0;
        for (String from : ability.needsConstructs().split(",")) { // (a list: any of those abilities' constructs)
            String id = from.trim();
            total += engine.constructs().count(caster, id);
            if (ability.alsoFlying() != null) total += engine.projectiles().countFlying(caster, id, ability.alsoFlying());
        }
        return total;
    }

    /** Nothing to use right now (needs_constructs, none standing): it can't be cast, and its icon is greyed out. */
    public boolean isUnavailable(UUID caster, Ability ability) {
        return constructsFor(caster, ability) == 0;
    }

    /** Could {@code caster} activate this right now? Side-effect free — safe for UI (greying out hotbar slots). */
    public ActivationResult check(UUID caster, Ability ability) {
        if (isPassive(caster, ability)) return ActivationResult.fail(PASSIVE);
        if (isUnavailable(caster, ability)) return ActivationResult.fail(UNAVAILABLE);
        if (ability.mode().exclusive() && engine.instances().isRunning(caster, ability.id())) {
            return ActivationResult.fail("already_active");
        }
        long cd = engine.cooldowns().remainingTicks(caster, ability.id());
        if (cd > 0) return ActivationResult.fail(String.format("on_cooldown:%.1fs", cd / 20.0));
        // An ultimate that charges up: not before 100%.
        if (engine.ultCharge().gates(caster, ability.id()) && !engine.ultCharge().ready(caster)) {
            return ActivationResult.fail(String.format(java.util.Locale.ROOT, "%s:%.0f%%", ULT_CHARGING,
                    Math.floor(engine.ultCharge().of(caster))));
        }

        String blocking = engine.tags().firstMatch(caster, ability.blockedBy());
        if (blocking != null) return ActivationResult.fail("blocked:" + blocking);

        for (Map.Entry<String, Integer> cost : ability.costs().entrySet()) {
            if (!engine.resources().has(caster, cost.getKey(), cost.getValue())) {
                return ActivationResult.fail("insufficient:" + cost.getKey());
            }
        }
        return ActivationResult.ok();
    }

    /** Pay the costs and start the cooldown. */
    public void commit(UUID caster, Ability ability) {
        engine.cooldowns().start(caster, ability.id(), engine.stats().cooldownTicks(caster, ability.id(), ability.cooldownTicks()));
        ability.costs().forEach((resource, amount) -> engine.resources().consume(caster, resource, amount));
    }

    public ActivationResult activate(UUID caster, String abilityId) {
        return activate(caster, abilityId, true);
    }

    public ActivationResult activate(UUID caster, String abilityId, boolean freshPress) {
        return activate(caster, abilityId, freshPress, Map.of());
    }

    /** @param presets extra blackboard values for the cast (e.g. which slot it came from) */
    public ActivationResult activate(UUID caster, String abilityId, boolean freshPress, Map<String, Object> presets) {
        Optional<Ability> found = engine.abilities().find(abilityId);
        if (found.isEmpty()) return ActivationResult.fail("unknown_ability:" + abilityId);
        return activate(caster, found.get(), freshPress, presets);
    }

    public ActivationResult activate(UUID caster, Ability ability) {
        return activate(caster, ability, true);
    }

    public ActivationResult activateOnId(UUID caster, String abilityId, Target target, Map<String, Object> presets) {
        Optional<Ability> found = engine.abilities().find(abilityId);
        if (found.isEmpty()) return ActivationResult.fail("unknown_ability:" + abilityId);
        return activateOn(caster, found.get(), target, presets);
    }

    /**
     * @param freshPress false for auto-repeat (holding right click). Held input never recasts,
     *                   otherwise holding would recast ~4 ticks after the first cast.
     */
    public ActivationResult activate(UUID caster, Ability ability, boolean freshPress) {
        return activate(caster, ability, freshPress, Map.of());
    }

    public ActivationResult activate(UUID caster, Ability ability, boolean freshPress, Map<String, Object> presets) {
        // Passive right now (an ultimate keeps it up): the key does nothing at all, not even switching an aim.
        if (isPassive(caster, ability)) return ActivationResult.fail(PASSIVE);
        // Aiming something already? Its own key does nothing (only LMB confirms, via TargetingManager.confirm).
        // Others switch.
        Optional<Ability> aiming = engine.targeting().current(caster);
        if (aiming.isPresent()) {
            if (aiming.get().id().equals(ability.id())) return ActivationResult.targeting();
            if (!freshPress) return ActivationResult.fail("held");
            engine.targeting().cancel(caster, "switched");
        }

        // Toggle-off abilities (meditation): pressing the key again ends the running one early.
        if (ability.cancelOnRepress()) {
            for (AbilityInstance running : engine.instances().of(caster)) {
                if (running.ability().id().equals(ability.id())) {
                    if (!freshPress) return ActivationResult.fail("held");
                    running.cancel("cancelled_by_caster");
                    return ActivationResult.ok();
                }
            }
        }

        // A running hold of this ability: the input is still held, keep it going (every repeat, held or not).
        if (ability.mode() instanceof HoldActivation) {
            for (AbilityInstance running : engine.instances().of(caster)) {
                if (running.ability().id().equals(ability.id()) && running.keepAlive()) return ActivationResult.ok();
            }
        }

        // A charge of this ability that follows its input's repeats (release_gap): a repeat means still
        // held; a fresh press lets the old charge go, then this press casts as usual.
        for (AbilityInstance running : java.util.List.copyOf(engine.instances().of(caster))) {
            if (running.ability().id().equals(ability.id()) && running.chargingOnRepeats()) {
                if (!freshPress) {
                    running.inputRepeated();
                    return ActivationResult.ok();
                }
                running.release();
                if (engine.ultCharge().gates(caster, ability.id())) return ActivationResult.ok(); // (no second ultimate)
            } else if (running.ability().id().equals(ability.id()) && running.charging()) {
                // A charge that waits to be let go (no input repeats): pressing its key again lets it go. Held: nothing.
                if (freshPress) running.release();
                return ActivationResult.ok();
            }
        }

        // Recast comes first: it's free. While a window is open, a HELD key does nothing: it must never
        // recast, and (the cooldown not running yet) it must not start a second cast either.
        for (AbilityInstance running : engine.instances().of(caster)) {
            if (running.ability().id().equals(ability.id()) && running.awaitingRecast()) {
                if (!freshPress) return ActivationResult.fail("held");
                String blocking = engine.tags().firstMatch(caster, ability.blockedBy());
                if (blocking != null) return ActivationResult.fail("blocked:" + blocking);
                // A recast that moves you (a swap, a charge) waits out a root; the window stays open.
                if (ability.recastMovement() && engine.tags().has(caster, Tags.BLOCK_MOVE)) {
                    return ActivationResult.fail("blocked:" + Tags.BLOCK_MOVE);
                }
                running.recast();
                return ActivationResult.ok();
            }
        }

        // Pressed again before the recast window opened (mid-dash, the orb still spawning): keep the press
        // for a moment and recast as soon as the window opens, rather than dropping it.
        // (Its cooldown waits for that window, so this used to start a second, free cast instead.)
        // A held key's repeats meanwhile do nothing either: they never recast, and must not start a second cast.
        AbilityInstance early = null;
        if (ability.graph().anyNode(n -> n instanceof AwaitRecastNode)) {
            for (AbilityInstance running : engine.instances().of(caster)) {
                if (running.ability().id().equals(ability.id()) && running.isActive()) early = running;
            }
            boolean waitsForIt = early != null && early.cooldownPending() && ability.charges() <= 1
                    && (ability.cooldownAfterRecast() || ability.manualCooldown());
            if (!freshPress && waitsForIt) return ActivationResult.fail("held");
            if (!freshPress) early = null;
            if (early != null) {
                early.bufferRecast(engine.clock().now() + RECAST_BUFFER_TICKS);
                if (waitsForIt) return ActivationResult.fail(BUFFERED);
            }
        }

        ActivationResult check = check(caster, ability);
        if (!check.success()) return check;
        if (early != null) early.takeBufferedRecast(); // it can cast again anyway: a new cast, not a recast

        // Abilities with a preview open a targeting session instead: nothing is spent yet.
        if (ability.targeting() != null && !engine.targeting().isQuickCast(caster)) {
            engine.targeting().open(caster, ability, presets);
            return ActivationResult.targeting();
        }

        Optional<PointTarget> aim = AimPoint.resolve(engine, caster, ability.targeting());
        if (aim.isEmpty() && ability.targeting() != null && ability.targeting().ground()) {
            return ActivationResult.fail("no_ground"); // quick cast into the air: refuse, spend nothing
        }
        return start(caster, ability, withAim(presets, aim.orElse(null)));
    }

    private static Map<String, Object> withAim(Map<String, Object> presets, PointTarget aim) {
        Map<String, Object> all = new java.util.HashMap<>(presets);
        if (aim != null) all.put(Keys.AIM.name(), aim);
        return all;
    }

    /**
     * Cast at a confirmed spot (targeting confirm). Checks again: the caster may have been stunned
     * or run out of mana while aiming.
     */
    public ActivationResult activateAt(UUID caster, Ability ability, PointTarget aim) {
        return activateAt(caster, ability, aim, Map.of());
    }

    public ActivationResult activateAt(UUID caster, Ability ability, PointTarget aim, Map<String, Object> presets) {
        ActivationResult check = check(caster, ability);
        if (!check.success()) return check;
        return start(caster, ability, withAim(presets, aim));
    }

    /**
     * Cast at a specific entity (a melee click): "target" is that entity and "aim" its position,
     * so the graph can hit exactly what was clicked.
     */
    public ActivationResult activateOn(UUID caster, Ability ability, Target target) {
        return activateOn(caster, ability, target, Map.of());
    }

    public ActivationResult activateOn(UUID caster, Ability ability, Target target, Map<String, Object> extra) {
        ActivationResult check = check(caster, ability);
        if (!check.success()) return check;
        Map<String, Object> presets = new java.util.HashMap<>(extra);
        presets.put(Keys.TARGET.name(), target);
        engine.world().positionOf(target).ifPresent(p -> presets.put(Keys.AIM.name(), p));
        return start(caster, ability, presets);
    }

    private ActivationResult start(UUID caster, Ability ability, Map<String, Object> presets) {
        ability.costs().forEach((resource, amount) -> engine.resources().consume(caster, resource, amount));
        if (engine.ultCharge().gates(caster, ability.id())) engine.ultCharge().spend(caster); // the ultimate: all of it
        AbilityInstance instance = new AbilityInstance(engine, ability, caster, presets);
        if (ability.manualCooldown()) instance.deferCooldownManually();
        else if (ability.cooldownAfterRecast()) instance.deferCooldown();
        else engine.cooldowns().start(caster, ability.id(), engine.stats().cooldownTicks(caster, ability.id(), ability.cooldownTicks()));
        engine.instances().add(instance);
        instance.start();
        return ActivationResult.ok();
    }
}

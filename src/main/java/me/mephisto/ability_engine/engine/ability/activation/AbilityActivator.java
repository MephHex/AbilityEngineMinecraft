package me.mephisto.ability_engine.engine.ability.activation;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.Ability;
import me.mephisto.ability_engine.engine.ability.AbilityInstance;

import me.mephisto.ability_engine.engine.graph.Keys;
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

    private final AbilityEngine engine;

    public AbilityActivator(AbilityEngine engine) {
        this.engine = engine;
    }

    /** Could {@code caster} activate this right now? Side-effect free — safe for UI (greying out hotbar slots). */
    public ActivationResult check(UUID caster, Ability ability) {
        if (ability.mode().exclusive() && engine.instances().isRunning(caster, ability.id())) {
            return ActivationResult.fail("already_active");
        }
        long cd = engine.cooldowns().remainingTicks(caster, ability.id());
        if (cd > 0) return ActivationResult.fail(String.format("on_cooldown:%.1fs", cd / 20.0));

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
        // Aiming something already? Its own key does nothing (holding a key auto-repeats it, which the
        // server can't tell from a real second press, so it must never confirm). Other abilities switch.
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

        // Recast comes first: it's free. While a window is open, a HELD key does nothing: it must never
        // recast, and (the cooldown not running yet) it must not start a second cast either.
        for (AbilityInstance running : engine.instances().of(caster)) {
            if (running.ability().id().equals(ability.id()) && running.awaitingRecast()) {
                if (!freshPress) return ActivationResult.fail("held");
                String blocking = engine.tags().firstMatch(caster, ability.blockedBy());
                if (blocking != null) return ActivationResult.fail("blocked:" + blocking);
                running.recast();
                return ActivationResult.ok();
            }
        }

        ActivationResult check = check(caster, ability);
        if (!check.success()) return check;

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
        AbilityInstance instance = new AbilityInstance(engine, ability, caster, presets);
        if (ability.manualCooldown()) instance.deferCooldownManually();
        else if (ability.cooldownAfterRecast()) instance.deferCooldown();
        else engine.cooldowns().start(caster, ability.id(), engine.stats().cooldownTicks(caster, ability.id(), ability.cooldownTicks()));
        engine.instances().add(instance);
        instance.start();
        return ActivationResult.ok();
    }
}

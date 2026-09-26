package me.mephisto.ability_engine.engine.targeting;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.Ability;
import me.mephisto.ability_engine.engine.ability.activation.ActivationResult;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Open aiming sessions, one per player at most. One ticker draws every preview and stops itself
 * when nobody is aiming. Sessions end on confirm, cancel, when the caster becomes unable to cast
 * (stun, silence), when they're gone, or after the ability's optional timeout (none by default).
 */
public final class TargetingManager {

    private static final class Session {
        final Ability ability;
        final long openedAt;
        final Map<String, Object> presets; // e.g. which slot it was cast from, carried to the confirmed cast

        Session(Ability ability, long openedAt, Map<String, Object> presets) {
            this.ability = ability;
            this.openedAt = openedAt;
            this.presets = Map.copyOf(presets);
        }
    }

    private final AbilityEngine engine;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> quickCast = new HashSet<>();
    private TaskHandle ticker;

    public TargetingManager(AbilityEngine engine) {
        this.engine = engine;
    }

    public boolean isTargeting(UUID caster) { return sessions.containsKey(caster); }

    /** Ticks since the caster's preview opened, or -1 if they aren't aiming. */
    public long ageTicks(UUID caster) {
        Session s = sessions.get(caster);
        return s == null ? -1 : engine.clock().now() - s.openedAt;
    }

    public Optional<Ability> current(UUID caster) {
        Session s = sessions.get(caster);
        return s == null ? Optional.empty() : Optional.of(s.ability);
    }

    /** Quick cast: abilities with targeting fire instantly at the crosshair, like abilities without it. */
    public boolean isQuickCast(UUID caster) { return quickCast.contains(caster); }

    public boolean toggleQuickCast(UUID caster) {
        if (!quickCast.remove(caster)) quickCast.add(caster);
        return quickCast.contains(caster);
    }

    /** Called by the activator after its checks passed. Replaces any open session. */
    public void open(UUID caster, Ability ability) {
        open(caster, ability, Map.of());
    }

    public void open(UUID caster, Ability ability, Map<String, Object> presets) {
        if (sessions.containsKey(caster)) cancel(caster, "switched");
        sessions.put(caster, new Session(ability, engine.clock().now(), presets));
        draw(caster, sessions.get(caster));
        if (ticker == null) ticker = engine.scheduler().every(1, 1, this::tick);
    }

    /** Confirm at the current crosshair: re-checks, then spends the cooldown/costs and casts. */
    public ActivationResult confirm(UUID caster) {
        Session s = sessions.get(caster);
        if (s == null) return ActivationResult.fail("not_targeting");
        Optional<PointTarget> aim = AimPoint.resolve(engine, caster, s.ability.targeting());
        if (aim.isEmpty()) {
            // Invalid spot: keep aiming, spend nothing. Like trying to place a wall where it can't go.
            return ActivationResult.fail(s.ability.targeting().ground() ? "no_ground" : "no_aim");
        }
        sessions.remove(caster);
        engine.indicators().clear(caster, "confirmed");
        return engine.activator().activateAt(caster, s.ability, aim.get(), s.presets);
    }

    public void cancel(UUID caster, String reason) {
        if (sessions.remove(caster) != null) engine.indicators().clear(caster, reason);
    }

    public void cancelAll(String reason) {
        for (UUID caster : List.copyOf(sessions.keySet())) cancel(caster, reason);
    }

    private void tick() {
        long now = engine.clock().now();
        for (UUID caster : List.copyOf(sessions.keySet())) {
            Session s = sessions.get(caster);
            if (s == null) continue;
            if (!engine.world().isAlive(caster)) {
                cancel(caster, "gone");
            } else if (s.ability.targeting().timeoutTicks() > 0 && now - s.openedAt >= s.ability.targeting().timeoutTicks()) {
                cancel(caster, "timeout");
            } else if (engine.tags().firstMatch(caster, s.ability.blockedBy()) != null) {
                cancel(caster, "blocked");
            } else {
                draw(caster, s);
            }
        }
        if (sessions.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void draw(UUID caster, Session s) {
        Optional<Aim> from = engine.world().aimOf(caster);
        if (from.isEmpty()) return;
        Optional<PointTarget> valid = AimPoint.resolve(engine, caster, s.ability.targeting());
        Optional<PointTarget> at = valid.isPresent() ? valid : AimPoint.raw(engine, caster, s.ability.targeting());
        at.ifPresent(p -> engine.indicators().draw(caster, s.ability.display().name(), s.ability.targeting(),
                from.get(), p, valid.isPresent()));
    }
}

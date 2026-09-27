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
        final Targeting targeting;
        final long openedAt;
        final Map<String, Object> presets; // e.g. which slot it was cast from, carried to the confirmed cast
        final InCast inCast;               // null = before a cast (confirm casts it); else a running cast is waiting

        Session(Ability ability, Targeting targeting, long openedAt, Map<String, Object> presets, InCast inCast) {
            this.ability = ability;
            this.targeting = targeting;
            this.openedAt = openedAt;
            this.presets = Map.copyOf(presets);
            this.inCast = inCast;
        }
    }

    /** A running cast choosing a spot (choose_spot): told the confirmed spot, or why it ended without one. */
    private record InCast(java.util.function.Consumer<PointTarget> confirmed, java.util.function.Consumer<String> cancelled) {}

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
        start(caster, new Session(ability, ability.targeting(), engine.clock().now(), presets, null));
    }

    /**
     * An aim preview in the middle of a cast (choose_spot): LMB confirms (-> {@code confirmed}); RMB,
     * another ability, the timeout or the cast ending cancel it (-> {@code cancelled}, with the reason).
     */
    public void openInCast(UUID caster, Ability ability, Targeting targeting,
                           java.util.function.Consumer<PointTarget> confirmed, java.util.function.Consumer<String> cancelled) {
        start(caster, new Session(ability, targeting, engine.clock().now(), Map.of(), new InCast(confirmed, cancelled)));
    }

    /** Close a mid-cast preview without calling back (its cast ended). */
    public void cancelInCast(UUID caster) {
        Session s = sessions.get(caster);
        if (s != null && s.inCast != null) {
            sessions.remove(caster);
            engine.indicators().clear(caster, "cast_ended");
        }
    }

    private void start(UUID caster, Session session) {
        if (sessions.containsKey(caster)) cancel(caster, "switched");
        sessions.put(caster, session);
        draw(caster, session);
        if (ticker == null) ticker = engine.scheduler().every(1, 1, this::tick);
    }

    /** Confirm at the current crosshair: re-checks, then spends the cooldown/costs and casts. */
    public ActivationResult confirm(UUID caster) {
        Session s = sessions.get(caster);
        if (s == null) return ActivationResult.fail("not_targeting");
        Optional<PointTarget> aim = AimPoint.resolve(engine, caster, s.targeting);
        if (aim.isEmpty()) {
            // Invalid spot: keep aiming, spend nothing. Like trying to place a wall where it can't go.
            return ActivationResult.fail(s.targeting.ground() ? "no_ground" : "no_aim");
        }
        sessions.remove(caster);
        engine.indicators().clear(caster, "confirmed");
        if (s.inCast != null) {
            s.inCast.confirmed().accept(aim.get());
            return ActivationResult.ok();
        }
        return engine.activator().activateAt(caster, s.ability, aim.get(), s.presets);
    }

    public void cancel(UUID caster, String reason) {
        Session s = sessions.remove(caster);
        if (s == null) return;
        engine.indicators().clear(caster, reason);
        if (s.inCast != null) s.inCast.cancelled().accept(reason);
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
            } else if (s.targeting.timeoutTicks() > 0 && now - s.openedAt >= s.targeting.timeoutTicks()) {
                cancel(caster, "timeout");
            } else if (s.inCast == null && engine.tags().firstMatch(caster, s.ability.blockedBy()) != null) {
                // (a mid-cast preview belongs to the cast: its own block.ability doesn't close it)
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
        Optional<PointTarget> valid = AimPoint.resolve(engine, caster, s.targeting);
        Optional<PointTarget> at = valid.isPresent() ? valid : AimPoint.raw(engine, caster, s.targeting);
        at.ifPresent(p -> engine.indicators().draw(caster, s.ability.display().name(), s.targeting,
                from.get(), p, valid.isPresent()));
    }
}

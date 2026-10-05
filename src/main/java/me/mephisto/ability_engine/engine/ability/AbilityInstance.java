package me.mephisto.ability_engine.engine.ability;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.graph.Blackboard;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One cast of an ability, from activation until everything it started has finished.
 * The single handle to cancel a cast — including in-flight projectiles, delays and channels.
 *
 * <p>Lifetime is tracked by counting open branches: the root run, every forked projectile,
 * and a running channel each hold one. When the count reaches zero the instance completes.
 */
public final class AbilityInstance {

    public enum State { RUNNING, COMPLETED, CANCELLED }

    private static final AtomicLong IDS = new AtomicLong();

    private final long id = IDS.incrementAndGet();
    private final AbilityEngine engine;
    private final Ability ability;
    private final UUID caster;
    private final long startedAt;
    private final List<Runnable> onEnd = new ArrayList<>();

    private State state = State.RUNNING;
    private int openBranches;
    private String endReason;
    private boolean started;
    private Runnable recastHandler;
    private long recastBufferedUntil = Long.MIN_VALUE;
    private boolean cooldownPending;
    private boolean tagsReleased;
    private boolean landed; // a hit of this cast has charged the ultimate already (UltimateCharge)
    private Runnable keepAlive;
    private java.util.function.DoubleSupplier gauge;
    private CastProgress progress;

    /** A timed phase of this cast the HUD should show as a filling bar (wind-ups, channels). */
    public record CastProgress(long startTick, long durationTicks) {}

    private final java.util.Map<String, Object> presets;

    public AbilityInstance(AbilityEngine engine, Ability ability, UUID caster) {
        this(engine, ability, caster, java.util.Map.of());
    }

    /** @param presets blackboard values every branch starts with (e.g. "aim") */
    public AbilityInstance(AbilityEngine engine, Ability ability, UUID caster, java.util.Map<String, Object> presets) {
        this.engine = engine;
        this.ability = ability;
        this.caster = caster;
        this.startedAt = engine.clock().now();
        this.presets = java.util.Map.copyOf(presets);
    }

    public long id() { return id; }
    public AbilityEngine engine() { return engine; }
    public Ability ability() { return ability; }
    public UUID caster() { return caster; }
    public long startedAt() { return startedAt; }
    public State state() { return state; }
    public String endReason() { return endReason; }
    public boolean isActive() { return state == State.RUNNING; }

    /** Grants active tags and hands control to the activation mode. Called once by the activator. */
    public void start() {
        if (started) throw new IllegalStateException("instance already started");
        started = true;
        engine.tags().grantAll(caster, ability.activeTags());
        if (ability.aura() != null) {
            var aura = engine.cuesFor(caster).start(ability.aura(), caster);
            onEnd(aura::stop);
        }
        openBranch(); // guard branch: stops the instance completing while the mode is still starting up
        ability.mode().start(this);
        closeBranch();
    }

    /**
     * Started from outside its graph (no active tags, no aura, nothing run from its start node): a copy of someone
     * else's projectile (a reflected shot) running this ability's hit logic as this caster. The returned root branch
     * is open: fork from it, then close it.
     */
    public ExecutionContext adopt() {
        if (started) throw new IllegalStateException("instance already started");
        started = true;
        return newBranch();
    }

    /** A fresh root branch with a new blackboard containing the caster. */
    public ExecutionContext newBranch() {
        openBranch();
        ExecutionContext ctx = new ExecutionContext(this, new Blackboard());
        ctx.put(Keys.CASTER, caster);
        presets.forEach(ctx.blackboard()::putRaw);
        return ctx;
    }

    public void openBranch() {
        if (isActive()) openBranches++;
    }

    public void closeBranch() {
        if (!isActive()) return;
        if (--openBranches <= 0) end(State.COMPLETED, "completed");
    }

    public void cancel(String reason) { end(State.CANCELLED, reason); }

    public boolean tagsReleased() { return tagsReleased; }

    /** Drop the active tags before the cast ends (see ReleaseTagsNode). */
    public void releaseActiveTags() {
        if (tagsReleased || !isActive()) return;
        tagsReleased = true;
        engine.tags().revokeAll(caster, ability.activeTags());
    }

    // ---- deferred cooldown (recast abilities) -----------------------------------------------------

    /** The cooldown waits for the recast window to close (see Ability.cooldownAfterRecast). */
    public void deferCooldown() { cooldownPending = true; }

    private boolean cooldownOnEnd = true;

    /** Its cooldown is still waiting (for the recast window, or a start_cooldown node). */
    public boolean cooldownPending() { return cooldownPending; }

    /** The cooldown waits for a start_cooldown node; a cast that ends without one costs no cooldown. */
    public void deferCooldownManually() {
        cooldownPending = true;
        cooldownOnEnd = false;
    }

    /** Start the deferred cooldown now (window closed). Safe to call more than once. */
    public void startDeferredCooldown() {
        if (!cooldownPending) return;
        cooldownPending = false;
        engine.cooldowns().start(caster, ability.id(), engine.stats().cooldownTicks(caster, ability.id(), ability.cooldownTicks()));
    }

    // ---- cast bar --------------------------------------------------------------------------------

    /** Start showing a bar that fills over {@code durationTicks}. Returns a token for {@link #clearProgress}. */
    public CastProgress showProgress(long durationTicks) {
        return showProgress(engine.clock().now(), durationTicks);
    }

    /** Like {@link #showProgress(long)}, for a phase that began at {@code startTick} (shown part-filled). */
    public CastProgress showProgress(long startTick, long durationTicks) {
        progress = new CastProgress(startTick, Math.max(1, durationTicks));
        return progress;
    }

    /** Hide the bar, but only if {@code token} is still the one showing. */
    public void clearProgress(CastProgress token) {
        if (progress == token) progress = null;
    }

    /** 0..1 while a bar is showing and the cast is running; empty otherwise. */
    public Optional<Double> progressFraction() {
        if (!isActive()) return Optional.empty();
        if (gauge != null) return Optional.of(Math.max(0, Math.min(1, gauge.getAsDouble())));
        CastProgress p = progress;
        if (p == null) return Optional.empty();
        double f = (engine.clock().now() - p.startTick()) / (double) p.durationTicks();
        return Optional.of(Math.max(0, Math.min(1, f)));
    }

    /** Show a live value (e.g. a resource draining) on the cast bar instead of timed progress. */
    public void showGauge(java.util.function.DoubleSupplier gauge) { this.gauge = gauge; }

    // ---- hold (set by a hold activation while it runs) -----------------------------------------

    public void setKeepAlive(Runnable keepAlive) { this.keepAlive = keepAlive; }

    /** The held input is still held. Returns false if this instance isn't a running hold. */
    public boolean keepAlive() {
        if (!isActive() || keepAlive == null) return false;
        keepAlive.run();
        return true;
    }

    // ---- recast (set by an await_recast node while it waits) ----------------------------------

    public void setRecastHandler(Runnable handler) { this.recastHandler = handler; }

    /** Clears the handler only if it is still {@code handler}, so a newer waiter isn't removed. */
    public void clearRecastHandler(Runnable handler) {
        if (recastHandler == handler) recastHandler = null;
    }

    public boolean awaitingRecast() { return isActive() && recastHandler != null; }

    /** Deliver a recast press. One-shot: the waiter must re-register to accept another. */
    public void recast() {
        Runnable h = recastHandler;
        recastHandler = null;
        progress = null;
        keepAlive = null;
        gauge = null;
        if (h != null && isActive()) h.run();
    }

    /**
     * A recast press that came before the window opened (mid-dash, while the orb is still spawning): the
     * window that opens by {@code untilTick} takes it, instead of the press being lost.
     */
    public void bufferRecast(long untilTick) { recastBufferedUntil = untilTick; }

    /** A buffered recast press still in time? One-shot: taking it clears it. */
    public boolean takeBufferedRecast() {
        boolean pending = engine.clock().now() <= recastBufferedUntil;
        recastBufferedUntil = Long.MIN_VALUE;
        return pending;
    }

    // ---- charge (set by a charge node while it charges) -----------------------------------------

    private Runnable releaseHandler;

    public void setReleaseHandler(Runnable handler) { this.releaseHandler = handler; }

    public void clearReleaseHandler(Runnable handler) {
        if (releaseHandler == handler) releaseHandler = null;
    }

    public boolean charging() { return isActive() && releaseHandler != null; }

    /** The first hit of this cast to land: true once (it charges the ultimate), false for every later one. */
    public boolean markLanded() {
        if (landed) return false;
        landed = true;
        return true;
    }

    /** A charge that watches its input's repeats (charge's release_gap): told each time the input repeats. */
    private Runnable inputRepeat;

    public void setInputRepeat(Runnable onRepeat) { this.inputRepeat = onRepeat; }

    public void clearInputRepeat(Runnable onRepeat) {
        if (inputRepeat == onRepeat) inputRepeat = null;
    }

    /** Charging, and it learns about letting go from the input's repeats stopping (not from the platform). */
    public boolean chargingOnRepeats() { return charging() && inputRepeat != null; }

    /** The held input repeated (still held). Returns false if nothing is watching. */
    public boolean inputRepeated() {
        if (!chargingOnRepeats()) return false;
        lastInputRepeat = engine.clock().now();
        inputRepeat.run();
        return true;
    }

    /** When the input last repeated while a charge listened (Long.MIN_VALUE = never): see input_held. */
    private long lastInputRepeat = Long.MIN_VALUE;

    public long lastInputRepeat() { return lastInputRepeat; }

    /** The held input was let go: fire the charge. One-shot. */
    public void release() {
        Runnable h = releaseHandler;
        releaseHandler = null;
        if (h != null && isActive()) h.run();
    }

    // ---- kills (set by an await_kill node while it waits) ---------------------------------------

    /** Called with (victim, victim is a player). */
    private java.util.function.BiConsumer<UUID, Boolean> killHandler;

    public void setKillHandler(java.util.function.BiConsumer<UUID, Boolean> handler) { this.killHandler = handler; }

    public void clearKillHandler(java.util.function.BiConsumer<UUID, Boolean> handler) {
        if (killHandler == handler) killHandler = null;
    }

    /** The caster got a kill. One-shot: the waiter must re-register to hear another. */
    public void kill(UUID victim, boolean victimIsPlayer) {
        var h = killHandler;
        killHandler = null;
        if (h != null && isActive()) h.accept(victim, victimIsPlayer);
    }

    // ---- timer (a boss bar counting down, e.g. an ultimate's duration) --------------------------

    private CastProgress timer;

    /** Show a timer running out over {@code durationTicks}. Returns a token for {@link #clearTimer}. */
    public CastProgress showTimer(long durationTicks) {
        timer = new CastProgress(engine.clock().now(), Math.max(1, durationTicks));
        return timer;
    }

    public void clearTimer(CastProgress token) {
        if (timer == token) timer = null;
    }

    /** 1..0: how much of the timer is left, while one is showing and the cast is running. */
    public Optional<Double> timerLeft() {
        CastProgress t = timer;
        if (!isActive() || t == null) return Optional.empty();
        double f = 1 - (engine.clock().now() - t.startTick()) / (double) t.durationTicks();
        return Optional.of(Math.max(0, Math.min(1, f)));
    }

    /** Run when the instance ends for any reason. Hooks must be safe to run more than once. */
    public void onEnd(Runnable hook) {
        if (isActive()) onEnd.add(hook);
        else hook.run();
    }

    private void end(State newState, String reason) {
        if (state != State.RUNNING) return;
        state = newState;
        endReason = reason;
        if (cooldownOnEnd) startDeferredCooldown(); // e.g. stunned during the recast window: no free cast
        recastHandler = null;
        releaseHandler = null;
        killHandler = null;
        timer = null;
        progress = null;
        keepAlive = null;
        gauge = null;
        for (Runnable hook : List.copyOf(onEnd)) {
            try {
                hook.run();
            } catch (RuntimeException e) {
                engine.log().error("end hook failed for " + ability.id(), e);
            }
        }
        onEnd.clear();
        if (!tagsReleased) engine.tags().revokeAll(caster, ability.activeTags());
        engine.instances().remove(this);
        engine.log().debug(() -> ability.id() + "#" + id + " " + newState + " (" + reason + ")");
    }

    @Override
    public String toString() { return ability.id() + "#" + id + "[" + state + "]"; }
}

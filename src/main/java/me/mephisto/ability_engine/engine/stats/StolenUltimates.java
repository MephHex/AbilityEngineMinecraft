package me.mephisto.ability_engine.engine.stats;

import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.platform.GameClock;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stolen ultimates (the Fae Trickster's Stolen Fable, the {@code steal_ultimate} node): a copy of someone else's ultimate
 * sits in the thief's ultimate slot for one use.
 * <ul>
 *   <li>Until it's cast it's free: no charge and no cooldown needed. Unused for its window, it's lost.</li>
 *   <li>Cast, it stays in the slot while that cast runs (so its recasts, e.g. "F again", still work), but it can't be
 *       cast a second time. When that cast ends, however it ends, the slot holds their own ultimate again.</li>
 *   <li>A new character, death, or a new steal ends it.</li>
 * </ul>
 */
public final class StolenUltimates {

    private final LoadoutManager loadouts;
    private final TaskScheduler scheduler;
    private final GameClock clock;
    private final Map<UUID, Borrowed> borrowed = new HashMap<>();

    /** One stolen ultimate: which ability, until when it can be cast, and whether it has been. */
    private static final class Borrowed {
        final String ability;
        final long until;
        TaskHandle expiry;
        boolean used;

        Borrowed(String ability, long until) {
            this.ability = ability;
            this.until = until;
        }
    }

    /** Told when a stolen ultimate goes (used, lost or replaced): thief, ability. */
    private java.util.function.BiConsumer<UUID, String> onRelease = (thief, ability) -> {};

    public void setOnRelease(java.util.function.BiConsumer<UUID, String> listener) { this.onRelease = listener; }

    public StolenUltimates(LoadoutManager loadouts, TaskScheduler scheduler, GameClock clock) {
        this.loadouts = loadouts;
        this.scheduler = scheduler;
        this.clock = clock;
        loadouts.onAssign(this::release); // a new character: nothing borrowed
    }

    /** {@code thief}'s ultimate slot holds {@code abilityId} for one use, castable for {@code windowTicks}. */
    public void lend(UUID thief, String abilityId, int windowTicks) {
        release(thief);
        Borrowed b = new Borrowed(abilityId, clock.now() + windowTicks);
        borrowed.put(thief, b);
        loadouts.override(thief, Slots.ULTIMATE, abilityId);
        b.expiry = scheduler.after(Math.max(1, windowTicks), () -> {
            if (borrowed.get(thief) == b && !b.used) release(thief);
        });
    }

    /** The stolen ultimate {@code thief} holds right now, cast or not (empty: none). */
    public java.util.Optional<String> heldBy(UUID thief) {
        Borrowed b = borrowed.get(thief);
        return b == null ? java.util.Optional.empty() : java.util.Optional.of(b.ability);
    }

    /** Does {@code thief} hold a stolen {@code abilityId} (cast or not)? */
    public boolean holds(UUID thief, String abilityId) {
        Borrowed b = borrowed.get(thief);
        return b != null && b.ability.equals(abilityId);
    }

    /** Can {@code thief} cast the stolen {@code abilityId} right now (held and not cast yet)? */
    public boolean isFree(UUID thief, String abilityId) {
        Borrowed b = borrowed.get(thief);
        return b != null && !b.used && b.ability.equals(abilityId);
    }

    /** Held and already cast: its one use is running. */
    public boolean isSpent(UUID thief, String abilityId) {
        Borrowed b = borrowed.get(thief);
        return b != null && b.used && b.ability.equals(abilityId);
    }

    /** Ticks left to cast it (0 once cast, or with nothing stolen). */
    public long ticksLeft(UUID thief) {
        Borrowed b = borrowed.get(thief);
        return b == null || b.used ? 0 : Math.max(0, b.until - clock.now());
    }

    /** The stolen ultimate's one cast started: it's spent, and the slot goes back when {@code cast} ends. */
    public void cast(UUID thief, AbilityInstance cast) {
        Borrowed b = borrowed.get(thief);
        if (b == null || b.used) return;
        b.used = true;
        if (b.expiry != null) b.expiry.cancel();
        cast.onEnd(() -> {
            if (borrowed.get(thief) == b) release(thief);
        });
    }

    /** Whatever they hold goes: the slot holds their own ultimate again. */
    public void release(UUID thief) {
        Borrowed b = borrowed.remove(thief);
        if (b == null) return;
        if (b.expiry != null) b.expiry.cancel();
        if (loadouts.overrideOf(thief, Slots.ULTIMATE).map(b.ability::equals).orElse(false)) {
            loadouts.clearOverride(thief, Slots.ULTIMATE);
        }
        onRelease.accept(thief, b.ability);
    }
}

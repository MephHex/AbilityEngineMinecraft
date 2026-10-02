package me.mephisto.ability_engine.engine.state;

import me.mephisto.ability_engine.engine.platform.GameClock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Named resource pools per entity: mana, focus, ammo, heat...
 * A pool can be DEFINED (max + regen + regen delay, from the character's YAML) or plain (a bare
 * number, e.g. set by /ae setres). Regen is computed from elapsed time whenever the pool is read,
 * so no ticker is needed and nothing drifts.
 */
public final class ResourceManager {

    private static final class Pool {
        ResourceDef def;          // null = plain number, no max, no regen
        double value;
        long updatedAt;           // tick the value was last brought up to date
        long lastSpend = Long.MIN_VALUE / 2;
        long heldAt = Long.MIN_VALUE / 2; // last holdReload: still in use (a volley in hand or firing)
    }

    /** A holdReload this recent still counts as in use (volley bullets go every 3 ticks). */
    private static final long HELD_TICKS = 3;

    private final GameClock clock;
    private final Map<UUID, Map<String, Pool>> pools = new HashMap<>();

    public ResourceManager(GameClock clock) {
        this.clock = clock;
    }

    /** Give an owner a defined pool, starting full. Re-defining keeps the current value (clamped). */
    public void define(UUID owner, ResourceDef def) {
        Pool p = pools.computeIfAbsent(owner, k -> new HashMap<>()).get(def.id());
        if (p == null) {
            p = new Pool();
            p.value = def.max();
            pools.get(owner).put(def.id(), p);
        } else {
            update(p);
            p.value = Math.min(p.value, def.max());
        }
        p.def = def;
        p.updatedAt = clock.now();
    }

    public Optional<ResourceDef> definition(UUID owner, String resource) {
        Pool p = pool(owner, resource);
        return p == null ? Optional.empty() : Optional.ofNullable(p.def);
    }

    /** A cost of 0 is always affordable. */
    public boolean has(UUID owner, String resource, double amount) {
        return amount <= 0 || value(owner, resource) >= amount - 1e-9;
    }

    /** Spend (never below zero). Restarts the regen delay. */
    public void consume(UUID owner, String resource, double amount) {
        Pool p = poolOrCreate(owner, resource);
        update(p);
        p.value = Math.max(0, p.value - amount);
        p.lastSpend = clock.now();
    }

    /**
     * Restart the regen delay and the reload timer as if some was spent just now, without spending any
     * (e.g. while a volley of already-loaded bullets is still firing, the gun doesn't reload yet).
     */
    public void holdReload(UUID owner, String resource) {
        Pool p = pool(owner, resource);
        if (p == null) return;
        update(p);
        p.lastSpend = clock.now();
        p.heldAt = clock.now();
    }

    public void add(UUID owner, String resource, double amount) {
        Pool p = poolOrCreate(owner, resource);
        update(p);
        p.value = clamp(p, p.value + amount);
    }

    public void set(UUID owner, String resource, double amount) {
        Pool p = poolOrCreate(owner, resource);
        update(p);
        p.value = clamp(p, Math.max(0, amount));
    }

    /** Current amount, including regen up to now. */
    public double value(UUID owner, String resource) {
        Pool p = pool(owner, resource);
        if (p == null) return 0;
        update(p);
        return p.value;
    }

    /** Current amount rounded down (for display and whole-number costs). */
    public int get(UUID owner, String resource) {
        return (int) Math.floor(value(owner, resource) + 1e-9);
    }

    /** 0..1 of max for defined pools; empty for plain numbers. */
    public Optional<Double> fraction(UUID owner, String resource) {
        Pool p = pool(owner, resource);
        if (p == null || p.def == null || p.def.max() <= 0) return Optional.empty();
        update(p);
        return Optional.of(p.value / p.def.max());
    }

    // ---- internals ----------------------------------------------------------------------------

    /**
     * Ammo reloading right now (empty, with a reload time): ticks until it's full again; 0 otherwise, also
     * while it's held (holdReload in the last few ticks: a volley still in hand or firing).
     */
    public long reloadRemaining(UUID owner, String resource) {
        Pool p = pool(owner, resource);
        if (p == null || p.def == null || p.def.reloadTicks() <= 0) return 0;
        update(p);
        if (p.value >= 1 - 1e-9) return 0;
        if (clock.now() - p.heldAt <= HELD_TICKS) return 0;
        return Math.max(0, p.lastSpend + p.def.reloadTicks() - clock.now());
    }

    /**
     * Ticks until the next whole unit comes back by regen (e.g. the next shard of ammo), its regen delay included; 0 when
     * full or when it doesn't regen.
     */
    public long nextUnitTicks(UUID owner, String resource) {
        Pool p = pool(owner, resource);
        if (p == null || p.def == null || p.def.regenPerSecond() <= 0) return 0;
        update(p);
        if (p.value >= p.def.max() - 1e-9) return 0;
        long now = clock.now();
        long delayLeft = Math.max(0, p.lastSpend + p.def.delayTicks() - now);
        double toNext = Math.floor(p.value + 1e-9) + 1 - p.value;
        return delayLeft + (long) Math.ceil(toNext / p.def.regenPerSecond() * 20.0 - 1e-9);
    }

    private void update(Pool p) {
        long now = clock.now();
        // Ammo: empty, and the reload time since the last shot is up: full again.
        if (p.def != null && p.def.reloadTicks() > 0 && p.value < 1 - 1e-9 && now >= p.lastSpend + p.def.reloadTicks()) {
            p.value = p.def.max();
        }
        if (p.def != null && p.def.regenPerSecond() > 0 && p.value < p.def.max()) {
            long regenFrom = Math.max(p.updatedAt, p.lastSpend + p.def.delayTicks());
            if (now > regenFrom) p.value = Math.min(p.def.max(), p.value + p.def.regenPerSecond() * (now - regenFrom) / 20.0);
        }
        p.updatedAt = now;
    }

    private static double clamp(Pool p, double v) {
        return p.def == null ? v : Math.min(p.def.max(), v);
    }

    private Pool pool(UUID owner, String resource) {
        Map<String, Pool> mine = pools.get(owner);
        return mine == null ? null : mine.get(resource);
    }

    private Pool poolOrCreate(UUID owner, String resource) {
        return pools.computeIfAbsent(owner, k -> new HashMap<>()).computeIfAbsent(resource, k -> {
            Pool p = new Pool();
            p.updatedAt = clock.now();
            return p;
        });
    }
}

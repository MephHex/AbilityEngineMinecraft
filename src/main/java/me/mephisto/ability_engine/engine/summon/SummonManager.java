package me.mephisto.ability_engine.engine.summon;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.CloneSpawner;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Summons: entities an ability leaves behind that outlive the cast (a Dream Echo). Named per owner,
 * one of each name at a time (a new one replaces the old), gone after their lifetime or when the
 * owner dies, leaves or switches character. Other abilities find them by name.
 */
public final class SummonManager {

    private record Summon(UUID owner, String name, UUID entity, TaskHandle expiry) {}

    private final CloneSpawner spawner;
    private final TaskScheduler scheduler;
    private final List<Summon> active = new ArrayList<>();
    /** Summons that break after so many hits (a basic attack is one, anything else all of them), and the hits taken. */
    private final java.util.Map<UUID, Integer> hitsToBreak = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> hitsTaken = new java.util.HashMap<>();
    /** A status put on whoever breaks a summon (with its hits), from its owner. */
    private final java.util.Map<UUID, String> onBreak = new java.util.HashMap<>();

    /** Whoever breaks {@code entity} (a summon) by hitting it gets {@code status} (e.g. friendly fire). Null: nothing. */
    public void onBreak(UUID entity, String status) {
        if (status != null) onBreak.put(entity, status);
    }

    /** The status whoever breaks {@code entity} gets (null = none). */
    public String breakStatusOf(UUID entity) { return onBreak.get(entity); }

    /** A cue played where a summon is when it goes, however it goes. */
    private final java.util.Map<UUID, String> endCues = new java.util.HashMap<>();
    private final List<RemovalListener> removalListeners = new ArrayList<>();

    /** Told just before a summon goes (it's still there: where it is can be looked up). */
    public interface RemovalListener {
        /** @param endCue its end cue (null = none) */
        void removing(UUID owner, UUID entity, String endCue);
    }

    public void addRemovalListener(RemovalListener l) { removalListeners.add(l); }

    /**
     * {@code entity} (a summon) breaks after {@code hits} hits: each basic attack (primary / secondary / melee) is one, any
     * other hit (an ability) breaks it at once. 0 = it doesn't count hits.
     */
    public void breakAfter(UUID entity, int hits) {
        if (hits > 0) hitsToBreak.put(entity, hits);
    }

    /** Play {@code cue} where {@code entity} (a summon) is when it goes (expires, breaks, is replaced or dismissed). */
    public void endCue(UUID entity, String cue) {
        if (cue != null) endCues.put(entity, cue);
    }

    /**
     * A hit landed on {@code entity}: one that breaks after a number of hits counts it, and breaks (goes) on the last.
     * True if it broke.
     */
    public boolean hit(UUID entity, boolean basicAttack) {
        Integer max = hitsToBreak.get(entity);
        if (max == null) return false;
        int taken = hitsTaken.merge(entity, basicAttack ? 1 : max, Integer::sum);
        if (taken < max) return false;
        active.stream().filter(s -> s.entity().equals(entity)).findFirst().ifPresent(this::remove);
        return true;
    }

    /** Every summon {@code owner} has out right now. */
    public List<UUID> of(UUID owner) {
        return active.stream().filter(s -> s.owner().equals(owner)).map(Summon::entity).toList();
    }

    public SummonManager(CloneSpawner spawner, TaskScheduler scheduler) {
        this.spawner = spawner;
        this.scheduler = scheduler;
    }

    /** Spawn a look-alike of the owner named {@code name}, lasting {@code lifetime} ticks. */
    public Optional<UUID> summonClone(UUID owner, String name, String world, Vec3 center, int lifetime) {
        return summonClone(owner, name, world, center, lifetime, null);
    }

    /** @param facing the way the clone looks (horizontal); null = the way its owner looks */
    public Optional<UUID> summonClone(UUID owner, String name, String world, Vec3 center, int lifetime, Vec3 facing) {
        return summonClone(owner, name, owner, world, center, lifetime, facing, CloneSpawner.Options.DECOY);
    }

    /**
     * @param of      whose look-alike it is (e.g. an enemy's soul); the summon still belongs to {@code owner}
     * @param options decoy, or vulnerable (can be hit and killed)
     */
    public Optional<UUID> summonClone(UUID owner, String name, UUID of, String world, Vec3 center, int lifetime,
                                      Vec3 facing, CloneSpawner.Options options) {
        dismiss(owner, name);
        Optional<UUID> spawned = spawner.spawnClone(of, world, center, facing, options);
        spawned.ifPresent(entity -> {
            Summon[] self = new Summon[1];
            TaskHandle expiry = scheduler.after(lifetime, () -> remove(self[0]));
            self[0] = new Summon(owner, name, entity, expiry);
            active.add(self[0]);
        });
        return spawned;
    }

    public Optional<UUID> find(UUID owner, String name) {
        return active.stream().filter(s -> s.owner().equals(owner) && s.name().equals(name))
                .map(Summon::entity).findFirst();
    }

    /** Whose summon this entity is, if it is one. */
    public Optional<UUID> ownerOf(UUID entity) {
        return active.stream().filter(s -> s.entity().equals(entity)).map(Summon::owner).findFirst();
    }

    /** Is this entity someone's summon (a clone, a soul)? Its death is no one's kill. */
    public boolean isSummon(UUID entity) {
        return active.stream().anyMatch(s -> s.entity().equals(entity));
    }

    public void dismiss(UUID owner, String name) {
        List.copyOf(active).stream().filter(s -> s.owner().equals(owner) && s.name().equals(name)).forEach(this::remove);
    }

    public void dismissAll(UUID owner) {
        List.copyOf(active).stream().filter(s -> s.owner().equals(owner)).forEach(this::remove);
    }

    private boolean quiet; // shutting down: no end cues

    public void shutdown() {
        quiet = true;
        List.copyOf(active).forEach(this::remove);
        quiet = false;
    }

    public int count() { return active.size(); }

    private void remove(Summon s) {
        if (s == null || !active.remove(s)) return;
        s.expiry().cancel();
        String cue = endCues.remove(s.entity());
        hitsToBreak.remove(s.entity());
        hitsTaken.remove(s.entity());
        onBreak.remove(s.entity());
        if (!quiet) for (RemovalListener l : List.copyOf(removalListeners)) l.removing(s.owner(), s.entity(), cue);
        spawner.despawn(s.entity());
    }
}

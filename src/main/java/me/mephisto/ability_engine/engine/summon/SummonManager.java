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

    public SummonManager(CloneSpawner spawner, TaskScheduler scheduler) {
        this.spawner = spawner;
        this.scheduler = scheduler;
    }

    /** Spawn a look-alike of the owner named {@code name}, lasting {@code lifetime} ticks. */
    public Optional<UUID> summonClone(UUID owner, String name, String world, Vec3 center, int lifetime) {
        dismiss(owner, name);
        Optional<UUID> spawned = spawner.spawnClone(owner, world, center);
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

    public void dismiss(UUID owner, String name) {
        List.copyOf(active).stream().filter(s -> s.owner().equals(owner) && s.name().equals(name)).forEach(this::remove);
    }

    public void dismissAll(UUID owner) {
        List.copyOf(active).stream().filter(s -> s.owner().equals(owner)).forEach(this::remove);
    }

    public void shutdown() {
        List.copyOf(active).forEach(this::remove);
    }

    public int count() { return active.size(); }

    private void remove(Summon s) {
        if (s == null || !active.remove(s)) return;
        s.expiry().cancel();
        spawner.despawn(s.entity());
    }
}

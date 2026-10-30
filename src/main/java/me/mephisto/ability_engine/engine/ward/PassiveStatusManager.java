package me.mephisto.ability_engine.engine.ward;

import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.status.StatusManager;

import java.util.List;
import java.util.UUID;

/**
 * A character's {@code passive_statuses}: always on them while they play it and are alive (e.g. a passive that reacts
 * to being hit, through the status's on_damaged). They're put on again every {@link #PERIOD} ticks for a little longer
 * than that, so they come back soon after a death clears them, and drop off by themselves after a change of character.
 */
public final class PassiveStatusManager {

    static final int PERIOD = 5;
    static final int HOLD = PERIOD + 5;

    private final LoadoutManager loadouts;
    private final WorldQuery world;
    private final StatusManager statuses;
    private final TaskScheduler scheduler;
    private TaskHandle ticker;

    public PassiveStatusManager(LoadoutManager loadouts, WorldQuery world, StatusManager statuses, TaskScheduler scheduler) {
        this.loadouts = loadouts;
        this.world = world;
        this.statuses = statuses;
        this.scheduler = scheduler;
        loadouts.onAssign(id -> {
            if (defOf(id).isEmpty()) return;
            refresh(id); // right away, not at the next check
            if (ticker == null) ticker = scheduler.every(PERIOD, PERIOD, this::tick);
        });
    }

    private List<String> defOf(UUID player) {
        return loadouts.baseCharacterOf(player).map(CharacterDef::passiveStatuses).orElse(List.of());
    }

    /** Put theirs on now (e.g. right after a respawn). */
    public void refresh(UUID player) {
        if (!world.isAlive(player)) return;
        for (String status : defOf(player)) statuses.apply(player, status, HOLD, player);
    }

    private void tick() {
        boolean anyone = false;
        for (UUID id : loadouts.assignedPlayers()) {
            if (defOf(id).isEmpty()) continue;
            anyone = true;
            refresh(id);
        }
        if (!anyone) {
            ticker.cancel();
            ticker = null;
        }
    }
}

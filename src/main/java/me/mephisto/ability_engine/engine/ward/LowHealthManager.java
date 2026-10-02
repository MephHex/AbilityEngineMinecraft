package me.mephisto.ability_engine.engine.ward;

import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.status.StatusManager;

import java.util.Optional;
import java.util.UUID;

/**
 * The low-health passive ({@code low_health: { below, status }} in a kit): while a character's health is below that
 * share of their max, the status is kept on them. It's put on again every {@link #PERIOD} ticks for a little longer
 * than that, so it drops off by itself soon after they're healed back above it (or die, or change character).
 */
public final class LowHealthManager {

    /** How often health is looked at. */
    static final int PERIOD = 5;
    /** How long each application lasts: a little longer than the period, so it never flickers off in between. */
    static final int HOLD = PERIOD + 5;

    private final LoadoutManager loadouts;
    private final WorldQuery world;
    private final StatusManager statuses;
    private final TaskScheduler scheduler;
    private TaskHandle ticker;

    public LowHealthManager(LoadoutManager loadouts, WorldQuery world, StatusManager statuses, TaskScheduler scheduler) {
        this.loadouts = loadouts;
        this.world = world;
        this.statuses = statuses;
        this.scheduler = scheduler;
        loadouts.onAssign(id -> {
            if (defOf(id).isPresent() && ticker == null) ticker = scheduler.every(1, PERIOD, this::tick);
        });
    }

    private Optional<CharacterDef.LowHealth> defOf(UUID entity) {
        return loadouts.baseCharacterOf(entity).map(CharacterDef::lowHealth);
    }

    /** Look at their health right now (instead of at the next check): low, the status goes on. */
    public void refresh(UUID player) {
        defOf(player).ifPresent(def -> refresh(player, def));
    }

    private void refresh(UUID player, CharacterDef.LowHealth def) {
        if (!world.isAlive(player)) return;
        var share = world.healthFraction(player);
        if (share.isPresent() && share.getAsDouble() < def.below()) statuses.apply(player, def.status(), HOLD, player);
    }

    private void tick() {
        boolean anyone = false;
        for (UUID id : loadouts.assignedPlayers()) {
            Optional<CharacterDef.LowHealth> def = defOf(id);
            if (def.isEmpty()) continue;
            anyone = true;
            refresh(id, def.get());
        }
        if (!anyone) {
            ticker.cancel();
            ticker = null;
        }
    }
}

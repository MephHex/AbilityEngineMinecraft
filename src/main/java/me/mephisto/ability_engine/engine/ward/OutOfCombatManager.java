package me.mephisto.ability_engine.engine.ward;

import me.mephisto.ability_engine.engine.combat.CombatTracker;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.platform.GameClock;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.status.StatusManager;

import java.util.Optional;
import java.util.UUID;

/**
 * The out-of-combat passive ({@code out_of_combat: { after, status }} in a kit): once a character has gone that long
 * without dealing or taking damage, the status is kept on them (e.g. one whose tick builds a shield up). Like the
 * low-health passive, it's put on again every {@link #PERIOD} ticks for a little longer than that, so it drops off by
 * itself soon after they fight again (or die, or change character).
 */
public final class OutOfCombatManager {

    /** How often it's looked at. */
    static final int PERIOD = 5;
    /** How long each application lasts: a little longer than the period, so it never flickers off in between. */
    static final int HOLD = PERIOD + 5;

    private final LoadoutManager loadouts;
    private final WorldQuery world;
    private final StatusManager statuses;
    private final CombatTracker combat;
    private final GameClock clock;
    private final TaskScheduler scheduler;
    private TaskHandle ticker;

    public OutOfCombatManager(LoadoutManager loadouts, WorldQuery world, StatusManager statuses, CombatTracker combat,
                              GameClock clock, TaskScheduler scheduler) {
        this.loadouts = loadouts;
        this.world = world;
        this.statuses = statuses;
        this.combat = combat;
        this.clock = clock;
        this.scheduler = scheduler;
        loadouts.onAssign(id -> {
            if (defOf(id).isPresent() && ticker == null) ticker = scheduler.every(1, PERIOD, this::tick);
        });
    }

    private Optional<CharacterDef.OutOfCombat> defOf(UUID entity) {
        return loadouts.baseCharacterOf(entity).map(CharacterDef::outOfCombat);
    }

    /** Have they gone {@code after} ticks without a fight? (Never in one counts.) */
    private boolean calm(UUID player, CharacterDef.OutOfCombat def) {
        long last = combat.lastCombat(player);
        return last == Long.MIN_VALUE || clock.now() - last >= def.after();
    }

    private void tick() {
        boolean anyone = false;
        for (UUID id : loadouts.assignedPlayers()) {
            Optional<CharacterDef.OutOfCombat> def = defOf(id);
            if (def.isEmpty()) continue;
            anyone = true;
            if (world.isAlive(id) && calm(id, def.get())) statuses.apply(id, def.get().status(), HOLD, id);
        }
        if (!anyone) {
            ticker.cancel();
            ticker = null;
        }
    }
}

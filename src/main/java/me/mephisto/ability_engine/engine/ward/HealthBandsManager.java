package me.mephisto.ability_engine.engine.ward;

import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.status.StatusManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The health-bands passive ({@code health_bands: { size, cast, statuses }} in a kit). Their health is cut into bands of
 * {@code size} (0.2: 0-20%, 20-40% ... 80-100%):
 * <ul>
 *   <li>every band they drop out of casts {@code cast} once (a big hit through two bands: twice). Healing back up
 *       casts nothing, and losing it again casts again.</li>
 *   <li>{@code statuses} (lowest band first) - the one for the band they're in is kept on them, and only that one (e.g.
 *       a tag for a form that's smaller and faster the lower they are).</li>
 * </ul>
 * Dying, respawning or a new character starts over from wherever their health is then (nothing cast for that).
 */
public final class HealthBandsManager {

    /** How often health is looked at. */
    static final int PERIOD = 2;
    /** How long each application of a band's status lasts: a little longer than the period, so it never flickers off. */
    static final int HOLD = PERIOD + 5;

    private final LoadoutManager loadouts;
    private final WorldQuery world;
    private final StatusManager statuses;
    private final TaskScheduler scheduler;
    private final Map<UUID, Integer> bandOf = new HashMap<>();
    private BiConsumer<UUID, String> caster = (holder, ability) -> { };
    private TaskHandle ticker;

    public HealthBandsManager(LoadoutManager loadouts, WorldQuery world, StatusManager statuses, TaskScheduler scheduler) {
        this.loadouts = loadouts;
        this.world = world;
        this.statuses = statuses;
        this.scheduler = scheduler;
        loadouts.onAssign(id -> {
            bandOf.remove(id); // a new character: start over
            if (defOf(id).isPresent() && ticker == null) ticker = scheduler.every(1, PERIOD, this::tick);
        });
    }

    /** How a band lost casts its ability (the engine's activator). */
    public void setCaster(BiConsumer<UUID, String> caster) { this.caster = caster; }

    private Optional<CharacterDef.HealthBands> defOf(UUID entity) {
        return loadouts.baseCharacterOf(entity).map(CharacterDef::healthBands);
    }

    /** Which band a share of max HP is in: 1 for the lowest, {@code count} for the top (full health is in the top one). */
    static int band(double share, double size, int count) {
        int b = (int) Math.ceil(share / size - 1e-9);
        return Math.max(1, Math.min(count, b));
    }

    /** Look at their health right now (instead of at the next check). */
    public void refresh(UUID player) {
        defOf(player).ifPresent(def -> refresh(player, def));
    }

    private void refresh(UUID player, CharacterDef.HealthBands def) {
        if (!world.isAlive(player)) {
            bandOf.remove(player); // back alive: from wherever they are then
            return;
        }
        var share = world.healthFraction(player);
        if (share.isEmpty()) return;
        int band = band(share.getAsDouble(), def.size(), def.count());
        Integer before = bandOf.put(player, band);
        if (before != null && band < before && def.cast() != null) {
            for (int i = band; i < before; i++) caster.accept(player, def.cast());
        }
        if (def.statuses().isEmpty()) return;
        String current = def.statuses().get(Math.min(band, def.statuses().size()) - 1);
        if (before == null || band != before) {
            for (String other : def.statuses()) if (!other.equals(current)) statuses.remove(player, other);
        }
        statuses.apply(player, current, HOLD, player);
    }

    private void tick() {
        boolean anyone = false;
        for (UUID id : loadouts.assignedPlayers()) {
            Optional<CharacterDef.HealthBands> def = defOf(id);
            if (def.isEmpty()) {
                bandOf.remove(id);
                continue;
            }
            anyone = true;
            refresh(id, def.get());
        }
        if (!anyone) {
            ticker.cancel();
            ticker = null;
        }
    }
}

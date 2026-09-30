package me.mephisto.ability_engine.engine.ward;

import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.status.StatusManager;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.team.Teams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Characters' hearing ({@code hearing:} in a kit): which wounded enemies each of them hears right now,
 * refreshed every few ticks. Only runs while someone has hearing.
 * <ul>
 *   <li>Heard: an enemy (not an ally, not a summon) within {@code range} whose health is below
 *       {@code below} of its max. Nearest first.</li>
 *   <li>Moving toward a heard enemy within {@code toward.range} (within 50 degrees of where they're
 *       walking) keeps {@code toward.status} on them (e.g. faster).</li>
 * </ul>
 */
public final class HearingManager {

    /** How often the heard list is refreshed (ticks). */
    public static final int PERIOD = 4;
    private static final double TOWARD_COS = Math.cos(Math.toRadians(50));

    private final LoadoutManager loadouts;
    private final WorldQuery world;
    private final Teams teams;
    private final StatusManager statuses;
    private final me.mephisto.ability_engine.engine.summon.SummonManager summons;
    private final TaskScheduler scheduler;
    private final Map<UUID, List<UUID>> heard = new HashMap<>();
    private TaskHandle ticker;

    public HearingManager(LoadoutManager loadouts, WorldQuery world, Teams teams, StatusManager statuses,
                          me.mephisto.ability_engine.engine.summon.SummonManager summons, TaskScheduler scheduler) {
        this.loadouts = loadouts;
        this.world = world;
        this.teams = teams;
        this.statuses = statuses;
        this.summons = summons;
        this.scheduler = scheduler;
        loadouts.onAssign(id -> {
            if (defOf(id).isPresent() && ticker == null) ticker = scheduler.every(1, PERIOD, this::tick);
        });
    }

    private Optional<CharacterDef.Hearing> defOf(UUID entity) {
        return loadouts.baseCharacterOf(entity).map(CharacterDef::hearing);
    }

    /** The enemies {@code listener} hears right now, nearest first (empty without hearing). */
    public List<UUID> heard(UUID listener) {
        return heard.getOrDefault(listener, List.of());
    }

    public boolean hears(UUID listener, UUID enemy) { return heard(listener).contains(enemy); }

    /** Work it out right now (and remember it), instead of waiting for the next refresh. */
    public List<UUID> refresh(UUID listener) {
        Optional<CharacterDef.Hearing> def = defOf(listener);
        if (def.isEmpty()) {
            heard.remove(listener);
            return List.of();
        }
        var at = world.positionOf(new EntityTarget(listener));
        if (at.isEmpty()) {
            heard.remove(listener);
            return List.of();
        }
        Vec3 here = at.get().position();
        List<EntitySnapshot> near = new ArrayList<>(world.livingEntitiesNear(at.get(), def.get().range()));
        near.sort(java.util.Comparator.comparingDouble(e -> e.center().distance(here)));
        List<UUID> out = new ArrayList<>();
        for (EntitySnapshot e : near) {
            UUID id = e.id();
            if (id.equals(listener) || teams.allies(listener, id) || summons.isSummon(id)) continue;
            if (e.center().distance(here) > def.get().range()) continue;
            var f = world.healthFraction(id);
            if (f.isPresent() && f.getAsDouble() < def.get().belowHealth()) out.add(id);
        }
        heard.put(listener, List.copyOf(out));
        return out;
    }

    private void tick() {
        boolean anyone = false;
        for (UUID id : loadouts.assignedPlayers()) {
            Optional<CharacterDef.Hearing> def = defOf(id);
            if (def.isEmpty()) {
                heard.remove(id);
                continue;
            }
            anyone = true;
            List<UUID> now = refresh(id);
            if (def.get().towardStatus() != null && movingTowardOne(id, now, def.get().towardRange())) {
                statuses.apply(id, def.get().towardStatus(), PERIOD + 4, id);
            }
        }
        heard.keySet().removeIf(id -> defOf(id).isEmpty());
        if (!anyone) {
            ticker.cancel();
            ticker = null;
        }
    }

    private boolean movingTowardOne(UUID listener, List<UUID> enemies, double range) {
        Optional<Vec3> moving = world.movementOf(listener);
        var at = world.positionOf(new EntityTarget(listener));
        if (moving.isEmpty() || at.isEmpty()) return false;
        Vec3 here = at.get().position();
        for (UUID enemy : enemies) {
            var there = world.positionOf(new EntityTarget(enemy));
            if (there.isEmpty()) continue;
            Vec3 to = there.get().position().subtract(here);
            Vec3 flat = new Vec3(to.x(), 0, to.z());
            if (to.length() > range || flat.isZero()) continue;
            if (flat.normalize().dot(moving.get().normalize()) >= TOWARD_COS) return true;
        }
        return false;
    }
}

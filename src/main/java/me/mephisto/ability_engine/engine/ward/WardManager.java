package me.mephisto.ability_engine.engine.ward;

import me.mephisto.ability_engine.engine.combat.CombatTracker;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.platform.CuePlayer;
import me.mephisto.ability_engine.engine.platform.GameClock;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.status.StatusDef;
import me.mephisto.ability_engine.engine.status.StatusManager;
import me.mephisto.ability_engine.engine.tag.TagManager;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Characters' wards ({@code ward:} in a kit): debuff immunity that recharges out of combat.
 * <ul>
 *   <li>Ready: the holder has {@code state.debuff_immune} and the next debuff doesn't land. That uses the
 *       ward up. Being in combat doesn't: a ready ward stays ready until it blocks something.</li>
 *   <li>Recharging: it's ready again once the holder has gone {@code out_of_combat} ticks without dealing
 *       or taking damage, counted from the later of the last hit and the block. Any hit restarts it.</li>
 *   <li>{@link #reset} makes it ready at once (an ability that refreshes it).</li>
 * </ul>
 * A new character starts with it ready.
 * <p>A BARRIER ({@code absorb:} above 0) works the same way, but instead of blocking a debuff it takes that
 * share off the next hit's damage ({@link #absorbHit}); debuffs land as usual and there's no immunity tag.
 */
public final class WardManager {

    public static final String BLOCK_CUE = "ward_block";
    public static final String READY_CUE = "ward_ready";
    public static final String BARRIER_CUE = "barrier_break";

    /** The ward's state, for the HUD. */
    public record State(CharacterDef.Ward ward, boolean ready, long rechargeTicks) {}

    private static final class Ward {
        boolean ready = true;
        long usedAt = Long.MIN_VALUE;
        boolean tagged;
    }

    private final LoadoutManager loadouts;
    private final TagManager tags;
    private final CombatTracker combat;
    private final GameClock clock;
    private final CuePlayer cues;
    private final WorldQuery world;
    private final Map<UUID, Ward> wards = new HashMap<>();
    private final TaskScheduler scheduler;
    /** Runs only while someone has a ward (so an idle engine has no tasks). */
    private me.mephisto.ability_engine.engine.platform.TaskHandle ticker;

    public WardManager(LoadoutManager loadouts, TagManager tags, CombatTracker combat, GameClock clock,
                       CuePlayer cues, WorldQuery world, TaskScheduler scheduler, StatusManager statuses) {
        this.loadouts = loadouts;
        this.tags = tags;
        this.combat = combat;
        this.clock = clock;
        this.cues = cues;
        this.world = world;
        this.scheduler = scheduler;
        statuses.addGuard(this::blocks);
        loadouts.onAssign(id -> {
            if (defOf(id).isPresent()) {
                wards.put(id, new Ward()); // a new character: ready
                ensureTicking();
            }
        });
    }

    private void ensureTicking() {
        if (ticker == null) ticker = scheduler.every(1, 1, this::tick);
    }

    private Optional<CharacterDef.Ward> defOf(UUID entity) {
        return loadouts.baseCharacterOf(entity).map(CharacterDef::ward);
    }

    public Optional<State> state(UUID entity) {
        return defOf(entity).map(def -> {
            Ward w = wards.computeIfAbsent(entity, k -> new Ward());
            return new State(def, w.ready, w.ready ? 0 : Math.max(0, readyAt(entity, def, w) - clock.now()));
        });
    }

    /** Ready now, whatever the timer says. */
    public void reset(UUID entity) {
        if (defOf(entity).isEmpty()) return;
        Ward w = wards.computeIfAbsent(entity, k -> new Ward());
        if (!w.ready) becomeReady(entity, w);
    }

    /** Forget an entity (character removed, left). */
    public void forget(UUID entity) {
        Ward w = wards.remove(entity);
        if (w != null && w.tagged) tags.revoke(entity, Tags.DEBUFF_IMMUNE);
    }

    private long readyAt(UUID entity, CharacterDef.Ward def, Ward w) {
        long since = Math.max(combat.lastCombat(entity), w.usedAt);
        return since == Long.MIN_VALUE ? Long.MIN_VALUE : since + def.outOfCombatTicks();
    }

    /**
     * A hit is landing on {@code victim}: a ready barrier takes its share off (and is used up). Returns what's
     * left of the damage (all of it without a ready barrier).
     */
    public double absorbHit(UUID victim, double amount) {
        if (amount <= 0) return amount;
        Optional<CharacterDef.Ward> def = defOf(victim).filter(CharacterDef.Ward::isBarrier);
        if (def.isEmpty()) return amount;
        Ward w = wards.computeIfAbsent(victim, k -> new Ward());
        if (!w.ready) return amount;
        w.ready = false;
        w.usedAt = clock.now();
        world.positionOf(new EntityTarget(victim)).ifPresent(p -> cues.play(BARRIER_CUE, p.world(), p.position()));
        return amount * (1 - def.get().absorb());
    }

    private boolean blocks(UUID target, StatusDef def, UUID source) {
        if (defOf(target).filter(d -> !d.isBarrier()).isEmpty() || !StatusManager.isDebuff(target, def, source)) return false;
        Ward w = wards.computeIfAbsent(target, k -> new Ward()); // a new character starts ready
        if (!w.ready) return false;
        w.ready = false;
        w.usedAt = clock.now();
        untag(target, w);
        world.positionOf(new EntityTarget(target)).ifPresent(p -> cues.play(BLOCK_CUE, p.world(), p.position()));
        return true;
    }

    private void tick() {
        boolean anyone = false;
        for (UUID id : loadouts.assignedPlayers()) {
            Optional<CharacterDef.Ward> def = defOf(id);
            if (def.isEmpty()) {
                forget(id);
                continue;
            }
            anyone = true;
            Ward w = wards.computeIfAbsent(id, k -> new Ward());
            if (!w.ready && clock.now() >= readyAt(id, def.get(), w)) becomeReady(id, w);
            // Tags are wiped on death: put it back while ready. (A barrier has no tag.)
            if (w.ready && !def.get().isBarrier() && !tags.has(id, Tags.DEBUFF_IMMUNE)) {
                w.tagged = false;
                tag(id, w);
            }
        }
        if (!anyone) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void becomeReady(UUID entity, Ward w) {
        w.ready = true;
        if (defOf(entity).filter(CharacterDef.Ward::isBarrier).isEmpty()) tag(entity, w);
        world.positionOf(new EntityTarget(entity)).ifPresent(p -> cues.play(READY_CUE, p.world(), p.position()));
    }

    private void tag(UUID entity, Ward w) {
        if (w.tagged) return;
        tags.grant(entity, Tags.DEBUFF_IMMUNE);
        w.tagged = true;
    }

    private void untag(UUID entity, Ward w) {
        if (!w.tagged) return;
        tags.revoke(entity, Tags.DEBUFF_IMMUNE);
        w.tagged = false;
    }
}

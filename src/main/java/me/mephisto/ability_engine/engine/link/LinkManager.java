package me.mephisto.ability_engine.engine.link;

import me.mephisto.ability_engine.engine.EngineLog;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.CuePlayer;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.status.StatusDef;
import me.mephisto.ability_engine.engine.status.StatusManager;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tethers ("links") between an owner and another entity, e.g. a tank bonded to an ally. A link outlives
 * the cast that made it and holds until it breaks: too far apart, either one gone or dead, the owner
 * reset, or replaced by a new link with the same name. While it holds:
 * <ul>
 *   <li>damage to the target is multiplied by {@code damageTaken}, and {@code redirect} of the prevented
 *       damage is dealt to the owner instead (see DamageModifiers)</li>
 *   <li>with {@code copyPositive}, positive statuses applied to the owner are applied to the target too</li>
 *   <li>a line cue is drawn between them every few ticks</li>
 * </ul>
 */
public final class LinkManager {

    /** How often the tether is drawn. */
    private static final int DRAW_EVERY = 4;
    /** Cue played at the target when a link breaks. */
    public static final String BREAK_CUE = "tether_break";

    public record Link(UUID owner, String name, UUID target, double range, double damageTaken, double redirect,
                       boolean copyPositive, String cue) {}

    private final WorldQuery world;
    private final CuePlayer cues;
    private final TaskScheduler scheduler;
    private final EngineLog log;
    private final List<Link> active = new ArrayList<>();
    private StatusManager statuses;
    private boolean copying;
    private TaskHandle ticker;
    private int ticks;

    public LinkManager(WorldQuery world, CuePlayer cues, TaskScheduler scheduler, EngineLog log) {
        this.world = world;
        this.cues = cues;
        this.scheduler = scheduler;
        this.log = log;
    }

    /** Copies positive statuses applied to an owner onto their linked target. */
    public void attach(StatusManager statuses) {
        this.statuses = statuses;
        statuses.addApplyListener(this::onStatusApplied);
    }

    /** Link {@code owner} to {@code target} under {@code name}, replacing the owner's link of that name. */
    public void link(Link link) {
        unlink(link.owner(), link.name(), false);
        active.add(link);
        log.debug(() -> "link " + link.name() + ": " + link.owner() + " -> " + link.target());
        if (ticker == null) ticker = scheduler.every(1, 1, this::tick);
    }

    public Optional<Link> find(UUID owner, String name) {
        return active.stream().filter(l -> l.owner().equals(owner) && l.name().equals(name)).findFirst();
    }

    /** Links whose target is this entity (whose damage they reduce). */
    public List<Link> onTarget(UUID target) {
        return active.stream().filter(l -> l.target().equals(target)).toList();
    }

    public void unlink(UUID owner, String name) { unlink(owner, name, true); }

    private void unlink(UUID owner, String name, boolean cue) {
        for (Link l : List.copyOf(active)) {
            if (l.owner().equals(owner) && l.name().equals(name)) breakLink(l, cue);
        }
    }

    /** Break every link this entity is part of, either end (death, logout, character change). */
    public void breakAll(UUID entity) {
        for (Link l : List.copyOf(active)) {
            if (l.owner().equals(entity) || l.target().equals(entity)) breakLink(l, true);
        }
    }

    public void shutdown() {
        active.clear();
        if (ticker != null) ticker.cancel();
        ticker = null;
    }

    private void breakLink(Link l, boolean cue) {
        if (!active.remove(l)) return;
        log.debug(() -> "link " + l.name() + " broke");
        if (cue) world.positionOf(new EntityTarget(l.target())).ifPresent(p -> cues.play(BREAK_CUE, p.world(), p.position()));
    }

    private void tick() {
        ticks++;
        for (Link l : List.copyOf(active)) {
            Optional<PointTarget> a = world.isAlive(l.owner()) ? world.positionOf(new EntityTarget(l.owner())) : Optional.empty();
            Optional<PointTarget> b = world.isAlive(l.target()) ? world.positionOf(new EntityTarget(l.target())) : Optional.empty();
            if (a.isEmpty() || b.isEmpty() || !a.get().world().equals(b.get().world())
                    || a.get().position().distance(b.get().position()) > l.range()) {
                breakLink(l, true);
                continue;
            }
            if (l.cue() != null && ticks % DRAW_EVERY == 0) {
                Vec3 from = a.get().position();
                cues.playLine(l.cue(), a.get().world(), from, b.get().position());
            }
        }
        if (active.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void onStatusApplied(UUID target, StatusDef def, int durationTicks, UUID source) {
        if (copying || !def.positive() || statuses == null) return;
        copying = true; // a copy is never copied again (no loops between two linked owners)
        try {
            for (Link l : List.copyOf(active)) {
                if (l.copyPositive() && l.owner().equals(target)) statuses.apply(l.target(), def, durationTicks, source);
            }
        } finally {
            copying = false;
        }
    }
}

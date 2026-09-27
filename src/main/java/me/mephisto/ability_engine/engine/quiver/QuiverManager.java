package me.mephisto.ability_engine.engine.quiver;

import me.mephisto.ability_engine.engine.status.ActiveStatus;
import me.mephisto.ability_engine.engine.status.StatusManager;
import me.mephisto.ability_engine.engine.tag.TagManager;
import me.mephisto.ability_engine.engine.tag.Tags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Each owner's bolt queue plus the one bolt loaded in their weapon.
 * <ul>
 *   <li>{@link #load}: the front bolt moves into the weapon, the rest shift forward and a plain bolt
 *       joins at the back, so the queue always holds {@code size} bolts.</li>
 *   <li>{@link #infuse}: adds magic to the first N QUEUED bolts. The loaded bolt is never changed.</li>
 *   <li>{@link #take}: fires the loaded bolt (the weapon is empty again).</li>
 * </ul>
 * The quiver's rules (size, reload speed) are always read from the owner's CURRENT character, so
 * /ae reload applies right away. Owners without a quiver have nothing to load, infuse or take.
 */
public final class QuiverManager {

    private static final class State {
        final Deque<Bolt> queue = new ArrayDeque<>();
        Bolt loaded;
        long revision;
    }

    private final TagManager tags;
    private final StatusManager statuses;
    private final Function<UUID, Optional<QuiverDef>> definitions;
    private final Map<UUID, State> states = new HashMap<>();

    /** @param definitions the owner's current quiver rules (from their character), empty = no quiver */
    public QuiverManager(TagManager tags, StatusManager statuses, Function<UUID, Optional<QuiverDef>> definitions) {
        this.tags = tags;
        this.statuses = statuses;
        this.definitions = definitions;
    }

    public boolean has(UUID owner) { return definitions.apply(owner).isPresent(); }

    /** Empty the weapon and refill the queue with plain bolts (a new character). */
    public void reset(UUID owner) {
        State s = states.remove(owner);
        long revision = s == null ? 0 : s.revision + 1;
        if (has(owner)) state(owner).ifPresent(fresh -> fresh.revision = revision);
    }

    /** Forget everything (character removed, player gone). */
    public void clear(UUID owner) { states.remove(owner); }

    /** The queued bolts, next-to-load first. Empty without a quiver. */
    public List<Bolt> queue(UUID owner) {
        return state(owner).map(s -> List.copyOf(s.queue)).orElse(List.of());
    }

    public Optional<Bolt> loaded(UUID owner) { return state(owner).map(s -> s.loaded); }

    public boolean isLoaded(UUID owner) { return loaded(owner).isPresent(); }

    /** Changes every time the queue or the loaded bolt changes, so a HUD knows when to redraw. */
    public long revision(UUID owner) { return state(owner).map(s -> s.revision).orElse(-1L); }

    /** Load the front bolt. False if there's no quiver or something is already loaded. */
    public boolean load(UUID owner) {
        Optional<State> found = state(owner);
        if (found.isEmpty() || found.get().loaded != null) return false;
        State s = found.get();
        s.loaded = s.queue.pollFirst();
        s.queue.addLast(Bolt.PLAIN);
        s.revision++;
        return true;
    }

    /** Would a manual reload (drawing the weapon) be allowed right now? Not while blocked (stunned, casting) or rapid firing. */
    public boolean canLoad(UUID owner) {
        return has(owner) && !isLoaded(owner) && !tags.has(owner, Tags.BLOCK_ABILITY) && !rapidFire(owner);
    }

    /** A manual reload finished (the platform saw the weapon drawn): load, unless that isn't allowed. */
    public boolean tryLoad(UUID owner) {
        return canLoad(owner) && load(owner);
    }

    /** Fire: the loaded bolt leaves the weapon. Empty if nothing was loaded. */
    public Optional<Bolt> take(UUID owner) {
        Optional<State> found = state(owner);
        if (found.isEmpty() || found.get().loaded == null) return Optional.empty();
        State s = found.get();
        Bolt bolt = s.loaded;
        s.loaded = null;
        s.revision++;
        return Optional.of(bolt);
    }

    /**
     * Infuse the first {@code count} queued bolts (front first). The loaded bolt is never touched.
     * Returns how many bolts actually changed (bolts that already had it don't count).
     */
    public int infuse(UUID owner, String infusion, int count) {
        Optional<State> found = state(owner);
        if (found.isEmpty()) return 0;
        State s = found.get();
        List<Bolt> bolts = new ArrayList<>(s.queue);
        int changed = 0;
        for (int i = 0; i < Math.min(count, bolts.size()); i++) {
            Bolt infused = bolts.get(i).with(infusion);
            if (infused != bolts.get(i)) changed++;
            bolts.set(i, infused);
        }
        if (changed > 0) {
            s.queue.clear();
            s.queue.addAll(bolts);
            s.revision++;
        }
        return changed;
    }

    /** Infuse the queued bolt at {@code index} (0 = next to load). False if it already had it (or there's none). */
    public boolean infuseAt(UUID owner, int index, String infusion) {
        Optional<State> found = state(owner);
        if (found.isEmpty() || index < 0 || index >= found.get().queue.size()) return false;
        State s = found.get();
        List<Bolt> bolts = new ArrayList<>(s.queue);
        Bolt infused = bolts.get(index).with(infusion);
        if (infused == bolts.get(index)) return false;
        bolts.set(index, infused);
        s.queue.clear();
        s.queue.addAll(bolts);
        s.revision++;
        return true;
    }

    /**
     * Rapid fire (the quiver's {@code rapid_fire_while} tags): no drawing, the weapon shoots straight from
     * the quiver (the primary loads the next bolt itself, see ReloadNode), as fast as its cooldown allows.
     */
    public boolean rapidFire(UUID owner) {
        return definitions.apply(owner).map(d -> d.rapidFireWhile().stream().anyMatch(t -> tags.has(owner, t))).orElse(false);
    }

    /** Reload speed level right now (0 = normal), see {@link QuiverDef.ReloadSpeed}. */
    public int reloadSpeed(UUID owner) {
        QuiverDef.ReloadSpeed speed = definitions.apply(owner).map(QuiverDef::reloadSpeed).orElse(null);
        if (speed == null) return 0;
        int stacks = speed.stacksOf() == null ? 0
                : statuses.find(owner, speed.stacksOf()).map(ActiveStatus::stacks).orElse(0);
        int level = stacks <= 0 ? 0 : Math.min(speed.max(), speed.first() + stacks - 1);
        for (var entry : speed.whileTags().entrySet()) {
            if (tags.has(owner, entry.getKey())) level = Math.max(level, entry.getValue());
        }
        return level;
    }

    /** The owner's state, created (full of plain bolts) on first use and resized if their quiver changed. */
    private Optional<State> state(UUID owner) {
        Optional<QuiverDef> def = definitions.apply(owner);
        if (def.isEmpty()) return Optional.empty();
        State s = states.computeIfAbsent(owner, k -> new State());
        int size = Math.max(0, def.get().size());
        while (s.queue.size() > size) s.queue.pollLast();
        while (s.queue.size() < size) s.queue.addLast(Bolt.PLAIN);
        return Optional.of(s);
    }
}

package me.mephisto.ability_engine.engine.ability;

import me.mephisto.ability_engine.engine.tag.TagListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every running cast, per caster (replaces ActiveCastRegistry, which only knew channels).
 * Also cancels casts whose {@code interruptedBy} tags get applied to their caster.
 */
public final class AbilityInstanceRegistry implements TagListener {

    private final Map<UUID, List<AbilityInstance>> byCaster = new HashMap<>();

    public void add(AbilityInstance instance) {
        byCaster.computeIfAbsent(instance.caster(), k -> new ArrayList<>()).add(instance);
    }

    /** Called by the instance itself when it ends. */
    void remove(AbilityInstance instance) {
        List<AbilityInstance> list = byCaster.get(instance.caster());
        if (list == null) return;
        list.remove(instance);
        if (list.isEmpty()) byCaster.remove(instance.caster());
    }

    public List<AbilityInstance> of(UUID caster) {
        return List.copyOf(byCaster.getOrDefault(caster, List.of()));
    }

    public boolean isRunning(UUID caster, String abilityId) {
        return of(caster).stream().anyMatch(i -> i.ability().id().equals(abilityId));
    }

    /** Cast-bar fill (0..1) of the caster's most recently started cast that is showing one. */
    public java.util.Optional<Double> castProgress(UUID caster) {
        List<AbilityInstance> mine = byCaster.getOrDefault(caster, List.of());
        for (int i = mine.size() - 1; i >= 0; i--) {
            var f = mine.get(i).progressFraction();
            if (f.isPresent()) return f;
        }
        return java.util.Optional.empty();
    }

    /** Is one of the caster's casts of this ability waiting for a recast right now? */
    public boolean awaitingRecast(UUID caster, String abilityId) {
        return byCaster.getOrDefault(caster, List.of()).stream()
                .anyMatch(i -> i.ability().id().equals(abilityId) && i.awaitingRecast());
    }

    /** Is one of the caster's casts charging (a charge node waiting for the input to be let go)? */
    public boolean charging(UUID caster) {
        return of(caster).stream().anyMatch(AbilityInstance::charging);
    }

    /** The held input was let go: fire every charge the caster is holding. False if none was. */
    public boolean release(UUID caster) {
        boolean any = false;
        for (AbilityInstance i : of(caster)) {
            if (!i.charging()) continue;
            i.release();
            any = true;
        }
        return any;
    }

    /** A timer (boss bar) of one of the caster's casts: which ability, and how much is left (1..0). */
    public record Timer(Ability ability, double left) {}

    /** The most recently started cast showing a timer. */
    public java.util.Optional<Timer> timer(UUID caster) {
        List<AbilityInstance> mine = byCaster.getOrDefault(caster, List.of());
        for (int i = mine.size() - 1; i >= 0; i--) {
            AbilityInstance instance = mine.get(i);
            var left = instance.timerLeft();
            if (left.isPresent()) return java.util.Optional.of(new Timer(instance.ability(), left.get()));
        }
        return java.util.Optional.empty();
    }

    public int count() {
        return byCaster.values().stream().mapToInt(List::size).sum();
    }

    public void cancelAll(UUID caster, String reason) {
        of(caster).forEach(i -> i.cancel(reason));
    }

    public void cancelEverything(String reason) {
        for (UUID caster : new ArrayList<>(byCaster.keySet())) cancelAll(caster, reason);
    }

    @Override
    public void onTagAdded(UUID entity, String tag) {
        for (AbilityInstance i : of(entity)) {
            // A cast is never interrupted by tags it grants itself (e.g. its own block.ability).
            if (i.ability().activeTags().contains(tag) && !i.tagsReleased()) continue;
            if (i.ability().interruptedBy().contains(tag)) i.cancel("interrupted:" + tag);
        }
    }

    @Override
    public void onTagRemoved(UUID entity, String tag) {}
}

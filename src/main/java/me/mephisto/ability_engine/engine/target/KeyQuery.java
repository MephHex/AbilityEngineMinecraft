package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads a target that an earlier node stored on the blackboard (was SingleTargetSelector). */
public final class KeyQuery implements TargetQuery {

    private final String key;

    public KeyQuery(String key) {
        this.key = key;
    }

    @Override
    public List<Target> find(ExecutionContext ctx) {
        return read(ctx, key).map(List::of).orElse(List.of());
    }

    /**
     * Blackboard value as a Target. Accepts a Target, a raw entity UUID (e.g. "caster"), or a projectile
     * stored with {@code store:} (where it is now, or where it ended).
     */
    public static Optional<Target> read(ExecutionContext ctx, String key) {
        Object raw = ctx.blackboard().raw(key);
        if (raw instanceof Target t) return Optional.of(t);
        if (raw instanceof UUID id) return Optional.of(new EntityTarget(id));
        if (raw instanceof me.mephisto.ability_engine.engine.projectile.ProjectileHandle p && p.world() != null) {
            return Optional.of(new PointTarget(p.world(), p.position()));
        }
        return Optional.empty();
    }
}

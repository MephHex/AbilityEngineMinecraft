package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;

import java.util.List;

public final class SelfQuery implements TargetQuery {
    public static final SelfQuery INSTANCE = new SelfQuery();

    private SelfQuery() {}

    @Override
    public List<Target> find(ExecutionContext ctx) {
        return List.of(new EntityTarget(ctx.caster()));
    }
}

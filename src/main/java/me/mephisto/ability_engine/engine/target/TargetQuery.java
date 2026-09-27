package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;

import java.util.List;

/**
 * Finds targets. Replaces both CastMethod and TargetSelector, which were the same idea:
 * "given the current cast, who/where?". Implementations may return an empty list.
 */
public interface TargetQuery {
    List<Target> find(ExecutionContext ctx);
}

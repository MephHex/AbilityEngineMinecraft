package me.mephisto.ability_engine.engine.data;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.graph.GraphNode;

/** Builds a node from its YAML section. Register custom node types in {@link NodeTypes}. */
@FunctionalInterface
public interface NodeFactory {
    GraphNode create(Params params, AbilityEngine engine);
}

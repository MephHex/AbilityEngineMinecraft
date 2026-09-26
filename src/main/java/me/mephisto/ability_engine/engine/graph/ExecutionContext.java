package me.mephisto.ability_engine.engine.graph;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.AbilityInstance;

import java.util.UUID;

/**
 * One branch of execution through an ability graph (was AbilityGraphContext).
 * Owns a blackboard scope and knows which node it is currently at.
 */
public final class ExecutionContext {

    private final AbilityInstance instance;
    private final Blackboard blackboard;
    private String currentNode;

    public ExecutionContext(AbilityInstance instance, Blackboard blackboard) {
        this.instance = instance;
        this.blackboard = blackboard;
    }

    public AbilityInstance instance() { return instance; }
    public AbilityEngine engine() { return instance.engine(); }
    public AbilityGraph graph() { return instance.ability().graph(); }
    public UUID caster() { return instance.caster(); }
    public Blackboard blackboard() { return blackboard; }
    public String currentNode() { return currentNode; }

    public <T> T get(Key<T> key) { return blackboard.get(key); }
    public <T> void put(Key<T> key, T value) { blackboard.put(key, value); }

    void enter(String nodeId) { this.currentNode = nodeId; }

    /**
     * Call from a node that is about to return {@link NodeResult#SUSPENDED}. The returned
     * resumer continues the graph from this node's output port later.
     */
    public Resumer suspend() {
        return new Resumer(this, currentNode);
    }

    /**
     * Split off a new branch at the current node with its own child blackboard.
     * The instance stays alive until every branch has finished.
     */
    public ExecutionContext fork() {
        instance.openBranch();
        ExecutionContext child = new ExecutionContext(instance, blackboard.child());
        child.currentNode = currentNode;
        return child;
    }
}

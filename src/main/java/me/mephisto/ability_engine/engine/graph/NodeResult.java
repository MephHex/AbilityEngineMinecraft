package me.mephisto.ability_engine.engine.graph;

/**
 * What a node did: either "continue out of this port" or "suspended, someone will resume me".
 * The node never knows which node comes next; the graph's edges decide that.
 */
public record NodeResult(String port, boolean suspended) {

    public static final NodeResult NEXT = new NodeResult(Ports.OUT, false);
    public static final NodeResult SUSPENDED = new NodeResult(null, true);

    public static NodeResult out(String port) { return new NodeResult(port, false); }
}

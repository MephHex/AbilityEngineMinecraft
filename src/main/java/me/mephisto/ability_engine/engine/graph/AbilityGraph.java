package me.mephisto.ability_engine.engine.graph;

import java.util.Map;
import java.util.Set;

/** Immutable node graph. Build with {@link GraphBuilder}, which validates the wiring. */
public final class AbilityGraph {

    private final String id;
    private final String startNodeId;
    private final Map<String, GraphNode> nodes;
    private final Map<String, Map<String, String>> edges; // nodeId -> port -> next nodeId

    AbilityGraph(String id, String startNodeId, Map<String, GraphNode> nodes, Map<String, Map<String, String>> edges) {
        this.id = id;
        this.startNodeId = startNodeId;
        this.nodes = Map.copyOf(nodes);
        this.edges = Map.copyOf(edges);
    }

    public String id() { return id; }
    public String startNodeId() { return startNodeId; }
    public GraphNode node(String nodeId) { return nodes.get(nodeId); }
    public Set<String> nodeIds() { return nodes.keySet(); }

    /** Next node for (node, port), or null if that port isn't wired (the branch ends). */
    public String next(String fromNodeId, String port) {
        Map<String, String> out = edges.get(fromNodeId);
        return out == null ? null : out.get(port);
    }
}

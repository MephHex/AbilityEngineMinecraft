package me.mephisto.ability_engine.engine.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and validates an {@link AbilityGraph}. All wiring mistakes (typo'd node ids,
 * ports a node doesn't have) are reported at load time instead of as an NPE mid-cast.
 */
public final class GraphBuilder {

    private final String id;
    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> edges = new HashMap<>();
    private final List<String> problems = new ArrayList<>();
    private String start;

    private GraphBuilder(String id) { this.id = id; }

    public static GraphBuilder create(String id) { return new GraphBuilder(id); }

    public GraphBuilder node(String nodeId, GraphNode node) {
        if (nodes.putIfAbsent(nodeId, node) != null) problems.add("duplicate node '" + nodeId + "'");
        if (start == null) start = nodeId; // first node is the default entry point
        return this;
    }

    public GraphBuilder start(String nodeId) {
        this.start = nodeId;
        return this;
    }

    public GraphBuilder edge(String from, String port, String to) {
        String previous = edges.computeIfAbsent(from, k -> new HashMap<>()).put(port, to);
        if (previous != null) problems.add("'" + from + "." + port + "' wired twice");
        return this;
    }

    /** Shorthand for wiring the default "out" port. */
    public GraphBuilder next(String from, String to) { return edge(from, Ports.OUT, to); }

    public AbilityGraph build() {
        List<String> errors = new ArrayList<>(problems);
        if (start == null) errors.add("graph has no nodes");
        else if (!nodes.containsKey(start)) errors.add("start node '" + start + "' does not exist");

        for (var from : edges.entrySet()) {
            GraphNode node = nodes.get(from.getKey());
            if (node == null) {
                errors.add("edge from unknown node '" + from.getKey() + "'");
                continue;
            }
            for (var edge : from.getValue().entrySet()) {
                String port = edge.getKey();
                if (!node.outputs().contains(port)) {
                    errors.add("node '" + from.getKey() + "' has no port '" + port + "' (has " + node.outputs() + ")");
                }
                if (!nodes.containsKey(edge.getValue())) {
                    errors.add("'" + from.getKey() + "." + port + "' points at unknown node '" + edge.getValue() + "'");
                }
            }
        }
        if (!errors.isEmpty()) throw new GraphValidationException(id, errors);
        return new AbilityGraph(id, start, nodes, edges);
    }
}

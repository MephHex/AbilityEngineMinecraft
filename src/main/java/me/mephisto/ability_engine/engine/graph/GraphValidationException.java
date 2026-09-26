package me.mephisto.ability_engine.engine.graph;

import java.util.List;

public class GraphValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient List<String> problems;

    public GraphValidationException(String graphId, List<String> problems) {
        super("Graph '" + graphId + "' is invalid: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() { return problems; }
}

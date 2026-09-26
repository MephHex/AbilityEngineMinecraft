package me.mephisto.ability_engine.engine.graph;

import me.mephisto.ability_engine.engine.EngineLog;

/**
 * Walks a graph: execute node, follow the edge for the port it returned, repeat.
 * Stops when a node suspends or a port isn't wired (which closes the branch).
 */
public final class GraphRunner {

    /** Guards against accidental infinite loops in synchronous wiring (a -> b -> a). */
    private static final int MAX_SYNC_STEPS = 256;

    private final EngineLog log;

    public GraphRunner(EngineLog log) {
        this.log = log;
    }

    /** Run a fresh branch from the graph's start node. */
    public void start(ExecutionContext ctx) {
        runFrom(ctx, ctx.graph().startNodeId());
    }

    void continueFrom(ExecutionContext ctx, String fromNodeId, String port) {
        if (!ctx.instance().isActive()) return;
        String next = ctx.graph().next(fromNodeId, port);
        log.debug(() -> ctx.graph().id() + ": resumed " + fromNodeId + "." + port + " -> " + next);
        if (next == null) {
            ctx.instance().closeBranch();
            return;
        }
        runFrom(ctx, next);
    }

    private void runFrom(ExecutionContext ctx, String nodeId) {
        AbilityGraph graph = ctx.graph();
        String current = nodeId;
        int steps = 0;

        while (current != null) {
            if (!ctx.instance().isActive()) return;
            if (++steps > MAX_SYNC_STEPS) {
                log.warn(graph.id() + ": more than " + MAX_SYNC_STEPS + " synchronous steps, probably a loop. Cancelling.");
                ctx.instance().cancel("step_limit");
                return;
            }

            GraphNode node = graph.node(current);
            ctx.enter(current);
            NodeResult result;
            try {
                result = node.execute(ctx);
            } catch (RuntimeException e) {
                log.error(graph.id() + ": node '" + current + "' threw, cancelling cast", e);
                ctx.instance().cancel("node_error");
                return;
            }

            if (result.suspended()) {
                String paused = current;
                log.debug(() -> graph.id() + ": suspended at " + paused);
                return;
            }

            String from = current;
            current = graph.next(current, result.port());
            String to = current;
            log.debug(() -> graph.id() + ": " + from + "." + result.port() + " -> " + to);
        }
        ctx.instance().closeBranch();
    }
}

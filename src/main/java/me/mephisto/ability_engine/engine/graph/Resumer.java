package me.mephisto.ability_engine.engine.graph;

/**
 * One-shot continuation handed out by {@link ExecutionContext#suspend()}. Whoever holds it
 * (a delay timer, a projectile) calls {@link #resume(String)} exactly once with the port to exit.
 * Must be called on the main thread.
 */
public final class Resumer {

    private final ExecutionContext ctx;
    private final String nodeId;
    private boolean used;

    Resumer(ExecutionContext ctx, String nodeId) {
        this.ctx = ctx;
        this.nodeId = nodeId;
    }

    public ExecutionContext context() { return ctx; }

    /** The node it continues from. */
    public String node() { return nodeId; }

    public void resume(String port) {
        if (used) throw new IllegalStateException("Resumer for node '" + nodeId + "' used twice");
        used = true;
        ctx.engine().runner().continueFrom(ctx, nodeId, port);
    }

    /** End this branch without continuing (e.g. the projectile was discarded). */
    public void abandon() {
        if (used) return;
        used = true;
        ctx.instance().closeBranch();
    }
}

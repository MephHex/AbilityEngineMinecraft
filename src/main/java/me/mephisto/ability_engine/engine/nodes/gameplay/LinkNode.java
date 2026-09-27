package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.link.LinkManager;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Set;

/**
 * Tether the caster to the entity in {@code target} (a key) under {@code name}, replacing their tether of
 * that name (see LinkManager). Exits "out", or "none" if the key holds no entity.
 */
public final class LinkNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.NONE);

    private final String name;
    private final String targetKey;
    private final double range;
    private final double damageTaken;
    private final double redirect;
    private final boolean copyPositive;
    private final String cue;

    public LinkNode(String name, String targetKey, double range, double damageTaken, double redirect,
                    boolean copyPositive, String cue) {
        this.name = name;
        this.targetKey = targetKey;
        this.range = range;
        this.damageTaken = damageTaken;
        this.redirect = redirect;
        this.copyPositive = copyPositive;
        this.cue = cue;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var target = KeyQuery.read(ctx, targetKey);
        if (target.isEmpty() || !(target.get() instanceof EntityTarget e) || e.id().equals(ctx.caster())) {
            return NodeResult.out(Ports.NONE);
        }
        ctx.engine().links().link(new LinkManager.Link(ctx.caster(), name, e.id(), range, damageTaken, redirect,
                copyPositive, cue));
        return NodeResult.NEXT;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}

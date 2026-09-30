package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.platform.CueHandle;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.UUID;

/**
 * Start a LOOPING cue on an entity ({@code at: <key>}, default: the caster), e.g. the trident spin.
 * It runs until the cast ends, however it ends (finished, hit, interrupted, caster died): the cast
 * stops it. What the cue actually is (a vanilla pose, particles, another plugin's animation) is
 * decided by the platform's cue registry, not here.
 */
public final class StartCueNode implements GraphNode {

    private final String cueId;
    private final String onKey; // null = caster

    public StartCueNode(String cueId, String onKey) {
        this.cueId = cueId;
        this.onKey = onKey;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        UUID entity = onKey == null ? ctx.caster()
                : KeyQuery.read(ctx, onKey).filter(t -> t instanceof EntityTarget)
                        .map(t -> ((EntityTarget) t).id()).orElse(null);
        if (entity != null) {
            CueHandle handle = ctx.engine().cuesFor(ctx.caster()).start(cueId, entity);
            ctx.instance().onEnd(handle::stop);
        }
        return NodeResult.NEXT;
    }
}

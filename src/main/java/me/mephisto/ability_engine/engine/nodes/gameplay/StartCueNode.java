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
 * stops it. With {@code ticks: n} it stops after n ticks already, if the cast lasts longer (a quick
 * pose on a cast that goes on while its projectile flies). What the cue actually is (a vanilla pose, particles, another plugin's animation) is
 * decided by the platform's cue registry, not here.
 */
public final class StartCueNode implements GraphNode {

    private final String cueId;
    private final String onKey; // null = caster
    private final int ticks; // 0 = until the cast ends

    public StartCueNode(String cueId, String onKey) { this(cueId, onKey, 0); }

    public StartCueNode(String cueId, String onKey, int ticks) {
        this.cueId = cueId;
        this.onKey = onKey;
        this.ticks = ticks;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        UUID entity = onKey == null ? ctx.caster()
                : KeyQuery.read(ctx, onKey).filter(t -> t instanceof EntityTarget)
                        .map(t -> ((EntityTarget) t).id()).orElse(null);
        if (entity != null) {
            CueHandle handle = ctx.engine().cuesFor(ctx.caster()).start(cueId, entity);
            boolean[] stopped = {false};
            Runnable stop = () -> {
                if (stopped[0]) return;
                stopped[0] = true;
                handle.stop();
            };
            ctx.instance().onEnd(stop);
            if (ticks > 0) ctx.engine().scheduler().after(ticks, stop);
        }
        return NodeResult.NEXT;
    }
}

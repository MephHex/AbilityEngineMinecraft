package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.Optional;

/** Play a cosmetic cue at a blackboard target (default: the caster). Never affects gameplay. */
public final class PlayCueNode implements GraphNode {

    private final String cueId;
    private final String atKey; // null = caster
    private final String toKey; // null = a one-point cue; set = drawn from "at" to "to" (a beam)

    public PlayCueNode(String cueId, String atKey) {
        this(cueId, atKey, null);
    }

    public PlayCueNode(String cueId, String atKey, String toKey) {
        this.cueId = cueId;
        this.atKey = atKey;
        this.toKey = toKey;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Optional<Target> at = atKey == null ? Optional.of(new EntityTarget(ctx.caster())) : KeyQuery.read(ctx, atKey);
        var from = at.flatMap(ctx.engine().world()::positionOf);
        if (toKey == null) {
            from.ifPresent(p -> ctx.engine().cues().play(cueId, p.world(), p.position()));
        } else {
            var to = KeyQuery.read(ctx, toKey).flatMap(ctx.engine().world()::positionOf);
            if (from.isPresent() && to.isPresent()) {
                ctx.engine().cues().playLine(cueId, from.get().world(), from.get().position(), to.get().position());
            }
        }
        return NodeResult.NEXT;
    }
}

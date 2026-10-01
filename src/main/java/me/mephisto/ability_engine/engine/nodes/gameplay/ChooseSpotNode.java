package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.targeting.AimPoint;
import me.mephisto.ability_engine.engine.targeting.Targeting;

import java.util.Set;

/**
 * Pick a spot in the middle of a cast (e.g. while hovering in the sky): the same aim preview as
 * {@code targeting:}, confirmed with LMB. Not confirmed within {@code timeout} ticks, or cancelled (RMB,
 * another ability): the spot under the crosshair right then is used. Stored as {@code store}
 * (default "aim"). Exits "out", or "none" if there's no valid spot at all.
 */
public final class ChooseSpotNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.NONE);

    private final Targeting targeting;
    private final String store;

    public ChooseSpotNode(Targeting targeting, String store) {
        this.targeting = targeting;
        this.store = store;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Resumer resumer = ctx.suspend();
        var engine = ctx.engine();
        boolean[] done = {false};
        engine.targeting().openInCast(ctx.caster(), ctx.instance().ability(), targeting,
                spot -> {
                    if (done[0] || !ctx.instance().isActive()) return;
                    done[0] = true;
                    ctx.blackboard().putRaw(store, spot);
                    resumer.resume(Ports.OUT);
                },
                reason -> {
                    if (done[0] || !ctx.instance().isActive()) return;
                    done[0] = true;
                    var fallback = AimPoint.resolve(engine, ctx.caster(), targeting); // wherever they're looking
                    fallback.ifPresent(spot -> ctx.blackboard().putRaw(store, spot));
                    resumer.resume(fallback.isPresent() ? Ports.OUT : Ports.NONE);
                });
        ctx.instance().onEnd(() -> engine.targeting().cancelInCast(ctx.caster())); // stunned, died: stop aiming
        return NodeResult.SUSPENDED;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}

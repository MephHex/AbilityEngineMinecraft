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
 * (default "aim"). Exits "out", or "none" if there's no valid spot at all. With its "cancelled" port wired, RMB (or
 * another ability's preview taking over) exits "cancelled" instead of using the crosshair (only the timeout still does).
 * <p>{@code key_confirms: true}: the ability's own key confirms it too, not only LMB. {@code echo_same_spot: true}: a
 * second body's copy of the cast (see Bodies) doesn't pick a spot of its own: it gets the one its owner picks (or none).
 */
public final class ChooseSpotNode implements GraphNode {

    /** The port a cancelled choice exits by, when it's wired. */
    public static final String CANCELLED = "cancelled";
    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.NONE, CANCELLED);

    private final Targeting targeting;
    private final String store;
    private final boolean cancelPort;
    private boolean echoSameSpot;
    private boolean keyConfirms;

    /** A second body's copy of this cast takes the spot its owner picks. */
    public ChooseSpotNode echoSameSpot(boolean on) {
        this.echoSameSpot = on;
        return this;
    }

    /** The ability's own key confirms the spot too. */
    public ChooseSpotNode keyConfirms(boolean on) {
        this.keyConfirms = on;
        return this;
    }

    public ChooseSpotNode(Targeting targeting, String store) {
        this(targeting, store, false);
    }

    /** @param cancelPort "cancelled" is wired: a cancel goes there instead of using the crosshair */
    public ChooseSpotNode(Targeting targeting, String store, boolean cancelPort) {
        this.targeting = targeting;
        this.store = store;
        this.cancelPort = cancelPort;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Resumer resumer = ctx.suspend();
        var engine = ctx.engine();
        var pair = ctx.instance().pair();
        String shared = "spot:" + ctx.currentNode();
        if (echoSameSpot && pair != null && ctx.instance().isEcho()) { // the owner picks for both
            pair.await(shared, picked -> {
                if (!ctx.instance().isActive()) return;
                if (picked instanceof me.mephisto.ability_engine.engine.target.PointTarget spot) {
                    ctx.blackboard().putRaw(store, spot);
                    resumer.resume(Ports.OUT);
                } else resumer.resume(Ports.NONE);
            });
            return NodeResult.SUSPENDED;
        }
        boolean[] done = {false};
        engine.targeting().openInCast(ctx.caster(), ctx.instance().ability(), targeting,
                spot -> {
                    if (done[0] || !ctx.instance().isActive()) return;
                    done[0] = true;
                    ctx.blackboard().putRaw(store, spot);
                    if (pair != null) pair.post(shared, spot);
                    resumer.resume(Ports.OUT);
                },
                reason -> {
                    if (done[0] || !ctx.instance().isActive()) return;
                    done[0] = true;
                    if (cancelPort && !"timeout".equals(reason)) {
                        if (pair != null) pair.post(shared, Boolean.FALSE);
                        resumer.resume(CANCELLED);
                        return;
                    }
                    var fallback = AimPoint.resolve(engine, ctx.caster(), targeting); // wherever they're looking
                    fallback.ifPresent(spot -> ctx.blackboard().putRaw(store, spot));
                    if (pair != null) pair.post(shared, fallback.isPresent() ? fallback.get() : Boolean.FALSE);
                    resumer.resume(fallback.isPresent() ? Ports.OUT : Ports.NONE);
                }, keyConfirms);
        ctx.instance().onEnd(() -> engine.targeting().cancelInCast(ctx.caster())); // stunned, died: stop aiming
        return NodeResult.SUSPENDED;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}

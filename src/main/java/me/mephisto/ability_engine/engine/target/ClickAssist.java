package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;

import java.util.Optional;

/**
 * A swing the player's game saw land. A left click on an entity reaches the server as "this one was hit" (the platform
 * passes it with the cast under {@link #KEY}), but the swing's own aim runs on the server: a hitscan, a cone, a line, from
 * where everyone is on the server right now. With ping, a hitbox edge or half a block too far, that can miss what the
 * player plainly hit. So the queries count the clicked entity as hit too, as long as it's within their range plus the
 * engine's melee assist reach (config.yml {@code melee-assist-reach}, 1 block) - and still alive, and someone the query may
 * hit at all (a hitscan passes through allies as always; area queries leave that to apply_effects).
 */
public final class ClickAssist {

    /** The blackboard key the platform puts the clicked entity (an EntityTarget) under. */
    public static final String KEY = "clicked";

    /** The clicked entity, when there is one within {@code range} + the assist reach of the eyes (alive, not the caster). */
    public static Optional<EntitySnapshot> clicked(ExecutionContext ctx, Aim a, double range) {
        if (!(ctx.blackboard().raw(KEY) instanceof EntityTarget t) || t.id().equals(ctx.caster())) return Optional.empty();
        double reach = range + Math.max(0, ctx.engine().meleeAssistReach());
        return ctx.engine().world().livingEntitiesNear(new PointTarget(a.world(), a.eye()), reach).stream()
                .filter(e -> e.id().equals(t.id()) && e.center().distance(a.eye()) <= reach)
                .findFirst();
    }

    /** Is {@code e} the clicked entity, within {@code range} + the assist reach? */
    public static boolean is(ExecutionContext ctx, Aim a, EntitySnapshot e, double range) {
        return ctx.blackboard().raw(KEY) instanceof EntityTarget t && t.id().equals(e.id()) && !e.id().equals(ctx.caster())
                && e.center().distance(a.eye()) <= range + Math.max(0, ctx.engine().meleeAssistReach());
    }

    private ClickAssist() {}
}

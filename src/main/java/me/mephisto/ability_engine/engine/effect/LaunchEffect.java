package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * Effect id "launch": sends the target straight up ({@code up} above 0) or down (below 0) at that speed, blocks per
 * tick: e.g. {@code up: -2} slams someone hanging in the air into the ground. Nothing moves the unmovable (block.knockback
 * or block.displace).
 */
public final class LaunchEffect implements Effect {

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target)) return;
        var tags = ctx.engine().tags();
        if (tags.has(target.id(), Tags.BLOCK_KNOCKBACK) || tags.has(target.id(), Tags.BLOCK_DISPLACE)) return;
        ctx.engine().movement().setVelocity(target.id(), new Vec3(0, ctx.params().requireDouble("up"), 0));
    }

    @Override
    public void validate(Params params) {
        double up = params.requireDouble("up");
        if (up == 0 || Math.abs(up) > 5) throw params.error("up", "blocks per tick, up (above 0) or down (below 0), at most 5");
    }
}

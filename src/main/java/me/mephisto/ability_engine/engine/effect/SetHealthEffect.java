package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * Effect id "set_health": the target's health becomes {@code share} of their max HP, whatever it was (e.g. revived at
 * 25%). Not a heal: healing_taken doesn't change it, and it can lower their health too.
 */
public final class SetHealthEffect implements Effect {

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target)) return;
        if (!ctx.engine().world().isAlive(target.id())) return;
        ctx.engine().movement().setHealthShare(target.id(), ctx.params().requireDouble("share"));
    }

    @Override
    public void validate(Params params) {
        double share = params.requireDouble("share");
        if (share <= 0 || share > 1) throw params.error("share", "a share of max HP, above 0 and at most 1 (0.25 = a quarter)");
    }
}

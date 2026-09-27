package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.status.StatusRegistry;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * Effect id "remove_status": {@code status} (required). Ends it on the target, e.g. consuming a mark.
 * {@code stacks: N} only takes N stacks off (it ends when none are left).
 */
public final class RemoveStatusEffect implements Effect {

    private final StatusRegistry statuses;

    public RemoveStatusEffect(StatusRegistry statuses) {
        this.statuses = statuses;
    }

    @Override
    public void apply(EffectContext ctx) {
        if (ctx.target() instanceof EntityTarget target) {
            String status = ctx.params().requireString("status");
            if (ctx.params().has("stacks")) ctx.engine().statuses().removeStacks(target.id(), status, ctx.params().getInt("stacks", 1));
            else ctx.engine().statuses().remove(target.id(), status);
        }
    }

    @Override
    public void validate(Params params) {
        String id = params.requireString("status");
        if (statuses.find(id).isEmpty()) {
            throw params.error("status", "unknown status '" + id + "' (define it under 'statuses:')");
        }
        if (params.has("stacks") && params.getInt("stacks", 1) < 1) throw params.error("stacks", "must be at least 1");
    }
}

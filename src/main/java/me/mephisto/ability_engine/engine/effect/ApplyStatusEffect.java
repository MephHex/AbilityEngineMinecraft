package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.status.StatusRegistry;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * Effect id "status". Params: {@code status} (required), {@code duration} in ticks (optional,
 * defaults to the status definition). Replaces StunEffect: stun is now just data.
 */
public final class ApplyStatusEffect implements Effect {

    private final StatusRegistry statuses;

    public ApplyStatusEffect(StatusRegistry statuses) {
        this.statuses = statuses;
    }

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target)) return;
        String statusId = ctx.params().requireString("status");
        if (ctx.params().has("duration")) {
            ctx.engine().statuses().apply(target.id(), statusId, ctx.params().getInt("duration", 0), ctx.caster());
        } else {
            ctx.engine().statuses().apply(target.id(), statusId, ctx.caster());
        }
    }

    @Override
    public void validate(Params params) {
        String id = params.requireString("status");
        if (statuses.find(id).isEmpty()) {
            throw params.error("status", "unknown status '" + id + "' (define it under 'statuses:')");
        }
    }
}

package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.status.StatusRegistry;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * Effect id "status". Params: {@code status} (required), {@code duration} in ticks (optional,
 * defaults to the status definition), {@code from: world} (optional: it comes from nobody instead of the
 * caster, so it counts as a debuff even on the caster themselves - e.g. to test debuff immunity).
 * Replaces StunEffect: stun is now just data.
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
        java.util.UUID source = "world".equals(ctx.params().getString("from", "caster")) ? null : ctx.caster();
        if (ctx.params().has("duration")) {
            ctx.engine().statuses().apply(target.id(), statusId, ctx.params().getInt("duration", 0), source);
        } else {
            ctx.engine().statuses().apply(target.id(), statusId, source);
        }
    }

    @Override
    public void validate(Params params) {
        String id = params.requireString("status");
        if (statuses.find(id).isEmpty()) {
            throw params.error("status", "unknown status '" + id + "' (define it under 'statuses:')");
        }
        String from = params.getString("from", "caster");
        if (!from.equals("caster") && !from.equals("world")) throw params.error("from", "expected caster or world");
    }
}

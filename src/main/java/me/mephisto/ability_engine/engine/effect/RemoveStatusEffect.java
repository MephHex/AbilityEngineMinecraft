package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.status.StatusRegistry;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/** Effect id "remove_status": {@code status} (required). Ends it on the target, e.g. consuming a mark. */
public final class RemoveStatusEffect implements Effect {

    private final StatusRegistry statuses;

    public RemoveStatusEffect(StatusRegistry statuses) {
        this.statuses = statuses;
    }

    @Override
    public void apply(EffectContext ctx) {
        if (ctx.target() instanceof EntityTarget target) {
            ctx.engine().statuses().remove(target.id(), ctx.params().requireString("status"));
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

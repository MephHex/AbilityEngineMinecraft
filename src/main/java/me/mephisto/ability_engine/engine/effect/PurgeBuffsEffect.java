package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.List;

/** Effect id "purge_buffs": removes every buff (positive status) from the target. */
public final class PurgeBuffsEffect implements Effect {

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target)) return;
        var statuses = ctx.engine().statuses();
        for (var s : List.copyOf(statuses.on(target.id()))) {
            if (s.def().positive()) statuses.remove(target.id(), s.def().id());
        }
    }

    @Override
    public void validate(Params params) {}
}

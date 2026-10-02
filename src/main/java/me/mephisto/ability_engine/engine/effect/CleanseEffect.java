package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.status.StatusManager;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.List;

/** Effect id "cleanse": removes every debuff (a non-buff status someone else put on them: poison, burn, a slow...). */
public final class CleanseEffect implements Effect {

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target)) return;
        var statuses = ctx.engine().statuses();
        for (var s : List.copyOf(statuses.on(target.id()))) {
            if (StatusManager.isDebuff(target.id(), s.def(), s.source())) statuses.remove(target.id(), s.def().id());
        }
    }

    @Override
    public void validate(Params params) {}
}

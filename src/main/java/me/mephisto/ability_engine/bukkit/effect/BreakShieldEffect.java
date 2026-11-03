package me.mephisto.ability_engine.bukkit.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

/** Effect id "break_shield": the target's shield (absorption) is gone at once, all of it (no params). */
public final class BreakShieldEffect implements Effect {

    private final OverflowShields shields;

    public BreakShieldEffect(OverflowShields shields) {
        this.shields = shields;
    }

    @Override
    public void apply(EffectContext ctx) {
        if (ctx.target() instanceof EntityTarget t && Bukkit.getEntity(t.id()) instanceof LivingEntity living
                && !living.isDead() && living.getAbsorptionAmount() > 0) {
            shields.clear(living);
        }
    }

    @Override
    public void validate(Params params) {}
}

package me.mephisto.ability_engine.bukkit.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

/**
 * Effect id "heal". Params (design HP, like damage): {@code amount}, or {@code max_hp: 0.2} (20% of the
 * target's max HP), {@code overflow}
 * (default false: excess healing becomes a decaying shield), {@code overflow_max} (default 150),
 * {@code overflow_decay} per second (default 25).
 */
public final class HealEffect implements Effect {

    private final DamageEffect scaleSource;
    private final OverflowShields shields;

    public HealEffect(DamageEffect scaleSource, OverflowShields shields) {
        this.scaleSource = scaleSource;
        this.shields = shields;
    }

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget t) || !(Bukkit.getEntity(t.id()) instanceof LivingEntity living)) return;
        double scale = scaleSource.scale();
        Params p = ctx.params();
        double amount = me.mephisto.ability_engine.engine.combat.DamageAmount.heal(ctx) // flat, or max_hp share
                * ctx.engine().stats().healingMultiplier(t.id());                      // less while poisoned
        shields.heal(living, amount / scale, p.getBool("overflow", false),
                p.getDouble("overflow_max", 150) / scale, p.getDouble("overflow_decay", 25) / scale);
    }

    @Override
    public void validate(Params params) {
        me.mephisto.ability_engine.engine.combat.DamageAmount.validate(params, false);
    }
}

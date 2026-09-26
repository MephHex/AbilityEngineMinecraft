package me.mephisto.ability_engine.bukkit.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

/**
 * Effect id "heal". Params (design HP, like damage): {@code amount} (required), {@code overflow}
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
        shields.heal(living, p.requireDouble("amount") / scale, p.getBool("overflow", false),
                p.getDouble("overflow_max", 150) / scale, p.getDouble("overflow_decay", 25) / scale);
    }

    @Override
    public void validate(Params params) {
        if (params.requireDouble("amount") < 0) throw params.error("amount", "must be >= 0");
    }
}

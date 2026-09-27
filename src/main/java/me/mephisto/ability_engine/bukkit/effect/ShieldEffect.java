package me.mephisto.ability_engine.bukkit.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

/**
 * Effect id "shield": absorption hearts (yellow), eaten by damage before health. Params (design HP,
 * like damage): {@code amount} (required), {@code max} (the total it can build up to; default: no cap
 * beyond what's given), {@code decay} per second (default 0: lasts until it's broken).
 */
public final class ShieldEffect implements Effect {

    private final DamageEffect scaleSource;
    private final OverflowShields shields;

    public ShieldEffect(DamageEffect scaleSource, OverflowShields shields) {
        this.scaleSource = scaleSource;
        this.shields = shields;
    }

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget t) || !(Bukkit.getEntity(t.id()) instanceof LivingEntity living)
                || living.isDead()) return;
        double scale = scaleSource.scale();
        Params p = ctx.params();
        double amount = p.requireDouble("amount") / scale;
        double max = p.getDouble("max", 0) / scale;
        double cap = max > 0 ? max : living.getAbsorptionAmount() + amount;
        shields.add(living, amount, cap, p.getDouble("decay", 0) / scale);
    }

    @Override
    public void validate(Params params) {
        if (params.requireDouble("amount") < 0) throw params.error("amount", "must be >= 0");
        if (params.getDouble("max", 0) < 0) throw params.error("max", "must be >= 0");
        if (params.getDouble("decay", 0) < 0) throw params.error("decay", "must be >= 0");
    }
}

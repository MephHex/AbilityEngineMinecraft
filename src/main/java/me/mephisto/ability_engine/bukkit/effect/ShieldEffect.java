package me.mephisto.ability_engine.bukkit.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

/**
 * Effect id "shield": absorption hearts (yellow), eaten by damage before health. Params (design HP,
 * like damage): {@code amount}, or {@code max_hp: 0.4} (40% of the target's max HP); {@code max} (the
 * total it can build up to; default: no cap beyond what's given), {@code decay} per second (default 0:
 * lasts until it's broken), {@code lasts} ticks (whatever is left disappears that long after the last
 * shield given; default 0: no limit).
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
        double amount = me.mephisto.ability_engine.engine.combat.DamageAmount.heal(ctx) / scale; // flat, or max_hp share
        double max = p.getDouble("max", 0) / scale;
        double cap = max > 0 ? max : living.getAbsorptionAmount() + amount;
        shields.add(living, amount, cap, p.getDouble("decay", 0) / scale, p.getInt("lasts", 0));
    }

    @Override
    public void validate(Params params) {
        me.mephisto.ability_engine.engine.combat.DamageAmount.validate(params, false);
        if (params.getDouble("max", 0) < 0) throw params.error("max", "must be >= 0");
        if (params.getDouble("decay", 0) < 0) throw params.error("decay", "must be >= 0");
        if (params.getInt("lasts", 0) < 0) throw params.error("lasts", "must be >= 0 (ticks; 0 = no limit)");
    }
}

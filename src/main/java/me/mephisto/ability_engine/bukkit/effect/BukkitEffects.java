package me.mephisto.ability_engine.bukkit.effect;

import me.mephisto.ability_engine.engine.effect.EffectRegistry;

/** Registers the Bukkit-side effects (was EffectLoader). "status" is built into the engine. */
public final class BukkitEffects {

    /** Returns the damage effect so the plugin can apply damage-scale from config.yml on (re)load. */
    public static DamageEffect registerBuiltins(EffectRegistry registry, OverflowShields shields) {
        DamageEffect damage = new DamageEffect();
        damage.setShields(shields);
        registry.register("damage", damage);
        registry.register("heal", new HealEffect(damage, shields));
        registry.register("shield", new ShieldEffect(damage, shields));
        registry.register("teleport", new TeleportEffect());
        registry.register("knockback", new KnockbackEffect());
        return damage;
    }

    private BukkitEffects() {}
}

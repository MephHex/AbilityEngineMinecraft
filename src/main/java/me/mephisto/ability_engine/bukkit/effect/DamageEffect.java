package me.mephisto.ability_engine.bukkit.effect;

import me.mephisto.ability_engine.engine.EngineLog;
import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Effect id "damage". Params: {@code amount} (required, in design HP), {@code ignore_iframes} (default false).
 * Minecraft health dealt = amount / damage-scale (config.yml, default 10: 20 health = 200 design HP).
 * {@code lifesteal: 1.0} heals the caster for that share of the damage actually dealt; add
 * {@code overflow: true} (and optional overflow_max / overflow_decay) to turn excess into a shield.
 * {@code knockback: false} deals it as magic damage (like vanilla poison): no knockback, but the kill
 * is still credited to the caster. Meant for damage over time.
 * With /ae debug on, every hit logs the target's health before/after, so a hit that the game
 * silently refused (PvP off, creative target, armor stand, protection plugin) is visible.
 */
public final class DamageEffect implements Effect {

    private static int applying;

    private double scale = 10;
    private OverflowShields shields;

    /** Design HP per Minecraft health point. */
    public void setScale(double scale) { this.scale = scale > 0 ? scale : 10; }

    public double scale() { return scale; }

    public void setShields(OverflowShields shields) { this.shields = shields; }

    /**
     * True while this effect is dealing damage. Ability damage is sourced to the caster, so Bukkit
     * reports it exactly like a melee hit; listeners that intercept melee must check this first.
     */
    public static boolean isApplying() { return applying > 0; }

    @Override
    public void apply(EffectContext ctx) {
        EngineLog log = ctx.engine().log();
        if (!(ctx.target() instanceof EntityTarget target)) {
            log.debug(() -> "damage: target is a point, not an entity -> skipped");
            return;
        }
        Entity entity = Bukkit.getEntity(target.id());
        if (!(entity instanceof LivingEntity living) || living.isDead()) {
            log.debug(() -> "damage: target " + target.id() + " is gone or not living (" + entity + ") -> skipped");
            return;
        }

        double design = ctx.params().requireDouble("amount");
        double amount = design / scale;
        // Vanilla ignores a hit landing within ~10 ticks of the last one. Rapid channels need this.
        if (ctx.params().getBool("ignore_iframes", false)) living.setNoDamageTicks(0);

        double before = living.getHealth();
        Entity damager = Bukkit.getEntity(ctx.caster());
        boolean knockback = ctx.params().getBool("knockback", true);
        Vector velocityBefore = living.getVelocity();
        applying++;
        try {
            if (!knockback) living.damage(amount, magic(damager, living));
            else if (damager != null && !damager.equals(living)) living.damage(amount, damager);
            else living.damage(amount);
        } finally {
            applying--;
        }
        // Magic damage has no knockback in vanilla; if anything pushed them anyway, undo it.
        if (!knockback && !living.getVelocity().equals(velocityBefore)) living.setVelocity(velocityBefore);
        double after = living.getHealth();

        double lifesteal = ctx.params().getDouble("lifesteal", 0);
        if (lifesteal > 0 && after < before && shields != null && damager instanceof LivingEntity self && !self.equals(living)) {
            Params p = ctx.params();
            shields.heal(self, (before - after) * lifesteal, p.getBool("overflow", false),
                    p.getDouble("overflow_max", 150) / scale, p.getDouble("overflow_decay", 25) / scale);
        }

        log.debug(() -> String.format("damage: %s %.0f design HP = %.1f health, health %.1f -> %.1f%s",
                living.getType(), design, amount, before, after,
                after < before ? "" : "   <-- NOT APPLIED (cancelled event / invulnerable / armor stand)"));
    }

    /** Magic damage (no knockback), still credited to the caster for kills. */
    private static DamageSource magic(Entity damager, LivingEntity target) {
        DamageSource.Builder source = DamageSource.builder(DamageType.MAGIC);
        if (damager != null && !damager.equals(target)) source.withCausingEntity(damager).withDirectEntity(damager);
        return source.build();
    }

    @Override
    public void validate(Params params) {
        if (params.requireDouble("amount") < 0) throw params.error("amount", "must be >= 0");
    }
}

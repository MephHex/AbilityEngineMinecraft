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
 * Effect id "damage". Params, added together: {@code amount} (flat design HP), {@code base: 1.2} (120% of
 * the caster's base damage), {@code max_hp: 0.1} (10% of the target's max HP; this part ignores armor);
 * {@code ignore_iframes} (default false).
 * Minecraft health dealt = amount / damage-scale (config.yml, default 10: 20 health = 200 design HP).
 * {@code lifesteal: 1.0} heals the caster for that share of the damage actually dealt; add
 * {@code overflow: true} (and optional overflow_max / overflow_decay) to turn excess into a shield.
 * {@code knockback: false} deals it as magic damage (like vanilla poison): no knockback, but the kill
 * is still credited to the caster. Meant for damage over time.
 * {@code damage_type: freeze | fire | magic} deals it as that vanilla damage (no knockback either):
 * freeze = ice damage (frozen hurt sound), fire = burning. {@code scale_by: <key>} multiplies the amount
 * by a number the cast stored (a charged shot's power).
 * With /ae debug on, every hit logs the target's health before/after, so a hit that the game
 * silently refused (PvP off, creative target, armor stand, protection plugin) is visible.
 */
public final class DamageEffect implements Effect {

    private static int applying;
    /** Share of the hit being dealt that skips armor (its max_hp part): read by DamageModifierListener. */
    private static double pierceShare;

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

    /** The hit being applied right now is primary / secondary fire (not an ability): see StatSheets#isFire. */
    private static boolean firing;

    /** Damage being applied now that comes from an ability (1, 2, 3, the ultimate), not from fire or vanilla. */
    public static boolean isAbilityDamage() { return applying > 0 && !firing; }

    /** Share (0..1) of the hit this effect is dealing right now that armor doesn't reduce (its max_hp part). */
    public static double pierceShare() { return applying > 0 ? pierceShare : 0; }

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

        // Backstab crits (backstab: 1.5): from behind the target, multiply.
        // amount (flat), base (x the caster's base damage) or max_hp (x the target's max HP); backstab, scale_by
        var parts = me.mephisto.ability_engine.engine.combat.DamageAmount.damage(ctx);
        double design = parts.total();
        if (ctx.engine().spellShields().absorb(ctx, design)) { // a spell shield ate it: nothing is dealt
            log.debug(() -> "damage: absorbed by a spell shield (" + design + ")");
            return;
        }
        me.mephisto.ability_engine.engine.combat.HitReactions.onHit(ctx); // e.g. basic attacks on a golem cut his cooldowns
        double amount = design / scale;
        // Vanilla ignores a hit landing within ~10 ticks of the last one. Rapid channels need this.
        if (ctx.params().getBool("ignore_iframes", false)) living.setNoDamageTicks(0);

        double before = living.getHealth();
        Entity damager = Bukkit.getEntity(ctx.caster());
        DamageType type = typeOf(ctx.params().getString("damage_type", null));
        boolean knockback = type == null && ctx.params().getBool("knockback", true);
        Vector velocityBefore = living.getVelocity();
        applying++;
        boolean previousFiring = firing;
        firing = ctx.engine().stats().fromFire(ctx);
        double previousShare = pierceShare;
        pierceShare = parts.pierceShare();
        try {
            if (!knockback) living.damage(amount, typed(type != null ? type : DamageType.MAGIC, damager, living));
            else if (damager != null && !damager.equals(living)) living.damage(amount, damager);
            else living.damage(amount);
        } finally {
            applying--;
            pierceShare = previousShare;
            firing = previousFiring;
        }
        // Magic damage has no knockback in vanilla; if anything pushed them anyway, undo it.
        if (!knockback && !living.getVelocity().equals(velocityBefore)) living.setVelocity(velocityBefore);
        double after = living.getHealth();
        if (after < before) ctx.engine().notifyDamageDealt(ctx.caster(), target.id()); // e.g. stealth breaks

        double lifesteal = ctx.params().getDouble("lifesteal", 0)
                + (ctx.engine().stats().fromFire(ctx) ? 0 : ctx.engine().stats().abilityLifesteal(ctx.caster())); // abilities only
        if (lifesteal > 0 && after < before && shields != null && damager instanceof LivingEntity self && !self.equals(living)) {
            Params p = ctx.params();
            double healing = ctx.engine().stats().healingMultiplier(ctx.caster()); // a poisoned attacker heals less
            shields.heal(self, (before - after) * lifesteal * healing, p.getBool("overflow", false),
                    p.getDouble("overflow_max", 150) / scale, p.getDouble("overflow_decay", 25) / scale);
        }

        log.debug(() -> String.format("damage: %s %.0f design HP = %.1f health, health %.1f -> %.1f%s",
                living.getType(), design, amount, before, after,
                after < before ? "" : "   <-- NOT APPLIED (cancelled event / invulnerable / armor stand)"));
    }

    /** damage_type: null (normal) or a vanilla type. */
    private static DamageType typeOf(String name) {
        if (name == null) return null;
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "freeze" -> DamageType.FREEZE;
            case "fire" -> DamageType.ON_FIRE;
            case "magic" -> DamageType.MAGIC;
            default -> null;
        };
    }

    /** Damage of a vanilla type without knockback (magic, freeze, fire), still credited to the caster for kills. */
    private static DamageSource typed(DamageType type, Entity damager, LivingEntity target) {
        DamageSource.Builder source = DamageSource.builder(type);
        if (damager != null && !damager.equals(target)) source.withCausingEntity(damager).withDirectEntity(damager);
        return source.build();
    }

    @Override
    public void validate(Params params) {
        me.mephisto.ability_engine.engine.combat.DamageAmount.validate(params, true);
        String type = params.getString("damage_type", null);
        if (type != null && typeOf(type) == null) throw params.error("damage_type", "expected freeze, fire or magic");
    }
}

package me.mephisto.ability_engine.bukkit.status;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

/**
 * Movement attribute modifiers driven by tags: full lock (block.move) and slow (state.slowed).
 * The lock keys match the old StunEffect, so {@link #remove} also cleans up its leftovers.
 */
final class MovementLock {

    private static final NamespacedKey SPEED_KEY = new NamespacedKey("ability_engine", "stun_speed");
    private static final NamespacedKey JUMP_KEY = new NamespacedKey("ability_engine", "stun_jump");
    private static final NamespacedKey SLOW_KEY = new NamespacedKey("ability_engine", "slow_speed");
    private static final NamespacedKey STEADFAST_KEY = new NamespacedKey("ability_engine", "steadfast");
    private static final NamespacedKey STURDY_KEY = new NamespacedKey("ability_engine", "sturdy");
    private static final NamespacedKey HASTE_KEY = new NamespacedKey("ability_engine", "haste_speed");
    /** -40% movement speed. One fixed strength for now; per-status magnitudes need stat modifiers. */
    private static final double SLOW_AMOUNT = -0.4;
    /** +30% movement speed (state.hasted). Stacks multiplicatively-ish with a slow: both apply. */
    private static final double HASTE_AMOUNT = 0.3;

    /** Vanilla's flying speed (players): creative-style flight ignores the movement attribute. */
    private static final float DEFAULT_FLY_SPEED = 0.1f;

    static void apply(LivingEntity living) {
        add(living.getAttribute(Attribute.MOVEMENT_SPEED), SPEED_KEY, -1.0);
        add(living.getAttribute(Attribute.JUMP_STRENGTH), JUMP_KEY, -1.0);
        if (living instanceof org.bukkit.entity.Player p) p.setFlySpeed(0f); // flying (state.flying) too
    }

    static void remove(LivingEntity living) {
        strip(living.getAttribute(Attribute.MOVEMENT_SPEED), SPEED_KEY);
        strip(living.getAttribute(Attribute.JUMP_STRENGTH), JUMP_KEY);
        if (living instanceof org.bukkit.entity.Player p) p.setFlySpeed(DEFAULT_FLY_SPEED);
    }

    static void applySlow(LivingEntity living) {
        add(living.getAttribute(Attribute.MOVEMENT_SPEED), SLOW_KEY, SLOW_AMOUNT);
    }

    static void removeSlow(LivingEntity living) {
        strip(living.getAttribute(Attribute.MOVEMENT_SPEED), SLOW_KEY);
    }

    static void applyHaste(LivingEntity living) {
        add(living.getAttribute(Attribute.MOVEMENT_SPEED), HASTE_KEY, HASTE_AMOUNT);
    }

    static void removeHaste(LivingEntity living) {
        strip(living.getAttribute(Attribute.MOVEMENT_SPEED), HASTE_KEY);
    }

    /** Full vanilla knockback resistance (our own knockback effect checks the tag instead). */
    static void applySteadfast(LivingEntity living) {
        AttributeInstance attr = living.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attr != null && attr.getModifier(STEADFAST_KEY) == null) {
            attr.addModifier(new AttributeModifier(STEADFAST_KEY, 1.0, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    static void removeSteadfast(LivingEntity living) {
        strip(living.getAttribute(Attribute.KNOCKBACK_RESISTANCE), STEADFAST_KEY);
    }

    /** state.sturdy: half knockback from vanilla hits (ability knockback is halved by KnockbackEffect). */
    static void applySturdy(LivingEntity living) {
        AttributeInstance attr = living.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attr != null && attr.getModifier(STURDY_KEY) == null) {
            attr.addModifier(new AttributeModifier(STURDY_KEY, 0.5, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    static void removeSturdy(LivingEntity living) {
        strip(living.getAttribute(Attribute.KNOCKBACK_RESISTANCE), STURDY_KEY);
    }

    private static void add(AttributeInstance attr, NamespacedKey key, double amount) {
        if (attr != null && attr.getModifier(key) == null) {
            attr.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private static void strip(AttributeInstance attr, NamespacedKey key) {
        if (attr == null) return;
        AttributeModifier mod = attr.getModifier(key);
        if (mod != null) attr.removeModifier(mod);
    }

    private MovementLock() {}
}

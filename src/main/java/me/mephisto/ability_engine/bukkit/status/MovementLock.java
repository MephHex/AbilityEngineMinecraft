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
    /** -40% movement speed. One fixed strength for now; per-status magnitudes need stat modifiers. */
    private static final double SLOW_AMOUNT = -0.4;

    static void apply(LivingEntity living) {
        add(living.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED), SPEED_KEY, -1.0);
        add(living.getAttribute(Attribute.GENERIC_JUMP_STRENGTH), JUMP_KEY, -1.0);
    }

    static void remove(LivingEntity living) {
        strip(living.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED), SPEED_KEY);
        strip(living.getAttribute(Attribute.GENERIC_JUMP_STRENGTH), JUMP_KEY);
    }

    static void applySlow(LivingEntity living) {
        add(living.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED), SLOW_KEY, SLOW_AMOUNT);
    }

    static void removeSlow(LivingEntity living) {
        strip(living.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED), SLOW_KEY);
    }

    /** Full vanilla knockback resistance (our own knockback effect checks the tag instead). */
    static void applySteadfast(LivingEntity living) {
        AttributeInstance attr = living.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (attr != null && attr.getModifier(STEADFAST_KEY) == null) {
            attr.addModifier(new AttributeModifier(STEADFAST_KEY, 1.0, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    static void removeSteadfast(LivingEntity living) {
        strip(living.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE), STEADFAST_KEY);
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

package me.mephisto.ability_engine.bukkit.effect;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Overflow: excess healing becomes a decaying shield, shown as Minecraft's absorption hearts (yellow).
 * Absorption is capped by the max-absorption attribute, so we raise that cap while a shield exists.
 * Damage eats absorption first, the vanilla way. Decay is applied every 5 ticks.
 */
public final class OverflowShields {

    private static final NamespacedKey CAP_KEY = new NamespacedKey("ability_engine", "overflow_cap");

    private final Map<UUID, Double> decayPerTick = new HashMap<>();
    /** Shields that vanish at a set time (server tick), whatever is left of them. */
    private final Map<UUID, Integer> expiresAt = new HashMap<>();

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5, 5);
    }

    /** Add shield (Minecraft health units), capped at {@code cap}, decaying {@code decayPerSecond}. */
    public void add(LivingEntity e, double amount, double cap, double decayPerSecond) {
        add(e, amount, cap, decayPerSecond, 0);
    }

    /** ... and with {@code lastsTicks} above 0, the whole shield is gone that long after this add. */
    public void add(LivingEntity e, double amount, double cap, double decayPerSecond, int lastsTicks) {
        if (amount <= 0) return;
        if (lastsTicks > 0) expiresAt.put(e.getUniqueId(), Bukkit.getCurrentTick() + lastsTicks);
        else expiresAt.remove(e.getUniqueId());
        AttributeInstance max = e.getAttribute(Attribute.MAX_ABSORPTION);
        if (max != null) {
            AttributeModifier old = max.getModifier(CAP_KEY);
            if (old != null) max.removeModifier(old);
            max.addModifier(new AttributeModifier(CAP_KEY, cap, AttributeModifier.Operation.ADD_NUMBER));
        }
        e.setAbsorptionAmount(Math.min(cap, e.getAbsorptionAmount() + amount));
        decayPerTick.put(e.getUniqueId(), decayPerSecond / 20.0);
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        for (UUID id : List.copyOf(expiresAt.keySet())) {
            if (now < expiresAt.get(id)) continue;
            expiresAt.remove(id);
            if (Bukkit.getEntity(id) instanceof LivingEntity living && living.isValid()) clear(living);
        }
        for (UUID id : List.copyOf(decayPerTick.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                decayPerTick.remove(id);
                continue;
            }
            double left = living.getAbsorptionAmount() - decayPerTick.get(id) * 5;
            if (left <= 0) clear(living);
            else living.setAbsorptionAmount(left);
        }
    }

    public void clear(LivingEntity living) {
        decayPerTick.remove(living.getUniqueId());
        expiresAt.remove(living.getUniqueId());
        living.setAbsorptionAmount(0);
        AttributeInstance max = living.getAttribute(Attribute.MAX_ABSORPTION);
        if (max != null) {
            AttributeModifier mod = max.getModifier(CAP_KEY);
            if (mod != null) max.removeModifier(mod);
        }
    }

    /**
     * Heal (Minecraft health units). With overflow, whatever doesn't fit becomes shield.
     * Shared by the heal effect and lifesteal.
     */
    public void heal(LivingEntity e, double amount, boolean overflow, double cap, double decayPerSecond) {
        if (amount <= 0 || e.isDead()) return;
        AttributeInstance maxHealth = e.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20;
        double total = e.getHealth() + amount;
        e.setHealth(Math.min(max, total));
        if (overflow && total > max) add(e, total - max, cap, decayPerSecond);
    }
}

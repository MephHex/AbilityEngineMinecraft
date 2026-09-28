package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;

/**
 * Tethers that copy buffs (a link with {@code copy_positive}, e.g. Radiant Bond) also copy the owner's
 * beneficial VANILLA potion effects to the target: a drunk Speed potion, a beacon's Haste, /effect...
 * Engine statuses are copied by the engine itself; the potions our own tags add (cause PLUGIN) aren't
 * copied here, they come with the status that grants them.
 */
public final class BondPotions implements Listener {

    private final AbilityEngine engine;

    public BondPotions(AbilityEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent event) {
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        if (event.getCause() == EntityPotionEffectEvent.Cause.PLUGIN) return; // ours (tags), or a copy: no loops
        PotionEffect effect = event.getNewEffect();
        if (effect == null || effect.getType().getCategory() != PotionEffectTypeCategory.BENEFICIAL) return;
        for (var link : engine.links().ownedBy(event.getEntity().getUniqueId())) {
            if (!link.copyPositive()) continue;
            if (Bukkit.getEntity(link.target()) instanceof LivingEntity target && !target.isDead()) {
                target.addPotionEffect(effect);
            }
        }
    }
}

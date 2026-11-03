package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Traits blind_immune / darkness_immune: vanilla Blindness / Darkness never land on them either (a Warden, a potion,
 * another plugin). The engine's own statuses are stopped by its status guards.
 */
public final class SightGuard implements Listener {

    private final AbilityEngine engine;

    public SightGuard(AbilityEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (e.getNewEffect() == null) return; // (one going away)
        PotionEffectType type = e.getNewEffect().getType();
        String trait = type.equals(PotionEffectType.BLINDNESS) ? CharacterDef.BLIND_IMMUNE
                : type.equals(PotionEffectType.DARKNESS) ? CharacterDef.DARKNESS_IMMUNE : null;
        if (trait == null) return;
        if (engine.loadouts().characterOf(e.getEntity().getUniqueId()).map(c -> c.has(trait)).orElse(false)) e.setCancelled(true);
    }
}

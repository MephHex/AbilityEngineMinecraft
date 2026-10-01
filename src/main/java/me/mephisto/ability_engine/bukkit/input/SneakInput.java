package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.bukkit.hud.HotbarHud;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.Slots;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.UUID;

/**
 * SHIFT for characters with a {@code sneak} slot: pressing it casts the slot, letting go releases that ability's
 * charge (e.g. "hold SHIFT to uproot": a charge node that fires when full, or stops if let go early). Characters
 * without one aren't affected (SHIFT still hops a rider off: RideGuard).
 */
public final class SneakInput implements Listener {

    private final AbilityEngine engine;
    private final HotbarHud hud;

    public SneakInput(AbilityEngine engine, HotbarHud hud) {
        this.engine = engine;
        this.hud = hud;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        var ability = engine.loadouts().abilityIn(id, Slots.SNEAK);
        if (ability.isEmpty() || engine.rides().mountOf(id).isPresent()) return; // riding: SHIFT hops off instead
        if (event.isSneaking()) {
            if (engine.loadouts().activate(id, Slots.SNEAK).success()) hud.refresh(p);
        } else {
            engine.instances().release(id, ability.get());
        }
    }
}

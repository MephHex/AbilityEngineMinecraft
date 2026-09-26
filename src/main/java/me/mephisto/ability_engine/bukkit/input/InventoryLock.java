package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Players with a character can't move, drop or pick up items: the inventory is a display.
 * Note: creative mode lets the client edit its own inventory; test in survival/adventure.
 * A future E-screen menu can still react to clicks: cancelled events reach every listener
 * that doesn't set ignoreCancelled.
 */
public final class InventoryLock implements Listener {

    private final AbilityEngine engine;

    public InventoryLock(AbilityEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && engine.loadouts().has(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && engine.loadouts().has(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && engine.loadouts().has(p.getUniqueId())) e.setCancelled(true);
    }
}

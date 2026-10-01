package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Players with a character can't move, drop or pick up items: the inventory is a display. Except, from
 * config.yml {@code inventory:}, some slots of their own inventory ({@code free-slots}, e.g. a bag for an
 * item plugin) and the 2x2 crafting grid ({@code free-crafting}): items can be moved in, out and between
 * those by plain clicks and drags. Anything that could put an item somewhere else (shift-click, number-key
 * swaps, double-click collecting, dropping) stays blocked, and what's left on the cursor or in the grid
 * when the inventory closes goes back into the free slots, never into the HUD.
 * Note: creative mode lets the client edit its own inventory; test in survival/adventure.
 * A future E-screen menu can still react to clicks: cancelled events reach every listener
 * that doesn't set ignoreCancelled.
 */
public final class InventoryLock implements Listener {

    /** Plain clicks: they only touch the clicked slot and the cursor. */
    private static final Set<InventoryAction> PLAIN = EnumSet.of(
            InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF, InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME, InventoryAction.PLACE_ALL, InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME, InventoryAction.SWAP_WITH_CURSOR, InventoryAction.NOTHING);

    private final AbilityEngine engine;
    /** PlayerInventory indexes players may use (9-35 = the main inventory, 9 top-left). */
    private Set<Integer> freeSlots = Set.of();
    private boolean freeCrafting;

    public InventoryLock(AbilityEngine engine) {
        this.engine = engine;
    }

    /** Reads {@code inventory.free-slots} and {@code inventory.free-crafting} (on start and /ae reload). */
    public void load(ConfigurationSection config, Logger log) {
        Set<Integer> slots = new HashSet<>();
        for (int slot : config.getIntegerList("inventory.free-slots")) {
            if (slot >= 9 && slot <= 35) slots.add(slot);
            else log.warning("inventory.free-slots: " + slot + " ignored (only 9-35, the main inventory, can be freed)");
        }
        freeSlots = Set.copyOf(slots);
        freeCrafting = config.getBoolean("inventory.free-crafting", false);
    }

    private boolean locked(Player p) { return engine.loadouts().has(p.getUniqueId()); }

    /** A raw slot of the view that's free: a free slot of their own inventory, or the crafting grid. */
    private boolean free(InventoryView view, int rawSlot) {
        if (view.getType() != InventoryType.CRAFTING) return false; // only their own inventory screen
        if (view.getInventory(rawSlot) instanceof PlayerInventory) return freeSlots.contains(view.convertSlot(rawSlot));
        return freeCrafting && view.getInventory(rawSlot) instanceof CraftingInventory && rawSlot >= 1 && rawSlot <= 4;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !locked(p)) return;
        InventoryView view = e.getView();
        if (PLAIN.contains(e.getAction()) && free(view, e.getRawSlot())) return;
        // The crafting result: take what the grid makes onto the cursor (not shift-click: that goes anywhere).
        if (freeCrafting && e.getSlotType() == InventoryType.SlotType.RESULT && view.getType() == InventoryType.CRAFTING
                && (e.getAction() == InventoryAction.PICKUP_ALL || e.getAction() == InventoryAction.NOTHING)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !locked(p)) return;
        for (int raw : e.getRawSlots()) {
            if (!free(e.getView(), raw)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Closing returns the cursor and the crafting grid to the inventory, wherever vanilla finds room (the
     * HUD's empty hotbar slots too). Put them in the free slots first. Runs before vanilla's own return.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !locked(p) || freeSlots.isEmpty()) return;
        InventoryView view = e.getView();
        if (view.getType() != InventoryType.CRAFTING) return;
        ItemStack cursor = view.getCursor();
        if (cursor != null && !cursor.isEmpty()) view.setCursor(stash(p.getInventory(), cursor));
        if (view.getTopInventory() instanceof CraftingInventory grid) {
            ItemStack[] matrix = grid.getMatrix();
            for (int i = 0; i < matrix.length; i++) {
                if (matrix[i] != null && !matrix[i].isEmpty()) matrix[i] = stash(p.getInventory(), matrix[i]);
            }
            grid.setMatrix(matrix);
        }
    }

    /** Into the free slots (stacking first, then empty ones); returns what didn't fit (null if all did). */
    private ItemStack stash(PlayerInventory inv, ItemStack item) {
        ItemStack left = item.clone();
        for (boolean emptyOnes : new boolean[] {false, true}) {
            for (int slot : freeSlots.stream().sorted().toList()) {
                ItemStack there = inv.getItem(slot);
                if (emptyOnes && (there == null || there.isEmpty())) {
                    inv.setItem(slot, left);
                    return null;
                }
                if (!emptyOnes && there != null && there.isSimilar(left)) {
                    int room = there.getMaxStackSize() - there.getAmount();
                    int moved = Math.min(room, left.getAmount());
                    if (moved <= 0) continue;
                    there.setAmount(there.getAmount() + moved);
                    left.setAmount(left.getAmount() - moved);
                    if (left.getAmount() <= 0) return null;
                }
            }
        }
        return left;
    }

    /** A HUD icon (e.g. a crowd-control barrier) is never placed as a block. */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) e.setCancelled(true);
    }
}

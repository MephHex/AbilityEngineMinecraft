package me.mephisto.ability_engine.bukkit.input;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Stores an ability id on an item (PersistentDataContainer). Replaces the hardcoded
 * "projectile_test" in the listener. Survives renames, restarts and moving between slots.
 */
public final class ItemBindings {

    private final NamespacedKey key;

    public ItemBindings(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "ability");
    }

    public boolean bind(ItemStack item, String abilityId) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, abilityId);
        item.setItemMeta(meta);
        return true;
    }

    public void unbind(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(key);
        item.setItemMeta(meta);
    }

    /** Ability id on this item, or null. */
    public String read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}

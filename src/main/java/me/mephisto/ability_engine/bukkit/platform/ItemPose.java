package me.mephisto.ability_engine.bukkit.platform;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import me.mephisto.ability_engine.engine.platform.CueHandle;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Vanilla poses as looping cues ({@code pose_bow}, {@code pose_crossbow}, {@code pose_spear}, ...): the caster "uses"
 * the item in their main hand with that item-use animation - a bow drawn, a crossbow aimed, a trident raised - for as
 * long as the cue runs. Everyone's own game draws it on the real player model, so it's as smooth as vanilla, in first
 * and third person.
 * <p>
 * How: for the time of the pose the held item gets a {@code consumable} component with that animation (one that never
 * finishes, silent, no crumbs) and the server starts using it; stopped, it stops using it and the component goes again.
 * Using an item slows a player to 20% (like drawing a bow): on Minecraft 1.21.11+ the item also gets
 * {@code use_effects} that keep their full speed and sprint; on older servers they're slowed while it lasts.
 */
final class ItemPose {

    /** Never finishes by itself (seconds): the cue ends it. Also how we know the component is ours. */
    private static final float FOREVER = 1_000_000f;
    private static final String FULL_SPEED = "[minecraft:use_effects={can_sprint:true,speed_multiplier:1.0f}]";
    private static final String NO_FULL_SPEED = "[!minecraft:use_effects]";
    private static boolean warnedSlow;

    private ItemPose() {}

    /** The cue names: pose_ + the animation, lowercase (pose_bow, pose_spear, pose_toot_horn...). */
    static String cueName(ItemUseAnimation animation) {
        return "pose_" + animation.name().toLowerCase(Locale.ROOT);
    }

    static CueHandle start(Plugin plugin, Entity entity, ItemUseAnimation animation) {
        if (!(entity instanceof LivingEntity living) || !living.isValid() || living.getEquipment() == null) {
            return CueHandle.NONE;
        }
        ItemStack held = living.getEquipment().getItemInMainHand();
        if (held.getType().isAir()) return CueHandle.NONE;
        if (living.hasActiveItem()) living.clearActiveItem(); // (a pose still on from the shot before: anew)
        int slot = entity instanceof Player p ? p.getInventory().getHeldItemSlot() : -1;
        boolean ours = !held.hasData(DataComponentTypes.CONSUMABLE) || isOurs(held);
        if (ours) {
            held.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                    .consumeSeconds(FOREVER)
                    .animation(animation)
                    .sound(Key.key("minecraft", "intentionally_empty"))
                    .hasConsumeParticles(false)
                    .build());
            held = fullSpeed(plugin, held, true);
            living.getEquipment().setItemInMainHand(held);
        }
        living.startUsingItem(EquipmentSlot.HAND);
        return () -> stop(plugin, living, slot, ours);
    }

    private static void stop(Plugin plugin, LivingEntity living, int slot, boolean ours) {
        if (!living.isValid()) return;
        if (living.hasActiveItem()) living.clearActiveItem();
        if (!ours) return;
        // The item it was put on: the same hotbar slot (they may have switched since), else the main hand.
        ItemStack item = living instanceof Player p && slot >= 0 ? p.getInventory().getItem(slot)
                : living.getEquipment() == null ? null : living.getEquipment().getItemInMainHand();
        if (item == null || !isOurs(item)) return;
        item.unsetData(DataComponentTypes.CONSUMABLE);
        item = fullSpeed(plugin, item, false);
        if (living instanceof Player p && slot >= 0) p.getInventory().setItem(slot, item);
        else living.getEquipment().setItemInMainHand(item);
    }

    private static boolean isOurs(ItemStack item) {
        Consumable c = item.getData(DataComponentTypes.CONSUMABLE);
        return c != null && c.consumeSeconds() >= FOREVER;
    }

    /** use_effects (1.21.11+): full speed and sprint while using it. Older servers: as it is, warned once. */
    @SuppressWarnings("deprecation")
    private static ItemStack fullSpeed(Plugin plugin, ItemStack item, boolean on) {
        try {
            return Bukkit.getUnsafe().modifyItemStack(item, on ? FULL_SPEED : NO_FULL_SPEED);
        } catch (RuntimeException ex) {
            if (on && !warnedSlow) {
                warnedSlow = true;
                plugin.getLogger().warning("Vanilla poses (pose_bow...): this server's Minecraft has no use_effects "
                        + "item component (1.21.11+), so players are slowed while a pose plays: " + ex.getMessage());
            }
            return item;
        }
    }
}

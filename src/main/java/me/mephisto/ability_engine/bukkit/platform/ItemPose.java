package me.mephisto.ability_engine.bukkit.platform;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import me.mephisto.ability_engine.engine.platform.CueHandle;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vanilla poses as looping cues ({@code pose_bow}, {@code pose_crossbow}, {@code pose_spear}, ...): the caster holds
 * the item in their main hand with that item-use animation - a bow drawn, a crossbow aimed, a trident raised - for as
 * long as the cue runs, drawn by everyone's own game on the real model, so it's as smooth as vanilla.
 * <p>
 * A player's own game drops any item use the moment they aren't holding the use key, so the server can't make a
 * player use an item. Instead everyone who sees them is told (packets) that they're using their held item, and shown
 * that item with the animation (a {@code consumable} component); their real item and state are untouched, so they
 * aren't slowed, and they don't see the pose on themselves. A mob (or mannequin) has no game of its own: it really
 * uses the item for the time of the pose.
 */
final class ItemPose {

    /** Never finishes by itself (seconds): the cue ends it. */
    private static final float FOREVER = 1_000_000f;
    private static boolean warned;
    /** /ae debug: each pose says what it did (chat to the caster, and the console). */
    static java.util.function.BooleanSupplier debug = () -> false;

    private static void report(Plugin plugin, Entity entity, ItemUseAnimation animation, String what) {
        if (!debug.getAsBoolean()) return;
        String line = "[" + cueName(animation) + "] " + entity.getName() + ": " + what;
        plugin.getLogger().info(line);
        if (entity instanceof Player p) p.sendMessage(org.bukkit.ChatColor.GRAY + line);
    }

    /** What a mob holds for a pose when its hands are empty (a mannequin playing a character holds nothing). */
    private static org.bukkit.Material stand_in(ItemUseAnimation animation) {
        return switch (animation.name()) {
            case "BOW" -> org.bukkit.Material.BOW;
            case "CROSSBOW" -> org.bukkit.Material.CROSSBOW;
            case "BLOCK" -> org.bukkit.Material.SHIELD;
            case "SPYGLASS" -> org.bukkit.Material.SPYGLASS;
            case "TOOT_HORN" -> org.bukkit.Material.GOAT_HORN;
            case "BRUSH" -> org.bukkit.Material.BRUSH;
            case "EAT" -> org.bukkit.Material.BREAD;
            case "DRINK" -> org.bukkit.Material.POTION;
            case "BUNDLE" -> org.bukkit.Material.BUNDLE;
            default -> org.bukkit.Material.TRIDENT; // TRIDENT, SPEAR (and anything new)
        };
    }

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
        if (held.getType().isAir() && living instanceof Player) {
            report(plugin, entity, animation, "nothing in the main hand: no pose");
            return CueHandle.NONE;
        }
        try {
            return living instanceof Player player ? shown(plugin, player, held, animation)
                    : used(plugin, living, held, animation);
        } catch (RuntimeException | ReflectiveOperationException | LinkageError ex) {
            report(plugin, entity, animation, "FAILED: " + ex);
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Vanilla poses (pose_bow...) don't work on this server: " + ex);
            }
            return CueHandle.NONE;
        }
    }

    private static ItemStack posed(ItemStack item, ItemUseAnimation animation) {
        ItemStack copy = item.clone();
        copy.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(FOREVER)
                .animation(animation)
                .sound(Key.key("minecraft", "intentionally_empty"))
                .hasConsumeParticles(false)
                .build());
        return copy;
    }

    /** A player: everyone who sees them sees them use their (posed) item; nothing real changes. */
    private static CueHandle shown(Plugin plugin, Player player, ItemStack held, ItemUseAnimation animation)
            throws ReflectiveOperationException {
        ItemStack copy = posed(held, animation);
        List<Player> told = new ArrayList<>();
        for (Player viewer : player.getTrackedBy()) {
            viewer.sendEquipmentChange(player, EquipmentSlot.HAND, copy); // (first: the use reads the item it's told of)
            Packets.livingFlags(viewer, player, (byte) 1); // using, main hand
            told.add(viewer);
        }
        report(plugin, player, animation, told.isEmpty()
                ? "nobody else sees you right now (a player never sees their own pose: only others do)"
                : "shown to " + told.stream().map(Player::getName).toList());
        return () -> {
            if (!player.isOnline()) return;
            ItemStack real = player.getInventory().getItemInMainHand();
            byte flags = (byte) ((player.hasActiveItem() ? 1 : 0)
                    | (player.getActiveItemHand() == EquipmentSlot.OFF_HAND ? 2 : 0) | (player.isRiptiding() ? 4 : 0));
            for (Player viewer : told) {
                if (!viewer.isOnline()) continue;
                try {
                    Packets.livingFlags(viewer, player, flags);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // (warned when it started)
                }
                viewer.sendEquipmentChange(player, EquipmentSlot.HAND, real);
            }
        };
    }

    /** A mob: it really uses its item (empty-handed: a stand-in), given the animation for the time of the pose. */
    private static CueHandle used(Plugin plugin, LivingEntity living, ItemStack held, ItemUseAnimation animation) {
        if (living.hasActiveItem()) living.clearActiveItem();
        ItemStack before = held.clone();
        ItemStack shown = held.getType().isAir() ? new ItemStack(stand_in(animation)) : held;
        living.getEquipment().setItemInMainHand(posed(shown, animation));
        living.startUsingItem(EquipmentSlot.HAND);
        report(plugin, living, animation, living.hasActiveItem() ? "using " + shown.getType() : "couldn't use " + shown.getType());
        return () -> {
            if (!living.isValid()) return;
            if (living.hasActiveItem()) living.clearActiveItem();
            ItemStack now = living.getEquipment().getItemInMainHand();
            if (now.getType() == shown.getType() && now.hasData(DataComponentTypes.CONSUMABLE)) {
                living.getEquipment().setItemInMainHand(before); // (its own item back, or empty hands again)
            }
        };
    }

    /** The one packet Paper has no API for: an entity's living flags (using an item...), told to one player. */
    private static final class Packets {
        private static Object flagsAccessor;
        private static java.lang.reflect.Method createValue;
        private static java.lang.reflect.Constructor<?> packet;
        private static java.lang.reflect.Field connection;
        private static java.lang.reflect.Method send;

        /** Finds what the packet needs (once): Minecraft's own classes, by their (Mojang) names. */
        static synchronized void resolve() throws ReflectiveOperationException {
            if (send != null) return;
            Class<?> living = Class.forName("net.minecraft.world.entity.LivingEntity");
            var field = living.getDeclaredField("DATA_LIVING_ENTITY_FLAGS");
            field.setAccessible(true);
            flagsAccessor = field.get(null);
            Class<?> accessor = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            createValue = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue")
                    .getMethod("create", accessor, Object.class);
            packet = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
                    .getConstructor(int.class, List.class);
            connection = Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection");
            send = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl")
                    .getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
        }

        static void livingFlags(Player viewer, LivingEntity of, byte flags) throws ReflectiveOperationException {
            resolve();
            Object value = createValue.invoke(null, flagsAccessor, flags);
            Object p = packet.newInstance(of.getEntityId(), List.of(value));
            Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            send.invoke(connection.get(handle), p);
        }
    }

    /** At startup: can poses be shown to other players here? Null: yes; otherwise why not. */
    static String selfCheck() {
        try {
            Packets.resolve();
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return ex.toString();
        }
    }
}

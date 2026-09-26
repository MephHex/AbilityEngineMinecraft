package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.activation.ActivationResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * DEBUG PATH: right-click an item bound with /ae bind to cast that ability.
 * Only for players without a character; with a character, CombatInputListener owns the input.
 */
public final class AbilityInputListener implements Listener {

    private final AbilityEngine engine;
    private final ItemBindings bindings;

    public AbilityInputListener(AbilityEngine engine, ItemBindings bindings) {
        this.engine = engine;
        this.bindings = bindings;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // event fires once per hand
        if (engine.loadouts().has(event.getPlayer().getUniqueId())) return;
        if (engine.targeting().isTargeting(event.getPlayer().getUniqueId())) return; // RMB cancels aiming (CombatInputListener)
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        String abilityId = bindings.read(event.getItem());
        if (abilityId == null) return;
        event.setCancelled(true); // don't also place/use the item

        Player player = event.getPlayer();
        ActivationResult result = engine.activator().activate(player.getUniqueId(), abilityId);
        if (!result.success() && !result.openedTargeting()) {
            player.sendActionBar(Component.text(result.reason(), NamedTextColor.RED));
        }
    }
}

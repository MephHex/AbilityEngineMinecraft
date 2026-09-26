package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.bukkit.hud.HotbarHud;
import me.mephisto.ability_engine.bukkit.status.TagBindings;
import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Keeps engine state and the HUD in sync with players joining, dying and leaving. */
public final class PlayerLifecycleListener implements Listener {

    private final AbilityEngine engine;
    private final TagBindings tagBindings;
    private final HotbarHud hud;

    public PlayerLifecycleListener(AbilityEngine engine, TagBindings tagBindings, HotbarHud hud) {
        this.engine = engine;
        this.tagBindings = tagBindings;
        this.hud = hud;
    }

    /**
     * While the player is still online: status removal undoes attribute changes, and the HUD items
     * are removed so they aren't saved into the player's inventory file.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        engine.resetEntity(p.getUniqueId(), "quit");
        hud.unequip(p);
    }

    /** Covers players (PlayerDeathEvent extends this) and mobs. The character is kept through death. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        engine.resetEntity(event.getEntity().getUniqueId(), "death");
        if (event.getEntity() instanceof Player) event.getDrops().removeIf(hud::isHudItem);
    }

    /** Redraw next tick: the respawned player's inventory isn't final during the event. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (!engine.loadouts().has(p.getUniqueId())) return;
        engine.scheduler().after(1, () -> {
            if (p.isOnline()) hud.render(p);
        });
    }

    /** Remove leftovers from a crash: frozen-movement modifiers and HUD items. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        tagBindings.scrub(event.getPlayer());
        hud.clear(event.getPlayer());
    }
}

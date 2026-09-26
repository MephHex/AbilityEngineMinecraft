package me.mephisto.ability_engine.bukkit.input;

import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import me.mephisto.ability_engine.bukkit.hud.HotbarHud;
import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;

/**
 * The vanilla crossbow of quiver characters. Vanilla does the drawing (animation, sound, slowdown, and
 * the draw time from Quick Charge, which the HUD sets from the quiver's reload speed). When the draw
 * completes, the ENGINE loads the next bolt from the quiver; the crossbow item is just its picture.
 * Vanilla never shoots it: LMB fires the primary ability, which takes the loaded bolt.
 */
public final class CrossbowListener implements Listener {

    private final AbilityEngine engine;
    private final HotbarHud hud;

    public CrossbowListener(AbilityEngine engine, HotbarHud hud) {
        this.engine = engine;
        this.hud = hud;
    }

    private boolean ours(Player p) { return engine.loadouts().has(p.getUniqueId()) && engine.quivers().has(p.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoad(EntityLoadCrossbowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !ours(p)) return;
        e.setConsumeItem(false); // the arrows in the hotbar are the quiver's display, not ammo
        // Stunned (or anything blocking abilities) since the draw started: it doesn't load.
        if (!engine.quivers().tryLoad(p.getUniqueId())) e.setCancelled(true);
        // Next tick, after vanilla has charged the item: redraw it from the engine, and shift the bolts.
        engine.scheduler().after(1, () -> {
            if (p.isOnline()) hud.refresh(p);
        });
    }

    /** Safety net: a quiver character's crossbow never fires a vanilla arrow. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !engine.loadouts().has(p.getUniqueId())) return;
        e.setCancelled(true);
        p.updateInventory();
    }
}

package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.Slots;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Arm swings other players don't see (their own game still swings: it does that before the server hears of it):
 * <ul>
 *   <li>tag {@code state.no_swing}: never (a kit whose attacks are its floating blades, not its arm);</li>
 *   <li>a click while the primary fire (or, on a target, the melee slot) is on cooldown: a swing that hits nothing
 *       isn't shown. A click that does fire swings as usual.</li>
 * </ul>
 * The server hears the click before the swing, and a click that fires puts the primary on cooldown at once, so
 * whether it was ready is read the moment the click comes in, before anything fires.
 */
public final class NoSwing implements Listener {

    public static final String TAG = "state.no_swing";

    private final AbilityEngine engine;
    /** Each player's last left click: the tick it came in, and whether what it would fire was ready then. */
    private final Map<UUID, Click> clicks = new HashMap<>();

    private record Click(long tick, boolean ready) {}

    public NoSwing(AbilityEngine engine) {
        this.engine = engine;
    }

    /** First, before the click fires anything: was the primary ready? */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            clicks.put(e.getPlayer().getUniqueId(), new Click(Bukkit.getCurrentTick(), ready(e.getPlayer(), false)));
        }
    }

    /** A left click on someone: the melee slot if the kit has one, else the primary. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        clicks.put(e.getPlayer().getUniqueId(), new Click(Bukkit.getCurrentTick(), ready(e.getPlayer(), true)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (engine.tags().has(id, TAG)) {
            e.setCancelled(true);
            return;
        }
        if (!engine.loadouts().has(id)) return; // (no character: vanilla)
        Click click = clicks.get(id);
        boolean ready = click != null && Bukkit.getCurrentTick() - click.tick() <= 1 ? click.ready() : ready(p, false);
        if (!ready) e.setCancelled(true); // on cooldown: a swing at nothing, not shown
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { clicks.remove(e.getPlayer().getUniqueId()); }

    /** Whether the left click would fire something now: the primary (on a target: the melee slot first, if any). */
    private boolean ready(Player p, boolean onTarget) {
        UUID id = p.getUniqueId();
        String ability = onTarget ? engine.loadouts().abilityIn(id, Slots.MELEE).orElse(null) : null;
        if (ability == null) ability = engine.loadouts().abilityIn(id, Slots.PRIMARY).orElse(null);
        return ability == null || engine.cooldowns().isReady(id, ability); // (no primary: nothing to wait for)
    }
}

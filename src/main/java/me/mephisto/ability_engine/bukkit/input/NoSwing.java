package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;

/**
 * Tag {@code state.no_swing}: the holder's arm swing isn't shown to anyone else (their clicks still work: they're read
 * from the click itself). For a kit whose attacks are its floating blades, not its arm. Their own game still swings.
 */
public final class NoSwing implements Listener {

    public static final String TAG = "state.no_swing";

    private final AbilityEngine engine;

    public NoSwing(AbilityEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING && engine.tags().has(e.getPlayer().getUniqueId(), TAG)) {
            e.setCancelled(true);
        }
    }
}

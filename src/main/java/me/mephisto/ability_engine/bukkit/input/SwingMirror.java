package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;

/** A player's arm swing (a basic attack, a click at nothing): their mirroring clones swing too (see Mirrors). */
public final class SwingMirror implements Listener {

    private final AbilityEngine engine;

    public SwingMirror(AbilityEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        engine.mirrors().swung(e.getPlayer().getUniqueId());
    }
}

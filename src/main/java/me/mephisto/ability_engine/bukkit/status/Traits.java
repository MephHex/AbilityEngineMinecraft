package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Character traits ({@code traits:} in a kit), always on.
 * <ul>
 *   <li>{@code sneak_slow_fall}: holding SHIFT while falling gives Slow Falling; letting go (or landing)
 *       takes it away at once.</li>
 * </ul>
 */
public final class Traits implements Listener {

    /** Refreshed every tick while it applies, so it runs out right after it stops applying. */
    private static final int SLOW_FALL_TICKS = 3;

    private final AbilityEngine engine;
    /** Players we gave Slow Falling (so we only take away our own). */
    private final Set<UUID> drifting = new HashSet<>();
    /** Each player's height last tick: a player's own velocity isn't reliable on the server. */
    private final java.util.Map<UUID, Double> lastY = new java.util.HashMap<>();

    public Traits(AbilityEngine engine) {
        this.engine = engine;
    }

    public void start() {
        engine.scheduler().every(1, 1, this::tick);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean has = engine.loadouts().characterOf(id).map(c -> c.has(CharacterDef.SNEAK_SLOW_FALL)).orElse(false);
            double y = p.getLocation().getY();
            Double before = lastY.put(id, y);
            boolean falling = before != null && y < before - 1e-3 && !p.isOnGround() && !p.isFlying() && !p.isGliding();
            if (has && p.isSneaking() && falling) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, false));
                drifting.add(id);
            } else if (drifting.remove(id)) {
                p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastY.remove(event.getPlayer().getUniqueId());
        if (drifting.remove(event.getPlayer().getUniqueId())) event.getPlayer().removePotionEffect(PotionEffectType.SLOW_FALLING);
    }
}

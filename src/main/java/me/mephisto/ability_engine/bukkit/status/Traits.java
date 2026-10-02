package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.tag.Tags;
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
 *   <li>{@code slow_fall}: always Slow Falling while falling (not flying or gliding).</li>
 *   <li>{@code elytra}: an elytra in the chest slot (HotbarHud puts it there).</li>
 * </ul>
 * Also keeps {@code state.gliding} on whoever glides on an elytra (anyone), for abilities to check.
 */
public final class Traits implements Listener {

    /** Refreshed every tick while it applies, so it runs out right after it stops applying. */
    private static final int SLOW_FALL_TICKS = 3;

    private final AbilityEngine engine;
    /** Players we gave Slow Falling (so we only take away our own). */
    private final Set<UUID> drifting = new HashSet<>();
    /** Players we gave state.gliding. */
    private final Set<UUID> gliding = new HashSet<>();
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
            boolean sneakFall = engine.loadouts().characterOf(id).map(c -> c.has(CharacterDef.SNEAK_SLOW_FALL)).orElse(false);
            boolean alwaysFall = engine.loadouts().characterOf(id).map(c -> c.has(CharacterDef.SLOW_FALL)).orElse(false);
            double y = p.getLocation().getY();
            Double before = lastY.put(id, y);
            boolean falling = before != null && y < before - 1e-3 && !p.isOnGround() && !p.isFlying() && !p.isGliding();
            if (p.isGliding() && !p.isDead() ? gliding.add(id) : gliding.remove(id)) { // only on a change
                if (gliding.contains(id)) engine.tags().grant(id, Tags.GLIDING);
                else engine.tags().revoke(id, Tags.GLIDING);
            }
            if ((alwaysFall || sneakFall && p.isSneaking()) && falling) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, false));
                drifting.add(id);
            } else if (drifting.remove(id)) {
                p.removePotionEffect(PotionEffectType.SLOW_FALLING);
            }
        }
    }

    /** Who held jump at their last input (a press is the change to held). */
    private final Set<UUID> jumpHeld = new HashSet<>();

    /**
     * elytra: a jump pressed in the air opens the wings. The game's client won't do it by itself while flying is
     * allowed (the Valkyrie's wings): it takes that press as the first half of a double-tap to fly. A quick second press
     * still toggles flight as usual.
     */
    @EventHandler
    public void onInput(org.bukkit.event.player.PlayerInputEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        boolean jump = event.getInput().isJump();
        boolean pressed = jump && !jumpHeld.contains(id);
        if (jump) jumpHeld.add(id);
        else jumpHeld.remove(id);
        if (!pressed || !engine.loadouts().characterOf(id).map(c -> c.has(CharacterDef.ELYTRA)).orElse(false)) return;
        if (p.isOnGround() || p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isInWater() || p.isClimbing()) return;
        org.bukkit.inventory.ItemStack chest = p.getInventory().getChestplate();
        if (chest == null || chest.getType() != org.bukkit.Material.ELYTRA) return;
        p.setGliding(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        jumpHeld.remove(event.getPlayer().getUniqueId());
        lastY.remove(event.getPlayer().getUniqueId());
        if (gliding.remove(event.getPlayer().getUniqueId())) engine.tags().revoke(event.getPlayer().getUniqueId(), Tags.GLIDING);
        if (drifting.remove(event.getPlayer().getUniqueId())) event.getPlayer().removePotionEffect(PotionEffectType.SLOW_FALLING);
    }
}

package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.math.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Gliding on a floor of your own (a character's {@code glide:}, see Floors): holding SHIFT with a movement key glides you
 * that way (W ahead, S back, A / D to the side, diagonals too, relative to where you face), again as soon as its gap
 * allows while they're held. On your floor, sneaking doesn't slow you down (vanilla's sneak speed is lifted there), so
 * holding SHIFT to glide never drags you. The keys come from the player's input (Paper 1.21.3+), read every tick.
 */
public final class GlideInput implements Listener {

    /** The tag a floor of your own gives you while you stand on it (the kit's owner_status). */
    public static final String ON_FLOOR = "state.on_floor";

    private final AbilityEngine engine;
    private final Plugin plugin;
    private final NamespacedKey sneakKey;

    public GlideInput(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
        this.sneakKey = new NamespacedKey(plugin, "floor_sneak_speed");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var id = p.getUniqueId();
            CharacterDef.Glide glide = engine.loadouts().characterOf(id).map(CharacterDef::glide).orElse(null);
            // (working a puppet: the puppet is what has to be in the room, and Floors checks that)
            boolean onFloor = glide != null && (engine.tags().has(id, ON_FLOOR) || engine.mirrors().puppeting(id));
            syncSneakSpeed(p, onFloor);
            if (!onFloor) continue;
            Input in = p.getCurrentInput();
            if (!in.isSneak()) continue;
            double ahead = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
            double side = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
            if (ahead == 0 && side == 0) continue;
            double yaw = Math.toRadians(p.getLocation().getYaw());
            Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            engine.floors().glide(id, forward.multiply(ahead).add(left.multiply(side)));
        }
    }

    /** On your floor: sneaking at full walking speed (vanilla's 30% lifted). Off it: as usual. Only touched when it changes. */
    private void syncSneakSpeed(Player p, boolean onFloor) {
        AttributeInstance sneak = p.getAttribute(Attribute.SNEAKING_SPEED);
        if (sneak == null) return;
        AttributeModifier ours = sneak.getModifier(sneakKey);
        if (onFloor == (ours != null)) return;
        if (ours != null) sneak.removeModifier(sneakKey);
        if (onFloor) {
            sneak.addModifier(new AttributeModifier(sneakKey, 1 - sneak.getBaseValue(), AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { syncSneakSpeed(e.getPlayer(), false); }

    /** Plugin disable: nobody keeps the lifted sneak speed. */
    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) syncSneakSpeed(p, false);
    }
}

package me.mephisto.ability_engine.bukkit.status;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.mephisto.ability_engine.bukkit.platform.VisualEntities;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code state.puppeteer}: a player working their Changeling's puppet.
 * <ul>
 *   <li>They can't walk or jump: walk speed 0 and a modifier zeroing the movement speed (not steps undone afterwards,
 *       which rubber-bands). With a walk speed of 0 the game leaves the camera's zoom alone. Their keys still reach the
 *       server, so {@link #speedOf} says how fast they'd walk if they could (the puppet walks that fast).</li>
 *   <li>They turn freely, unseen: the engine hides them from everyone else meanwhile ({@code state.hidden}), and a DECOY
 *       stands in their place for everyone but them: a look-alike (their skin, name and gear) frozen looking the way they
 *       looked when they started, swinging when they swing. Their real body is still right there, so hits on "them"
 *       still land on them; the decoy itself can't be hurt or targeted.</li>
 * </ul>
 */
public final class PuppetHold implements Listener {

    private static final NamespacedKey SPEED_KEY = new NamespacedKey("ability_engine", "puppet_speed");
    private static final NamespacedKey JUMP_KEY = new NamespacedKey("ability_engine", "puppet_jump");
    /** Vanilla's walk speed (Bukkit's scale: twice the movement speed attribute's base). */
    private static final float DEFAULT_WALK = 0.2f;
    /** Each held player's walk speed before, to give back. */
    private static final Map<UUID, Float> walk = new HashMap<>();
    /** Each held player's decoy. */
    private static final Map<UUID, Mannequin> decoys = new HashMap<>();
    private static BukkitTask follow;

    static void hold(LivingEntity living) {
        if (!(living instanceof Player p)) return;
        // (controls inverted: their walk speed is already 0, theirs from before is the one to keep)
        Float inverted = InvertedControls.walkBefore(p.getUniqueId());
        walk.putIfAbsent(p.getUniqueId(), inverted != null ? inverted : p.getWalkSpeed());
        p.setWalkSpeed(0);
        add(p.getAttribute(Attribute.MOVEMENT_SPEED), SPEED_KEY);
        add(p.getAttribute(Attribute.JUMP_STRENGTH), JUMP_KEY);
        if (!decoys.containsKey(p.getUniqueId())) decoys.put(p.getUniqueId(), spawnDecoy(p));
        if (follow == null) follow = Bukkit.getScheduler().runTaskTimer(plugin(), PuppetHold::follow, 1, 1);
    }

    static void release(LivingEntity living) {
        if (!(living instanceof Player p)) return;
        strip(p.getAttribute(Attribute.MOVEMENT_SPEED), SPEED_KEY);
        strip(p.getAttribute(Attribute.JUMP_STRENGTH), JUMP_KEY);
        Mannequin decoy = decoys.remove(p.getUniqueId());
        if (decoy != null && decoy.isValid()) decoy.remove();
        Float before = walk.remove(p.getUniqueId());
        if (InvertedControls.isHeld(p.getUniqueId())) { // (still inverted: it gives the walk speed back when it lets go)
            if (decoys.isEmpty() && follow != null) {
                follow.cancel();
                follow = null;
            }
            return;
        }
        if (before != null) p.setWalkSpeed(before);
        else if (p.getWalkSpeed() == 0) p.setWalkSpeed(DEFAULT_WALK); // (a leftover from a crash)
        if (decoys.isEmpty() && follow != null) {
            follow.cancel();
            follow = null;
        }
    }

    /** Their stand-in: looks like them, looking where they looked, for everyone but them. */
    private static Mannequin spawnDecoy(Player p) {
        Location at = p.getLocation();
        Mannequin m = p.getWorld().spawn(at, Mannequin.class, d -> {
            d.setProfile(ResolvableProfile.resolvableProfile(p.getPlayerProfile()));
            d.setDescription(null);
            d.customName(net.kyori.adventure.text.Component.text(p.getName()));
            d.setCustomNameVisible(true);
            var from = p.getEquipment();
            var to = d.getEquipment();
            to.setItemInMainHand(from.getItemInMainHand().clone());
            to.setItemInOffHand(from.getItemInOffHand().clone());
            to.setHelmet(from.getHelmet() == null ? null : from.getHelmet().clone());
            to.setChestplate(from.getChestplate() == null ? null : from.getChestplate().clone());
            to.setLeggings(from.getLeggings() == null ? null : from.getLeggings().clone());
            to.setBoots(from.getBoots() == null ? null : from.getBoots().clone());
            d.setInvulnerable(true);
            d.setSilent(true);
            d.setGravity(false);
            d.setImmovable(true);
            d.setCollidable(false);
            d.setPersistent(false);
            d.setBodyYaw(at.getYaw());
            VisualEntities.mark(d); // abilities ignore it: hits go to the real (hidden) them, right there
        });
        p.hideEntity(plugin(), m); // they don't see their own decoy
        return m;
    }

    /** Every tick: each decoy where its player is (a knockback still moves them), still looking the frozen way. */
    private static void follow() {
        for (var e : List.copyOf(decoys.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            Mannequin d = e.getValue();
            if (p == null || !d.isValid()) continue;
            Location to = p.getLocation();
            Location now = d.getLocation();
            if (!to.getWorld().equals(now.getWorld()) || to.distanceSquared(now) > 1e-6) {
                to.setYaw(now.getYaw());
                to.setPitch(now.getPitch());
                d.teleport(to);
            }
        }
    }

    /** They swing (an attack while working the puppet): so does their decoy, for everyone to see. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Mannequin d = decoys.get(e.getPlayer().getUniqueId());
        if (d != null && d.isValid()) d.swingMainHand();
    }

    /** Is this player held as a puppeteer right now? */
    public static boolean isHeld(Player p) { return walk.containsKey(p.getUniqueId()); }

    /** Their walk speed from before they were held as a puppeteer. */
    static float walkBefore(Player p) { return walk.getOrDefault(p.getUniqueId(), p.getWalkSpeed()); }

    /**
     * The movement speed attribute this player would have if they weren't held (a puppeteer, or controls inverted): their
     * walk speed from before, with every other modifier (their stats, slows, haste, sprinting) applied as the game does.
     */
    public static double speedOf(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return 0.1;
        Float before = walk.get(p.getUniqueId());
        if (before == null) before = InvertedControls.walkBefore(p.getUniqueId());
        if (before == null) return speed.getValue();
        double value = before / 2.0; // (Bukkit's walk speed is twice the attribute's base)
        for (AttributeModifier m : speed.getModifiers()) {
            if (m.getOperation() == AttributeModifier.Operation.ADD_NUMBER) value += m.getAmount();
        }
        double base = value;
        for (AttributeModifier m : speed.getModifiers()) {
            if (m.getOperation() == AttributeModifier.Operation.ADD_SCALAR) value += base * m.getAmount();
        }
        for (AttributeModifier m : speed.getModifiers()) {
            if (m.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1 && !m.getKey().equals(SPEED_KEY)
                    && !m.getKey().equals(InvertedControls.SPEED_KEY)) {
                value *= 1 + m.getAmount();
            }
        }
        return Math.max(0, value);
    }

    /** Plugin disable: every decoy goes, everyone walks again. */
    public static void shutdown() {
        for (UUID id : List.copyOf(walk.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) release(p);
        }
        decoys.values().forEach(d -> { if (d.isValid()) d.remove(); });
        decoys.clear();
    }

    private static Plugin plugin() { return JavaPlugin.getProvidingPlugin(PuppetHold.class); }

    private static void add(AttributeInstance attr, NamespacedKey key) {
        if (attr == null || attr.getModifier(key) != null) return;
        attr.addModifier(new AttributeModifier(key, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private static void strip(AttributeInstance attr, NamespacedKey key) {
        if (attr != null && attr.getModifier(key) != null) attr.removeModifier(key);
    }
}

package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.ModelBehavior;
import me.mephisto.ability_engine.engine.platform.Animator;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The player as their model: every tick, what they do (moving, sprinting, sneaking, in the air...) picks the model's
 * state ({@code model: states}), which loops until another one holds; jumps, landings, swings, hits taken and death
 * play the model's {@code on:} animations once. Part of the Model Engine hook: only loaded with Model Engine.
 */
final class ModelBehaviorDriver implements Listener {

    /** Blocks per second above which they count as moving (sneak-walking is ~1.3). */
    private static final double MOVING = 0.5;
    /** Ticks a new state must hold before it plays (no flicker on the edge of a condition). */
    private static final int SETTLE = 2;
    /** Ticks in the air before touching down counts as a landing (not a step down a block). */
    private static final int LANDING_AIR_TICKS = 4;

    private final AbilityEngine engine;
    private final ModelEngineHook hook;
    private final Map<UUID, Track> tracks = new HashMap<>();

    /** What we know of one wearer from tick to tick. */
    private static final class Track {
        CharacterDef.Model model;
        Location last;
        /** Horizontal speed, blocks per second (smoothed). */
        double speed;
        boolean wasOnGround = true;
        int airTicks;
        ModelBehavior.State current;
        ModelBehavior.State pending;
        int pendingTicks;
        double playedSpeed;
    }

    ModelBehaviorDriver(AbilityEngine engine, ModelEngineHook hook) {
        this.engine = engine;
        this.hook = hook;
    }

    /** Every tick, for each entity wearing a model with states / on. */
    void tick(UUID id, Entity entity, CharacterDef.Model model) {
        Track t = tracks.computeIfAbsent(id, k -> new Track());
        if (t.model != model) { // a new model (or a reload): start over
            if (t.current != null && t.model != null) hook.stop(id, t.current.animation());
            t.model = model;
            t.current = null;
            t.pending = null;
        }
        ModelBehavior behavior = model.behavior();

        Location at = entity.getLocation();
        double dy = 0, horizontal = 0;
        if (t.last != null && t.last.getWorld() == at.getWorld()) {
            double dx = at.getX() - t.last.getX(), dz = at.getZ() - t.last.getZ();
            horizontal = Math.sqrt(dx * dx + dz * dz) * 20;
            dy = at.getY() - t.last.getY();
            if (horizontal > 40) horizontal = 0; // (a teleport, not a step)
        }
        t.last = at;
        t.speed = t.speed * 0.5 + horizontal * 0.5;

        boolean onGround = entity.isOnGround();
        Set<String> facts = facts(id, entity, t, onGround, dy, behavior);

        if (!onGround && t.wasOnGround && dy > 0.05 && !(entity instanceof Player)
                && !facts.contains("riding") && !facts.contains("swimming")) {
            event(id, "jump"); // (players: PlayerJumpEvent, exact)
        }
        if (onGround && !t.wasOnGround && t.airTicks >= LANDING_AIR_TICKS) event(id, "land");
        t.airTicks = onGround ? 0 : t.airTicks + 1;
        t.wasOnGround = onGround;

        double health = 1;
        if (entity instanceof LivingEntity living) {
            var max = living.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && max.getValue() > 0) health = living.getHealth() / max.getValue();
        }
        ModelBehavior.State want = behavior.pick(facts, tag -> engine.tags().has(id, tag), health);
        if (want != t.current) {
            if (want == t.pending) t.pendingTicks++;
            else {
                t.pending = want;
                t.pendingTicks = 1;
            }
            if (t.current == null || t.pendingTicks >= SETTLE) switchTo(id, t, want);
        } else {
            t.pending = null;
            if (t.current != null) {
                if (!hook.isPlaying(id, t.current.animation())) {
                    play(id, t, t.current); // (dropped: the model came back after a respawn, or it wasn't a loop)
                } else if (t.current.byMovement()) {
                    double speed = t.current.speedAt(t.speed);
                    if (Math.abs(speed - t.playedSpeed) > 0.05) {
                        hook.respeed(id, t.current.animation(), speed);
                        t.playedSpeed = speed;
                    }
                }
            }
        }
    }

    private Set<String> facts(UUID id, Entity entity, Track t, boolean onGround, double dy, ModelBehavior behavior) {
        Set<String> facts = new HashSet<>();
        if (t.speed > MOVING) facts.add("moving");
        if (onGround) {
            facts.add("on_ground");
        } else {
            facts.add("airborne");
            if (dy < -0.01) facts.add("falling");
            if (dy > 0.01) facts.add("rising");
        }
        if (entity.isInsideVehicle()) facts.add("riding");
        if (entity.isInWater()) facts.add("in_water");
        if (entity instanceof LivingEntity living) {
            if (living.isSwimming()) facts.add("swimming");
            if (living.isGliding()) facts.add("gliding");
            if (living.isClimbing()) facts.add("climbing");
        }
        if (entity instanceof Player p) {
            if (p.isSprinting()) facts.add("sprinting");
            if (p.isSneaking()) facts.add("sneaking");
            if (p.isFlying()) facts.add("flying");
            if (p.isBlocking()) facts.add("blocking");
        }
        if (hook.playingOther(id, behavior.animations())) facts.add("animating");
        return facts;
    }

    private void switchTo(UUID id, Track t, ModelBehavior.State want) {
        ModelBehavior.State was = t.current;
        t.current = want;
        t.pending = null;
        if (was != null && want != null && was.animation().equalsIgnoreCase(want.animation())) {
            double speed = want.speedAt(t.speed); // (the same animation: it only changes speed)
            hook.respeed(id, want.animation(), speed);
            t.playedSpeed = speed;
            return;
        }
        if (was != null) hook.stop(id, was.animation()); // (eases out over its blend)
        if (want != null) play(id, t, want);
    }

    private void play(UUID id, Track t, ModelBehavior.State state) {
        int blend = state.blend() >= 0 ? state.blend() : t.model.blend();
        double speed = state.speedAt(t.speed);
        hook.play(id, state.animation(), new Animator.Settings(speed, blend, blend, true));
        t.playedSpeed = speed;
    }

    /** {@code event}'s animation (if the model has one for it), once. */
    private void event(UUID id, String event) {
        CharacterDef.Model model = hook.behaving(id);
        if (model == null) return;
        String animation = model.behavior().on().get(event);
        if (animation == null) return;
        int blend = model.blend();
        hook.play(id, animation, new Animator.Settings(1, blend, blend, true));
    }

    /** The model's gone: nothing to follow. */
    void forget(UUID id) { tracks.remove(id); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(com.destroystokyo.paper.event.player.PlayerJumpEvent e) {
        event(e.getPlayer().getUniqueId(), "jump");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(io.papermc.paper.event.player.PlayerArmSwingEvent e) {
        if (e.getHand() == EquipmentSlot.HAND) event(e.getPlayer().getUniqueId(), "swing");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getFinalDamage() > 0 && !(e.getEntity() instanceof LivingEntity l && l.getHealth() - e.getFinalDamage() <= 0)) {
            event(e.getEntity().getUniqueId(), "hurt"); // (a lethal hit: death plays instead)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        Track t = tracks.get(id);
        if (t != null && t.current != null) {
            hook.stop(id, t.current.animation());
            t.current = null;
        }
        event(id, "death");
    }
}

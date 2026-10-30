package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.tag.Tags;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inverted controls ({@code state.inverted}, Topsy-Turvy) for a player. Their own walking is held the way a puppeteer's is
 * (walk speed 0 and the speed attribute off: no rubber-banding, no camera zoom), and every tick on the ground their WASD
 * is read and they're moved the opposite way at the speed they'd walk (W walks back, A walks right). In the air (a jump, a
 * fall) their keys steer them the opposite way too, as little as the game lets keys steer in the air; their momentum and
 * their up/down speed carry on (the arc of a jump or a knockback). Their jump key still jumps. Not pressing anything,
 * they're left alone.
 * Mobs aren't held here: a brain-driven one walks away from its target instead (BrainManager).
 */
public final class InvertedControls {

    static final NamespacedKey SPEED_KEY = new NamespacedKey("ability_engine", "inverted_speed");
    /** Each held player's walk speed before, to give back. */
    private static final Map<UUID, Float> walk = new HashMap<>();

    private final AbilityEngine engine;
    private final Plugin plugin;

    /** The engine (for a release to see whether something else still holds them). */
    private static AbilityEngine current;

    public InvertedControls(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
        current = engine;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }


    static void hold(LivingEntity living) {
        if (!(living instanceof Player p)) return;
        // (a puppeteer's walk speed is already 0: theirs from before is the one to keep)
        walk.putIfAbsent(p.getUniqueId(), PuppetHold.isHeld(p) ? PuppetHold.walkBefore(p) : p.getWalkSpeed());
        p.setWalkSpeed(0);
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_KEY) == null) {
            speed.addModifier(new AttributeModifier(SPEED_KEY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    static void release(LivingEntity living) {
        if (!(living instanceof Player p)) return;
        // (held for two things, inverted and confused: only the last one going lets them walk again)
        if (current != null && (current.tags().has(p.getUniqueId(), Tags.INVERTED)
                || current.tags().has(p.getUniqueId(), Tags.CONFUSED))) return;
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_KEY) != null) speed.removeModifier(SPEED_KEY);
        Float before = walk.remove(p.getUniqueId());
        if (PuppetHold.isHeld(p)) return; // (still working a puppet: it gives the walk speed back when it lets go)
        if (before != null) p.setWalkSpeed(before);
        else if (p.getWalkSpeed() == 0) p.setWalkSpeed(0.2f); // (a leftover from a crash)
    }

    /** Is this player's walking held for inverted controls right now? */
    static boolean isHeld(UUID id) { return walk.containsKey(id); }

    /** Their walk speed from before they were held (null: not held). */
    static Float walkBefore(UUID id) { return walk.get(id); }

    /** Vanilla's air physics (per tick): sideways drag, gravity, vertical drag, and the push a key gives in the air. */
    private static final double AIR_DRAG = 0.91, GRAVITY = 0.08, FALL_DRAG = 0.98, AIR_PUSH = 0.02, AIR_PUSH_SPRINT = 0.026;
    /** Moving this much (blocks a tick) off how they should be, something else pushed them: follow that instead. */
    private static final double KNOCKED = 0.2;

    /** Each held player's spot a tick ago (their last move: how fast they're going up or down). */
    private final Map<UUID, Location> last = new HashMap<>();
    /** Each held player's sideways speed in the air as it should be, keys turned round (null: not steering in the air). */
    private final Map<UUID, Vector> air = new HashMap<>();

    private void tick() {
        last.keySet().retainAll(walk.keySet());
        air.keySet().retainAll(walk.keySet());
        for (UUID id : List.copyOf(walk.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isValid() || p.isDead()) continue;
            Location now = p.getLocation();
            Location before = last.put(id, now.clone());
            var tags = engine.tags();
            if (!tags.has(id, Tags.INVERTED)) { // (held because confused: Confusion runs them, their keys do nothing)
                air.remove(id);
                continue;
            }
            var keys = engine.world().inputOf(id);
            if (tags.has(id, Tags.BLOCK_MOVE) || tags.has(id, Tags.STUNNED) || tags.has(id, Tags.BLOCK_WALK)
                    || keys.isEmpty() || keys.get().move().isZero()) { // not walking: their momentum is their own
                air.remove(id);
                continue;
            }
            var move = keys.get().move();
            if (p.isOnGround()) {
                double up = 0;
                if (p.getCurrentInput().isJump()) { // (a jump while walking: theirs is cut by the push, so it's given here)
                    AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
                    up = jump == null ? 0.42 : jump.getValue();
                }
                p.setVelocity(new Vector(-move.x(), up, -move.z()));
                air.remove(id); // (taking off, what they really move with is where the air steering starts)
                continue;
            }
            // In the air (jumping, falling): the game still steers them a little by their keys. Their sideways speed is
            // set to what it would be with the keys turned round, and their up/down speed kept as it is.
            if (before == null || !before.getWorld().equals(now.getWorld())) continue;
            Vector moved = now.toVector().subtract(before.toVector());
            Vector really = new Vector(moved.getX(), 0, moved.getZ());
            Vector h = air.get(id);
            // just took off, or something else moved them meanwhile (a knockback): start from how they really move
            if (h == null || h.distance(really) > KNOCKED) h = really;
            Vector dir = new Vector(move.x(), 0, move.z()).normalize();
            double push = (p.isSprinting() ? AIR_PUSH_SPRINT : AIR_PUSH) * (keys.get().sneak() ? 0.3 : 1);
            Vector own = dir.multiply(push);                                 // what their keys add on their side
            h = h.multiply(AIR_DRAG).subtract(own);                           // the way they should go: keys turned round
            Vector set = h.clone().subtract(own);                             // (their client adds its own push again)
            p.setVelocity(new Vector(set.getX(), fallSpeed(p, moved.getY()), set.getZ()));
            air.put(id, h);
        }
    }

    /**
     * Their up/down speed when this push reaches them: from how far they went up or down in their last move, carried on by
     * gravity for the ticks it takes to get there (their ping), so a jump or a fall keeps its arc.
     */
    private static double fallSpeed(Player p, double lastMove) {
        int ticks = (int) Math.round(Math.max(0, p.getPing()) / 50.0) + 1;
        double v = lastMove;
        for (int i = 0; i < ticks; i++) v = (v - GRAVITY) * FALL_DRAG;
        return v;
    }

    /** Plugin disable: everyone walks normally again. */
    public static void shutdown() {
        for (UUID id : List.copyOf(walk.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) release(p);
        }
        walk.clear();
    }
}

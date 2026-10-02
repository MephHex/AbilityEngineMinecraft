package me.mephisto.ability_engine.bukkit.platform;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.construct.ConstructHandle;
import me.mephisto.ability_engine.engine.construct.ConstructVisual;
import me.mephisto.ability_engine.engine.construct.Strike;
import me.mephisto.ability_engine.engine.platform.ConstructRenderer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Draws constructs and gives them a punchable hitbox.
 * <ul>
 *   <li>Visual: whatever {@code visual:} says (item or "entity:..."). Item visuals spin faster and
 *       faster as the fuse burns down, with sparks growing more intense.</li>
 *   <li>When the fragile window closes, a chime plays and the geometry starts glowing: "too late
 *       to break it now".</li>
 *   <li>Hitbox: an invisible Interaction entity. Punching it is reported to the engine as a melee
 *       strike; the engine decides whether that breaks it.</li>
 *   <li>Traps (not solid): the item lies flat on the ground, no hitbox, and a click when it arms.</li>
 *   <li>Hidden ones ({@code hidden: true}): only the owner and their allies see it (and hear it arm).</li>
 * </ul>
 */
public final class BukkitConstructRenderer implements ConstructRenderer, Listener {

    private static final Color ARMED_GLOW = Color.fromRGB(190, 120, 255);

    private final Logger log;
    private final Map<UUID, ConstructHandle> hitboxes = new HashMap<>();
    private AbilityEngine engine;

    public BukkitConstructRenderer(Logger log) {
        this.log = log;
    }

    public void setEngine(AbilityEngine engine) { this.engine = engine; }

    @Override
    public ConstructVisual spawn(ConstructHandle construct, String visual) {
        java.util.Set<java.util.UUID> audience = engine == null ? java.util.Set.of() : engine.audienceOf(construct.owner());
        return VisualEntities.withAudience(audience, () -> spawnFor(construct, visual)); // a duel: only the two see it
    }

    private ConstructVisual spawnFor(ConstructHandle construct, String visual) {
        Location center = Convert.location(construct.world(), construct.position());
        if (center == null) return NONE;
        float size = (float) construct.size();
        if (!construct.solid()) return trap(construct, center, visual, size);

        VisualSpawner.Spawned spawned = VisualSpawner.spawn(center, visual, size * 1.5f, log);
        Entity look = spawned.entity();

        Location feet = center.clone().subtract(0, size, 0); // Interaction boxes grow up from their feet
        Interaction hitbox = feet.getWorld().spawn(feet, Interaction.class, i -> {
            i.setInteractionWidth(size * 2);
            i.setInteractionHeight(size * 2);
            i.setResponsive(true);   // punch sound/animation feedback
            i.setPersistent(false);
            VisualEntities.mark(i);
        });
        hitboxes.put(hitbox.getUniqueId(), construct);
        // The visual may itself be hittable (an end crystal's box is bigger than ours): count punches on it too.
        hitboxes.put(look.getUniqueId(), construct);

        return new ConstructVisual() {
            float angle;
            int ticks;
            boolean armedShown;

            @Override
            public void update(double progress, boolean fragile) {
                ticks++;
                if (look instanceof ItemDisplay d && d.isValid()) {
                    angle += (float) Math.toRadians(4 + 26 * progress);            // spins up as it destabilises
                    float pulse = size * 1.5f * (1 + 0.08f * (float) Math.sin(ticks * (0.3 + progress)));
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(1);
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(angle),
                            new Vector3f(pulse, pulse, pulse), new Quaternionf()));
                }
                if (ticks % 3 == 0) {
                    center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 1 + (int) (progress * 6),
                            0.3, 0.3, 0.3, 0.02);
                }
                if (!fragile && !armedShown) {
                    armedShown = true;
                    center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
                    if (look instanceof Display d) d.setGlowColorOverride(ARMED_GLOW);
                    look.setGlowing(true);
                }
            }

            @Override
            public void remove() {
                hitboxes.remove(hitbox.getUniqueId());
                hitboxes.remove(look.getUniqueId());
                if (hitbox.isValid()) hitbox.remove();
                if (look.isValid()) look.remove();
            }
        };
    }

    /** How often a hidden construct re-checks who may see it (someone joined, changed team). */
    private static final int HIDDEN_REFRESH_TICKS = 10;

    /** A trap: lies flat, nothing to punch, clicks once when it's armed. */
    /** A visual written {@code "hover:<item>"}: it stands upright (facing whoever looks) and bobs gently in the air. */
    private static final String HOVER = "hover:";

    private ConstructVisual trap(ConstructHandle construct, Location center, String visual, float size) {
        if (visual != null && visual.startsWith(HOVER)) return hovering(construct, center, visual.substring(HOVER.length()), size);
        VisualSpawner.Spawned spawned = VisualSpawner.spawn(center, visual, size * 1.5f, log);
        Entity look = spawned.entity();
        if (look instanceof ItemDisplay d) {
            float s = size * 1.5f;
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX((float) (Math.PI / 2)),
                    new Vector3f(s, s, s), new Quaternionf()));
        }
        if (construct.hidden()) showOnlyToAllies(construct, look);
        return new ConstructVisual() {
            boolean armedShown;
            int ticks;

            @Override
            public void update(double progress, boolean fragile) {
                if (construct.hidden() && ++ticks % HIDDEN_REFRESH_TICKS == 0) showOnlyToAllies(construct, look);
                if (armedShown || !construct.armed()) return;
                armedShown = true;
                for (org.bukkit.entity.Player viewer : viewers(construct, center)) {
                    viewer.playSound(center, Sound.BLOCK_TRIPWIRE_ATTACH, 0.8f, 1.2f);
                    viewer.spawnParticle(Particle.CRIT, center, 6, 0.3, 0.05, 0.3, 0.02);
                }
            }

            @Override
            public void remove() {
                if (look.isValid()) look.remove();
            }
        };
    }

    /**
     * Turns an item's sprite (drawn corner to corner, bottom left to top right, like a sword or a shard) to stand upright,
     * as seen by whoever it faces (a VERTICAL billboard: it's mirrored to them, so it turns the other way).
     */
    private static final float UPRIGHT = (float) (-Math.PI / 4);

    /** How far a hovering one bobs up and down (blocks), and how fast (radians a tick). */
    private static final float BOB_HEIGHT = 0.12f;
    private static final double BOB_SPEED = 0.12;

    /**
     * An item hanging in the air ({@code "hover:<item>"}): upright (an item's sprite runs corner to corner: turned so
     * it points straight up), always facing whoever looks at it, and bobbing gently, each one at its own pace.
     */
    private ConstructVisual hovering(ConstructHandle construct, Location center, String visual, float size) {
        VisualSpawner.Spawned spawned = VisualSpawner.spawn(center, visual, size * 1.5f, log);
        Entity look = spawned.entity();
        float s = size * 1.5f;
        double phase = java.util.concurrent.ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        if (look instanceof ItemDisplay d) {
            d.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ(UPRIGHT),
                    new Vector3f(s, s, s), new Quaternionf())); // upright from the start
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(2);
        }
        if (construct.hidden()) showOnlyToAllies(construct, look);
        return new ConstructVisual() {
            int ticks;

            @Override
            public void update(double progress, boolean fragile) {
                ticks++;
                if (construct.hidden() && ticks % HIDDEN_REFRESH_TICKS == 0) showOnlyToAllies(construct, look);
                if (ticks % 2 != 0 || !(look instanceof ItemDisplay d) || !d.isValid()) return;
                float y = (float) (Math.sin(ticks * BOB_SPEED + phase) * BOB_HEIGHT);
                d.setInterpolationDelay(0);
                d.setTransformation(new Transformation(new Vector3f(0, y, 0), new Quaternionf().rotateZ(UPRIGHT),
                        new Vector3f(s, s, s), new Quaternionf()));
            }

            @Override
            public void remove() {
                if (look.isValid()) look.remove();
            }
        };
    }

    /**
     * Who may see a construct: everyone in its world, or for a hidden one only its owner's allies (and in a
     * duel, only those of the two who are).
     */
    private java.util.List<org.bukkit.entity.Player> viewers(ConstructHandle construct, Location at) {
        java.util.Set<UUID> audience = engine == null ? java.util.Set.of() : engine.audienceOf(construct.owner());
        return at.getWorld().getPlayers().stream()
                .filter(p -> audience.isEmpty() || audience.contains(p.getUniqueId()))
                .filter(p -> !construct.hidden() || engine == null || engine.teams().allies(construct.owner(), p.getUniqueId()))
                .toList();
    }

    /** A hidden construct: shown to the owner's allies only, hidden from everyone else. */
    private void showOnlyToAllies(ConstructHandle construct, Entity look) {
        if (!look.isValid()) return;
        var plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(BukkitConstructRenderer.class);
        look.setVisibleByDefault(false);
        java.util.Set<UUID> allowed = new java.util.HashSet<>();
        viewers(construct, look.getLocation()).forEach(p -> allowed.add(p.getUniqueId()));
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            boolean sees = p.canSee(look);
            if (allowed.contains(p.getUniqueId()) && !sees) p.showEntity(plugin, look);
            else if (!allowed.contains(p.getUniqueId()) && sees) p.hideEntity(plugin, look);
        }
    }

    /** Fires for every attack attempt, including on Interaction entities that can't take damage. */
    @EventHandler(priority = EventPriority.LOW)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        ConstructHandle construct = hitboxes.get(event.getAttacked().getUniqueId());
        if (construct == null || engine == null) return;
        event.setCancelled(true);
        engine.constructs().strike(construct, Strike.melee(event.getPlayer().getUniqueId()));
    }

    private static final ConstructVisual NONE = new ConstructVisual() {
        @Override public void update(double progress, boolean fragile) {}
        @Override public void remove() {}
    };
}

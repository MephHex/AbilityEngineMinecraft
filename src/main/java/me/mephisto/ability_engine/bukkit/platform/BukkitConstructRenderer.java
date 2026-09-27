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

    /** A trap: lies flat, nothing to punch, clicks once when it's armed. */
    private ConstructVisual trap(ConstructHandle construct, Location center, String visual, float size) {
        VisualSpawner.Spawned spawned = VisualSpawner.spawn(center, visual, size * 1.5f, log);
        Entity look = spawned.entity();
        if (look instanceof ItemDisplay d) {
            float s = size * 1.5f;
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX((float) (Math.PI / 2)),
                    new Vector3f(s, s, s), new Quaternionf()));
        }
        return new ConstructVisual() {
            boolean armedShown;

            @Override
            public void update(double progress, boolean fragile) {
                if (armedShown || !construct.armed()) return;
                armedShown = true;
                center.getWorld().playSound(center, Sound.BLOCK_TRIPWIRE_ATTACH, 0.8f, 1.2f);
                center.getWorld().spawnParticle(Particle.CRIT, center, 6, 0.3, 0.05, 0.3, 0.02);
            }

            @Override
            public void remove() {
                if (look.isValid()) look.remove();
            }
        };
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

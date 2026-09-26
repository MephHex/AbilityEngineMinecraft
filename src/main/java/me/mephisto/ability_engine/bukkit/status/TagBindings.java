package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.tag.TagListener;
import me.mephisto.ability_engine.engine.tag.Tags;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Translates engine tags into Minecraft side effects. The engine decides WHEN (status durations,
 * stacking); this only reacts to tag added/removed. No timers of its own — that duplication was
 * the root of the old stun having two sources of truth.
 */
public final class TagBindings implements TagListener {

    private record Binding(Consumer<LivingEntity> onAdded, Consumer<LivingEntity> onRemoved) {}

    private final Map<String, Binding> bindings = new HashMap<>();

    public static TagBindings withDefaults() {
        TagBindings b = new TagBindings();
        b.bind(Tags.BLOCK_MOVE, MovementLock::apply, MovementLock::remove);
        b.bind(Tags.SLOWED, MovementLock::applySlow, MovementLock::removeSlow);
        b.bind(Tags.BLOCK_KNOCKBACK, MovementLock::applySteadfast, MovementLock::removeSteadfast);
        // Air Anchor: levitation at level -1 pulls vertical speed to exactly 0, so you hover in place.
        b.bind(Tags.ANCHORED,
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, PotionEffect.INFINITE_DURATION, -1, false, false)),
                e -> {
                    e.removePotionEffect(PotionEffectType.LEVITATION);
                    e.setFallDistance(0); // hovering high up doesn't turn into fall damage
                });
        b.bind(Tags.GLOWING, e -> e.setGlowing(true), e -> e.setGlowing(false));
        b.bind(Tags.BURNING, e -> e.setVisualFire(true), e -> e.setVisualFire(false)); // looks on fire, no vanilla fire damage
        b.bind(Tags.RESISTANT,   // Resistance II: -40% damage taken (applies to ability damage too)
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false)),
                e -> e.removePotionEffect(PotionEffectType.RESISTANCE));
        b.bind(Tags.STUNNED,
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, PotionEffect.INFINITE_DURATION, 3, false, false)),
                e -> e.removePotionEffect(PotionEffectType.NAUSEA));
        return b;
    }

    public void bind(String tag, Consumer<LivingEntity> onAdded, Consumer<LivingEntity> onRemoved) {
        bindings.put(tag, new Binding(onAdded, onRemoved));
    }

    @Override
    public void onTagAdded(UUID entity, String tag) {
        Binding b = bindings.get(tag);
        if (b != null && Bukkit.getEntity(entity) instanceof LivingEntity living) b.onAdded.accept(living);
    }

    @Override
    public void onTagRemoved(UUID entity, String tag) {
        Binding b = bindings.get(tag);
        if (b != null && Bukkit.getEntity(entity) instanceof LivingEntity living) b.onRemoved.accept(living);
    }

    /**
     * Remove persistent leftovers on join. Attribute modifiers are saved with player data, so a
     * crash (or the old code) can leave a player frozen with no status to expire it.
     */
    public void scrub(LivingEntity living) {
        MovementLock.remove(living);
        MovementLock.removeSlow(living);
        MovementLock.removeSteadfast(living);
    }
}

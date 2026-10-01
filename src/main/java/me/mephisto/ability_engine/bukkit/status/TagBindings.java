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

    private static void forOthers(LivingEntity e, java.util.function.BiConsumer<org.bukkit.entity.Player, org.bukkit.plugin.Plugin> action) {
        org.bukkit.plugin.Plugin plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TagBindings.class);
        for (org.bukkit.entity.Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(e)) action.accept(viewer, plugin);
        }
    }

    public static TagBindings withDefaults() {
        TagBindings b = new TagBindings();
        b.bind(Tags.BLOCK_MOVE, MovementLock::apply, MovementLock::remove);
        b.bind(Tags.SLOWED, MovementLock::applySlow, MovementLock::removeSlow);
        b.bind(Tags.BLOCK_KNOCKBACK, MovementLock::applySteadfast, MovementLock::removeSteadfast);
        b.bind(Tags.HASTED, MovementLock::applyHaste, MovementLock::removeHaste);
        b.bind(Tags.STURDY, MovementLock::applySturdy, MovementLock::removeSturdy);
        b.bind(Tags.INVISIBLE,   // vanilla invisibility: held items and armor still show
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false)),
                e -> e.removePotionEffect(PotionEffectType.INVISIBILITY));
        // Air Anchor: levitation at level -1 pulls vertical speed to exactly 0, so you hover in place.
        b.bind(Tags.ANCHORED,
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, PotionEffect.INFINITE_DURATION, -1, false, false)),
                e -> {
                    e.removePotionEffect(PotionEffectType.LEVITATION);
                    e.setFallDistance(0); // hovering high up doesn't turn into fall damage
                });
        b.bind(Tags.GLOWING, e -> e.setGlowing(true), e -> e.setGlowing(false));
        b.bind(Tags.FROZEN, FrostAndFlight::freeze, FrostAndFlight::thaw);   // blue hearts + frost overlay
        b.bind(Tags.FLYING, FrostAndFlight::fly, FrostAndFlight::land);
        // Truly hidden: other players don't see the entity at all (armor and held items included).
        b.bind(Tags.HIDDEN, e -> forOthers(e, (viewer, plugin) -> viewer.hideEntity(plugin, e)),
                e -> forOthers(e, (viewer, plugin) -> viewer.showEntity(plugin, e)));
        b.bind(Tags.BLINDED,
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false)),
                e -> e.removePotionEffect(PotionEffectType.BLINDNESS));
        b.bind(Tags.BURNING, e -> e.setVisualFire(true), e -> e.setVisualFire(false)); // looks on fire, no vanilla fire damage
        b.bind(Tags.RESISTANT,   // Resistance II: -40% damage taken (applies to ability damage too)
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 1, false, false)),
                e -> e.removePotionEffect(PotionEffectType.RESISTANCE));
        b.bind(Tags.STUNNED,
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, PotionEffect.INFINITE_DURATION, 3, false, false)),
                e -> e.removePotionEffect(PotionEffectType.NAUSEA));
        // The Whisperer: vanilla Darkness, and Wither's black hearts (its damage is the ability's: see WitherGuard)
        b.bind("state.darkness",
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, PotionEffect.INFINITE_DURATION, 0, false, false)),
                e -> e.removePotionEffect(PotionEffectType.DARKNESS));
        b.bind("state.withered",
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, PotionEffect.INFINITE_DURATION, 1, false, true)),
                e -> e.removePotionEffect(PotionEffectType.WITHER));
        // The Fae's trap: Poison's green hearts (its damage is the trap's: see WitherGuard)...
        b.bind("state.poison_hearts",
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, PotionEffect.INFINITE_DURATION, 0, false, false, true)),
                e -> e.removePotionEffect(PotionEffectType.POISON));
        // ...and a wobbling screen (the stun's own Nausea is stronger; whichever ends first takes it off)
        b.bind(Tags.NAUSEOUS,
                e -> e.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, PotionEffect.INFINITE_DURATION, 0, false, false)),
                e -> e.removePotionEffect(PotionEffectType.NAUSEA));
        StatusAuras.bindAll(b);   // silenced / poisoned / paralyzed: particles on them while it lasts
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
        MovementLock.removeHaste(living);
        MovementLock.removeSturdy(living);
    }
}

package me.mephisto.ability_engine.engine.graph;

import me.mephisto.ability_engine.engine.target.Target;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Well-known blackboard keys. YAML refers to them by name ("caster", "target", "hit"). */
public final class Keys {

    private static final Map<String, Key<?>> KNOWN = new ConcurrentHashMap<>();

    /** The UUID of the entity that activated the ability. Always present. */
    public static final Key<UUID> CASTER = register("caster", UUID.class);
    /** Default output of acquire_target. */
    public static final Key<Target> TARGET = register("target", Target.class);
    /** What a projectile hit (EntityTarget or PointTarget). */
    public static final Key<Target> HIT = register("hit", Target.class);
    /** Where the caster aimed: the confirmed targeting spot, or the crosshair at the moment of casting. */
    public static final Key<Target> AIM = register("aim", Target.class);
    /** Which character slot the cast came from ("primary", "ability_1"...), if any. */
    public static final Key<String> SLOT = register("slot", String.class);
    /** Where a direct hit came from (a projectile's approach). Default: the caster's position. */
    public static final Key<Target> HIT_FROM = register("hit_from", Target.class);

    public static <T> Key<T> register(String name, Class<T> type) {
        Key<T> key = new Key<>(name, type);
        Key<?> existing = KNOWN.putIfAbsent(name, key);
        if (existing != null && existing.type() != type) {
            throw new IllegalArgumentException("Key '" + name + "' already registered as " + existing.type().getSimpleName());
        }
        return key;
    }

    /** A Target-typed key by name. Creates it if it doesn't exist yet. */
    @SuppressWarnings("unchecked")
    public static Key<Target> target(String name) {
        Key<?> existing = KNOWN.get(name);
        if (existing == null) return register(name, Target.class);
        if (existing.type() != Target.class) {
            throw new IllegalArgumentException("Key '" + name + "' holds " + existing.type().getSimpleName() + ", not a target");
        }
        return (Key<Target>) existing;
    }

    private Keys() {}
}

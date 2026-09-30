package me.mephisto.ability_engine.engine.team;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Duels in a veil (the Whisperer's Into the Veil): two entities pulled out of the fight together. While
 * they're veiled, each can only affect (and, on the platform, only see) the other: nobody outside can
 * hit, heal or buff them, and they can't touch anyone outside. Shots, rays and dashes pass through
 * whoever is across the veil (see Teams.passThroughFor), and effects skip them.
 */
public final class Veils {

    private final Map<UUID, UUID> partner = new HashMap<>();
    /** Who started each veil (the one who cast it). */
    private final Set<UUID> initiators = new java.util.HashSet<>();
    /** Whose thing an entity is (a summon, a projectile's body): it counts as its owner. */
    private java.util.function.Function<UUID, Optional<UUID>> ownerOf = id -> Optional.empty();

    /** Summons and projectile bodies belong to someone: on which side of a veil they are is their owner's. */
    public void setOwnerResolver(java.util.function.Function<UUID, Optional<UUID>> ownerOf) { this.ownerOf = ownerOf; }

    /** The entity itself, or whoever owns it (a summon's summoner). */
    public UUID root(UUID entity) {
        if (entity == null) return null;
        return ownerOf.apply(entity).orElse(entity);
    }

    /** Did this entity start its veil (the caster), rather than being pulled in? */
    public boolean isInitiator(UUID entity) { return initiators.contains(entity); }

    /** The two in {@code entity}'s veil (empty if it isn't in one). Summons count as their owner. */
    public Set<UUID> audienceOf(UUID entity) {
        UUID self = root(entity);
        UUID other = self == null ? null : partner.get(self);
        return other == null ? Set.of() : Set.of(self, other);
    }

    /** Pull {@code a} and {@code b} into a veil together (leaving any veil they were in). */
    public void enter(UUID a, UUID b) {
        leave(a);
        leave(b);
        partner.put(a, b);
        partner.put(b, a);
        initiators.add(a);
    }

    /** {@code entity} and its partner come back. */
    public void leave(UUID entity) {
        UUID other = partner.remove(entity);
        if (other != null) partner.remove(other);
        initiators.remove(entity);
        if (other != null) initiators.remove(other);
    }

    public Optional<UUID> partnerOf(UUID entity) { return Optional.ofNullable(partner.get(entity)); }

    public boolean isVeiled(UUID entity) { return partner.containsKey(entity); }

    /** Everyone in a veil right now. */
    public Set<UUID> veiled() { return Set.copyOf(partner.keySet()); }

    /** Is there a veil between these two (one is inside and the other isn't its partner)? */
    public boolean blocks(UUID a, UUID b) {
        a = root(a);
        b = root(b);
        if (a == null || b == null || a.equals(b)) return false;
        UUID pa = partner.get(a);
        UUID pb = partner.get(b);
        if (pa == null && pb == null) return false;
        return !b.equals(pa);
    }
}

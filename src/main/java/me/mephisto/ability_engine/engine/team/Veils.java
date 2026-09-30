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

    /** Pull {@code a} and {@code b} into a veil together (leaving any veil they were in). */
    public void enter(UUID a, UUID b) {
        leave(a);
        leave(b);
        partner.put(a, b);
        partner.put(b, a);
    }

    /** {@code entity} and its partner come back. */
    public void leave(UUID entity) {
        UUID other = partner.remove(entity);
        if (other != null) partner.remove(other);
    }

    public Optional<UUID> partnerOf(UUID entity) { return Optional.ofNullable(partner.get(entity)); }

    public boolean isVeiled(UUID entity) { return partner.containsKey(entity); }

    /** Everyone in a veil right now. */
    public Set<UUID> veiled() { return Set.copyOf(partner.keySet()); }

    /** Is there a veil between these two (one is inside and the other isn't its partner)? */
    public boolean blocks(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        UUID pa = partner.get(a);
        UUID pb = partner.get(b);
        if (pa == null && pb == null) return false;
        return !b.equals(pa);
    }
}

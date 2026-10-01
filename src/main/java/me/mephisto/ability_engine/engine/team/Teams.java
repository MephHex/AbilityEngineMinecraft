package me.mephisto.ability_engine.engine.team;

import me.mephisto.ability_engine.engine.platform.WorldQuery;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Who is on whose side. Team membership comes from the platform (on Bukkit: the main scoreboard,
 * i.e. vanilla /team), so players, mobs and mannequins can all be put on teams.
 * <ul>
 *   <li>Allies: the same entity, or both on the same team.</li>
 *   <li>Everyone else is an enemy, including anything without a team.</li>
 * </ul>
 */
public final class Teams {

    private final WorldQuery world;

    public Teams(WorldQuery world) {
        this.world = world;
    }

    public Optional<String> teamOf(UUID entity) { return world.teamOf(entity); }

    public boolean allies(UUID a, UUID b) {
        if (a.equals(b)) return true;
        Optional<String> ta = world.teamOf(a);
        return ta.isPresent() && Objects.equals(ta.get(), world.teamOf(b).orElse(null));
    }

    public boolean enemies(UUID a, UUID b) { return !allies(a, b); }

    /** For ray sweeps: entities a shot from {@code caster} flies through (the caster and their allies). */
    private me.mephisto.ability_engine.engine.tag.TagManager tags;

    /** Lets untargetable entities (state.untargetable) be passed through like allies. */
    public void setTags(me.mephisto.ability_engine.engine.tag.TagManager tags) { this.tags = tags; }

    private final Veils veils = new Veils();

    /** Duels in a veil: who can only affect whom (see Veils). */
    public Veils veils() { return veils; }

    /** What the caster's shots, rays and dashes go through: allies, anyone untargetable, anyone across a veil. */
    public Predicate<UUID> passThroughFor(UUID caster) {
        return id -> allies(caster, id) || unreachable(caster, id);
    }

    /**
     * Like {@link #passThroughFor}, but allies are hit too (a seed that latches onto friend or foe): only the
     * caster, anyone untargetable and anyone across a veil are passed through.
     */
    public Predicate<UUID> passThroughAlliesHitFor(UUID caster) {
        return id -> id.equals(caster) || unreachable(caster, id);
    }

    /** Untargetable (state.untargetable), or across a veil from the caster. */
    private boolean unreachable(UUID caster, UUID id) {
        return (tags != null && tags.has(id, me.mephisto.ability_engine.engine.tag.Tags.UNTARGETABLE))
                || veils.blocks(caster, id);
    }
}

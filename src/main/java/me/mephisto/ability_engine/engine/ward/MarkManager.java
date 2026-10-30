package me.mephisto.ability_engine.engine.ward;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A character's {@code marks:} passive: which enemies near them get a marker only they see. Enemies within the range
 * that don't have the {@code unless} status from them (e.g. Sucker Punch: the ones they can still silence). Nobody
 * hidden, invisible or untargetable (a marker mustn't give them away), and no summons.
 */
public final class MarkManager {

    private final AbilityEngine engine;

    public MarkManager(AbilityEngine engine) {
        this.engine = engine;
    }

    /** The viewer's marks passive, if their character has one. */
    public Optional<CharacterDef.Marks> defOf(UUID viewer) {
        return engine.loadouts().baseCharacterOf(viewer).map(CharacterDef::marks);
    }

    /** Who {@code viewer} sees marked right now (empty without the passive, or while they're dead). */
    public List<UUID> marked(UUID viewer) {
        Optional<CharacterDef.Marks> def = defOf(viewer);
        if (def.isEmpty() || !engine.world().isAlive(viewer)) return List.of();
        var at = engine.world().positionOf(new EntityTarget(viewer));
        if (at.isEmpty()) return List.of();
        double range = def.get().range();
        List<UUID> out = new ArrayList<>();
        for (EntitySnapshot e : engine.world().livingEntitiesNear(at.get(), range)) {
            UUID id = e.id();
            if (id.equals(viewer) || !engine.teams().enemies(viewer, id) || engine.summons().isSummon(id)) continue;
            if (e.center().distance(at.get().position()) > range) continue;
            var tags = engine.tags();
            // (a puppeteer is hidden only behind their decoy, standing right there: a marker gives nothing away)
            boolean hidden = tags.has(id, Tags.HIDDEN) && !tags.has(id, Tags.PUPPETEER);
            if (hidden || tags.has(id, Tags.INVISIBLE) || tags.has(id, Tags.UNTARGETABLE)) continue;
            String unless = def.get().unlessStatus();
            if (unless != null && engine.statuses().find(id, unless).map(s -> Objects.equals(s.source(), viewer)).orElse(false)) {
                continue;
            }
            out.add(id);
        }
        return out;
    }
}

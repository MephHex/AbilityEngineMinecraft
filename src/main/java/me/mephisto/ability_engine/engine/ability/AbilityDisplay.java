package me.mephisto.ability_engine.engine.ability;

import java.util.List;

/**
 * How an ability is shown to players (hotbar icon, tooltips, the E screen later).
 * Pure data: gameplay never reads it.
 *
 * @param icon platform-specific icon id; on Bukkit a Material name. Null = pick a default.
 */
public record AbilityDisplay(String name, String icon, List<String> description) {

    public AbilityDisplay {
        description = List.copyOf(description);
    }

    public static AbilityDisplay of(String abilityId) {
        return new AbilityDisplay(abilityId, null, List.of());
    }
}

package me.mephisto.ability_engine.engine.ability;

import java.util.List;

/**
 * How an ability is shown to players (hotbar icon, tooltips, the E screen later).
 * Pure data: gameplay never reads it.
 *
 * @param icon           platform-specific icon id; on Bukkit a Material name. Null = pick a default.
 * @param hotbar         1-9: a secondary / melee ability (described on the weapon) also gets an icon of its own in that
 *                       hotbar slot (its cooldown, what it can use); 0 = none
 * @param glintWhenReady its icon glints while it can be cast (off cooldown, not running), e.g. a passive ready to use
 * @param countdown      a status: while the caster has it, the icon's stack counts the seconds it has left (null = none)
 */
public record AbilityDisplay(String name, String icon, List<String> description, int hotbar, boolean glintWhenReady,
                             String countdown) {

    public AbilityDisplay {
        description = List.copyOf(description);
    }

    public AbilityDisplay(String name, String icon, List<String> description, int hotbar) {
        this(name, icon, description, hotbar, false, null);
    }

    public AbilityDisplay(String name, String icon, List<String> description) {
        this(name, icon, description, 0);
    }

    public static AbilityDisplay of(String abilityId) {
        return new AbilityDisplay(abilityId, null, List.of());
    }
}

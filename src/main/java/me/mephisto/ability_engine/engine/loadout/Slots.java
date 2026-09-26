package me.mephisto.ability_engine.engine.loadout;

import java.util.List;

/**
 * The slots a character kit can fill. Which physical input fires which slot is decided by the
 * platform (bukkit/input/Keybinds), so the engine never knows about keys.
 * Adding a slot = one constant + add it to ALL.
 */
public final class Slots {
    /** Primary fire (LMB by default). */
    public static final String PRIMARY = "primary";
    /** Secondary / hold action (RMB by default), e.g. the Archmage's Arcane Focus. */
    public static final String SECONDARY = "secondary";
    /**
     * Optional: left-clicking an entity at melee range uses this slot instead of primary, aimed at
     * the entity that was clicked. Characters without one just use primary.
     */
    public static final String MELEE = "melee";
    public static final String ABILITY_1 = "ability_1";
    public static final String ABILITY_2 = "ability_2";
    public static final String ABILITY_3 = "ability_3";
    public static final String ULTIMATE = "ultimate";

    public static final List<String> ALL = List.of(PRIMARY, SECONDARY, MELEE, ABILITY_1, ABILITY_2, ABILITY_3, ULTIMATE);

    private Slots() {}
}

package me.mephisto.ability_engine.engine.tag;

/**
 * Tag naming convention (same idea as Unreal GameplayTags / Dota modifier states):
 * <ul>
 *   <li>{@code state.*} — what an entity IS (stunned, channeling, carrying.flag)</li>
 *   <li>{@code block.*} — what an entity CAN'T DO (ability, move)</li>
 * </ul>
 * Tags are plain strings so YAML can invent new ones; these constants are just the built-ins.
 */
public final class Tags {
    public static final String STUNNED = "state.stunned";
    public static final String SILENCED = "state.silenced";
    public static final String ROOTED = "state.rooted";
    public static final String CHANNELING = "state.channeling";
    public static final String SLOWED = "state.slowed";
    public static final String GLOWING = "state.glowing";
    public static final String BURNING = "state.burning";
    /** Air Anchor: holds the entity's height (no falling) while it lasts. */
    public static final String ANCHORED = "state.anchored";
    /** Takes reduced damage (on Bukkit: Resistance II, -40%). */
    public static final String RESISTANT = "state.resistant";

    public static final String BLOCK_ABILITY = "block.ability";
    public static final String BLOCK_MOVE = "block.move";
    /** Immune to knockback (e.g. while guarding). */
    public static final String BLOCK_KNOCKBACK = "block.knockback";

    private Tags() {}
}

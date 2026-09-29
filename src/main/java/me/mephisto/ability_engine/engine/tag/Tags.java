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
    /** No abilities, but basic attacks (primary / melee) still work (checked per slot by the loadout). */
    public static final String SILENCED = "state.silenced";
    /** No basic attacks (primary / melee); abilities still work (checked per slot by the loadout). */
    public static final String DISARMED = "state.disarmed";
    public static final String ROOTED = "state.rooted";
    public static final String CHANNELING = "state.channeling";
    public static final String SLOWED = "state.slowed";
    public static final String GLOWING = "state.glowing";
    public static final String BURNING = "state.burning";
    /** Air Anchor: holds the entity's height (no falling) while it lasts. */
    public static final String ANCHORED = "state.anchored";
    /** Takes reduced damage (on Bukkit: Resistance II, -40%). */
    public static final String RESISTANT = "state.resistant";
    /** Moves faster (on Bukkit: +30% movement speed). */
    public static final String HASTED = "state.hasted";
    /** Can't be seen (on Bukkit: Invisibility, no particles; held items still show, like vanilla). */
    public static final String INVISIBLE = "state.invisible";
    /** Half as much knockback (e.g. behind a raised shield). block.knockback is full immunity. */
    public static final String STURDY = "state.sturdy";
    /** Truly hidden from other players (armor and held items too), not just the invisibility potion. */
    public static final String HIDDEN = "state.hidden";
    /** Abilities ignore them: projectiles, rays and dashes pass through, area effects skip them. */
    public static final String UNTARGETABLE = "state.untargetable";
    public static final String BLINDED = "state.blinded";
    /** Frozen (on Bukkit: frozen, blue hearts and the powder-snow slow; no vanilla freeze damage). */
    public static final String FROZEN = "state.frozen";
    /** Can fly (on Bukkit: creative-style flight; no fall damage from the landing after it ends). */
    public static final String FLYING = "state.flying";
    /** Debuffs (non-buff statuses from others) don't land; see the character ward. */
    public static final String DEBUFF_IMMUNE = "state.debuff_immune";

    public static final String BLOCK_ABILITY = "block.ability";
    public static final String BLOCK_MOVE = "block.move";
    /** Immune to knockback (e.g. while guarding). */
    public static final String BLOCK_KNOCKBACK = "block.knockback";

    private Tags() {}
}

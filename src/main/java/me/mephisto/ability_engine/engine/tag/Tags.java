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
    /** Lifted: rises slowly while it lasts (on Bukkit: levitation), still able to drift sideways (Graviton's ultimate). */
    public static final String LIFTED = "state.lifted";
    /** Doesn't take fall damage while it lasts (e.g. slammed down from up high, where the slam is the damage). */
    public static final String SAFE_FALL = "state.safe_fall";
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
    /** Poisoned (on Bukkit: green poison swirls). */
    public static final String POISONED = "state.poisoned";
    /** Paralyzed (on Bukkit: yellow sparks). The status itself carries the slow, silence and attack speed. */
    public static final String PARALYZED = "state.paralyzed";
    /** Frozen (on Bukkit: frozen, blue hearts and the powder-snow slow; no vanilla freeze damage). */
    public static final String FROZEN = "state.frozen";
    /** Walks through other players and mobs (no collision). */
    public static final String PHASING = "state.phasing";
    /** Can fly (on Bukkit: creative-style flight; no fall damage from the landing after it ends). */
    public static final String FLYING = "state.flying";
    /** With state.flying: half the flying speed (on Bukkit: a fly speed of 0.05 instead of 0.1), e.g. a slow hover. */
    public static final String SLOW_FLIGHT = "state.slow_flight";
    /** With state.flying: held at the height they had when it started (no flying up or down; teleports move it). */
    public static final String LEVEL_FLIGHT = "state.level_flight";
    /** In the middle of a dash (granted by the dash itself to whoever it moves), e.g. a hover passive holds off. */
    public static final String DASHING = "state.dashing";
    /** Gliding on an elytra right now (the platform keeps it up to date). */
    public static final String GLIDING = "state.gliding";
    /** Nauseous (on Bukkit: vanilla Nausea, the wobbling screen). */
    public static final String NAUSEOUS = "state.nauseous";
    /** Debuffs (non-buff statuses from others) don't land; see the character ward. */
    public static final String DEBUFF_IMMUNE = "state.debuff_immune";
    /**
     * Crowd control doesn't hold them: its parts (stunned, rooted, silenced, disarmed, slowed...: see
     * StatusManager#isCrowdControl) are switched off while they have it. Pure crowd control (a stun, a root) doesn't
     * land at all and ends as they get it; anything else lands and keeps the rest (a slowing poison still poisons).
     * Knockback and being moved are block.knockback and block.displace: give those too for a full "unstoppable".
     */
    public static final String UNSTOPPABLE = "state.unstoppable";

    public static final String BLOCK_ABILITY = "block.ability";
    public static final String BLOCK_MOVE = "block.move";
    /** Immune to knockback (e.g. while guarding). */
    public static final String BLOCK_KNOCKBACK = "block.knockback";
    /**
     * Can't be moved by anyone else: no pulls (leash), drags (someone else's dash moving them), teleports or swaps
     * from others. With block.knockback: completely unmovable (a tree).
     */
    public static final String BLOCK_DISPLACE = "block.displace";
    /**
     * Can't walk or jump: held where they stand, but unlike block.move their speed isn't touched, so the camera
     * doesn't zoom in (a root's slowness does), and abilities still work (e.g. planted in the ground).
     */
    public static final String BLOCK_WALK = "block.walk";
    /**
     * Working a Changeling puppet (Mirrors' PUPPET mode): they can't walk or jump (on Bukkit: walk speed 0, so no
     * rubber-banding and no camera zoom) while their keys move the clone.
     */
    public static final String PUPPETEER = "state.puppeteer";
    /**
     * Friendly fire: they're everyone's enemy while they have it, their own team's too (their abilities hit their allies,
     * their allies' hit them), e.g. having broken a Changeling.
     */
    public static final String FRIENDLY_FIRE = "state.friendly_fire";
    /**
     * Inverted controls (Topsy-Turvy): their movement keys go the other way (W walks back, A walks right). A brain-driven
     * enemy walks away from its target instead of after it.
     */
    public static final String INVERTED = "state.inverted";
    /**
     * Confused (Confuse Ray): turned to face their closest teammate, running at them and using their primary on them
     * (see Confusion). Crowd control.
     */
    public static final String CONFUSED = "state.confused";
    /** Being swapped (Switcheroo): flown through walls on the way, so being inside a block doesn't hurt them. */
    public static final String SWAPPING = "state.swapping";

    private Tags() {}
}

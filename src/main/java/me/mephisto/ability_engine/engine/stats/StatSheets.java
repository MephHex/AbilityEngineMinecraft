package me.mephisto.ability_engine.engine.stats;

import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.platform.WorldQuery;

import java.util.UUID;

/**
 * Everyone's stats: a character's sheet ({@code stats:} in its kit), else the defaults (max HP from the
 * platform when it knows it: a mob's own health).
 * <p>Armor: damage taken x {@code constant / (constant + armor)}, so each armor point is +1% effective HP
 * (constant 100: 25 armor = 20% less damage, 100 armor = 50% less).
 */
public final class StatSheets {

    public static final double DEFAULT_ARMOR_CONSTANT = 100;

    private final LoadoutManager loadouts;
    private final WorldQuery world;
    private double armorConstant = DEFAULT_ARMOR_CONSTANT;

    public StatSheets(LoadoutManager loadouts, WorldQuery world) {
        this.loadouts = loadouts;
        this.world = world;
    }

    public void setArmorConstant(double constant) { this.armorConstant = constant > 0 ? constant : DEFAULT_ARMOR_CONSTANT; }

    public double armorConstant() { return armorConstant; }

    /** The sheet of the entity's character (its base kit), or the defaults. */
    public CharacterDef.Stats of(UUID entity) {
        return loadouts.baseCharacterOf(entity).map(CharacterDef::stats).orElse(CharacterDef.Stats.DEFAULT);
    }

    public boolean hasSheet(UUID entity) { return loadouts.baseCharacterOf(entity).isPresent(); }

    /** Max HP (design HP): a character's sheet, else what the platform says, else the default. */
    public double maxHealth(UUID entity) {
        if (hasSheet(entity)) return of(entity).health();
        return world.maxHealth(entity).orElse(CharacterDef.Stats.DEFAULT.health());
    }

    public double armor(UUID entity) { return hasSheet(entity) ? of(entity).armor() : 0; }

    public double baseDamage(UUID entity) { return of(entity).baseDamage(); }

    /** What's left of a hit after armor: x constant / (constant + armor). */
    public double afterArmor(UUID victim, double amount) {
        double armor = armor(victim);
        return armor <= 0 ? amount : amount * armorConstant / (armorConstant + armor);
    }

    /** Share of damage armor takes off (0..1), for display. */
    public double armorReduction(UUID entity) {
        double armor = armor(entity);
        return armor / (armorConstant + armor);
    }

    /**
     * Cooldown of an ability for this caster: the character's primary follows their attack speed
     * ({@code 20 / attack_speed} ticks) when the sheet has one; everything else its own cooldown.
     */
    public int cooldownTicks(UUID caster, String abilityId, int ownCooldown) {
        return loadouts.baseCharacterOf(caster)
                .filter(c -> c.stats().attackSpeed() > 0 && abilityId.equals(c.abilityIn(Slots.PRIMARY)))
                .map(c -> Math.max(1, (int) Math.round(20 / c.stats().attackSpeed())))
                .orElse(ownCooldown);
    }
}

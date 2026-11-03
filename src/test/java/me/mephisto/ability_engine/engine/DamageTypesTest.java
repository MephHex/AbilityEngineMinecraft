package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Damage types ({@code type: physical | magic | true}): physical and magic go through armor (max_hp parts too) and spell
 * resistance, true damage through neither. And the newer parts: bonus_hp, max_hp_sp, spell power on heals.
 */
class DamageTypesTest {

    private TestEngine t;

    private static Map<String, Object> hit(Map<String, Object> damage) {
        return map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "key", "key", "target"), "effects", list(damage))));
    }

    /** "caster": 200 HP, base damage 40. "tank": 300 HP, 100 armor (half damage). */
    private void setup() {
        t = new TestEngine();
        t.load(map(
                "abilities", map(
                        "legacy", hit(map("id", "damage", "max_hp", 0.1)),
                        "physical", hit(map("id", "damage", "max_hp", 0.1, "type", "physical")),
                        "magic", hit(map("id", "damage", "base", 1.0, "max_hp", 0.1, "type", "magic")),
                        "true_hit", hit(map("id", "damage", "base", 1.0, "max_hp", 0.1, "type", true)),
                        "brawn", hit(map("id", "damage", "bonus_hp", 0.5, "type", "physical")),
                        "soul", hit(map("id", "damage", "max_hp", 0.1, "max_hp_sp", 0.0002, "type", "true")),
                        "mend", map("nodes", map("hit", map("type", "apply_effects", "targets", map("type", "self"),
                                "effects", list(map("id", "heal", "amount", 60, "sp", 0.2))))),
                        "jab", map("cooldown", 20, "nodes", map("n", map("type", "print", "message", "jab"))),
                        "reap", hit(map("id", "damage", "max_hp", 0.08, "max_hp_bonus_dmg", 0.001, "type", "true")),
                        "wither", hit(map("id", "damage", "max_hp_dmg", 0.001, "type", "true")),
                        "blast", hit(map("id", "damage", "amount", 100, "type", "true", "falloff_from", "caster",
                                "falloff_radius", 6, "falloff_edge", 0.5)),
                        "spell", hit(map("id", "damage", "amount", 100, "type", "magic")),
                        "recover", map("nodes", map("hit", map("type", "apply_effects", "targets", map("type", "self"),
                                "effects", list(map("id", "heal", "missing_hp", 0.4)))))),
                "statuses", map(
                        "tome", map("spell_power", 50),
                        "ward", map("spell_resistance", 100),
                        "girdle", map("max_health", 1.5),
                        "might", map("damage_dealt", 1.5),
                        "shred", map("spell_resistance_mult", 0.5)),
                "characters", map(
                        "caster", map("stats", map("health", 200, "base_damage", 40), "slots", map("primary", "jab")),
                        "tank", map("stats", map("health", 300, "armor", 100), "slots", map("primary", "jab")))));
    }

    private UUID character(String kit, double x) {
        UUID id = t.spawn(x, 1, 0);
        t.engine.loadouts().assign(id, kit);
        return id;
    }

    private void use(UUID caster, String ability, UUID target) {
        assertTrue(t.engine.activator().activateOnId(caster, ability, new EntityTarget(target), Map.of()).success());
    }

    @Test
    void physicalAndMagicGoThroughArmorAllOfIt() {
        setup();
        UUID me = character("caster", 0);
        UUID a = character("tank", 3), b = character("tank", 6), c = character("tank", 9);
        use(me, "legacy", a);
        assertEquals(30, t.damage(a), 1e-9, "no type: the max_hp part still ignores armor");
        use(me, "physical", b);
        assertEquals(15, t.damage(b), 1e-9, "physical: 10% of 300, halved by 100 armor");
        use(me, "magic", c);
        assertEquals((40 + 30) * 0.5, t.damage(c), 1e-9, "magic: base and max_hp parts, both halved");
    }

    @Test
    void trueDamageIgnoresArmorAndSpellResistance() {
        setup();
        UUID me = character("caster", 0);
        UUID tank = character("tank", 3), other = character("tank", 6);
        t.engine.statuses().apply(tank, "ward", 0, tank);
        t.engine.statuses().apply(other, "ward", 0, other);
        use(me, "true_hit", tank);
        assertEquals(40 + 30, t.damage(tank), 1e-9, "true damage (a YAML true works): nothing reduces it");
        use(me, "magic", other);
        assertEquals((40 + 30) * 0.5 * 0.5, t.damage(other), 1e-9, "magic: armor, then spell resistance");
    }

    @Test
    void bonusHpIsMaxHpAboveTheCharactersOwn() {
        setup();
        UUID me = character("caster", 0);
        UUID dummy = t.spawn(4, 1, 0);
        use(me, "brawn", dummy);
        assertEquals(0, t.damage(dummy), 1e-9, "no bonus HP yet");
        t.engine.statuses().apply(me, "girdle", 0, me); // 200 -> 300 max HP: 100 bonus
        assertEquals(100, t.engine.stats().bonusHealth(me), 1e-9);
        use(me, "brawn", dummy);
        assertEquals(50, t.damage(dummy), 1e-9, "50% of 100 bonus HP");
    }

    @Test
    void maxHpGrowsWithSpellPower() {
        setup();
        UUID me = character("caster", 0);
        UUID tank = character("tank", 3);
        t.engine.statuses().apply(me, "tome", 0, me); // 50 spell power: +1%
        use(me, "soul", tank);
        assertEquals(300 * 0.11, t.damage(tank), 1e-9, "10% + 1% per 50 spell power, of 300 (true: no armor)");
    }

    @Test
    void healsScaleWithSpellPower() {
        setup();
        UUID me = character("caster", 0);
        use(me, "mend", me);
        assertEquals(60, t.healed.get(me), 1e-9, "no spell power: the flat 60");
        t.engine.statuses().apply(me, "tome", 0, me);
        use(me, "mend", me);
        assertEquals(60 + 60 + 10, t.healed.get(me), 1e-9, "50 spell power: +10");
    }

    @Test
    void maxHpGrowsWithBonusDamageAndWithDamage() {
        setup();
        UUID me = character("caster", 0);
        UUID a = character("tank", 3), b = character("tank", 6);
        t.engine.statuses().apply(me, "might", 0, me); // 40 base damage x1.5: 60 damage, 20 of it bonus
        assertEquals(20, t.engine.stats().bonusDamage(me), 1e-9);
        use(me, "reap", a);
        assertEquals(300 * (0.08 + 0.02) * 1.5, t.damage(a), 1e-9, "8% + 0.1% per bonus damage (20): 10%, then x1.5 damage dealt");
        use(me, "wither", b);
        assertEquals(300 * 0.06 * 1.5, t.damage(b), 1e-9, "0.1% per damage (60): 6%, then x1.5 damage dealt");
    }

    @Test
    void falloffDealsLessFartherOut() {
        setup();
        UUID me = character("caster", 0);
        UUID near = t.spawn(0, 1, 0.001), mid = t.spawn(3, 1, 0), far = t.spawn(9, 1, 0);
        use(me, "blast", near);
        use(me, "blast", mid);
        use(me, "blast", far);
        assertEquals(100, t.damage(near), 0.01, "at the centre: all of it");
        assertEquals(75, t.damage(mid), 1e-9, "halfway out: 75%");
        assertEquals(50, t.damage(far), 1e-9, "past the radius: the edge's 50%");
    }

    @Test
    void spellResistanceCanBeShredded() {
        setup();
        UUID me = character("caster", 0);
        UUID tank = t.spawn(3, 1, 0);
        t.engine.statuses().apply(tank, "ward", 0, tank);
        t.engine.statuses().apply(tank, "shred", 0, me); // 100 -> 50 spell resistance
        assertEquals(50, t.engine.stats().spellResistance(tank), 1e-9);
        use(me, "spell", tank);
        assertEquals(100 * 100.0 / 150, t.damage(tank), 1e-9);
    }

    @Test
    void aHealCanBeAShareOfMissingHealth() {
        setup();
        UUID me = character("caster", 0); // 200 HP
        t.world.setHealthShare(me, 0.25); // 150 missing
        use(me, "recover", me);
        assertEquals(60, t.healed.get(me), 1e-9, "40% of the 150 missing");
    }
}

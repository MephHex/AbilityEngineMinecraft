package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Character stat sheets: base damage, armor, % max HP damage and heals, attack speed. */
class StatsTest {

    private TestEngine t;

    /** Two kits: "bruiser" (base damage 40, 100 armor, 300 HP, 2 attacks/s) and "plain" (no sheet). */
    private void setup() {
        t = new TestEngine();
        Map<String, Object> hit = map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "key", "key", "target"),
                "effects", list(map("id", "damage", "base", 1.5)))));
        Map<String, Object> flat = map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "key", "key", "target"),
                "effects", list(map("id", "damage", "amount", 100)))));
        Map<String, Object> percent = map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "key", "key", "target"),
                "effects", list(map("id", "damage", "max_hp", 0.1)))));
        Map<String, Object> mixed = map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "key", "key", "target"),
                "effects", list(map("id", "damage", "base", 1.2, "max_hp", 0.1)))));
        Map<String, Object> mixedHeal = map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "self"),
                "effects", list(map("id", "heal", "amount", 10, "max_hp", 0.1)))));
        Map<String, Object> mend = map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "self"),
                "effects", list(map("id", "heal", "max_hp", 0.2), map("id", "shield", "amount", 30)))));
        Map<String, Object> soul = map("nodes", map("tear", map("type", "summon_clone", "summon", "soul",
                "of", "target", "health_share", 0.5, "lifetime", 100)));
        t.load(map(
                "abilities", map("hit", hit, "flat", flat, "percent", percent, "mend", mend, "soul", soul,
                        "mixed", mixed, "mixed_heal", mixedHeal,
                        "jab", map("cooldown", 30, "nodes", map("n", map("type", "print", "message", "jab"))),
                        "zap", map("cooldown", 30, "nodes", map("n", map("type", "print", "message", "zap")))),
                "statuses", map("sluggish", map("duration", 100, "tags", list("state.sluggish"), "attack_speed", 0.5)),
                "characters", map(
                        "bruiser", map("stats", map("health", 300, "armor", 100, "base_damage", 40,
                                "move_speed", 0.9, "attack_speed", 2.0), "slots", map("primary", "jab", "ability_1", "zap")),
                        "plain", map("slots", map("primary", "jab")))));
    }

    private UUID character(String kit) {
        UUID id = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(id, kit);
        return id;
    }

    private void use(UUID caster, String ability, UUID target) {
        assertTrue(t.engine.activator().activateOnId(caster, ability, new EntityTarget(target), Map.of()).success());
    }

    @Test
    void abilitiesDealAPercentOfTheCastersBaseDamage() {
        setup();
        UUID bruiser = character("bruiser");
        UUID dummy = t.spawn(5, 1, 0);
        use(bruiser, "hit", dummy);
        assertEquals(60, t.damage(dummy), 1e-9, "150% of 40");

        UUID plain = character("plain");
        UUID other = t.spawn(5, 1, 3);
        use(plain, "hit", other);
        assertEquals(60, t.damage(other), 1e-9, "no sheet: the default base damage, 40");
    }

    @Test
    void armorReducesDamageByArmorOverArmorPlus100() {
        setup();
        UUID bruiser = character("bruiser");
        UUID attacker = t.spawn(5, 1, 0);
        use(attacker, "flat", bruiser);
        assertEquals(50, t.damage(bruiser), 1e-9, "100 armor: half");
        assertEquals(0.5, t.engine.stats().armorReduction(bruiser), 1e-9);

        UUID plain = character("plain");
        use(attacker, "flat", plain);
        assertEquals(100, t.damage(plain), 1e-9, "no armor");
    }

    @Test
    void theArmorConstantIsASetting() {
        setup();
        t.engine.stats().setArmorConstant(300);
        UUID bruiser = character("bruiser");
        use(t.spawn(5, 1, 0), "flat", bruiser);
        assertEquals(75, t.damage(bruiser), 1e-9, "100 armor vs 300: 25% off");
    }

    @Test
    void maxHpDamageIsAShareOfTheTargetsMaxHpAndIgnoresArmor() {
        setup();
        UUID bruiser = character("bruiser");
        use(t.spawn(5, 1, 0), "percent", bruiser);
        assertEquals(30, t.damage(bruiser), 1e-9, "10% of 300, armor or not");

        UUID mob = t.spawn(5, 1, 3);
        use(bruiser, "percent", mob);
        assertEquals(20, t.damage(mob), 1e-9, "no sheet: 10% of the default 200");
    }

    @Test
    void partsAddUpAndOnlyTheMaxHpPartIgnoresArmor() {
        setup();
        UUID bruiser = character("bruiser");                 // 300 HP, 100 armor
        UUID plain = character("plain");                     // base damage 40
        use(plain, "mixed", bruiser);
        // 120% of 40 = 48, halved by 100 armor = 24; plus 10% of 300 = 30 straight through
        assertEquals(24 + 30, t.damage(bruiser), 1e-9);

        UUID mob = t.spawn(5, 1, 3);
        use(bruiser, "mixed", mob);
        assertEquals(48 + 20, t.damage(mob), 1e-9, "no armor: 48 + 10% of the default 200");
    }

    @Test
    void healPartsAddUpToo() {
        setup();
        UUID bruiser = character("bruiser");
        use(bruiser, "mixed_heal", bruiser);
        assertEquals(10 + 30, t.healed.get(bruiser), 1e-9, "10 flat + 10% of 300");
    }

    @Test
    void healsCanBeAShareOfMaxHpShieldsStayFlat() {
        setup();
        UUID bruiser = character("bruiser");
        use(bruiser, "mend", bruiser);
        assertEquals(60, t.healed.get(bruiser), 1e-9, "20% of 300");
        assertEquals(30, t.shields.get(bruiser), 1e-9, "flat");
    }

    @Test
    void attackSpeedSetsThePrimarysCooldown() {
        setup();
        UUID bruiser = character("bruiser");
        assertTrue(t.engine.loadouts().activate(bruiser, Slots.PRIMARY).success());
        assertEquals(10, t.engine.cooldowns().remainingTicks(bruiser, "jab"), "2 attacks a second = 10 ticks");

        UUID plain = character("plain");
        assertTrue(t.engine.loadouts().activate(plain, Slots.PRIMARY).success());
        assertEquals(30, t.engine.cooldowns().remainingTicks(plain, "jab"), "no attack speed: its own 30");
    }

    @Test
    void slowedAttacksStretchOnlyTheBasicAttack() {
        setup();
        UUID bruiser = character("bruiser");
        UUID plain = character("plain");
        t.engine.statuses().apply(bruiser, "sluggish", 100, null);
        t.engine.statuses().apply(plain, "sluggish", 100, null);
        assertEquals(0.5, t.engine.stats().attackSpeedMultiplier(bruiser), 1e-9);
        assertTrue(t.engine.loadouts().activate(bruiser, Slots.PRIMARY).success());
        assertEquals(20, t.engine.cooldowns().remainingTicks(bruiser, "jab"), "2 attacks/s at half speed: 1 a second");
        assertTrue(t.engine.loadouts().activate(bruiser, Slots.ABILITY_1).success());
        assertEquals(30, t.engine.cooldowns().remainingTicks(bruiser, "zap"), "abilities keep their cooldown");
        assertTrue(t.engine.loadouts().activate(plain, Slots.PRIMARY).success());
        assertEquals(60, t.engine.cooldowns().remainingTicks(plain, "jab"), "its own 30, at half speed");
    }

    @Test
    void aSoulsHealthCanFollowItsOwnersMaxHp() {
        setup();
        UUID bruiser = character("bruiser");
        UUID caster = t.spawn(5, 1, 0);
        use(caster, "soul", bruiser);
        UUID soul = t.engine.summons().find(caster, "soul").orElseThrow();
        assertEquals(150, t.world.health.get(soul), 1e-9, "50% of the owner's 300");
    }

    @Test
    void badStatsAreReported() {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(map(
                "abilities", map("jab", map("nodes", map("n", map("type", "print", "message", "x")))),
                "characters", map(
                        "a", map("stats", map("health", 0), "slots", map("primary", "jab")),
                        "b", map("stats", map("speed", 2), "slots", map("primary", "jab"))),
                "statuses", map("broken", map("duration", 20, "attack_speed", 0))), "test");
        String errors = String.join("\n", report.errors());
        assertTrue(errors.contains("health: must be above 0"), errors);
        assertTrue(errors.contains("unknown stat"), errors);
        assertTrue(errors.contains("attack_speed: must be above 0"), errors);
    }
}

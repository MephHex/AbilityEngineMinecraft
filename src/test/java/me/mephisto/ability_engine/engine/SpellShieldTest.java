package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The charging spell shield (spell_shield + shield_charge + purge_buffs): a 3s shield that stores spell
 * damage, then a blast of what it stored; at max charge it also strips buffs.
 */
class SpellShieldTest {

    private TestEngine t;
    private UUID p;
    private UUID enemy;

    private void setup() {
        t = new TestEngine();
        t.load(map(
                "statuses", map("pumped", map("duration", 100, "tags", list("buff.pumped"))),
                "abilities", map(
                        "punch", map("nodes", map("hit", map("type", "apply_effects",
                                "targets", map("type", "key", "key", "target"),
                                "effects", list(map("id", "damage", "amount", 100))))),
                        "shield", map("nodes", map(
                                "raise", map("type", "spell_shield", "max", 150, "next", "hold"),
                                "hold", map("type", "delay", "ticks", 60, "next", "measure"),
                                "measure", map("type", "shield_charge", "store", "absorbed",
                                        "on", map("out", "blast", "full", "purge")),
                                "blast", map("type", "apply_effects", "targets", map("type", "radius", "radius", 4.5),
                                        "effects", list(map("id", "damage", "amount", 1, "scale_by", "absorbed"))),
                                "purge", map("type", "apply_effects", "targets", map("type", "radius", "radius", 4.5),
                                        "effects", list(map("id", "damage", "amount", 1, "scale_by", "absorbed"),
                                                map("id", "purge_buffs"))))))));
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        enemy = t.spawn(2.5, 1, 0);
        t.world.team(enemy, "red");
    }

    private void hit(boolean spell) {
        assertTrue(t.engine.activator().activateOnId(enemy, "punch", new EntityTarget(p),
                Map.of("slot", spell ? Slots.ABILITY_1 : Slots.PRIMARY)).success());
    }

    @Test
    void itEatsSpellsButNotBasicAttacks() {
        setup();
        assertTrue(t.engine.activator().activate(p, "shield").success());
        hit(true);
        assertEquals(0, t.damage(p), 1e-9, "a spell: absorbed");
        assertEquals(100, t.engine.spellShields().charge(p), 1e-9, "stored as charge");
        hit(false);
        assertEquals(100, t.damage(p), 1e-9, "a basic attack still lands");
    }

    @Test
    void itReleasesWhatItStored() {
        setup();
        t.engine.activator().activate(p, "shield");
        hit(true);
        t.time.advance(61);
        assertEquals(100, t.damage(enemy), 1e-9);
        assertFalse(t.engine.spellShields().has(p), "the shield is down");
    }

    @Test
    void atFullChargeItStripsBuffs() {
        setup();
        t.engine.statuses().apply(enemy, "pumped", enemy);
        t.engine.activator().activate(p, "shield");
        hit(true);
        t.time.advance(11);
        hit(true);                                           // 200 > the 150 max
        assertEquals(150, t.engine.spellShields().charge(p), 1e-9, "capped");
        t.time.advance(61);
        assertEquals(150, t.damage(enemy), 1e-9);
        assertFalse(t.engine.statuses().has(enemy, "pumped"), "its buff is gone");
    }
}

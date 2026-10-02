package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.combat.DamageModifiers;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Seraph (fixtures/seraph.yml): 190 HP, 10 armor, base damage 34. She stands at the origin on a floor, looking +x,
 * team blue. Allies are blue, enemies red (no sheet: 200 HP, no armor).
 */
class SeraphTest {

    private static final double BASE = 34;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "seraph");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private UUID ally(double x, double z) {
        UUID a = t.spawn(x, 1, z);
        t.world.team(a, "blue");
        return a;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success(), slot); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    // ---- Gilded Slash, Radiant Thrust -----------------------------------------------------------------------

    @Test
    void aGildedSlashHitsWhoeverIsInFront() throws IOException {
        setup();
        UUID enemy = foe(2, 0);
        use(Slots.PRIMARY);
        assertEquals(BASE, t.damage(enemy), 1e-6);
    }

    @Test
    void onFootRadiantThrustJustStabs() throws IOException {
        setup();
        UUID enemy = foe(3, 0);
        use(Slots.ABILITY_1);
        t.time.advance(10);
        assertEquals(BASE * 1.2, t.damage(enemy), 1e-6, "120% base damage");
        assertFalse(has(enemy, "stun"));
        assertFalse(has(enemy, "seraph_dazzled"));
    }

    @Test
    void glidingItsADiveThatStunsBlindsAndKnocksUp() throws IOException {
        setup();
        t.engine.tags().grant(p, Tags.GLIDING); // (the platform keeps this on while she glides)
        UUID enemy = foe(3, 0);
        use(Slots.ABILITY_1);
        t.time.advance(10);
        assertEquals(BASE * 1.4, t.damage(enemy), 1e-6, "140% base damage");
        assertTrue(has(enemy, "stun"), "stunned");
        assertTrue(has(enemy, "seraph_dazzled"), "blinded");
        assertTrue(t.knockbackVec.get(enemy).y() > 0, "knocked up");
    }

    // ---- Guardian's Tether ----------------------------------------------------------------------------------

    @Test
    void sheFliesToAnAllyStopsShortAndHealsThemWhileTheTetherHolds() throws IOException {
        setup();
        UUID friend = ally(12, 0);
        use(Slots.ABILITY_2);
        assertTrue(t.engine.cooldowns().remainingTicks(p, "seraph_ab2") > 0, "picked: the cooldown starts");
        t.time.advance(20);
        double apart = pos(p).subtract(pos(friend)).length();
        assertTrue(apart > 2 && apart < 5, "stopped about 3 blocks short: " + apart);

        t.time.advance(100); // the 4s tether, held all the way
        assertEquals(8 * 200 * 0.03, t.healed.getOrDefault(friend, 0.0), 1e-6, "3% max HP every 0.5s, 8 times");
        assertTrue(has(friend, "seraph_valor"), "held all the way: Strength");
    }

    @Test
    void aBrokenTetherStopsHealingAndGivesNoStrength() throws IOException {
        setup();
        UUID friend = ally(12, 0);
        use(Slots.ABILITY_2);
        t.time.advance(40);
        double before = t.healed.getOrDefault(friend, 0.0);
        assertTrue(before > 0, "healing while it holds");
        t.world.move(friend, new Vec3(60, 1, 0)); // far out of reach: it breaks
        t.time.advance(100);
        assertTrue(t.healed.getOrDefault(friend, 0.0) <= before + 200 * 0.03 + 1e-6, "no more healing");
        assertFalse(has(friend, "seraph_valor"));
    }

    @Test
    void aimingAtNobodyCostsNothing() throws IOException {
        setup();
        use(Slots.ABILITY_2);
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "seraph_ab2"));
    }

    // ---- Light Arrows ---------------------------------------------------------------------------------------

    @Test
    void lightArrowsSwapInABowWithThreeShotsThatWeakenFoesAndCleanseFriends() throws IOException {
        setup();
        UUID enemy = foe(8, 0);
        UUID friend = ally(8, 2); // next to the enemy, out of the arrow's way
        t.engine.statuses().apply(friend, "root", enemy);
        use(Slots.ABILITY_3);
        assertEquals("seraph_light_arrow", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow(), "the bow");
        assertEquals(3, t.engine.resources().get(p, "light"));

        use(Slots.PRIMARY);
        t.time.advance(15);
        assertTrue(has(enemy, "seraph_weakened"), "the enemy: weakened (and glowing)");
        assertTrue(t.engine.tags().has(enemy, Tags.GLOWING));
        assertFalse(has(friend, "root"), "the ally: cleansed");
        assertTrue(has(friend, "seraph_swiftness"), "and faster");

        use(Slots.PRIMARY);
        t.time.advance(15);
        use(Slots.PRIMARY);
        t.time.advance(2);
        assertEquals("seraph_primary", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow(), "out of arrows: the sword");
    }

    @Test
    void pressingThreeAgainPutsTheBowAway() throws IOException {
        setup();
        use(Slots.ABILITY_3);
        assertTrue(t.engine.tags().has(p, "state.radiant_bow"));
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        assertFalse(t.engine.tags().has(p, "state.radiant_bow"));
    }

    // ---- Divine Ward ----------------------------------------------------------------------------------------

    @Test
    void divineWardLmbWardsTheAllyInHerSights() throws IOException {
        setup();
        UUID friend = ally(6, 0);
        UUID enemy = foe(10, 5);
        use(Slots.ULTIMATE);
        t.time.advance(2);
        assertTrue(has(friend, "seraph_marked"), "the ally in her sights glows");
        use(Slots.PRIMARY); // LMB: them
        assertTrue(has(friend, "seraph_warded"));
        assertEquals(0, DamageModifiers.apply(t.engine, enemy, friend, 500, 0).amount(), 1e-9, "no damage at all");
        assertFalse(t.engine.tags().has(p, "state.choosing_ward"), "chosen: over");
        assertEquals("seraph_primary", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow());

        t.time.advance(80);
        assertFalse(has(friend, "seraph_warded"), "4s");
    }

    @Test
    void divineWardRmbWardsHerself() throws IOException {
        setup();
        use(Slots.ULTIMATE);
        use(Slots.SECONDARY); // RMB: herself
        assertTrue(has(p, "seraph_warded"));
        assertFalse(t.engine.tags().has(p, "state.choosing_ward"));
    }

    @Test
    void cleanseTakesOffDebuffsButNotBuffs() throws IOException {
        setup();
        UUID enemy = foe(10, 0);
        t.engine.statuses().apply(p, "root", enemy);
        t.engine.statuses().apply(p, "seraph_valor", p);
        t.load(java.util.Map.of("abilities", java.util.Map.of("cleanse_me", java.util.Map.of("nodes", java.util.Map.of(
                "c", java.util.Map.of("type", "apply_effects", "targets", java.util.Map.of("type", "self"),
                        "effects", java.util.List.of(java.util.Map.of("id", "cleanse"))))))));
        assertTrue(t.engine.activator().activate(p, "cleanse_me").success());
        assertFalse(has(p, "root"), "the debuff is gone");
        assertTrue(has(p, "seraph_valor"), "the buff stays");
    }
}

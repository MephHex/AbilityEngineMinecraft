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
 * The Valkyrie (fixtures/valkyrie.yml): 220 HP, 15 armor, base damage 38. She stands at the origin on a floor, looking +x,
 * team blue. Allies are blue, enemies red (no sheet: 200 HP, no armor).
 */
class ValkyrieTest {

    private static final double BASE = 38;
    private static final double MAX_HP = 220;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "valkyrie");
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

    private int fervor() {
        return t.engine.statuses().on(p).stream().filter(s -> s.def().id().equals("valkyrie_fervor"))
                .mapToInt(s -> s.stacks()).findFirst().orElse(0);
    }

    private long cooldown(String ability) { return t.engine.cooldowns().remainingTicks(p, ability); }

    // ---- Gilded Slash, Fervor -------------------------------------------------------------------------------

    @Test
    void aGildedSlashHitsWhoeverIsInFrontAndBuildsFervor() throws IOException {
        setup();
        UUID enemy = foe(2, 0);
        use(Slots.PRIMARY);
        assertEquals(BASE, t.damage(enemy), 1e-6);
        assertEquals(1, fervor(), "a stack of Fervor");
        assertEquals(1.08, t.engine.stats().attackSpeedMultiplier(p), 1e-9, "8% faster");
    }

    @Test
    void fervorStacksUpToFiveAndFadesWithoutHits() throws IOException {
        setup();
        foe(2, 0);
        for (int i = 0; i < 7; i++) {
            use(Slots.PRIMARY);
            t.time.advance(20);
        }
        assertEquals(5, fervor(), "5 at most");
        assertEquals(Math.pow(1.08, 5), t.engine.stats().attackSpeedMultiplier(p), 1e-9, "each stack: 8% faster");
        t.time.advance(61);
        assertEquals(0, fervor(), "3s without a hit: gone");
    }

    @Test
    void missingBuildsNoFervor() throws IOException {
        setup();
        use(Slots.PRIMARY);
        assertEquals(0, fervor());
    }

    // ---- Valkyrie's Leap -------------------------------------------------------------------------------------

    @Test
    void twoLeapingSlashesThenADiveOntoTheSpotSheChooses() throws IOException {
        setup();
        UUID enemy = foe(6, 0);
        use(Slots.PRIMARY); // (the basic attack is on cooldown...)
        assertTrue(cooldown("valkyrie_primary") > 0);

        use(Slots.ABILITY_1);
        t.time.advance(15);
        assertTrue(pos(p).x() > 3.5, "she leapt forward: " + pos(p));
        assertEquals(BASE, t.damage(enemy), 1e-6, "the first slash: 100%");
        assertEquals(0, cooldown("valkyrie_primary"), "...and is ready at once");
        assertEquals(0, cooldown("valkyrie_ab1"), "the chain's still going");

        t.world.move(enemy, pos(p).add(5.5, 0, 0)); // ahead of her again
        use(Slots.ABILITY_1);
        t.time.advance(15);
        assertEquals(2 * BASE, t.damage(enemy), 1e-6, "the second slash");

        use(Slots.ABILITY_1); // soar up
        for (int i = 0; i < 40 && !t.engine.targeting().isTargeting(p); i++) t.time.advance(1);
        assertTrue(t.engine.targeting().isTargeting(p), "choosing where to dive");
        assertTrue(pos(p).y() > 5, "high up: " + pos(p));
        assertTrue(t.engine.tags().has(p, Tags.ANCHORED), "hanging there");
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_WALK), "can't move");

        Vec3 spot = pos(enemy).add(0, -1, 0);
        t.world.look(p, spot.subtract(pos(p)));
        assertTrue(t.engine.targeting().confirm(p).success()); // 1 again: there (in game the key confirms it)
        t.time.advance(20);
        assertTrue(Math.abs(pos(p).x() - spot.x()) < 1.5 && pos(p).y() < 2, "dove onto the spot: " + pos(p));
        assertEquals(2 * BASE + BASE * 1.4, t.damage(enemy), 1e-6, "the dive: 140%");
        assertTrue(t.knockbackVec.get(enemy).y() > 0.5, "knocked up");
        assertEquals(3, fervor(), "every hit: Fervor");
        assertTrue(cooldown("valkyrie_ab1") > 100, "the last recast used: the cooldown runs");
    }

    @Test
    void aMissedLeapEndsTheChain() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        t.time.advance(15);
        assertTrue(cooldown("valkyrie_ab1") > 140, "nothing hit: no second leap, the cooldown starts");
    }

    @Test
    void noLeapingWhileGliding() throws IOException {
        setup();
        t.engine.tags().grant(p, Tags.GLIDING); // (the platform keeps this on while she glides)
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        assertEquals(0, cooldown("valkyrie_ab1"));
    }

    // ---- Valkyrie's Charge -----------------------------------------------------------------------------------

    @Test
    void onFootTheChargeFlingsTheEnemyBehindHerAndStunsThem() throws IOException {
        setup();
        UUID enemy = foe(4, 0);
        use(Slots.ABILITY_2);
        t.time.advance(10);
        assertEquals(BASE * 0.8, t.damage(enemy), 1e-6, "80% base damage");
        assertTrue(has(enemy, "stun"), "stunned");
        Vec3 fling = t.knockbackVec.get(enemy);
        assertTrue(fling.x() < -1 && fling.y() > 0, "flung back over her: " + fling);
        assertEquals(MAX_HP * 0.15, t.shields.getOrDefault(p, 0.0), 1e-6, "a hit: a shield, 15% of her max HP");
        assertEquals(1, fervor());
        assertEquals(200, cooldown("valkyrie_ab2"), 12, "10s");
    }

    @Test
    void onFootAMissStillCostsTheCooldownButGivesNoShield() throws IOException {
        setup();
        use(Slots.ABILITY_2);
        t.time.advance(10);
        assertTrue(cooldown("valkyrie_ab2") > 0, "she dashed: it's spent");
        assertEquals(0, t.shields.getOrDefault(p, 0.0), 1e-9);
    }

    @Test
    void glidingSheHomesOntoAnEnemyAndCarriesThem() throws IOException {
        setup();
        t.engine.tags().grant(p, Tags.GLIDING);
        UUID enemy = foe(10, 0);
        use(Slots.ABILITY_2);
        assertTrue(cooldown("valkyrie_ab2") > 0, "someone to fly at: the cooldown starts");
        t.time.advance(30);
        assertEquals(BASE * 0.6, t.damage(enemy), 1e-6, "caught: 60%, no terrain in the way");
        assertTrue(has(enemy, "stun"), "held while she carries them");
        assertTrue(pos(enemy).x() > 15, "carried along: " + pos(enemy));
        assertEquals(MAX_HP * 0.15, t.shields.getOrDefault(p, 0.0), 1e-6, "a shield");
    }

    @Test
    void glidingCarryingThemIntoAWallHurts15PercentOfTheirMaxHp() throws IOException {
        setup();
        t.engine.tags().grant(p, Tags.GLIDING);
        UUID enemy = foe(10, 0);
        t.world.box(13, 14, -4, 4, 10); // a wall just behind them
        use(Slots.ABILITY_2);
        t.time.advance(30);
        assertTrue(pos(enemy).x() < 13, "stopped by the wall: " + pos(enemy));
        assertEquals(BASE * 0.6 + 200 * 0.15, t.damage(enemy), 1e-6, "crashed: 15% of their max HP on top");
    }

    @Test
    void glidingWithNobodyInSightCostsNothing() throws IOException {
        setup();
        t.engine.tags().grant(p, Tags.GLIDING);
        use(Slots.ABILITY_2);
        assertEquals(0, cooldown("valkyrie_ab2"));
    }

    // ---- War Cry ---------------------------------------------------------------------------------------------

    @Test
    void warCryHastesAndStrengthensHerAndNearbyAllies() throws IOException {
        setup();
        UUID near = ally(5, 0);
        UUID far = ally(12, 0);
        UUID enemy = foe(3, 0);
        use(Slots.ABILITY_3);
        assertTrue(has(p, "valkyrie_battle_cry"), "herself");
        assertTrue(has(near, "valkyrie_battle_cry"), "an ally within 8 blocks");
        assertFalse(has(far, "valkyrie_battle_cry"), "not one farther away");
        assertFalse(has(enemy, "valkyrie_battle_cry"), "nor an enemy");
        assertEquals(1.25, t.engine.stats().moveSpeedMultiplier(near), 1e-9, "25% faster");
        assertEquals(120, DamageModifiers.apply(t.engine, near, enemy, 100, 0).amount(), 1e-9, "20% more damage");
        t.time.advance(81);
        assertFalse(has(near, "valkyrie_battle_cry"), "4s");
    }

    // ---- Divine Ward ----------------------------------------------------------------------------------------

    @Test
    void divineWardLmbWardsTheAllyInHerSights() throws IOException {
        setup();
        UUID friend = ally(6, 0);
        UUID enemy = foe(10, 5);
        use(Slots.ULTIMATE);
        t.time.advance(2);
        assertTrue(has(friend, "valkyrie_marked"), "the ally in her sights glows");
        use(Slots.PRIMARY); // LMB: them
        assertTrue(has(friend, "valkyrie_warded"));
        assertEquals(0, DamageModifiers.apply(t.engine, enemy, friend, 500, 0).amount(), 1e-9, "no damage at all");
        assertFalse(t.engine.tags().has(p, "state.choosing_ward"), "chosen: over");
        assertEquals("valkyrie_primary", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow());

        t.time.advance(80);
        assertFalse(has(friend, "valkyrie_warded"), "4s");
    }

    @Test
    void divineWardRmbWardsHerself() throws IOException {
        setup();
        use(Slots.ULTIMATE);
        use(Slots.SECONDARY); // RMB: herself
        assertTrue(has(p, "valkyrie_warded"));
        assertFalse(t.engine.tags().has(p, "state.choosing_ward"));
    }

    @Test
    void cleanseTakesOffDebuffsButNotBuffs() throws IOException {
        setup();
        UUID enemy = foe(10, 0);
        t.engine.statuses().apply(p, "root", enemy);
        t.engine.statuses().apply(p, "valkyrie_battle_cry", p);
        t.load(java.util.Map.of("abilities", java.util.Map.of("cleanse_me", java.util.Map.of("nodes", java.util.Map.of(
                "c", java.util.Map.of("type", "apply_effects", "targets", java.util.Map.of("type", "self"),
                        "effects", java.util.List.of(java.util.Map.of("id", "cleanse"))))))));
        assertTrue(t.engine.activator().activate(p, "cleanse_me").success());
        assertFalse(has(p, "root"), "the debuff is gone");
        assertTrue(has(p, "valkyrie_battle_cry"), "the buff stays");
    }
}

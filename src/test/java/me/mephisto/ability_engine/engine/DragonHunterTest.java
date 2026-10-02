package me.mephisto.ability_engine.engine;

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
 * The Dragon Hunter (fixtures/dragon_hunter.yml): 230 HP, 20 armor, base damage 42. He stands at the origin on a floor,
 * looking +x, team blue. Enemies are red (no sheet: 200 HP, no armor), unless they're given a character.
 */
class DragonHunterTest {

    private static final double BASE = 42;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "dragon_hunter");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    /** An enemy with armor: another Dragon Hunter (20 armor). */
    private UUID armoredFoe(double x, double z) {
        UUID e = foe(x, z);
        t.engine.loadouts().assign(e, "dragon_hunter");
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success(), slot); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private int obsession(UUID id) {
        return t.engine.statuses().find(id, "dh_obsession").map(s -> s.stacks()).orElse(0);
    }

    private void swing() {
        use(Slots.PRIMARY);
        t.time.advance(26); // (0.8 a second)
    }

    // ---- Zweihander Swing, Blind Obsession -------------------------------------------------------------------

    @Test
    void aSwingHitsTheFirstEnemyInReach() throws IOException {
        setup();
        UUID near = foe(2, 0);
        UUID far = foe(5, 0);
        use(Slots.PRIMARY);
        assertEquals(BASE, t.damage(near), 1e-6, "100% base damage");
        assertEquals(0, t.damage(far), 1e-9, "out of reach");
        assertEquals(1, obsession(near));
    }

    @Test
    void blindObsessionShredsSixPercentArmorAStackUpToFive() throws IOException {
        setup();
        UUID enemy = armoredFoe(2, 0);
        assertEquals(20, t.engine.stats().armor(enemy), 1e-9);
        for (int i = 0; i < 7; i++) swing();
        assertEquals(5, obsession(enemy), "5 at most");
        assertEquals(20 * Math.pow(0.94, 5), t.engine.stats().armor(enemy), 1e-9, "27% shredded");
        t.time.advance(101);
        assertEquals(20, t.engine.stats().armor(enemy), 1e-9, "5s without a hit: back to normal");
    }

    @Test
    void hittingSomeoneElseMovesTheObsession() throws IOException {
        setup();
        UUID first = foe(2, 0);
        for (int i = 0; i < 3; i++) swing();
        assertEquals(3, obsession(first));
        t.world.move(first, new Vec3(-10, 1, 0));
        UUID second = foe(2, 0);
        swing();
        assertEquals(1, obsession(second), "the new target");
        assertEquals(0, obsession(first), "the old one's stacks are gone");
    }

    // ---- Hunter's Lunge ------------------------------------------------------------------------------------

    @Test
    void theLungeResetsTheSwingAndTheNextSwingStuns() throws IOException {
        setup();
        UUID enemy = foe(6, 0);
        use(Slots.PRIMARY); // (a whiff: on cooldown)
        assertTrue(t.engine.cooldowns().remainingTicks(p, "dh_primary") > 0);
        use(Slots.ABILITY_1);
        t.time.advance(6);
        assertTrue(pos(p).x() > 3, "dashed forward: " + pos(p));
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "dh_primary"), "the swing is ready at once");

        use(Slots.PRIMARY);
        assertEquals(BASE, t.damage(enemy), 1e-6);
        assertTrue(has(enemy, "stun"), "the empowered swing stuns");
        assertFalse(has(p, "dh_empowered"), "used up");

        t.time.advance(26);
        t.engine.statuses().remove(enemy, "stun");
        use(Slots.PRIMARY);
        assertFalse(has(enemy, "stun"), "the next one doesn't");
    }

    // ---- Dragonbone Slam -----------------------------------------------------------------------------------

    @Test
    void lmbSlamsEarlyForLessTheShorterTheWindUp() throws IOException {
        setup();
        UUID enemy = foe(3, 0);
        use(Slots.ABILITY_2);
        t.time.advance(10);
        assertTrue(t.engine.stats().moveSpeedMultiplier(p) < 0.9, "slower as he winds up");
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_ABILITY), "nothing else usable");
        assertEquals("dh_slam_early", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow(), "LMB: slam now");
        use(Slots.PRIMARY);
        t.time.advance(3);
        double dealt = t.damage(enemy);
        assertTrue(dealt > BASE * (0.6 + 0.03 * 10) - 1e-6 && dealt < BASE * (0.6 + 0.03 * 14), "60% + 3% a tick: " + dealt);
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_ABILITY), "slammed: over");
        t.time.advance(10);
        assertEquals(1, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "full speed again");
        assertEquals(dealt, t.damage(enemy), 1e-9, "one slam only");
    }

    @Test
    void fullyWoundUpItSlamsByItselfIgnoringArmor() throws IOException {
        setup();
        UUID enemy = armoredFoe(3, 0);
        use(Slots.ABILITY_2);
        t.time.advance(45);
        assertEquals(BASE * 2.0, t.damage(enemy), 1e-6, "200% base damage, armor ignored");
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_ABILITY), "over");
    }

    @Test
    void aStunCancelsTheWindUp() throws IOException {
        setup();
        UUID enemy = foe(3, 0);
        use(Slots.ABILITY_2);
        t.time.advance(10);
        t.engine.statuses().apply(p, "stun", enemy);
        t.time.advance(45);
        assertEquals(0, t.damage(enemy), 1e-9, "no slam");
    }

    // ---- Venom Dagger --------------------------------------------------------------------------------------

    @Test
    void theDaggerPoisonsEveryoneAroundAndReadiesTheLunge() throws IOException {
        setup();
        UUID front = foe(2, 0);
        UUID back = foe(-2, 1);
        use(Slots.ABILITY_1);
        t.time.advance(10);
        assertTrue(t.engine.cooldowns().remainingTicks(p, "dh_ab1") > 0);
        t.world.move(p, new Vec3(0, 1, 0));
        use(Slots.ABILITY_3);
        assertEquals(BASE * 0.6, t.damage(back), 1e-6, "all around: 60%");
        assertTrue(has(front, "dh_poison") && has(back, "dh_poison"), "poisoned");
        assertEquals(0.6, t.engine.stats().healingMultiplier(back), 1e-9, "40% less healing");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "dh_ab1"), "Hunter's Lunge is ready again");
        t.time.advance(81);
        assertEquals(BASE * 0.6 + 8 * 200 * 0.015, t.damage(back), 1e-6, "12% of their max HP over 4s");
    }

    // ---- Dragon Harpoon ------------------------------------------------------------------------------------

    private UUID harpoon() {
        UUID enemy = foe(10, 0);
        use(Slots.ULTIMATE);
        assertTrue(t.engine.tags().has(p, "state.dh_harpoon_drawn"), "the crossbow");
        t.time.advance(10);
        assertTrue(has(enemy, "dh_harpooned"), "harpooned");
        return enemy;
    }

    @Test
    void theHarpoonDisarmsAndHeavilySlowsWearingOff() throws IOException {
        setup();
        UUID enemy = harpoon();
        assertEquals(BASE * 0.8, t.damage(enemy), 1e-6);
        assertTrue(t.engine.tags().has(enemy, Tags.DISARMED), "disarmed");
        assertFalse(t.engine.tags().has(p, "state.dh_harpoon_drawn"), "back to the sword");
        double slowed = t.engine.stats().moveSpeedMultiplier(enemy);
        assertTrue(slowed < 0.45, "heavily slowed: " + slowed);
        t.time.advance(40);
        assertTrue(t.engine.stats().moveSpeedMultiplier(enemy) > slowed, "wearing off");
        t.time.advance(60);
        assertFalse(has(enemy, "dh_harpooned"), "5s");
        assertEquals(1, t.engine.stats().moveSpeedMultiplier(enemy), 1e-9);
    }

    @Test
    void belowTwentyPercentFAgainBlinksAndExecutes() throws IOException {
        setup();
        UUID enemy = harpoon();
        use(Slots.ULTIMATE); // healthy: nothing
        assertEquals(BASE * 0.8, t.damage(enemy), 1e-6, "too healthy: no execute");
        assertTrue(pos(p).x() < 2, "no blink");

        t.world.healthFraction.put(enemy, 0.15);
        use(Slots.ULTIMATE);
        assertTrue(Math.abs(pos(p).x() - 10) < 1, "blinked to them: " + pos(p));
        assertEquals(BASE * 0.8 + 200, t.damage(enemy), 1e-6, "executed: all of their max HP");
    }

    @Test
    void noExecuteOnceTheHarpoonIsOut() throws IOException {
        setup();
        UUID enemy = harpoon();
        t.time.advance(101);
        t.world.healthFraction.put(enemy, 0.1);
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        assertEquals(BASE * 0.8, t.damage(enemy), 1e-6, "too late");
    }
}

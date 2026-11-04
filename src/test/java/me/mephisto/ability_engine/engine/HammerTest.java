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
 * The Hammer, from the shipped hammer.yml (base damage 36, no spell power, no bonus HP). He stands at x=0 on a floor at
 * y=0, facing +x; enemies are on team red.
 */
class HammerTest {

    private static final double BASE = 36;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        var report = ShippedContent.realReport(t.engine);
        assertTrue(report.isClean(), () -> String.join("\n", report.errors()));
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.world.players.add(p);
        t.world.look(p, new Vec3(1, 0, 0));
        t.engine.loadouts().assign(p, "hammer");
        t.time.advance(1);
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private boolean use(String slot) { return t.engine.loadouts().activate(p, slot).success(); }

    private Vec3 where(UUID id) { return t.engine.world().positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private boolean stunned(UUID e) { return t.engine.tags().has(e, Tags.STUNNED); }

    // ---- primary ----

    @Test
    void hisSwingHitsEveryoneInFront() throws IOException {
        setup();
        UUID a = foe(3, 0), b = foe(2, 1.5), behind = foe(-2, 0);
        assertTrue(use(Slots.PRIMARY));
        t.time.advance(1);
        assertEquals(BASE, t.damage(a), 1e-6, "100% base damage");
        assertEquals(BASE, t.damage(b), 1e-6, "a wide swing: both");
        assertEquals(0, t.damage(behind), 1e-9);
        assertFalse(use(Slots.PRIMARY), "a slow one");
    }

    // ---- 1: Earthshatter ----

    @Test
    void aTappedSlamIsSmall() throws IOException {
        setup();
        UUID near = foe(3, 0), far = foe(6, 0);
        assertTrue(use(Slots.ABILITY_1));
        assertTrue(t.engine.tags().has(p, "state.slowed"), "slowed while charging");
        assertFalse(use(Slots.ABILITY_2), "nothing else meanwhile");
        t.time.advance(1);
        assertTrue(use(Slots.ABILITY_1), "1 again: slam");
        t.time.advance(1);
        assertEquals(BASE, t.damage(near), 1e-6, "a circle 1.75 wide, 1.75 ahead: reaches 3.5");
        assertEquals(0, t.damage(far), 1e-9);
        assertFalse(stunned(near), "not fully charged: no stun");
        assertFalse(t.engine.tags().has(p, "state.slowed"), "free again");
        assertFalse(t.engine.tags().has(p, "hammer.charging"), "(the circle's gone)");
    }

    @Test
    void leftClickSlamsToo() throws IOException {
        setup();
        UUID near = foe(3, 0);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(1);
        assertTrue(use(Slots.PRIMARY), "LMB while charging: slam");
        t.time.advance(1);
        assertEquals(BASE, t.damage(near), 1e-6);
        assertFalse(t.engine.tags().has(p, "hammer.charging"));
    }

    @Test
    void aFullSlamIsBigAndStuns() throws IOException {
        setup();
        UUID far = foe(6.5, 0);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(42); // full: it slams by itself
        assertEquals(BASE, t.damage(far), 1e-6, "3.5 wide, 3.5 ahead: reaches 7");
        assertTrue(stunned(far), "fully charged: stunned");
        t.time.advance(21);
        assertFalse(stunned(far), "1s");
    }

    // ---- passive: Crumbling Earth ----

    @Test
    void stonesRiseUnderThoseHitAndASwingSendsThemFlying() throws IOException {
        setup();
        foe(3, 0);
        foe(4, 1);
        foe(5, -1);
        foe(6, 0);
        UUID target = foe(13, 0);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(50); // (the stones come up one after another)
        assertEquals(3, t.engine.constructs().activeCount(), "a stone beside him for each of them, at most 3");
        for (var c : t.engine.constructs().all()) {
            assertTrue(c.position().x() < 2.5 && Math.abs(c.position().z()) < 2, "near him: " + c.position());
        }
        assertEquals(0, t.damage(target), 1e-9, "out of the slam's reach");

        for (int i = 0; i < 40 && !use(Slots.PRIMARY); i++) t.time.advance(1);
        t.time.advance(12);
        assertEquals(0, t.engine.constructs().activeCount(), "the swing sent them all flying");
        assertTrue(t.damage(target) >= BASE * 0.6 - 1e-6, "a stone flew 10 blocks into them: " + t.damage(target));
        assertTrue(stunned(target), "stunned");
    }

    @Test
    void nobodyHitNoStones() throws IOException {
        setup();
        assertTrue(use(Slots.ABILITY_1));
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(10);
        assertEquals(0, t.engine.constructs().activeCount());
    }

    @Test
    void aStoneFallsAfterFiveSeconds() throws IOException {
        setup();
        foe(3, 0);
        assertTrue(use(Slots.ABILITY_1));
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(2);
        assertEquals(1, t.engine.constructs().activeCount());
        t.time.advance(110);
        assertEquals(0, t.engine.constructs().activeCount(), "fell and broke");
    }

    // ---- 2: Rock Launch ----

    @Test
    void heLaunchesOntoTheSpotAndKnocksThemUp() throws IOException {
        setup();
        UUID e = foe(9, 0);
        t.world.look(p, new Vec3(8, -1, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
        for (int i = 0; i < 60 && t.damage(e) == 0; i++) t.time.advance(1);
        assertEquals(BASE * 0.8, t.damage(e), 1e-6, "80% base damage");
        assertTrue(t.knockbackVec.get(e).y() > 0.3, "knocked up a little");
        assertTrue(Math.abs(where(p).x() - 8) < 1.5, "he came down on the spot: " + where(p));
        assertEquals(1, t.engine.constructs().activeCount(), "a stone for them, beside him");
        t.time.advance(6);
        assertFalse(t.engine.tags().has(p, "state.safe_fall"), "landed: his tags gone");
        assertTrue(use(Slots.ABILITY_1), "free to act");
    }

    // ---- 3: Stonebreaker ----

    @Test
    void stonebreakerResetsTheSwingAndMakesItHeavy() throws IOException {
        setup();
        UUID e = foe(3, 0);
        assertTrue(use(Slots.PRIMARY));
        t.time.advance(1);
        assertFalse(use(Slots.PRIMARY), "on cooldown");
        assertTrue(use(Slots.ABILITY_3));
        assertTrue(t.engine.statuses().has(p, "hammer_empowered"));
        assertTrue(use(Slots.PRIMARY), "ready again at once");
        t.time.advance(1);
        assertEquals(BASE * 2 + 200 * 0.07, t.damage(e), 1e-6, "+ 7% of their max HP (200)");
        assertTrue(t.knockbackVec.get(e).x() > 0.5, "knocked back");
        assertFalse(t.engine.statuses().has(p, "hammer_empowered"), "used up");
    }

    // ---- ULTIMATE: Tectonic Slam ----

    @Test
    void theShockwaveThrowsThemHighAndAway() throws IOException {
        setup();
        t.engine.ultCharge().set(p, 1.0);
        UUID inLine = foe(10, 0), aside = foe(6, 5);
        assertTrue(use(Slots.ULTIMATE));
        assertFalse(use(Slots.PRIMARY), "no swings while he winds up");
        assertFalse(use(Slots.ABILITY_1));
        t.time.advance(10);
        assertEquals(0, t.damage(inLine), 1e-9, "still winding up");
        t.time.advance(16);
        assertEquals(BASE * 1.2, t.damage(inLine), 1e-6, "120% base damage");
        assertEquals(0, t.damage(aside), 1e-9, "a thin wave");
        var kb = t.knockbackVec.get(inLine);
        assertTrue(kb.y() > 1.0 && kb.x() > 1.0, "very high and away: " + kb);
        assertTrue(use(Slots.ABILITY_3), "free again");
    }
}

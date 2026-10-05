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

import static org.junit.jupiter.api.Assertions.*;

/** The Archmage's ultimate, Arcane Barrage (fixtures/archmage.yml). He starts at the origin on a floor. */
class ArcaneBarrageTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "archmage");
    }

    private UUID enemy(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private int charges() {
        return t.engine.statuses().find(p, "barrage_charges").map(s -> s.stacks()).orElse(0);
    }

    /** Ultimate: rise, then the kit changes. */
    private void ult() {
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        t.time.advance(20); // the rise
    }

    /** Hold RMB for {@code ticks}, then let go (it only fires when let go). */
    private void chargeAndFire(int ticks) {
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        t.time.advance(ticks);
        t.engine.instances().release(p);
        t.time.advance(10); // the shot flies
    }

    private void aimAt(Vec3 point) { t.world.look(p, point.subtract(pos(p))); }

    @Test
    void itRisesFliesAndSwapsTheKit() throws IOException {
        setup();
        assertEquals("arcane_bolt", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow());
        ult();
        assertTrue(pos(p).y() > 4, "up in the air: " + pos(p));
        assertTrue(t.engine.tags().has(p, Tags.FLYING));
        assertEquals("arcanist_barrage", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow(), "primary replaced");
        assertEquals("SPYGLASS", t.engine.loadouts().characterOf(p).orElseThrow().weapon());
        assertEquals("barrage_charges", t.engine.loadouts().characterOf(p).orElseThrow().statusBar().status(),
                "the XP bar shows the shots");
        assertEquals(3, charges());
        assertEquals("arcanist_ult1", t.engine.instances().timer(p).orElseThrow().ability().id(), "boss bar");
    }

    @Test
    void chargingRootsYouBlocksAbilitiesAndFillsTheCastBar() throws IOException {
        setup();
        ult();
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        t.time.advance(25);
        assertTrue(t.engine.instances().charging(p));
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE), "can't move");
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "can't use abilities");
        assertEquals(0.5, t.engine.instances().castProgress(p).orElseThrow(), 0.01, "half charged (2.5s to full)");
        assertEquals(3, charges(), "nothing spent yet");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "arcanist_barrage"),
                "no cooldown while charging (on the spyglass it would end the zoom)");

        t.engine.instances().release(p);
        assertFalse(t.engine.instances().charging(p));
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_MOVE), "free again once it's fired");
        assertEquals(2, charges());
        assertEquals(20, t.engine.cooldowns().remainingTicks(p, "arcanist_barrage"), "1s, from the shot");
    }

    @Test
    void aDirectHitDealsDamageByChargeWithoutExploding() throws IOException {
        setup();
        UUID target = enemy(20, 0);
        UUID beside = enemy(20, 2);
        ult();
        aimAt(pos(target));
        chargeAndFire(25); // half charged: 40% + 60% x 0.5 = 70%
        assertEquals(220 * 0.7, t.damage(target), 1e-6);
        assertEquals(0, t.damage(beside), 1e-9, "no explosion on a direct hit");
    }

    @Test
    void fullyChargedItWaitsForYouToLetGo() throws IOException {
        setup();
        UUID target = enemy(20, 0);
        ult();
        aimAt(pos(target));
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        t.time.advance(80); // held well past full
        assertTrue(t.engine.instances().charging(p), "still holding it, full");
        assertEquals(0, t.damage(target), 1e-9, "nothing fired yet");
        assertEquals(3, charges());
        t.engine.instances().release(p);
        t.time.advance(10);
        assertEquals(220, t.damage(target), 1e-6, "let go: full power");
        assertEquals(2, charges());
    }

    @Test
    void lettingGoTooSoonFiresNothingAndSpendsNothing() throws IOException {
        setup();
        UUID target = enemy(20, 0);
        ult();
        aimAt(pos(target));
        chargeAndFire(5); // under the 0.5s minimum
        assertEquals(0, t.damage(target), 1e-9);
        assertEquals(3, charges(), "no shot spent");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "arcanist_barrage"), "and no cooldown");
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_MOVE));
        chargeAndFire(10); // exactly the minimum: it fires
        assertTrue(t.damage(target) > 0);
        assertEquals(2, charges());
    }

    @Test
    void itExplodesOnTerrain() throws IOException {
        setup();
        UUID near = enemy(15, 1.5);
        UUID far = enemy(15, 6);
        ult();
        aimAt(new Vec3(15, 0, 0)); // the floor between them
        chargeAndFire(50);
        assertEquals(90, t.damage(near), 1e-6, "caught in the blast");
        assertEquals(0, t.damage(far), 1e-9);
    }

    @Test
    void theLastShotEndsTheUltimate() throws IOException {
        setup();
        ult();
        aimAt(new Vec3(10, 0, 0));
        for (int i = 0; i < 3; i++) {
            chargeAndFire(10);
            t.time.advance(10); // the rest of the 1s shot cooldown
        }
        assertEquals(0, charges());
        assertFalse(t.engine.instances().isRunning(p, "arcanist_ult1"), "over");
        assertFalse(t.engine.tags().has(p, Tags.FLYING));
        assertEquals("arcane_bolt", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow(), "the kit is back");
        assertEquals("BREEZE_ROD", t.engine.loadouts().characterOf(p).orElseThrow().weapon());
    }

    @Test
    void eliminationsGiveUpToTwoMoreShots() throws IOException {
        setup();
        ult();
        t.engine.notifyKill(p, UUID.randomUUID(), true);
        assertEquals(4, charges());
        t.engine.notifyKill(p, UUID.randomUUID(), false);
        assertEquals(5, charges());
        t.engine.notifyKill(p, UUID.randomUUID(), true);
        assertEquals(5, charges(), "no more than 2 extra");
    }

    @Test
    void whenTimeRunsOutUnusedShotsAreGone() throws IOException {
        setup();
        ult();
        t.time.advance(241);
        assertFalse(t.engine.instances().isRunning(p, "arcanist_ult1"));
        assertEquals(0, charges());
        assertEquals("arcane_bolt", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow());
    }

    @Test
    void chargingWhenTimeRunsOutFiresNothing() throws IOException {
        setup();
        UUID target = enemy(20, 0);
        ult();
        aimAt(pos(target));
        t.time.advance(215);
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        t.time.advance(40); // the ult ends meanwhile
        t.engine.instances().release(p);
        t.time.advance(10);
        assertEquals(0, t.damage(target), 1e-9);
    }

    @Test
    void theNextUltimateStartsWithThreeShotsAgain() throws IOException {
        setup();
        ult();
        t.engine.notifyKill(p, UUID.randomUUID(), true);
        t.engine.instances().cancelAll(p, "src/test"); // ended some other way, a shot left over
        t.engine.cooldowns().clear(p, "arcanist_ult1");
        ult();
        assertEquals(3, charges());
    }
}

package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.quiver.Bolt;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Hunter kit (fixtures/hunter.yml): quiver, infusions, Venom Step, Volatile Flask, Overdrive. */
class HunterTest {

    private static final Bolt PLAIN = Bolt.PLAIN;
    private static final Bolt POISON = new Bolt(List.of("poison"));

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        p = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(p, "hunter");
    }

    private List<Bolt> queue() { return t.engine.quivers().queue(p); }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    /** LMB, then let the bolt fly. */
    private void shoot() {
        assertTrue(t.engine.loadouts().activate(p, "primary").success());
        t.time.advance(10);
    }

    // ---- quiver ------------------------------------------------------------------------------

    @Test
    void startsWithThreePlainBoltsAndNothingLoaded() throws IOException {
        setup();
        assertEquals(List.of(PLAIN, PLAIN, PLAIN), queue());
        assertFalse(t.engine.quivers().isLoaded(p));
    }

    @Test
    void loadingTakesTheLeftmostBoltShiftsTheRestAndAddsAPlainOneOnTheRight() throws IOException {
        setup();
        t.engine.quivers().infuse(p, "poison", 1);
        t.engine.quivers().infuse(p, "slowness", 2);
        Bolt both = new Bolt(List.of("poison", "slowness"));
        Bolt slow = new Bolt(List.of("slowness"));
        assertEquals(List.of(both, slow, PLAIN), queue(), "one bolt can carry several infusions");

        assertTrue(t.engine.quivers().tryLoad(p));
        assertEquals(both, t.engine.quivers().loaded(p).orElseThrow());
        assertEquals(List.of(slow, PLAIN, PLAIN), queue());
        assertFalse(t.engine.quivers().tryLoad(p), "already loaded");
        assertEquals(List.of(slow, PLAIN, PLAIN), queue(), "a refused load moves nothing");
    }

    @Test
    void infusionsNeverTouchTheLoadedBolt() throws IOException {
        setup();
        t.engine.quivers().tryLoad(p);
        t.engine.quivers().infuse(p, "poison", 2);
        assertEquals(PLAIN, t.engine.quivers().loaded(p).orElseThrow());
        assertEquals(List.of(POISON, POISON, PLAIN), queue());
    }

    @Test
    void cantReloadWhileStunned() throws IOException {
        setup();
        t.engine.statuses().apply(p, "stun", 20, null);
        assertFalse(t.engine.quivers().canLoad(p));
        assertFalse(t.engine.quivers().tryLoad(p));
        t.time.advance(21);
        assertTrue(t.engine.quivers().tryLoad(p));
    }

    @Test
    void aNewCharacterGetsAFreshQuiver() throws IOException {
        setup();
        t.engine.quivers().tryLoad(p);
        t.engine.quivers().infuse(p, "poison", 3);
        t.engine.loadouts().assign(p, "hunter");
        assertFalse(t.engine.quivers().isLoaded(p));
        assertEquals(List.of(PLAIN, PLAIN, PLAIN), queue());
        t.engine.loadouts().clear(p);
        assertEquals(List.of(), queue(), "no character, no quiver");
        assertFalse(t.engine.quivers().tryLoad(p));
    }

    // ---- primary + passive ---------------------------------------------------------------------

    @Test
    void shootingWithNothingLoadedIsADryClick() throws IOException {
        setup();
        UUID target = t.spawn(10, 1, 0);
        shoot();
        assertEquals(0, t.damage(target), 1e-9);
        assertEquals(0, t.render.spawned, "no bolt flew");
        assertTrue(t.render.cues.contains("dry_fire"));
    }

    @Test
    void aLoadedBoltHitsAndEmptiesTheCrossbow() throws IOException {
        setup();
        UUID target = t.spawn(10, 1, 0);
        t.engine.quivers().tryLoad(p);
        shoot();
        assertEquals(45, t.damage(target), 1e-9);
        assertFalse(t.engine.quivers().isLoaded(p));
    }

    @Test
    void infusedBoltsApplyTheirInfusions() throws IOException {
        setup();
        UUID target = t.spawn(10, 1, 0);
        t.engine.quivers().infuse(p, "poison", 1);
        t.engine.quivers().infuse(p, "slowness", 1);
        t.engine.quivers().tryLoad(p);
        shoot();
        assertTrue(t.engine.statuses().has(target, "poisoned"));
        assertTrue(t.engine.tags().has(target, Tags.SLOWED), "slowness infusion");
        double onImpact = t.damage(target);
        t.time.advance(80);
        assertEquals(40, t.damage(target) - onImpact, 1e-9, "poison: 40 over 4s");
    }

    @Test
    void plainBoltsCarryNoInfusions() throws IOException {
        setup();
        UUID target = t.spawn(10, 1, 0);
        t.engine.quivers().infuse(p, "poison", 1);
        t.engine.quivers().tryLoad(p); // the poisoned one
        shoot();
        t.engine.quivers().tryLoad(p); // a plain one
        t.engine.statuses().clear(target);
        t.time.advance(10); // primary cooldown
        shoot();
        assertFalse(t.engine.statuses().has(target, "poisoned"));
    }

    @Test
    void aFiredBoltIsTintedByItsInfusions() throws IOException {
        setup();
        t.engine.quivers().infuse(p, "poison", 1);
        t.engine.quivers().infuse(p, "slowness", 2);
        for (int i = 0; i < 3; i++) {
            t.engine.quivers().tryLoad(p);
            shoot();
        }
        // poison + slowness mixed (#4E9331 and #5A6C81 averaged), then slowness alone, then a plain bolt
        assertEquals(java.util.Arrays.asList("#547F59", "#5A6C81", null), t.render.tints);
    }

    @Test
    void hitsSpeedUpReloadingUpToThreeTimesThenItFades() throws IOException {
        setup();
        UUID target = t.spawn(10, 1, 0);
        assertEquals(0, t.engine.quivers().reloadSpeed(p));
        for (int i = 1; i <= 4; i++) {
            t.engine.quivers().tryLoad(p);
            shoot();
            assertEquals(Math.min(i, 3), t.engine.quivers().reloadSpeed(p), "after hit " + i);
        }
        t.time.advance(101);
        assertEquals(0, t.engine.quivers().reloadSpeed(p), "5s without a hit: back to normal");
        assertTrue(t.damage(target) > 0);
    }

    @Test
    void missesDontCountForTheRhythm() throws IOException {
        setup();
        t.engine.quivers().tryLoad(p);
        shoot();
        assertEquals(0, t.engine.quivers().reloadSpeed(p));
    }

    // ---- Venom Step ------------------------------------------------------------------------------

    @Test
    void venomStepDashesWhereYouWalkNotWhereYouLook() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, 0, 0));
        t.world.walk(p, new Vec3(0, 0, 1)); // strafing sideways
        t.engine.loadouts().activate(p, "ability_1");
        t.time.advance(10);
        Vec3 at = pos(p);
        assertTrue(at.z() >= 5.5, "dashed sideways: " + at);
        assertEquals(0, at.x(), 1e-6, "not forward");
    }

    @Test
    void venomStepGoesForwardWhenStandingStill() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, 1, 0)); // looking up: still a ground dash
        t.engine.loadouts().activate(p, "ability_1");
        t.time.advance(10);
        Vec3 at = pos(p);
        assertTrue(at.x() >= 5.5, "forward: " + at);
        assertEquals(1, at.y(), 1e-6, "flat");
    }

    @Test
    void venomStepReloadsThenPoisonsTheNextThreeQueuedBolts() throws IOException {
        setup();
        t.engine.loadouts().activate(p, "ability_1");
        assertEquals(PLAIN, t.engine.quivers().loaded(p).orElseThrow(), "reloaded with the bolt that was next");
        assertEquals(List.of(POISON, POISON, POISON), queue());
    }

    @Test
    void venomStepWhenAlreadyLoadedStillPoisons() throws IOException {
        setup();
        t.engine.quivers().tryLoad(p);
        t.engine.loadouts().activate(p, "ability_1");
        assertEquals(PLAIN, t.engine.quivers().loaded(p).orElseThrow());
        assertEquals(List.of(POISON, POISON, POISON), queue(), "nothing shifted");
    }

    @Test
    void venomStepOnlyTurnsYouInvisibleDuringOverdrive() throws IOException {
        setup();
        t.engine.loadouts().activate(p, "ability_1");
        assertFalse(t.engine.tags().has(p, Tags.INVISIBLE));
        t.time.advance(200);

        t.engine.loadouts().activate(p, "ultimate");
        t.engine.loadouts().activate(p, "ability_1");
        assertTrue(t.engine.tags().has(p, Tags.INVISIBLE));
        t.time.advance(51);
        assertFalse(t.engine.tags().has(p, Tags.INVISIBLE), "2.5s");
    }

    // ---- Volatile Flask --------------------------------------------------------------------------

    @Test
    void theFlaskBouncesOnceThenBurstsDamagingAndSlowing() throws IOException {
        setup();
        t.world.floor(0);
        // A lob at ~17 degrees: first touches the ground near x=21, bounces, and lands near x=29.
        UUID atTheBounce = t.spawn(21, 0.5, 2.5);
        UUID atTheLanding = t.spawn(29.5, 1, 1.5);
        t.world.look(p, new Vec3(1, 0.3, 0));
        t.engine.loadouts().activate(p, "ability_2");
        t.time.advance(40); // bursts after ~25 ticks; the 3s slow is still on
        assertEquals(0, t.damage(atTheBounce), 1e-9, "the first bounce doesn't burst");
        assertEquals(50, t.damage(atTheLanding), 1e-9);
        assertTrue(t.engine.tags().has(atTheLanding, Tags.SLOWED));
    }

    @Test
    void theFlaskBurstsOnTheFirstEnemyItHits() throws IOException {
        setup();
        UUID enemy = t.spawn(6, 1, 0);
        t.engine.loadouts().activate(p, "ability_2");
        t.time.advance(20);
        assertEquals(50, t.damage(enemy), 1e-9);
    }

    @Test
    void burstingItOnYourselfBuffsYouAndInfusesSlownessIntoTheNextBolt() throws IOException {
        setup();
        t.world.floor(0);
        t.world.look(p, new Vec3(0.05, -1, 0)); // straight at your feet
        t.engine.loadouts().activate(p, "ability_2");
        t.time.advance(40);
        assertEquals(0, t.damage(p), 1e-9, "your own flask doesn't hurt you");
        assertTrue(t.engine.tags().has(p, Tags.HASTED), "speed");
        t.time.advance(60);
        assertTrue(t.healed.getOrDefault(p, 0.0) > 0, "a little regen");
        assertEquals(new Bolt(List.of("slowness")), queue().get(0));
        assertEquals(PLAIN, queue().get(1), "only the next one");
    }

    @Test
    void aFlaskFarFromYouDoesntBuffYou() throws IOException {
        setup();
        t.world.floor(0);
        t.world.look(p, new Vec3(1, 0.3, 0));
        t.engine.loadouts().activate(p, "ability_2");
        t.time.advance(100);
        assertFalse(t.engine.tags().has(p, Tags.HASTED));
        assertEquals(List.of(PLAIN, PLAIN, PLAIN), queue());
    }

    // ---- Overdrive ---------------------------------------------------------------------------------

    @Test
    void overdriveMaxesReloadSpeedAndSpeedsYouUpFor8Seconds() throws IOException {
        setup();
        t.engine.loadouts().activate(p, "ultimate");
        assertEquals(4, t.engine.quivers().reloadSpeed(p));
        assertTrue(t.engine.tags().has(p, Tags.HASTED));
        t.time.advance(161);
        assertEquals(0, t.engine.quivers().reloadSpeed(p));
        assertFalse(t.engine.tags().has(p, Tags.HASTED));
    }

    // ---- loading ----------------------------------------------------------------------------------

    @Test
    void quiverMistakesAreReportedAtLoad() {
        TestEngine t = new TestEngine();
        LoadReport report = new me.mephisto.ability_engine.engine.data.AbilityLoader(t.engine).load(map(
                "infusions", map("bad_color", map("color", "green", "on_hit", list(map("id", "status", "status", "stun")))),
                "abilities", map("x", map("nodes", map("i", map("type", "infuse", "infusion", "nope")))),
                "characters", map("c", map("slots", map(), "quiver", map("size", 3, "hotbar", 8)))), "test");
        String errors = String.join("\n", report.errors());
        assertTrue(errors.contains("color: expected #RRGGBB"), errors);
        assertTrue(errors.contains("unknown infusion 'nope'"), errors);
        assertTrue(errors.contains("don't fit in the hotbar"), errors);
    }
}

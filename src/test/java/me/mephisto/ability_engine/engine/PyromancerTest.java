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
 * The Pyromancer (fixtures/pyromancer.yml): 180 HP, no armor, base damage 30, 2 Fire Bolts a second. She stands at
 * the origin on a floor, looking +x, team blue. Enemies are team red (no sheet: 200 HP, no armor).
 */
class PyromancerTest {

    private static final double BASE = 30;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "pyromancer");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success(), slot); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private int heat() { return t.engine.statuses().find(p, "pyro_heat").map(s -> s.stacks()).orElse(0); }

    private void fullHeat() {
        for (int i = 0; i < 15; i++) t.engine.statuses().apply(p, "pyro_heat", p);
        assertEquals(15, heat());
    }

    private long count(String cue) { return t.render.cues.stream().filter(cue::equals).count(); }

    /** In orange or blue (she may overheat midway). */
    private long either(String cue) { return count(cue) + count(cue + "_blue"); }

    private boolean overheated() { return t.engine.tags().has(p, "state.overheated"); }

    /** Fire a bolt at whoever stands ahead and let it land (and the primary come back). */
    private void bolt() {
        use(Slots.PRIMARY);
        t.time.advance(10);
    }

    // ---- Fire Bolt and Overheat ---------------------------------------------------------------------------

    @Test
    void aFireBoltHitsAndHeatsHerUp() throws IOException {
        setup();
        UUID enemy = foe(6, 0);
        use(Slots.PRIMARY);
        t.time.advance(5);
        assertEquals(BASE, t.damage(enemy), 1e-6, "100% base damage");
        assertEquals(1, heat());
        assertEquals(1, count("pyro_bolt_hit"));
    }

    @Test
    void aMissDoesntHeatHerUp() throws IOException {
        setup();
        use(Slots.PRIMARY);
        t.time.advance(30);
        assertEquals(0, heat());
    }

    @Test
    void fifteenHitsFillTheGaugeAndNoMore() throws IOException {
        setup();
        foe(6, 0);
        for (int i = 0; i < 14; i++) bolt();
        assertEquals(14, heat());
        assertFalse(overheated());
        bolt();
        assertEquals(15, heat());
        bolt();
        assertEquals(15, heat(), "15 at most");
        var gauge = t.engine.statuses().stackGauge(p, "pyro_heat").orElseThrow();
        assertEquals(1.0, gauge.fraction(), 1e-9, "the XP bar is full");
    }

    @Test
    void threeSecondsWithoutAHitItCoolsAStackAtATime() throws IOException {
        setup();
        foe(6, 0);
        bolt();
        bolt();
        bolt(); // the last one landed ~5 ticks into this one
        assertEquals(3, heat());
        t.time.advance(50);
        assertEquals(3, heat(), "not yet");
        t.time.advance(6);
        assertEquals(2, heat(), "3s: a stack off...");
        t.time.advance(4);
        assertEquals(1, heat(), "...every 0.2s");
        t.time.advance(4);
        assertEquals(0, heat());
    }

    @Test
    void hittingAgainStopsTheCooling() throws IOException {
        setup();
        foe(6, 0);
        for (int i = 0; i < 4; i++) bolt();
        t.time.advance(57); // the last hit was ~5 ticks in: 62 ticks since, cooling: one off
        assertEquals(3, heat());
        bolt(); // (another stack cools off while it flies)
        int now = heat();
        assertTrue(now >= 3, "a stack back on: " + now);
        t.time.advance(40);
        assertEquals(now, heat(), "the 3s start over");
    }

    // ---- Overheat ----------------------------------------------------------------------------------------

    @Test
    void aFullGaugeOverheatsHer() throws IOException {
        setup();
        foe(6, 0);
        for (int i = 0; i < 14; i++) bolt();
        assertFalse(overheated());
        bolt();
        assertTrue(overheated(), "15: overheated");
        assertTrue(t.render.loops.stream().anyMatch(id -> id.startsWith("pyro_overheat")), "soul flames around her");
        assertEquals(1.25, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "25% faster");
        use(Slots.PRIMARY);
        assertEquals(3, t.engine.cooldowns().remainingTicks(p, "pyro_primary"), "a bolt every 3 ticks");
    }

    @Test
    void overheatedHerHitsDealTheSameAndDontHeal() throws IOException {
        setup();
        UUID enemy = foe(6, 0);
        fullHeat();
        assertTrue(overheated());
        use(Slots.PRIMARY);
        t.time.advance(5);
        assertEquals(BASE, t.damage(enemy), 1e-6, "no extra damage");
        assertFalse(t.lifesteal.containsKey(p), "no healing");
        assertEquals(0, t.damage(p), "and it doesn't burn her");
    }

    @Test
    void sheStaysOverheatedWhileSheKeepsHitting() throws IOException {
        setup();
        foe(6, 0);
        fullHeat();
        for (int i = 0; i < 30; i++) { // 10s of bolts, a hit every 0.5s
            use(Slots.PRIMARY);
            t.time.advance(10);
        }
        assertTrue(overheated());
        assertEquals(15, heat());
    }

    @Test
    void itLastsUntilTheGaugeIsEmpty() throws IOException {
        setup();
        fullHeat();
        t.time.advance(61);
        assertEquals(14, heat(), "cooling...");
        assertTrue(overheated(), "...still overheated below full");
        t.time.advance(52);
        assertEquals(1, heat());
        assertTrue(overheated());
        t.time.advance(4);
        assertEquals(0, heat());
        assertFalse(overheated(), "empty: over");
        assertTrue(t.render.loops.isEmpty(), "her flames go out");
        use(Slots.PRIMARY);
        assertEquals(10, t.engine.cooldowns().remainingTicks(p, "pyro_primary"), "back to normal");
    }

    @Test
    void overheatedHerFlamesAreBlue() throws IOException {
        setup();
        foe(6, 0);
        bolt();
        assertEquals("block:FIRE", t.render.visuals.get(0));
        assertEquals(1, count("pyro_bolt_hit"));
        fullHeat();
        bolt();
        assertEquals("block:SOUL_FIRE", t.render.visuals.get(1), "a soul fire bolt");
        assertEquals(1, count("pyro_bolt_hit_blue"));
        assertEquals(1, count("pyro_bolt_hit"), "not an orange one");
        t.engine.statuses().remove(p, "pyro_heat"); // cooled right down
        bolt();
        assertEquals("block:FIRE", t.render.visuals.get(2), "orange again after");
    }

    @Test
    void aCueWithNoBlueVersionPlaysAsItIs() throws IOException {
        setup();
        foe(6, 0);
        t.render.knownCues = java.util.Set.of("pyro_bolt_hit", "pyro_bolt_cast", "pyro_bolt_trail");
        fullHeat();
        bolt();
        assertEquals(1, count("pyro_bolt_hit"));
        assertEquals(0, count("pyro_bolt_hit_blue"));
    }

    @Test
    void anyOfHerHitsHeatsHerUp() throws IOException {
        setup();
        foe(6, 0);
        foe(7, 2);
        use(Slots.ABILITY_1); // a Fireball on two
        t.time.advance(10);
        assertEquals(2, heat(), "a stack per enemy hit");
    }

    @Test
    void burningDoesntHeatHerUp() throws IOException {
        setup();
        foe(6, 0);
        use(Slots.ABILITY_1);
        t.time.advance(10);
        assertEquals(1, heat());
        t.time.advance(50); // the burn ticks on
        assertEquals(1, heat());
    }

    // ---- 1: Fireball --------------------------------------------------------------------------------------

    @Test
    void theFireballExplodesBurnsAndKnocksBack() throws IOException {
        setup();
        UUID hit = foe(6, 0);
        UUID beside = foe(7, 2);
        UUID farOff = foe(6, 7);
        use(Slots.ABILITY_1);
        t.time.advance(10);
        assertEquals(1.6 * BASE, t.damage(hit), 1e-6);
        assertEquals(1.6 * BASE, t.damage(beside), 1e-6, "within 3.5 blocks");
        assertEquals(0, t.damage(farOff));
        assertTrue(has(hit, "pyro_burn") && has(beside, "pyro_burn"), "burning");
        assertTrue(t.engine.tags().has(hit, Tags.BURNING));
        assertTrue(t.knockback.containsKey(beside), "knocked back");
        assertEquals(1, count("pyro_fireball_explode"));
    }

    @Test
    void theBurnDealsTwelvePercentOverThreeSeconds() throws IOException {
        setup();
        UUID enemy = foe(6, 0);
        use(Slots.ABILITY_1);
        t.time.advance(10);
        double afterBlast = t.damage(enemy);
        t.time.advance(70);
        assertEquals(0.12 * 200, t.damage(enemy) - afterBlast, 1e-6, "6 ticks of 2% max HP");
        assertFalse(has(enemy, "pyro_burn"));
    }

    // ---- 2: Hunting Wisp ----------------------------------------------------------------------------------

    /** Send the wisp to the floor 10 blocks ahead (the crosshair on it), and let it get there. */
    private void sendWisp() {
        t.world.look(p, new Vec3(1, -0.1, 0));
        assertTrue(t.engine.activator().activate(p, "pyro_ab2").openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
        t.time.advance(30);
    }

    private Vec3 wisp() { return t.engine.projectiles().latest(p, "pyro_ab2").orElseThrow().position(); }

    /** Where the crosshair puts it: the floor 10 blocks ahead, backed off 0.3 toward her (AimPoint). */
    private static final double SPOT_X = 9.7;

    @Test
    void theWispFliesToTheSpotAndWaits() throws IOException {
        setup();
        sendWisp();
        assertEquals(SPOT_X, wisp().x(), 0.05);
        assertEquals(1.2, wisp().y(), 0.05, "floating above the ground");
        t.time.advance(200);
        assertEquals(SPOT_X, wisp().x(), 0.05, "still waiting");
    }

    @Test
    void anEnemyComingNearIsHuntedDownGlowingAndBlownUp() throws IOException {
        setup();
        sendWisp();
        UUID enemy = foe(25, 0);
        t.time.advance(20);
        assertEquals(SPOT_X, wisp().x(), 0.05, "15 blocks off: not seen");
        t.world.move(enemy, new Vec3(16, 1, 0)); // 6 blocks: seen
        t.time.advance(2);
        assertTrue(wisp().x() > SPOT_X + 0.5, "after them");
        assertTrue(has(enemy, "glowing"), "they glow while it hunts them");
        assertEquals(0, t.damage(enemy));
        t.world.move(enemy, new Vec3(18, 1, 2)); // running
        t.time.advance(30);
        double burnt = t.damage(enemy) - 1.4 * BASE; // the burn ticks on after it
        assertTrue(burnt >= 0 && burnt <= 0.12 * 200, "caught: 140% base damage, then the burn: " + t.damage(enemy));
        assertTrue(has(enemy, "pyro_burn"));
        assertEquals(1, count("pyro_wisp_explode"));
    }

    @Test
    void itKeepsAfterTheFirstOneItFound() throws IOException {
        setup();
        sendWisp();
        UUID first = foe(16, 0);
        t.time.advance(2); // after them
        UUID second = foe(9, 0); // now nearer to it
        t.time.advance(30);
        assertTrue(t.damage(first) >= 1.4 * BASE, "caught (and burning)");
        assertEquals(0, t.damage(second), "never turned around");
        assertFalse(has(second, "glowing"));
    }

    @Test
    void nobodyComesItFadesAfterThirtySeconds() throws IOException {
        setup();
        sendWisp();
        t.time.advance(560);
        assertTrue(t.engine.projectiles().latest(p, "pyro_ab2").isPresent(), "still waiting at ~29s");
        t.time.advance(60);
        assertTrue(t.engine.projectiles().latest(p, "pyro_ab2").isEmpty(), "gone");
        assertEquals(1, count("pyro_wisp_fade"));
    }

    // ---- 3: Hot Coals --------------------------------------------------------------------------------------

    /** A jump: up in the air for a few ticks, then down again where she is. */
    private void jump() {
        Vec3 at = pos(p);
        t.world.move(p, at.add(0, 2.5, 0));
        t.time.advance(6);
        t.world.move(p, at);
        t.time.advance(1);
    }

    @Test
    void hotCoalsGiveHerJumpBoostForSixSeconds() throws IOException {
        setup();
        use(Slots.ABILITY_3);
        assertTrue(t.engine.tags().has(p, "state.jump_boost"));
        assertTrue(t.render.loops.stream().anyMatch(id -> id.startsWith("pyro_coals")), "juggling them");
        t.time.advance(120);
        assertFalse(t.engine.tags().has(p, "state.jump_boost"), "6s");
        assertTrue(t.render.loops.isEmpty(), "the coals are gone with it");
    }

    @Test
    void walkingAboutScorchesNothing() throws IOException {
        setup();
        use(Slots.ABILITY_3);
        t.world.move(p, new Vec3(3, 1, 0));
        t.time.advance(20);
        assertEquals(0, count("pyro_coal_scorch"));
    }

    @Test
    void eachLandingScorchesTheGroundSlowingAndBurningEnemiesInIt() throws IOException {
        setup();
        use(Slots.ABILITY_3);
        jump();
        assertEquals(1, count("pyro_coal_scorch"));
        UUID enemy = foe(4, 0);
        t.time.advance(10);
        assertFalse(has(enemy, "pyro_scorched"), "4 blocks away: not in it");
        t.world.move(enemy, new Vec3(1, 1, 0)); // walks in
        t.time.advance(10);
        assertTrue(has(enemy, "pyro_scorched"), "slowed");
        assertEquals(0.65, t.engine.stats().moveSpeedMultiplier(enemy), 1e-9);
        assertTrue(has(enemy, "pyro_burn"), "burning");
        assertTrue(heat() >= 1, "it heats her up too");
        t.world.move(enemy, new Vec3(6, 1, 0)); // out again
        t.time.advance(20);
        assertFalse(has(enemy, "pyro_scorched"), "not slowed once out");
    }

    @Test
    void everyJumpLeavesItsOwnPatchForFourSeconds() throws IOException {
        setup();
        use(Slots.ABILITY_3);
        jump();
        t.world.move(p, new Vec3(5, 1, 0));
        jump();
        assertEquals(2, count("pyro_coal_scorch"));
        UUID first = foe(0, 0.5);
        UUID second = foe(5, 0.5);
        t.time.advance(10);
        assertTrue(has(first, "pyro_scorched") && has(second, "pyro_scorched"), "both patches");
        t.time.advance(80);
        long patches = count("pyro_coal_patch");
        t.time.advance(20);
        assertEquals(patches, count("pyro_coal_patch"), "4s each: gone");
        assertEquals(16, patches, "8 x 0.5s each");
    }

    @Test
    void afterSixSecondsLandingsDontScorch() throws IOException {
        setup();
        use(Slots.ABILITY_3);
        t.time.advance(121);
        jump();
        assertEquals(0, count("pyro_coal_scorch"));
    }

    // ---- F: Scorching Judgment ----------------------------------------------------------------------------

    /** Mark the floor 10 blocks ahead. */
    private void judgment() {
        t.world.look(p, new Vec3(1, -0.1, 0));
        assertTrue(t.engine.activator().activate(p, "pyro_ult1").openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
    }

    @Test
    void theMeteorFallsOnTheMarkedAreaAfterAWarning() throws IOException {
        setup();
        UUID inside = foe(13, 2);   // ~3.6 from the spot
        UUID outside = foe(18, 0);  // 8 away
        judgment();
        t.time.advance(30);
        assertTrue(count("pyro_judgment_ring") >= 5, "everyone sees where it'll land");
        assertEquals(0, t.damage(inside), "not yet");
        t.time.advance(15);
        assertEquals(1, count("pyro_meteor_impact"), "~2s");
        assertEquals(3.0 * BASE, t.damage(inside), 0.2 * BASE + 1e-6, "300% base damage (and the first of the scorch)");
        assertTrue(has(inside, "pyro_burn"));
        assertTrue(t.knockback.containsKey(inside));
        assertEquals(0, t.damage(outside));
    }

    @Test
    void theGroundStaysScorchedForEightSeconds() throws IOException {
        setup();
        UUID enemy = foe(12, 0);
        judgment();
        t.time.advance(45);
        assertEquals(1, count("pyro_meteor_impact"));
        long rings = either("pyro_judgment_ring");
        t.time.advance(200);
        assertEquals(16, either("pyro_scorched"), "16 x 0.5s");
        assertEquals(rings + 15, either("pyro_judgment_ring"), "its edge keeps showing");
        assertTrue(count("pyro_scorched_blue") > 0, "its hits overheated her on the way (1 + 14 of them)");
        assertTrue(has(enemy, "pyro_burn") || t.damage(enemy) > 3.0 * BASE + 16 * 0.15 * BASE, "burnt all along");
        assertTrue(t.damage(enemy) >= 3.0 * BASE + 16 * 0.15 * BASE, "the blast and 16 scorches");
        t.time.advance(100);
        assertEquals(16, either("pyro_scorched"), "then it's gone");
        assertEquals(0, t.engine.instances().of(p).size(), "the ultimate is over");
    }

    @Test
    void theMeteorComesFromTheSkyNotFromHer() throws IOException {
        setup();
        judgment();
        t.time.advance(1);
        var meteor = t.engine.projectiles().latest(p, "pyro_ult1").orElseThrow();
        assertTrue(meteor.position().y() > 20, "high up: " + meteor.position());
        assertTrue(meteor.velocity().y() < 0, "falling");
        assertEquals("block:MAGMA_BLOCK", t.render.visuals.get(0));
    }

    // ---- The kit ------------------------------------------------------------------------------------------

    @Test
    void theOverheatGaugeIsOnTheXpBar() throws IOException {
        setup();
        var bar = t.engine.loadouts().characterOf(p).orElseThrow().statusBar();
        assertEquals("pyro_heat", bar.status());
        assertEquals(me.mephisto.ability_engine.engine.loadout.CharacterDef.StatusBar.Fill.STACKS, bar.fill());
    }

    @Test
    void anotherCharacterKeepsOrangeFlamesWhateverTheyHave() throws IOException {
        setup();
        UUID other = t.spawn(0, 1, 5);
        t.engine.tags().grant(other, "state.overheated");
        assertEquals("pyro_bolt_hit", t.engine.cueFor(other, "pyro_bolt_hit"), "no character: no variants");
        assertEquals("block:FIRE", t.engine.visualFor(other, "block:FIRE"));
        assertEquals(new Vec3(0, 1, 5), pos(other));
    }
}

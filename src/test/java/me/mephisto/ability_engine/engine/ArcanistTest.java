package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reworked Arcanist (fixtures/arcanist.yml): base damage 28; his bolt bursts for 50% of it + 50% spell power. He stands at the origin on a floor, looking +x, team blue;
 * enemies are red (no sheet: no armor).
 */
class ArcanistTest {

    private static final double BASE = 28;
    /** A bolt's burst with no spell power. */
    private static final double BOLT = BASE * 0.5;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "arcanist");
        t.time.advance(4);
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private boolean use(String slot) { return t.engine.loadouts().activate(p, slot).success(); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private int stacks(UUID id, String status) {
        return t.engine.statuses().find(id, status).map(s -> s.stacks()).orElse(0);
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    /** Fire a bolt and let it land (cooldown cleared first). */
    private void bolt() {
        t.engine.cooldowns().clear(p, "arcanist_primary");
        assertTrue(use(Slots.PRIMARY));
        t.time.advance(10);
    }

    // ---- primary: Arcane Bolt ----

    @Test
    void theBoltBurstsOnWhoeverItHitsAndRightNextToThem() throws IOException {
        setup();
        UUID hit = foe(6, 0);
        UUID beside = foe(6, 0.8);
        UUID far = foe(6, 2.5);
        bolt();
        assertEquals(BOLT, t.damage(hit), 1e-6);
        assertEquals(BOLT, t.damage(beside), 1e-6, "within the 1-block burst");
        assertEquals(0, t.damage(far), 1e-9, "not a big area");
        assertTrue(t.render.cues.contains("arcanist_bolt_burst"));
    }

    @Test
    void theBoltScalesHalfWithSpellPower() throws IOException {
        setup();
        t.load(map("statuses", map("tome", map("spell_power", 40))));
        t.engine.statuses().apply(p, "tome", 0, p);
        UUID e = foe(6, 0);
        bolt();
        assertEquals(BASE * 0.5 + 40 * 0.5, t.damage(e), 1e-6);
    }

    @Test
    void theBoltBurstsOnTerrainToo() throws IOException {
        setup();
        t.world.box(6, 7, -3, 3, 10);  // a wall
        UUID byTheWall = foe(5.5, 0.6);
        t.world.look(p, new Vec3(1, 0, -0.02)); // (just past them, into the wall)
        bolt();
        assertTrue(t.damage(byTheWall) > 0, "the burst at the wall caught them: ");
    }

    // ---- passive: Arcane Barrage ----

    @Test
    void sixHitsEnterBarrageModeWithBiggerBursts() throws IOException {
        setup();
        UUID e = foe(6, 0);
        for (int i = 0; i < 5; i++) bolt();
        assertEquals(5, stacks(p, "arcane_charge"));
        assertFalse(t.engine.tags().has(p, "arcanist.barrage"));
        bolt();
        assertTrue(t.engine.tags().has(p, "arcanist.barrage"), "6 hits: Barrage mode");
        assertEquals(0, stacks(p, "arcane_charge"), "the charges are spent");
        assertTrue(t.render.loops.contains("arcanist_barrage_mode") || t.render.started.stream()
                .anyMatch(s -> "arcanist_barrage_mode".equals(s[0])), "enemies can see it");

        UUID aside = foe(6, 2);   // 2 blocks off: outside a normal burst, inside a Barrage one
        double before = t.damage(aside);
        bolt();
        assertEquals(BOLT, t.damage(aside) - before, 1e-6, "the burst is wider in Barrage mode");
        assertTrue(t.render.cues.contains("arcanist_bolt_burst_big"));
        assertEquals(0, stacks(p, "arcane_charge"), "no charges during it");
        t.time.advance(100);
        assertFalse(t.engine.tags().has(p, "arcanist.barrage"), "5s and it's over");
    }

    @Test
    void aBoltIntoTheGroundChargesNothing() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -1, 0));
        bolt();
        assertEquals(0, stacks(p, "arcane_charge"));
    }

    // ---- 1: Arcane Missile ----

    @Test
    void theMissileMarksAndBoltsCurveTowardTheMarked() throws IOException {
        setup();
        UUID e = foe(10, 0);
        // A bolt aimed a little to the side of them misses...
        Vec3 aside = new Vec3(10, 0, 1.0).normalize(); // passes a block to the side of them
        t.world.look(p, aside);
        bolt();
        assertEquals(0, t.damage(e), 1e-9, "missed: nobody marked");
        // ...the missile marks them...
        t.world.look(p, new Vec3(1, 0, 0));
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(40);
        assertEquals(BASE, t.damage(e), 1e-6);
        assertTrue(has(e, "arcanist_mark"));
        assertTrue(t.engine.tags().has(e, "state.glowing"), "glowing");
        assertEquals(2, stacks(p, "arcane_charge"), "a missile hit is 2 charges");
        // ...and the same bolt now curves into them.
        t.world.look(p, aside);
        double before = t.damage(e);
        bolt();
        assertEquals(BOLT, t.damage(e) - before, 1e-6, "the bolt curved into the marked enemy");
        t.time.advance(80);
        assertFalse(has(e, "arcanist_mark"), "4s");
    }

    @Test
    void recastInFlightItTurnsToTheCrosshairEmpowered() throws IOException {
        setup();
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(5);
        UUID e = foe(4, 5);
        t.world.look(p, new Vec3(4, 0, 5));
        assertTrue(use(Slots.ABILITY_1), "the recast, mid-flight");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "arcanist_ab1"), "not before it's all over");
        for (int i = 0; i < 30 && !t.engine.tags().has(e, Tags.STUNNED); i++) t.time.advance(1);
        assertTrue(t.engine.tags().has(e, Tags.STUNNED), "empowered: stunned");
        assertTrue(has(e, "arcanist_mark"));
        assertEquals(BASE * 1.65 + 200 * 0.1, t.damage(e), 1e-6, "and it exploded");
        t.time.advance(1);
        assertTrue(t.engine.cooldowns().remainingTicks(p, "arcanist_ab1") > 0, "now on cooldown");
    }

    @Test
    void intoAWallItSinksInAndTheRecastFiresItOutEmpowered() throws IOException {
        setup();
        t.world.box(6, 7, -5, 5, 10);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(30);
        assertEquals(1, t.engine.constructs().activeCount(), "sunk into the wall");
        assertTrue(t.render.cues.contains("arcanist_missile_sink"));
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "arcanist_ab1"), "waiting for the recast");
        UUID e = foe(3, 4);
        t.world.look(p, new Vec3(3, 0, 4));
        assertTrue(use(Slots.ABILITY_1), "the recast");
        assertEquals(0, t.engine.constructs().activeCount(), "out of the wall");
        for (int i = 0; i < 30 && !t.engine.tags().has(e, Tags.STUNNED); i++) t.time.advance(1);
        assertTrue(t.engine.tags().has(e, Tags.STUNNED), "empowered: stunned");
        assertTrue(has(e, "arcanist_mark"));
        assertEquals(BASE * 1.65 + 200 * 0.1, t.damage(e), 1e-6, "and it exploded");
        t.time.advance(1);
        assertTrue(t.engine.cooldowns().remainingTicks(p, "arcanist_ab1") > 0);
    }

    @Test
    void outOfTheWallItStartsABlockAwayFromIt() throws IOException {
        setup();
        t.world.box(6, 7, -5, 5, 10);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(30);
        t.world.look(p, new Vec3(-1, 0, 0)); // aiming back the way it came
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(1);
        var flying = t.engine.projectiles().latest(p, "arcanist_ab1").orElseThrow();
        assertTrue(flying.position().x() < 5.2, "a block off the wall (its face is at x=6): " + flying.position());
    }

    @Test
    void notRecastInTimeTheMissileFades() throws IOException {
        setup();
        t.world.box(6, 7, -5, 5, 10);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(30);
        assertEquals(1, t.engine.constructs().activeCount());
        t.time.advance(85);
        assertEquals(0, t.engine.constructs().activeCount());
        assertTrue(t.engine.cooldowns().remainingTicks(p, "arcanist_ab1") > 0, "on cooldown now");
    }

    // ---- 3: Arcane Screen ----

    /** An enemy who shoots back at the Arcanist along -x. */
    private UUID shooter(double x) {
        t.load(map("abilities", map("enemy_shot", map("cooldown", 1, "nodes", map( // (ordered: "fire" goes first)
                "fire", map("type", "projectile", "speed", 1.0, "size", 0.3, "lifetime", 200, "visual", "STONE",
                        "on", map("hit_entity", "hurt")),
                "hurt", map("type", "apply_effects", "targets", map("type", "key", "key", "hit"),
                        "effects", List.of(map("id", "damage", "amount", 10))))))));
        UUID e = foe(x, 0);
        t.world.look(e, new Vec3(-1, 0, 0));
        return e;
    }

    private void placeScreen() {
        assertTrue(use(Slots.ABILITY_3), "at once: no preview");
        assertFalse(t.engine.targeting().isTargeting(p));
        assertEquals(1, t.engine.screens().active().size());
    }

    @Test
    void theScreenFacesHisLookAndHoldsEnemyShotsFor2s() throws IOException {
        setup();
        placeScreen();
        var s = t.engine.screens().active().get(0);
        assertEquals(1, s.normal().x(), 1e-9, "facing the way he looks");
        assertEquals(3, s.center().x(), 0.01, "3 blocks ahead");
        assertEquals(6, s.width(), 1e-9);
        UUID e = shooter(14);
        assertTrue(t.engine.activator().activate(e, "enemy_shot").success());
        t.time.advance(30);
        assertEquals(0, t.damage(p), 1e-9, "the shot is stuck in the screen");
        assertTrue(t.render.cues.contains("arcanist_screen_catch"));
        t.time.advance(40);
        assertEquals(10 * 100.0 / 110, t.damage(p), 1e-9, "2s later it flew on (his armor: 10)");
    }

    @Test
    void aWallInTheWayPutsItShortOfTheWall() throws IOException {
        setup();
        t.world.box(2, 3, -5, 5, 10);
        placeScreen();
        assertEquals(2 - 0.6, t.engine.screens().active().get(0).center().x(), 0.01, "just in front of the wall");
    }

    @Test
    void alliedShotsFlyFasterAndHitForSpellPowerMore() throws IOException {
        setup();
        t.load(Map.of("statuses", Map.of("tome", Map.of("spell_power", 50))));
        t.engine.statuses().apply(p, "tome", 0, p);
        placeScreen();
        UUID e = foe(12, 0);
        bolt();
        assertEquals(BOLT + 50 * 0.5 + 50 * 0.6, t.damage(e), 1e-6, "the burst (half his spell power too), plus 60% of it");
        assertTrue(t.render.cues.contains("arcanist_screen_boost"));
        t.time.advance(130);
        assertTrue(t.engine.screens().active().isEmpty(), "6s and it's gone");
        double before = t.damage(e);
        bolt();
        assertEquals(BOLT + 50 * 0.5, t.damage(e) - before, 1e-6, "no screen, no bonus");
    }

    @Test
    void anAllysShotIsBoostedButTheBonusIsHis() throws IOException {
        setup();
        t.load(Map.of("statuses", Map.of("tome", Map.of("spell_power", 50))));
        t.engine.statuses().apply(p, "tome", 0, p);
        placeScreen();
        UUID friend = shooter(-3);   // (an "enemy_shot" ability, but on his side, shooting +x)
        t.world.team(friend, "blue");
        t.world.look(friend, new Vec3(1, 0, 0));
        UUID e = foe(12, 0.3);
        assertTrue(t.engine.activator().activate(friend, "enemy_shot").success());
        t.time.advance(30);
        assertEquals(10 + 50 * 0.6, t.damage(e), 1e-6, "his ally's shot, with his bonus");
    }

    // ---- ULTIMATE: Arcane Ascension ----

    private void ascend() {
        assertTrue(use(Slots.ULTIMATE));
        t.time.advance(25);
        assertTrue(t.engine.tags().has(p, Tags.FLYING), "flying");
        assertTrue(t.engine.tags().has(p, Tags.SLOW_FLIGHT), "slowly");
        assertTrue(pos(p).y() > 8, "high up: " + pos(p));
        assertEquals(3, stacks(p, "arcanist_geometry_charges"));
        assertTrue(t.engine.tags().has(p, Tags.LEVEL_FLIGHT), "no flying up or down from up there");
    }

    /** F again, look at the spot, LMB. */
    private void place(Vec3 spot) {
        assertTrue(use(Slots.ULTIMATE), "F again");
        assertTrue(t.engine.targeting().isTargeting(p), "the area selector");
        t.world.look(p, spot.subtract(pos(p)));
        assertTrue(t.engine.targeting().confirm(p).success());
    }

    @Test
    void geometriesExplodeASecondAfterTheyArePlaced() throws IOException {
        setup();
        UUID e = foe(8, 0);
        ascend();
        place(new Vec3(8, 0, 0));
        assertEquals(2, stacks(p, "arcanist_geometry_charges"));
        assertTrue(t.render.cues.contains("arcanist_geometry_warn"), "the circle shows where");
        t.time.advance(10);
        assertEquals(0, t.damage(e), 1e-9, "not yet");
        t.time.advance(12);
        assertEquals(BASE * 2.2, t.damage(e), 1e-6, "boom");
        assertTrue(t.knockback.containsKey(e));
    }

    @Test
    void theBossBarCountsDownAndShowsTheGeometriesLeft() throws IOException {
        setup();
        ascend();
        var timer = t.engine.instances().timer(p).orElseThrow();
        assertEquals("arcanist_ascension", timer.ability().id());
        assertTrue(timer.ticksLeft().isPresent(), "a countdown");
        assertEquals("3 geometries", timer.note());
        place(new Vec3(8, 0, 0));
        assertEquals("2 geometries", t.engine.instances().timer(p).orElseThrow().note());
    }

    @Test
    void channelingHeCantUseAbilitiesOrBasicAttacks() throws IOException {
        setup();
        ascend();
        place(new Vec3(8, 0, 0));
        t.engine.cooldowns().clear(p, "arcanist_primary");
        assertFalse(use(Slots.PRIMARY), "no basic attacks until it goes off");
        assertFalse(use(Slots.ABILITY_1), "no abilities either");
        t.time.advance(21);
        assertTrue(use(Slots.PRIMARY), "gone off: free again");
        assertTrue(use(Slots.ABILITY_1));
    }

    @Test
    void eachOneIsChanneledForASecond() throws IOException {
        setup();
        ascend();
        place(new Vec3(8, 0, 0));
        t.time.advance(5);
        use(Slots.ULTIMATE);
        assertFalse(t.engine.targeting().isTargeting(p), "still channeling: no picking the next one");
        t.time.advance(20);
        assertTrue(use(Slots.ULTIMATE));
        assertTrue(t.engine.targeting().isTargeting(p), "channel over: the next one");
    }

    @Test
    void rmbPutsTheSelectorAwayForFree() throws IOException {
        setup();
        ascend();
        assertTrue(use(Slots.ULTIMATE));
        t.engine.targeting().cancel(p, "cancelled");
        t.time.advance(2);
        assertEquals(3, stacks(p, "arcanist_geometry_charges"), "nothing spent");
        assertTrue(use(Slots.ULTIMATE), "and F opens it again");
        assertTrue(t.engine.targeting().isTargeting(p));
    }

    @Test
    void aKillGivesAGeometryBack() throws IOException {
        setup();
        UUID e = foe(8, 0);
        t.world.health.put(e, 10.0);    // one blast kills them
        ascend();
        place(new Vec3(8, 0, 0));
        t.time.advance(25);
        assertFalse(t.world.isAlive(e));
        assertEquals(3, stacks(p, "arcanist_geometry_charges"), "one back");
    }

    @Test
    void theLastGeometryEndsTheUltimate() throws IOException {
        setup();
        ascend();
        for (int i = 0; i < 3; i++) {
            place(new Vec3(8 + i * 3, 0, 0));
            t.time.advance(i < 2 ? 22 : 2); // (each one channeled 1s)
        }
        assertEquals(0, stacks(p, "arcanist_geometry_charges"));
        assertTrue(t.engine.tags().has(p, Tags.FLYING), "still up while the last one burns");
        t.time.advance(30);
        assertFalse(t.engine.tags().has(p, Tags.FLYING), "over");
        assertTrue(t.engine.instances().of(p).stream().noneMatch(i -> i.ability().id().equals("arcanist_ascension")));
    }

    @Test
    void itRunsOutAfter12s() throws IOException {
        setup();
        ascend();
        t.time.advance(240);
        assertFalse(t.engine.tags().has(p, Tags.FLYING));
        assertEquals(0, stacks(p, "arcanist_geometry_charges"), "unused ones are gone");
    }

    @Test
    void foldstepWorksUpThere() throws IOException {
        setup();
        ascend();
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).openedTargeting());
        t.world.look(p, new Vec3(6, 0, 0).subtract(pos(p)));
        assertTrue(t.engine.targeting().confirm(p).success());
        t.time.advance(12);
        assertTrue(Math.abs(pos(p).x() - 6) < 1, "teleported: " + pos(p));
        assertTrue(pos(p).y() > 8, "level, up there (not down to the ground): " + pos(p));
        assertTrue(t.engine.tags().has(p, Tags.FLYING), "and still flying");
    }
}

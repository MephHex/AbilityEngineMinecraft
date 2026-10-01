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
 * The Sylvan (fixtures/sylvan.yml): a seed that takes root and grows, a sapling, a young tree, a large tree; each
 * stage a form with its own kit and stats. She stands at the origin on a floor, looking +x, team blue. Enemies are
 * team red (no sheet: 200 HP, no armor), allies team blue.
 */
class SylvanTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "sylvan");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private UUID friend(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "blue");
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success(), slot); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private String in(String slot) { return t.engine.loadouts().abilityIn(p, slot).orElse(null); }

    private double base() { return t.engine.stats().baseDamage(p); }

    private long count(String cue) { return t.render.cues.stream().filter(cue::equals).count(); }

    /** Straight to a stage, planted (as if she'd grown there). */
    private void stage(String status) {
        t.engine.statuses().apply(p, "sylvan_planted", p);
        t.engine.statuses().apply(p, status, p);
    }

    /** Aim at the floor ~10 blocks ahead and confirm the ability's aim preview there. */
    private void aimAndCast(String ability) {
        t.world.look(p, new Vec3(1, -0.1, 0));
        assertTrue(t.engine.activator().activate(p, ability).openedTargeting());
        assertTrue(t.engine.targeting().confirm(p).success());
    }

    // ---- growing up -------------------------------------------------------------------------------------

    @Test
    void sheStartsAsASmallQuickSeed() throws IOException {
        setup();
        var stats = t.engine.stats().of(p);
        assertEquals(0.45, stats.scale(), 1e-9);
        assertEquals(120, stats.health(), 1e-9);
        assertEquals(1.15, stats.moveSpeed(), 1e-9);
        assertEquals("sylvan_seed_shot", in(Slots.PRIMARY));
        assertEquals("sylvan_blink", in(Slots.ABILITY_1));
        assertEquals("sylvan_bury", in(Slots.ABILITY_2));
        assertEquals(null, in(Slots.SNEAK));
    }

    @Test
    void takingRootSheIsUntouchableThenBuriedThenSprouts() throws IOException {
        setup();
        UUID enemy = foe(4, 0);
        use(Slots.ABILITY_2);
        assertTrue(t.engine.tags().has(p, Tags.UNTARGETABLE), "digging in: untouchable");
        assertEquals(0, DamageModifiers.apply(t.engine, enemy, p, 100).amount(), 1e-9);
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE));
        t.time.advance(10);
        assertFalse(t.engine.tags().has(p, Tags.UNTARGETABLE), "buried: hittable again");
        assertTrue(has(p, "sylvan_buried"));
        assertEquals(0.25, t.engine.stats().of(p).scale(), 1e-9, "sunk into the soil");
        assertFalse(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "no abilities while buried");
        t.time.advance(59);
        assertFalse(has(p, "sylvan_sapling"), "not yet");
        t.time.advance(2);
        assertTrue(has(p, "sylvan_sapling"), "3s: she sprouts");
        assertEquals("sylvan_thorn", in(Slots.PRIMARY));
        assertEquals("sylvan_uproot", in(Slots.SNEAK));
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE), "planted");
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "her abilities are back");
    }

    @Test
    void eachStageGrowsIntoTheNextAndGetsBiggerAndStronger() throws IOException {
        setup();
        use(Slots.ABILITY_2);
        t.time.advance(71);
        var sapling = t.engine.stats().of(p);
        assertEquals(0.7, sapling.scale(), 1e-9);
        assertEquals(160, sapling.health(), 1e-9);
        t.time.advance(400);
        assertTrue(has(p, "sylvan_tree"), "20s a sapling: a young tree");
        var tree = t.engine.stats().of(p);
        assertEquals(1.15, tree.scale(), 1e-9);
        assertEquals(250, tree.health(), 1e-9);
        assertTrue(tree.baseDamage() > sapling.baseDamage() && tree.armor() > sapling.armor());
        assertEquals("sylvan_sap_shot", in(Slots.PRIMARY));
        assertEquals(null, in(Slots.SNEAK), "no more digging out");
        t.time.advance(1200);
        assertTrue(has(p, "sylvan_large_tree"), "60s a tree: a large tree");
        var large = t.engine.stats().of(p);
        assertEquals(1.7, large.scale(), 1e-9);
        assertEquals(380, large.health(), 1e-9);
        assertEquals(null, in(Slots.PRIMARY), "no primary fire");
        assertEquals("sylvan_walk", in(Slots.ULTIMATE));
        t.time.advance(6000);
        assertTrue(has(p, "sylvan_large_tree"), "for good");
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE), "still planted");
    }

    @Test
    void theSaplingShootsABitFasterThanTheSeed() throws IOException {
        setup();
        use(Slots.PRIMARY);
        assertEquals(13, t.engine.cooldowns().remainingTicks(p, "sylvan_seed_shot"), "1.6 a second");
        stage("sylvan_sapling");
        use(Slots.PRIMARY);
        assertEquals(10, t.engine.cooldowns().remainingTicks(p, "sylvan_thorn"), "2 a second");
    }

    @Test
    void dyingSheIsASeedAgain() throws IOException {
        setup();
        stage("sylvan_large_tree");
        t.engine.resetOnDeath(p);
        assertEquals("sylvan_seed_shot", in(Slots.PRIMARY));
        assertEquals(0.45, t.engine.stats().of(p).scale(), 1e-9);
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_MOVE));
    }

    // ---- seed ---------------------------------------------------------------------------------------------

    @Test
    void seedsKnockBack() throws IOException {
        setup();
        UUID enemy = foe(6, 0);
        use(Slots.PRIMARY);
        t.time.advance(6);
        assertEquals(0.8 * 24, t.damage(enemy), 1e-6);
        assertTrue(t.knockbackVec.get(enemy).x() > 0, "pushed along the shot");
    }

    @Test
    void blinkPopsHerSevenBlocksAhead() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        assertEquals(7, pos(p).x(), 1e-6);
        assertEquals(2, count("sylvan_blink"), "where she left and where she arrived");
    }

    // ---- sapling --------------------------------------------------------------------------------------------

    @Test
    void holdingShiftDigsHerOut() throws IOException {
        setup();
        stage("sylvan_sapling");
        use(Slots.SNEAK);
        t.time.advance(29);
        assertTrue(has(p, "sylvan_sapling"), "not yet");
        t.time.advance(2);
        assertFalse(has(p, "sylvan_sapling"), "1.5s: out");
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_MOVE), "free to run");
        assertEquals("sylvan_seed_shot", in(Slots.PRIMARY), "a seed again");
        assertEquals(1, count("sylvan_uproot"));
    }

    @Test
    void lettingGoOfShiftEarlyKeepsHerPlanted() throws IOException {
        setup();
        stage("sylvan_sapling");
        use(Slots.SNEAK);
        t.time.advance(15);
        t.engine.instances().release(p, "sylvan_uproot");
        t.time.advance(30);
        assertTrue(has(p, "sylvan_sapling"));
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE));
    }

    @Test
    void theRootSnareRootsEveryoneInItsWay() throws IOException {
        setup();
        stage("sylvan_sapling");
        UUID first = foe(5, 0);
        UUID second = foe(10, 0);
        UUID aside = foe(6, 4);
        use(Slots.ABILITY_1);
        t.time.advance(10);
        assertTrue(has(first, "root") && has(second, "root"), "through both");
        assertFalse(has(aside, "root"));
        assertEquals(0.6 * base(), t.damage(second), 1e-6);
    }

    @Test
    void scatterTeleportsEnemiesEightBlocksOutOfTheArea() throws IOException {
        setup();
        stage("sylvan_sapling");
        UUID inside = foe(11, 0);     // the spot is ~9.7 ahead: 1.3 past it
        UUID outside = foe(16, 0);
        aimAndCast("sylvan_scatter");
        assertEquals(19, pos(inside).x(), 1e-6, "8 blocks further out");
        assertEquals(0, pos(inside).z(), 1e-6);
        assertEquals(new Vec3(16, 1, 0), pos(outside));
        assertEquals(0.5 * base(), t.damage(inside), 1e-6);
    }

    // ---- young tree -----------------------------------------------------------------------------------------

    @Test
    void inTheTreesDomainEnemiesHitFullyOutsideOnlyHalf() throws IOException {
        setup();
        stage("sylvan_tree");
        UUID near = foe(6, 0);
        UUID far = foe(12, 0);
        double inside = DamageModifiers.apply(t.engine, near, p, 100).amount();
        double outside = DamageModifiers.apply(t.engine, far, p, 100).amount();
        assertEquals(inside / 2, outside, 1e-9);
    }

    @Test
    void stickySapSlows() throws IOException {
        setup();
        stage("sylvan_tree");
        UUID enemy = foe(6, 0);
        use(Slots.PRIMARY);
        t.time.advance(10);
        assertEquals(0.9 * base(), t.damage(enemy), 1e-6);
        assertEquals(0.7, t.engine.stats().moveSpeedMultiplier(enemy), 1e-9);
    }

    @Test
    void theFruitBombFallsOnTheSpotAndBursts() throws IOException {
        setup();
        stage("sylvan_tree");
        UUID enemy = foe(10, 1);
        aimAndCast("sylvan_fruit_bomb");
        t.time.advance(5);
        assertEquals(0, t.damage(enemy), "still falling");
        t.time.advance(20);
        assertEquals(1.3 * base(), t.damage(enemy), 1e-6);
        assertTrue(t.knockback.containsKey(enemy));
        assertEquals(1, count("sylvan_fruit_bomb"));
    }

    @Test
    void theRootLashHoldsThemAndTheRecastDragsThem() throws IOException {
        setup();
        stage("sylvan_tree");
        UUID enemy = foe(6, 0);
        use(Slots.ABILITY_2);
        t.time.advance(6);
        assertTrue(has(enemy, "sylvan_bound"), "caught");
        assertTrue(t.engine.tags().has(enemy, Tags.BLOCK_MOVE));
        t.world.look(p, new Vec3(0, 0, 1)); // drag them off to her side
        use(Slots.ABILITY_2);
        t.time.advance(15);
        assertTrue(pos(enemy).z() > 4, "dragged toward where she looks: " + pos(enemy));
        assertFalse(has(enemy, "sylvan_bound"), "and let go");
    }

    @Test
    void strengthSapBuffsAlliesInTheDomain() throws IOException {
        setup();
        stage("sylvan_tree");
        UUID near = friend(5, 0);
        UUID far = friend(10, 0);
        UUID enemy = foe(3, 0);
        use(Slots.ABILITY_3);
        assertTrue(has(near, "sylvan_vigor") && has(p, "sylvan_vigor"));
        assertFalse(has(far, "sylvan_vigor"), "outside the domain");
        assertFalse(has(enemy, "sylvan_vigor"));
        assertEquals(1.2, t.engine.stats().moveSpeedMultiplier(near), 1e-9);
    }

    // ---- large tree -----------------------------------------------------------------------------------------

    @Test
    void outsideTheLargeTreesDomainNothingHurtsHer() throws IOException {
        setup();
        stage("sylvan_large_tree");
        UUID near = foe(10, 0);
        UUID far = foe(18, 0);
        assertTrue(DamageModifiers.apply(t.engine, near, p, 100).amount() > 0);
        assertEquals(0, DamageModifiers.apply(t.engine, far, p, 100).amount(), 1e-9);
        assertFalse(t.engine.loadouts().activate(p, Slots.PRIMARY).success(), "no primary fire");
    }

    @Test
    void anEnemyBurstsAFruitAndIsStunned() throws IOException {
        setup();
        stage("sylvan_large_tree");
        aimAndCast("sylvan_windfall");
        UUID enemy = foe(12, 0);
        t.time.advance(20);
        assertFalse(has(enemy, "stun"), "too far to set it off");
        t.world.move(enemy, new Vec3(9.9, 1, 0.3));
        t.time.advance(2);
        assertTrue(has(enemy, "stun"));
        assertEquals(1.0 * base(), t.damage(enemy), 1e-6);
        assertEquals(1, count("sylvan_fruit_burst"));
    }

    @Test
    void anAllyEatsAFruitAndHeals() throws IOException {
        setup();
        stage("sylvan_large_tree");
        aimAndCast("sylvan_windfall");
        t.time.advance(20);
        UUID pal = friend(9.8, 0);
        t.time.advance(2);
        assertEquals(0.12 * 200, t.healed.get(pal), 1e-6);
        assertEquals(1, count("sylvan_fruit_eaten"));
    }

    @Test
    void aFruitLeftLyingCompostsAndHealsHer() throws IOException {
        setup();
        stage("sylvan_large_tree");
        aimAndCast("sylvan_windfall");
        t.time.advance(201);
        assertEquals(0.04 * 380, t.healed.get(p), 1e-6);
        assertEquals(1, count("sylvan_fruit_compost"));
    }

    @Test
    void fiveFruitsAtMost() throws IOException {
        setup();
        stage("sylvan_large_tree");
        for (int i = 0; i < 6; i++) { // six in a row (well within the first one's 10s)
            aimAndCast("sylvan_windfall");
            t.engine.cooldowns().reduce(p, "sylvan_windfall", 1000);
            t.time.advance(2);
        }
        assertEquals(5, t.engine.constructs().activeCount());
        assertEquals(1, count("sylvan_fruit_compost"), "the oldest went");
    }

    @Test
    void deepRootsRootEveryEnemyInTheDomain() throws IOException {
        setup();
        stage("sylvan_large_tree");
        UUID near = foe(10, 0);
        UUID far = foe(16, 0);
        use(Slots.ABILITY_2);
        assertTrue(has(near, "root"));
        assertFalse(has(far, "root"));
        assertEquals(0.4 * base(), t.damage(near), 1e-6);
    }

    @Test
    void theScreechBlowsEnemiesAwayAndDisarmsThem() throws IOException {
        setup();
        stage("sylvan_large_tree");
        UUID enemy = foe(6, 0);
        use(Slots.ABILITY_3);
        assertTrue(has(enemy, "disarm"));
        assertTrue(t.knockbackVec.get(enemy).x() > 0, "away from her");
    }

    @Test
    void theWalkingTreeWalksDrainsAndTakesRootAgain() throws IOException {
        setup();
        stage("sylvan_large_tree");
        UUID enemy = foe(10, 0);
        use(Slots.ULTIMATE);
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_MOVE), "walking");
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_ABILITY), "no abilities meanwhile");
        t.time.advance(100);
        assertTrue(t.damage(enemy) > 0);
        assertEquals(t.damage(enemy), t.lifesteal.get(p), 1e-6, "all of it back to her");
        t.time.advance(70);
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE), "rooted again");
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_ABILITY));
        assertEquals(16 * 0.3 * base(), t.damage(enemy), 1e-6, "16 x 0.5s");
    }
}

package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Amethyst / Shard (fixtures/amethyst.yml): 180 HP, 5 armor, base damage 32. She stands at the origin on a floor,
 * looking +x, team blue. Enemies are team red (no sheet: 200 HP, no armor). Regular shards hit one; Shard Rush makes the
 * next 4 piercing (through everyone, they hang where they end); Recall brings them back, 5% less per shard on the same
 * enemy; Crystal Volley roots and grows with hits taken, then shatters; Prismatic Burst roots who it hits and shatters
 * behind them into a cone and 6 hanging shards.
 */
class AmethystTest {

    private static final double BASE = 32;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        // an enemy's plain hit on whoever's within 10 (to hurt her while she charges)
        t.load(map("abilities", map("punch", map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "radius", "radius", 10), "effects", list(map("id", "damage", "amount", 10))))))));
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "amethyst");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success(), slot); }

    /** A shot of the primary, then enough time for the next (5 a second; 7 a second rushed). */
    private void shoot() {
        use(Slots.PRIMARY);
        t.time.advance(5);
    }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private int stacks(UUID id, String status) {
        return t.engine.statuses().find(id, status).map(s -> s.stacks()).orElse(0);
    }

    private Vec3 pos(UUID id) {
        return t.world.positionOf(new me.mephisto.ability_engine.engine.target.EntityTarget(id)).orElseThrow().position();
    }

    private int ammo() { return t.engine.resources().get(p, "ammo"); }

    // ---- regular and piercing shards -------------------------------------------------------------------------

    @Test
    void aRegularShardHitsTheFirstEnemyOnlyAndDoesntHang() throws IOException {
        setup();
        UUID front = foe(4, 0);
        UUID back = foe(7, 0);
        shoot();
        t.time.advance(15);
        assertEquals(BASE * 0.8, t.damage(front), 1e-6, "80%");
        assertEquals(0, t.damage(back), 1e-9, "it stopped in the first");
        assertEquals(0, t.engine.constructs().activeCount(), "nothing hangs");
        assertEquals(5, ammo(), "a shard of ammo");
        assertTrue(t.engine.activator().isUnavailable(p, t.engine.abilities().find("amethyst_recall").orElseThrow()),
                "nothing to recall");
    }

    @Test
    void theAmmoDoesntRefillByItself() throws IOException {
        setup();
        for (int i = 0; i < 3; i++) shoot();
        assertEquals(3, ammo());
        t.time.advance(20 * 20);
        assertEquals(3, ammo(), "20s later: still 3 (only Recall, Shard Rush and the ult's shards bring them back)");
    }

    @Test
    void shardRushSpeedsHerUpAndMakesTheNextFourPiercing() throws IOException {
        setup();
        shoot();
        shoot();
        assertEquals(4, ammo());
        use(Slots.ABILITY_1);
        assertTrue(has(p, "amethyst_rush"));
        assertEquals(1.4, t.engine.stats().attackSpeedMultiplier(p), 1e-9, "+40% attack speed");
        assertEquals(4, stacks(p, "amethyst_piercing"));
        assertEquals(6, ammo(), "+4 (6 at most)");

        UUID front = foe(4, 0);
        UUID back = foe(7, 0);
        shoot();
        t.time.advance(15);
        assertEquals(BASE * 0.8, t.damage(front), 1e-6, "the first: 80%");
        assertEquals(BASE * 0.5, t.damage(back), 1e-6, "pierced: 50% behind it");
        assertEquals(3, stacks(p, "amethyst_piercing"));
        assertEquals(1, t.engine.constructs().activeCount(), "it hangs where it ended");
        for (int i = 0; i < 3; i++) shoot();
        assertFalse(has(p, "amethyst_piercing"), "4 used");
        shoot();
        t.time.advance(15);
        assertEquals(4, t.engine.constructs().activeCount(), "the 5th was a regular shard again");
    }

    @Test
    void recallBringsThemBackEachDoingFivePercentLessToTheSameEnemy() throws IOException {
        setup();
        use(Slots.ABILITY_1);
        for (int i = 0; i < 3; i++) shoot();
        t.time.advance(15);
        assertEquals(3, t.engine.constructs().activeCount(), "3 hanging at their full range");
        int before = ammo();

        UUID enemy = foe(10, 0); // between them and her
        use(Slots.ABILITY_3);
        t.time.advance(25);
        assertEquals(0, t.engine.constructs().activeCount(), "all came back");
        assertEquals(Math.min(6, before + 3), ammo(), "into the ammo");
        assertEquals(BASE * (1 + 0.95 + 0.9), t.damage(enemy), 1e-6, "100%, 95%, 90%");
        assertEquals(3, stacks(enemy, "amethyst_shard_slow"));
        assertTrue(t.engine.statuses().on(enemy).stream().noneMatch(x -> x.def().id().contains("bleed")), "no bleed");
    }

    // ---- Crystal Volley --------------------------------------------------------------------------------------

    @Test
    void crystalVolleyRootsAndGuardsHerHitsTakenDontAddShards() throws IOException {
        setup();
        UUID enemy = foe(5, 3);
        use(Slots.ABILITY_2);
        assertTrue(has(p, "amethyst_volley_charging"));
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE), "rooted");
        t.engine.activator().activate(enemy, "punch");
        t.engine.activator().activate(enemy, "punch");
        assertEquals(2 * 10 * 0.7 * 100 / 105.0, t.damage(p), 1e-6, "30% less (and her 5 armor)");
        use(Slots.PRIMARY);                    // LMB: loose it
        assertEquals(6, t.engine.projectiles().activeCount(), "still 6");
    }

    @Test
    void lmbLoosesSixShardsAllAroundHerKnockingEnemiesAwayWithHerOnHits() throws IOException {
        setup();
        t.load(map("statuses", map("spiked", map("duration", 0, "on_hit", list(map("id", "status", "status", "marked"))),
                "marked", map("duration", 100))));
        t.engine.statuses().apply(p, "spiked", 0, p);
        // her aim is +x: the 6 go at 0, 60, 120, 180, 240 and 300 degrees
        UUID ahead = foe(4, 0), behind = foe(-4, 0), off = foe(2, 3.46), between = foe(0, -4);
        use(Slots.ABILITY_2);
        use(Slots.PRIMARY);                    // LMB: loose it
        assertFalse(has(p, "amethyst_volley_charging"), "free again");
        assertEquals(6, t.engine.projectiles().activeCount(), "6 shards");
        t.time.advance(10);
        for (UUID hit : java.util.List.of(ahead, behind, off)) {
            assertEquals(BASE * 0.6, t.damage(hit), 1e-6, "60%");
            Vec3 away = t.knockbackVec.get(hit);
            assertTrue(away != null && away.x() * pos(hit).x() + away.z() * pos(hit).z() > 0, "knocked away from her");
            assertTrue(has(hit, "marked"), "her on-hit effects");
        }
        assertEquals(0, t.damage(between), 1e-9, "between two shards (90 degrees): missed");
    }

    @Test
    void theVolleyGoesByItselfAfterTwoSeconds() throws IOException {
        setup();
        use(Slots.ABILITY_2);
        t.time.advance(39);
        assertTrue(has(p, "amethyst_volley_charging"));
        t.time.advance(2);
        assertFalse(has(p, "amethyst_volley_charging"), "loosed at 2s");
    }

    // ---- Prismatic Burst -------------------------------------------------------------------------------------

    @Test
    void prismaticBurstRootsWhoItHitsAndShattersBehindIntoAConeAndSixHangingShards() throws IOException {
        setup();
        UUID hit = foe(6, 0);
        UUID behind = foe(10, 0.5);
        use(Slots.ULTIMATE);
        assertTrue(t.engine.tags().has(p, "state.prism_charging"));
        t.time.advance(31);                    // full charge: it goes by itself
        assertFalse(t.engine.tags().has(p, "state.prism_charging"));
        t.time.advance(6);
        assertTrue(t.engine.tags().has(hit, Tags.ROOTED), "rooted...");
        assertEquals(0, t.damage(hit), 1e-9, "...but no impact damage");
        assertTrue(t.damage(behind) >= BASE * 1.8 - 1e-6, "the cone behind them: 180% (full charge)");
        t.time.advance(15);
        assertEquals(6, t.engine.constructs().activeCount(), "6 shards hang where they flew");
        var spots = t.engine.constructs().all().stream().map(c -> c.position()).toList();
        for (int i = 0; i < spots.size(); i++) {
            for (int k = i + 1; k < spots.size(); k++) {
                assertTrue(spots.get(i).distance(spots.get(k)) > 0.8, "each where it flew, spread out: " + spots);
            }
        }
        assertFalse(t.engine.activator().isUnavailable(p, t.engine.abilities().find("amethyst_recall").orElseThrow()),
                "Recall can bring them back");
    }

    @Test
    void prismaticBurstOnAWallSpawnsNoShards() throws IOException {
        setup();
        t.world.box(6, 7, -3, 3, 10);          // a wall 6 blocks ahead
        use(Slots.ULTIMATE);
        t.time.advance(31 + 25);
        assertEquals(0, t.engine.constructs().activeCount(), "no shards off a wall");
        assertEquals(0, t.engine.projectiles().activeCount());
    }

    @Test
    void lmbFiresThePrismEarlyForLess() throws IOException {
        setup();
        foe(6, 0);
        UUID behind = foe(10, 0.5);
        use(Slots.ULTIMATE);
        t.time.advance(1);
        use(Slots.PRIMARY);                    // LMB: fire now
        t.time.advance(10);
        double cone = t.damage(behind);
        assertTrue(cone >= BASE * 1.8 * 0.6 - 1e-6 && cone < BASE * 1.8 * 0.7 + BASE, "about 60% of it: " + cone);
    }

    @Test
    void recalledBurstShardsComeBackIntoTheAmmo() throws IOException {
        setup();
        for (int i = 0; i < 4; i++) shoot();
        assertEquals(2, ammo());
        foe(6, 0);
        use(Slots.ULTIMATE);
        t.time.advance(31 + 25);
        assertEquals(6, t.engine.constructs().activeCount());
        use(Slots.ABILITY_3);
        t.time.advance(30);
        assertEquals(0, t.engine.constructs().activeCount());
        assertEquals(6, ammo(), "back in the ammo (6 at most)");
    }
}

package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.combat.DamageModifiers;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Iceman (fixtures/iceman.yml): 320 HP, 40 armor, base damage 34. He stands at the origin on a floor, looking +x,
 * team blue. Enemies are red (no sheet: 200 HP, no armor).
 */
class IcemanTest {

    private static final double BASE = 34;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "iceman");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success(), slot); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private int chill(UUID id) { return t.engine.statuses().find(id, "iceman_chill").map(s -> s.stacks()).orElse(0); }

    // ---- Ice Mace, Ice Prison, Cold-Blooded ------------------------------------------------------------------

    @Test
    void theThirdMaceHitOnSomeoneFreezesThem() throws IOException {
        setup();
        UUID enemy = foe(2, 0);
        use(Slots.PRIMARY);
        assertEquals(BASE, t.damage(enemy), 1e-6, "100% base damage");
        assertEquals(1, chill(enemy), "one shard over their head");
        t.time.advance(30);
        use(Slots.PRIMARY);
        assertEquals(2, chill(enemy));
        assertFalse(has(enemy, "iceman_freeze"));
        t.time.advance(30);
        use(Slots.PRIMARY);
        assertTrue(has(enemy, "iceman_freeze"), "the 3rd: frozen solid");
        assertTrue(t.engine.tags().has(enemy, Tags.STUNNED), "can't act");
        assertEquals(0, chill(enemy), "the stacks are used up");
        t.time.advance(26);
        assertFalse(has(enemy, "iceman_freeze"), "1.25s");
    }

    @Test
    void nothingCanFreezeHim() throws IOException {
        setup();
        UUID enemy = foe(5, 0);
        t.engine.statuses().apply(p, "iceman_freeze", enemy);
        t.engine.statuses().apply(p, "frozen", enemy); // the Alchemist's Frost
        assertFalse(has(p, "iceman_freeze"));
        assertFalse(has(p, "frozen"));
        t.engine.statuses().apply(p, "stun", enemy);
        assertTrue(has(p, "stun"), "other crowd control still lands");
    }

    // ---- Glacial Shockwave ------------------------------------------------------------------------------------

    @Test
    void theShockwavePullsEnemiesInAndShieldsHimPerEnemy() throws IOException {
        setup();
        UUID near = foe(3, 0);
        UUID far = foe(6, 1);
        UUID behind = foe(-3, 0);
        use(Slots.ABILITY_1);
        assertEquals(BASE * 0.8, t.damage(near), 1e-6);
        assertEquals(BASE * 0.8, t.damage(far), 1e-6);
        assertEquals(0, t.damage(behind), 1e-9, "only in front");
        assertTrue(t.knockbackVec.get(near).x() < 0 && t.knockbackVec.get(far).x() < 0, "pulled toward him");
        assertTrue(t.knockbackVec.get(far).x() < t.knockbackVec.get(near).x(), "the farther, the harder");
        assertEquals(80, t.shields.getOrDefault(p, 0.0), 1e-6, "40 HP per enemy hit");
    }

    // ---- Frostbreath ------------------------------------------------------------------------------------------

    @Test
    void standingInTheBreathSlowsThenFreezes() throws IOException {
        setup();
        UUID enemy = foe(3, 0);
        use(Slots.ABILITY_2);
        t.time.advance(11);
        assertTrue(t.engine.stats().moveSpeedMultiplier(enemy) < 0.9, "slowed more and more");
        assertFalse(has(enemy, "iceman_freeze"));
        t.time.advance(20);
        assertTrue(has(enemy, "iceman_freeze"), "1.5s in it: frozen");
    }

    @Test
    void pressingTwoAgainStopsTheBreath() throws IOException {
        setup();
        UUID enemy = foe(3, 0);
        use(Slots.ABILITY_2);
        t.time.advance(6);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        double dealt = t.damage(enemy);
        t.time.advance(40);
        assertEquals(dealt, t.damage(enemy), 1e-9, "stopped");
        assertFalse(has(enemy, "iceman_freeze"));
    }

    // ---- Ice Wall ---------------------------------------------------------------------------------------------

    @Test
    void theWallGoesWhereHeAims() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -0.2, 0).normalize());
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).openedTargeting(), "a wall preview first");
        assertTrue(t.engine.targeting().confirm(p).success());
        assertEquals(1, t.placed.size());
        var wall = t.placed.get(0);
        assertEquals("wall", wall.shape());
        assertEquals(100, wall.duration(), "5s");
        assertTrue(wall.at() instanceof PointTarget pt && pt.position().x() > 3, "on the spot he aimed at: " + wall.at());
        assertTrue(t.engine.cooldowns().remainingTicks(p, "iceman_ab3") > 0);
    }

    // ---- Glacial Tomb -----------------------------------------------------------------------------------------

    @Test
    void theTombBuriesThemThenTheStormFreezesEveryoneAround() throws IOException {
        setup();
        UUID target = foe(8, 0);
        UUID nearby = foe(10, 3);
        UUID farAway = foe(20, 10);
        use(Slots.ULTIMATE);
        t.time.advance(10);
        assertTrue(has(target, "iceman_entombed"), "buried in ice");
        assertTrue(t.engine.tags().has(target, Tags.UNTARGETABLE), "nothing can target them");
        assertEquals(0, DamageModifiers.apply(t.engine, nearby, target, 500, 0).amount(), 1e-9, "or hurt them");
        assertTrue(t.placed.stream().anyMatch(b -> b.shape().equals("tomb")
                && b.at() instanceof EntityTarget e && e.id().equals(target)), "a pillar of ice around them");
        assertTrue(has(nearby, "iceman_snowed"), "the storm slows enemies in it");

        t.time.advance(70);
        assertFalse(has(target, "iceman_entombed"), "the tomb shatters");
        assertTrue(has(target, "iceman_freeze"), "and the blast freezes them...");
        assertTrue(has(nearby, "iceman_freeze"), "...and everyone near");
        assertEquals(BASE * 1.0, t.damage(nearby), 1e-6, "100% base damage");
        assertFalse(has(farAway, "iceman_freeze"), "not beyond 5 blocks");
    }
}

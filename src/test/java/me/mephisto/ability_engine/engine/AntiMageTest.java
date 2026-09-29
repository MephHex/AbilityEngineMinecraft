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

import static me.mephisto.ability_engine.engine.testkit.Yml.abilities;
import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AntiMage (fixtures/antimage.yml): 210 HP, base damage 38. She stands at the origin on a floor,
 * looking +x, team blue; the enemy stands 2.5 blocks in front, team red (no sheet: 200 HP, no armor).
 */
class AntiMageTest {

    private TestEngine t;
    private UUID p;
    private UUID enemy;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        // The enemy's test attack: 100 damage to whoever it's aimed at (a spell or a basic attack: see hit()).
        t.load(abilities("punch", map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "key", "key", "target"),
                "effects", list(map("id", "damage", "amount", 100)))))));
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "antimage");
        enemy = t.spawn(2.5, 1, 0);
        t.world.team(enemy, "red");
    }

    /** The enemy hits her for 100, as a spell (an ability slot) or as a basic attack (primary). */
    private void hit(boolean spell) {
        assertTrue(t.engine.activator().activateOnId(enemy, "punch", new EntityTarget(p),
                Map.of("slot", spell ? Slots.ABILITY_1 : Slots.PRIMARY)).success());
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private void strike() {
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        t.time.advance(11); // her attack speed
    }

    private boolean wardReady() { return t.engine.wards().state(p).orElseThrow().ready(); }

    // ---- Passive: Null Ward ------------------------------------------------------------------------

    @Test
    void theWardBlocksTheNextDebuffThenRechargesOutOfCombat() throws IOException {
        setup();
        assertTrue(wardReady(), "a new character starts with it ready");
        t.time.advance(1);
        assertTrue(t.engine.tags().has(p, Tags.DEBUFF_IMMUNE));

        t.engine.statuses().apply(p, "stun", 20, enemy);
        assertFalse(t.engine.tags().has(p, Tags.STUNNED), "blocked");
        assertFalse(wardReady(), "used up");
        assertTrue(t.render.cues.contains("ward_block"));
        t.engine.statuses().apply(p, "stun", 20, enemy);
        assertTrue(t.engine.tags().has(p, Tags.STUNNED), "the next one lands");

        t.time.advance(119);
        assertFalse(wardReady());
        assertEquals(1, t.engine.wards().state(p).orElseThrow().rechargeTicks());
        t.time.advance(2);
        assertTrue(wardReady(), "6s out of combat");
    }

    @Test
    void anyHitRestartsTheRecharge() throws IOException {
        setup();
        t.engine.statuses().apply(p, "slow", 20, enemy); // uses the ward up
        t.time.advance(100);
        hit(false);                                        // in combat again
        t.time.advance(100);
        assertFalse(wardReady(), "counted again from the hit");
        t.time.advance(21);
        assertTrue(wardReady());
    }

    @Test
    void buffsAndHerOwnStatusesArentDebuffs() throws IOException {
        setup();
        t.engine.statuses().apply(p, "null_rush", p);      // her own
        t.engine.statuses().apply(p, "inspiration", enemy); // a buff (positive)
        assertTrue(t.engine.statuses().has(p, "null_rush"));
        assertTrue(t.engine.statuses().has(p, "inspiration"));
        assertTrue(wardReady(), "nothing blocked, still ready");
    }

    // ---- Primary + ability 1: Fated Dagger ---------------------------------------------------------

    @Test
    void daggerStrikeHitsTheEnemyInFront() throws IOException {
        setup();
        strike();
        assertEquals(38, t.damage(enemy), 1e-9, "100% of 38");
    }

    @Test
    void threeStrikesOnYourMarkHealAndThenDealTrueDamage() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(8, 1, 0));
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        t.time.advance(10);
        assertEquals(38 * 0.8, t.damage(enemy), 1e-9, "the dagger: 80%");
        assertTrue(t.engine.statuses().has(enemy, "hunted"), "marked");

        t.world.move(enemy, new Vec3(2.5, 1, 0));
        double before = t.damage(enemy);
        strike();
        strike();
        assertEquals(2 * 210 * 0.06, t.healed.getOrDefault(p, 0.0), 1e-9, "6% of her max HP a strike");
        assertTrue(t.engine.statuses().has(enemy, "hunted"));
        strike();
        assertEquals(3 * 38 + 200 * 0.12, t.damage(enemy) - before, 1e-9, "the 3rd: + 12% of their 200 max HP");
        assertFalse(t.engine.statuses().has(enemy, "hunted"), "used up");
    }

    @Test
    void theMarkRunsOutAfterFiveSeconds() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(8, 1, 0));
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        t.time.advance(10);
        t.world.move(enemy, new Vec3(2.5, 1, 0));
        strike();
        strike();
        t.time.advance(100);
        assertFalse(t.engine.statuses().has(enemy, "hunted"));
        double before = t.damage(enemy);
        strike();
        assertEquals(38, t.damage(enemy) - before, 1e-9, "a plain strike: the mark was gone");
    }

    @Test
    void runningAtYourMarkMakesYouFaster() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(8, 1, 0));
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        t.time.advance(10);
        t.world.walk(p, new Vec3(1, 0, 0.2));
        t.time.advance(5);
        assertTrue(t.engine.tags().has(p, Tags.HASTED), "toward them");
        t.world.walk(p, new Vec3(-1, 0, 0));
        t.time.advance(10);
        assertFalse(t.engine.tags().has(p, Tags.HASTED), "away: not");
    }

    // ---- Ability 2: Volatile Nullifier ------------------------------------------------------------

    @Test
    void theFlaskSilencesAndLeavesASilencingPool() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(4, 1, 0));             // straight at them: it bursts on contact
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
        t.time.advance(10);
        assertEquals(38 * 0.9, t.damage(enemy), 1e-9);
        assertTrue(t.engine.tags().has(enemy, Tags.SILENCED));

        UUID late = t.spawn(20, 1, 0);
        t.world.team(late, "red");
        t.time.advance(45);
        t.world.move(late, pos(enemy));                   // walks into the pool
        t.time.advance(11);
        assertTrue(t.engine.tags().has(late, Tags.SILENCED), "standing in the pool");
        t.world.move(late, new Vec3(20, 1, 0));
        t.time.advance(40);
        assertFalse(t.engine.tags().has(late, Tags.SILENCED), "left it");
    }

    @Test
    void caughtInYourOwnFlaskFasterWardReadyAndOneDaggerInfusion() throws IOException {
        setup();
        t.engine.statuses().apply(p, "slow", 20, enemy); // the ward is used up
        assertFalse(wardReady());
        t.world.look(p, new Vec3(0.05, -1, 0));          // at her feet
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        t.time.advance(40);                               // a bounce, then the burst
        assertTrue(t.engine.statuses().has(p, "null_rush"), "faster");
        assertTrue(wardReady(), "Null Ward ready again at once");
        long infusions = List.of("dagger_blind", "dagger_silence", "dagger_lifesteal", "dagger_weakness").stream()
                .filter(id -> t.engine.statuses().has(p, id)).count();
        assertEquals(1, infusions, "exactly one");
    }

    @Test
    void theInfusionAppliesOnDaggerStrikes() throws IOException {
        setup();
        t.engine.statuses().apply(p, "dagger_silence", p);
        strike();
        assertTrue(t.engine.tags().has(enemy, Tags.SILENCED));
    }

    // ---- Ability 3: Counterspell ------------------------------------------------------------------

    @Test
    void theShieldEatsSpellsButNotBasicAttacksAndSlowsHer() throws IOException {
        setup();
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        assertTrue(t.engine.tags().has(p, Tags.SLOWED));
        hit(true);
        assertEquals(0, t.damage(p), 1e-9, "a spell: absorbed");
        assertEquals(100, t.engine.spellShields().charge(p), 1e-9, "stored as charge");
        hit(false);
        assertEquals(100 * 100 / 115.0, t.damage(p), 1e-9, "a basic attack still lands (less her 15 armor)");
    }

    @Test
    void recastExplodesForHalfBaseDamagePlusTheCharge() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        hit(true);
        assertTrue(t.engine.instances().awaitingRecast(p, "antimage_ab3"), "the icon glints");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        assertEquals(38 * 0.5 + 100, t.damage(enemy), 1e-9);
        assertFalse(t.engine.spellShields().has(p), "the shield is down");
        assertFalse(t.engine.tags().has(p, Tags.SLOWED));
    }

    @Test
    void atFullChargeTheExplosionStripsBuffs() throws IOException {
        setup();
        t.engine.statuses().apply(enemy, "null_rush", enemy); // a buff on the enemy
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        hit(true);
        t.time.advance(11);
        hit(true);                                           // 200 > the 150 max
        assertEquals(150, t.engine.spellShields().charge(p), 1e-9, "capped");
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        assertEquals(38 * 0.5 + 150, t.damage(enemy), 1e-9);
        assertFalse(t.engine.statuses().has(enemy, "null_rush"), "its buff is gone");
    }

    @Test
    void notRecastItExplodesByItselfWhenTimeRunsOut() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        hit(true);
        t.time.advance(59);
        assertEquals(0, t.damage(enemy), 1e-9, "not yet");
        t.time.advance(2);
        assertEquals(38 * 0.5 + 100, t.damage(enemy), 1e-9, "the 3s ran out: it went off");
        assertFalse(t.engine.spellShields().has(p), "the shield is down");
        assertFalse(t.engine.tags().has(p, Tags.SLOWED));
        t.time.advance(20);
        assertEquals(38 * 0.5 + 100, t.damage(enemy), 1e-9, "once");
        assertTrue(t.engine.cooldowns().remainingTicks(p, "antimage_ab3") > 0, "on cooldown");
    }
}

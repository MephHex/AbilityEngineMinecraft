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
 * The Gunner (fixtures/gunner.yml): 210 HP, 15 armor, base damage 38. She stands at the
 * origin on a floor, looking +x, team blue; the enemy stands 2.5 blocks in front, team red (no sheet: 200 HP,
 * no armor).
 */
class GunnerTest {


    private TestEngine t;
    private UUID p;
    private UUID enemy;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        // The enemy's test attacks: 100 damage (punch), or 100 damage + a 2s silence (hex).
        t.load(abilities(
                "punch", map("nodes", map("hit", map("type", "apply_effects",
                        "targets", map("type", "key", "key", "target"),
                        "effects", list(map("id", "damage", "amount", 100))))),
                "hex", map("nodes", map("hit", map("type", "apply_effects",
                        "targets", map("type", "key", "key", "target"),
                        "effects", list(map("id", "damage", "amount", 100),
                                map("id", "status", "status", "silence", "duration", 40)))))));
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "gunner");
        enemy = t.spawn(2.5, 1, 0);
        t.world.team(enemy, "red");
    }

    /** The enemy's attack on her, as a spell (an ability slot) or as a basic attack (primary). */
    private void enemyUses(String ability, boolean spell) {
        assertTrue(t.engine.activator().activateOnId(enemy, ability, new EntityTarget(p),
                Map.of("slot", spell ? Slots.ABILITY_1 : Slots.PRIMARY)).success());
    }

    private void hit(boolean spell) { enemyUses("punch", spell); }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private boolean wardReady() { return t.engine.wards().state(p).orElseThrow().ready(); }

    private int ammo(String resource) { return t.engine.resources().get(p, resource); }

    private int stacks(String status) {
        return t.engine.statuses().find(p, status).map(s -> s.stacks()).orElse(0);
    }

    private void shotgun() {
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
    }

    /** Press RMB (loads a bullet; nothing fires until it's let go). */
    private void pressRevolver() {
        assertTrue(t.engine.loadouts().activate(p, Slots.SECONDARY).success());
    }

    /** A click: press, let go; the bullet fires once the let-go is noticed (6 ticks without a repeat). */
    private void revolver() {
        pressRevolver();
        t.time.advance(7);
    }

    private String weapon() { return t.engine.loadouts().characterOf(p).orElseThrow().weapon(); }

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
    void theSelfStunTestCountsAsADebuff() throws IOException {
        setup();
        t.time.advance(1);
        assertTrue(t.engine.activator().activate(p, "self_stun_test").success());
        assertFalse(t.engine.tags().has(p, Tags.STUNNED), "from: world - blocked like an enemy's stun");
        assertFalse(wardReady());
        assertTrue(t.engine.activator().activate(p, "self_stun_test").success());
        assertTrue(t.engine.tags().has(p, Tags.STUNNED), "the ward is used up: this one lands");
    }

    @Test
    void buffsAndHerOwnStatusesArentDebuffs() throws IOException {
        setup();
        t.engine.statuses().apply(p, "null_rush", p);      // her own
        t.engine.statuses().apply(p, "revolver_drawn", p);  // her own, and not a buff
        t.engine.statuses().apply(p, "inspiration", enemy); // a buff (positive)
        assertTrue(t.engine.statuses().has(p, "null_rush"));
        assertTrue(t.engine.statuses().has(p, "revolver_drawn"));
        assertTrue(t.engine.statuses().has(p, "inspiration"));
        assertTrue(wardReady(), "nothing blocked, still ready");
    }

    // ---- Passive: two guns -------------------------------------------------------------------------

    @Test
    void theShotgunHitsEveryoneInItsConeAndUsesAShell() throws IOException {
        setup();
        UUID beside = foe(5, 1.5);      // 17 degrees off: inside the 45 degree cone
        UUID outside = foe(3, 3);       // 45 degrees off: outside
        UUID far = foe(9, 0);           // past 7 blocks
        assertEquals(2, ammo("shells"));
        shotgun();
        assertEquals(38 * 1.3, t.damage(enemy), 1e-9, "130% of 38");
        assertEquals(38 * 1.3, t.damage(beside), 1e-9);
        assertEquals(0, t.damage(outside), 1e-9);
        assertEquals(0, t.damage(far), 1e-9);
        assertEquals(1, ammo("shells"));
        assertTrue(t.render.lines.stream().anyMatch(l -> l[0].equals("shotgun_blast")), "drawn toward the aim");
    }

    @Test
    void anEmptyGunReloadsByItself() throws IOException {
        setup();
        shotgun();
        t.time.advance(14);
        shotgun();
        assertEquals(0, ammo("shells"));
        assertEquals(30, t.engine.resources().reloadRemaining(p, "shells"), "1.5s after the last shot");
        t.time.advance(14);
        shotgun();                                          // click: nothing
        assertEquals(2 * 38 * 1.3, t.damage(enemy), 1e-9, "a dry fire");
        assertTrue(t.render.cues.contains("dry_fire"));
        t.time.advance(16);
        assertEquals(2, ammo("shells"), "reloaded");
        assertEquals(0, t.engine.resources().reloadRemaining(p, "shells"));
    }

    @Test
    void theRevolverHitsTheFirstEnemyInLineAndComesOut() throws IOException {
        setup();
        UUID behind = foe(6, 0);
        assertEquals("NETHERITE_HOE", weapon(), "the shotgun to start with");
        revolver();
        assertEquals(38 * 0.55, t.damage(enemy), 1e-9, "55% of 38");
        assertEquals(0, t.damage(behind), 1e-9, "only the first in line");
        assertEquals(5, ammo("bullets"));
        assertTrue(t.engine.tags().has(p, "state.revolver"));
        assertEquals("IRON_HOE", weapon(), "the revolver is in her hand");

        t.time.advance(20);
        shotgun();
        assertFalse(t.engine.tags().has(p, "state.revolver"));
        assertEquals("NETHERITE_HOE", weapon(), "back to the shotgun");
        assertEquals(5, ammo("bullets"), "each gun keeps its own ammo");
        assertEquals(1, ammo("shells"));
    }

    @Test
    void aClickFiresOneBulletAndLoadsNoVolley() throws IOException {
        setup();
        revolver();
        t.time.advance(30);
        assertEquals(38 * 0.55, t.damage(enemy), 1e-9);
        assertEquals(5, ammo("bullets"));
        assertFalse(t.engine.instances().isRunning(p, "gunner_revolver"), "over");
    }

    /** RMB held: it repeats every 4 ticks, {@code repeats} times. */
    private void holdRmb(int repeats) {
        for (int i = 0; i < repeats; i++) {
            t.time.advance(4);
            assertTrue(t.engine.loadouts().activate(p, Slots.SECONDARY, false).success());
        }
    }

    @Test
    void theRevolverFiresWhenYouLetGoNotOnThePress() throws IOException {
        setup();
        pressRevolver();
        assertEquals(5, ammo("bullets"), "the press loads a bullet");
        t.time.advance(5);
        assertEquals(0, t.damage(enemy), 1e-9, "not fired yet: still might be a hold");
        t.time.advance(2);                                  // no repeat for 6 ticks: let go
        assertEquals(38 * 0.55, t.damage(enemy), 1e-9, "one bullet");
        assertEquals(5, ammo("bullets"));
    }

    @Test
    void holdingLoadsBulletsOutOfTheCylinderThenFiresThemWhenYouLetGo() throws IOException {
        setup();
        pressRevolver();                                    // 1 loaded
        holdRmb(3);                                         // held 12 ticks: +2 (one every 5)
        assertEquals(3, ammo("bullets"), "the loaded bullets come out of the cylinder: you see the count");
        holdRmb(4);                                         // held 28 ticks: all 6
        assertEquals(0, ammo("bullets"));
        assertTrue(t.engine.instances().charging(p), "still loading (waiting for the let-go)");
        assertEquals(0, t.damage(enemy), 1e-9, "nothing fired yet");
        t.time.advance(7);                                  // let go (no more repeats)
        assertFalse(t.engine.instances().charging(p));
        t.time.advance(16);                                 // the volley: a bullet every 3 ticks
        assertEquals(38 * 0.55 + 5 * 38 * 0.45, t.damage(enemy), 1e-6, "6 bullets: the first 55%, the rest 45%");
        t.time.advance(38);
        assertEquals(0, ammo("bullets"), "reloading counts from the last shot of the volley...");
        t.time.advance(3);
        assertEquals(6, ammo("bullets"), "...2s after it");
    }

    @Test
    void holdingDoesntReloadTheLoadedBulletsAway() throws IOException {
        setup();
        pressRevolver();
        holdRmb(20);                                        // all 6 loaded (gun empty), then held 2.8s more
        assertEquals(0, ammo("bullets"), "no reload while the volley is still in your hand");
        assertEquals(0, t.engine.resources().reloadRemaining(p, "bullets"), "and it doesn't say reloading");
        t.time.advance(7 + 17);
        assertEquals(38 * 0.55 + 5 * 38 * 0.45, t.damage(enemy), 1e-6);
        assertEquals(0, ammo("bullets"));
    }

    @Test
    void aShortHoldLoadsFewerAndLoadingStopsWhenTheGunIsEmpty() throws IOException {
        setup();
        pressRevolver();
        holdRmb(2);                                         // held 8 ticks: 1 + 1
        assertEquals(4, ammo("bullets"));
        t.time.advance(7 + 10);
        assertEquals(38 * 0.55 + 38 * 0.45, t.damage(enemy), 1e-6);

        t.engine.resources().set(p, "bullets", 2);          // only 2 left to load
        t.time.advance(10);
        double before = t.damage(enemy);
        pressRevolver();
        holdRmb(7);
        assertEquals(0, ammo("bullets"));
        t.time.advance(7 + 10);
        assertEquals(38 * 0.55 + 38 * 0.45, t.damage(enemy) - before, 1e-6, "only 2 were left to load");
    }

    @Test
    void anEmptyRevolverClicks() throws IOException {
        setup();
        t.engine.resources().consume(p, "bullets", 6);      // just emptied (reloading)
        pressRevolver();
        assertTrue(t.render.cues.contains("dry_fire"));
        t.time.advance(10);
        assertEquals(0, t.damage(enemy), 1e-9);
    }

    @Test
    void aSecondClickRightAwayFiresTheFirstAtOnce() throws IOException {
        setup();
        pressRevolver();
        t.time.advance(3);                                  // (no repeats: that was a click)
        assertFalse(t.engine.loadouts().activate(p, Slots.SECONDARY).success(), "too soon for another (0.4s)");
        assertEquals(38 * 0.55, t.damage(enemy), 1e-9, "but it let the first one go at once");
        t.time.advance(30);
        assertEquals(38 * 0.55, t.damage(enemy), 1e-9, "once");
        assertEquals(5, ammo("bullets"));
    }

    // ---- Ability 1: Buckshot -----------------------------------------------------------------------

    @Test
    void buckshotBlastsSlowsKnocksBackThrowsYouBackAndRefillsTheShotgun() throws IOException {
        setup();
        UUID wide = foe(4, 2.5);                            // 32 degrees off: inside the 70 degree cone
        shotgun();
        assertEquals(1, ammo("shells"));
        t.time.advance(20);
        double before = t.damage(enemy);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        assertEquals(38 * 1.6, t.damage(enemy) - before, 1e-9, "160% of 38");
        assertEquals(38 * 1.6, t.damage(wide), 1e-9);
        assertTrue(t.engine.tags().has(enemy, Tags.SLOWED));
        assertTrue(t.knockbackVec.get(enemy).x() > 0, "pushed away");
        assertTrue(t.knockbackVec.get(p).x() < 0, "the recoil throws her back");
        assertEquals(2, ammo("shells"), "refilled");
    }

    @Test
    void buckshotPutsTheShotgunBackInHand() throws IOException {
        setup();
        revolver();
        t.time.advance(20);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        assertEquals("NETHERITE_HOE", weapon());
    }

    @Test
    void buckshotAtYourFeetThrowsYouUp() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(30, 1, 30));          // (nobody in the cone)
        t.world.look(p, new Vec3(0.05, -1, 0));             // straight down: the aim is the floor under her
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        Vec3 recoil = t.knockbackVec.get(p);
        assertTrue(recoil.y() > 1.3, "a blast jump: up, " + recoil);
        assertTrue(Math.abs(recoil.x()) < 0.3, "not sideways");
    }

    // ---- Ability 2: Smoke Grenade ------------------------------------------------------------

    @Test
    void theGrenadeBlindsAndLeavesBlindingSmoke() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(4, 1, 0));             // straight at them: it bursts on contact
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
        t.time.advance(10);
        assertEquals(38 * 0.9, t.damage(enemy), 1e-9);
        assertTrue(t.engine.tags().has(enemy, Tags.BLINDED));
        assertFalse(t.engine.tags().has(enemy, Tags.SILENCED), "no silence any more");

        UUID late = foe(20, 0);
        t.time.advance(45);
        t.world.move(late, pos(enemy));                   // walks into the smoke
        t.time.advance(11);
        assertTrue(t.engine.tags().has(late, Tags.BLINDED), "standing in the smoke");
        t.world.move(late, new Vec3(20, 1, 0));
        t.time.advance(40);
        assertFalse(t.engine.tags().has(late, Tags.BLINDED), "left it");
    }

    @Test
    void theGrenadeBouncesThreeTimes() throws IOException {
        setup();
        var thrown = (me.mephisto.ability_engine.engine.nodes.gameplay.ProjectileNode)
                t.engine.abilities().find("gunner_ab2").orElseThrow().graph().node("throw");
        assertEquals(3, thrown.spec().maxBounces());
    }

    @Test
    void thePoolStaysWhereItBurstNotOnWhoeverItHit() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(4, 1, 0));             // a direct hit
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        t.time.advance(10);
        assertTrue(t.engine.tags().has(enemy, Tags.BLINDED));
        t.world.move(enemy, new Vec3(20, 1, 0));            // they run
        UUID late = foe(4, 0);                              // someone walks onto the spot
        t.time.advance(50);                                 // past the burst's 2s blind
        assertFalse(t.engine.tags().has(enemy, Tags.BLINDED), "the smoke didn't follow them");
        assertTrue(t.engine.tags().has(late, Tags.BLINDED), "it's still where it burst");
    }

    @Test
    void caughtInYourOwnGrenadeFasterAndWardReady() throws IOException {
        setup();
        t.engine.statuses().apply(p, "slow", 20, enemy); // the ward is used up
        assertFalse(wardReady());
        t.world.look(p, new Vec3(0.05, -1, 0));          // at her feet
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        t.time.advance(40);                               // a bounce, then the burst
        assertTrue(t.engine.statuses().has(p, "null_rush"), "faster");
        assertTrue(wardReady(), "Null Ward ready again at once");
        assertFalse(t.engine.tags().has(p, Tags.BLINDED), "her own smoke doesn't blind her");
    }

    // ---- Ability 3: Counterspell ------------------------------------------------------------------

    @Test
    void counterspellBlocksOneSpellDamageAndDebuffsThenRewardsYou() throws IOException {
        setup();
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        assertFalse(t.engine.tags().has(p, Tags.SLOWED), "no slow any more");
        enemyUses("hex", true);
        assertEquals(0, t.damage(p), 1e-9, "the spell's damage: blocked");
        assertFalse(t.engine.tags().has(p, Tags.SILENCED), "and its silence");
        assertTrue(t.render.cues.contains("spell_blocked"));
        t.time.advance(1);
        assertTrue(t.engine.statuses().has(p, "null_rush"), "faster");
        assertFalse(t.engine.instances().isRunning(p, "gunner_ab3"), "one spell, then it's over");

        t.time.advance(11);
        enemyUses("hex", true);
        assertEquals(100 * 100 / 115.0, t.damage(p), 1e-9, "the next spell lands (less her 15 armor)");
    }

    @Test
    void basicAttacksPassAndUnusedItRunsOutAfterTwoSeconds() throws IOException {
        setup();
        t.time.advance(1);
        t.engine.statuses().apply(p, "slow", 20, enemy);    // (use up the ward: only the counterspell counts)
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        hit(false);
        assertEquals(100 * 100 / 115.0, t.damage(p), 1e-9, "a basic attack lands");
        assertTrue(t.engine.spellShields().hasBlock(p), "and doesn't use it up");
        t.time.advance(41);
        assertFalse(t.engine.spellShields().hasBlock(p), "2s: gone");
        assertFalse(t.engine.instances().isRunning(p, "gunner_ab3"));
        t.time.advance(11);
        enemyUses("hex", true);
        assertTrue(t.engine.tags().has(p, Tags.SILENCED));
        assertFalse(t.engine.statuses().has(p, "null_rush"), "nothing blocked, no speed");
    }

    // ---- Ultimate: Powder Keg ---------------------------------------------------------------------

    /** Throw the keg at the floor 6 blocks ahead; returns where it sits. */
    private Vec3 plantKeg() {
        t.world.move(enemy, new Vec3(30, 1, 30));           // out of the way
        t.world.look(p, new Vec3(6, -1, 0));
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        for (int i = 0; i < 40 && t.engine.constructs().all().isEmpty(); i++) t.time.advance(1);
        assertEquals(1, t.engine.constructs().all().size(), "it landed");
        return t.engine.constructs().all().get(0).position();
    }

    @Test
    void theKegExplodesAfterItsFuse() throws IOException {
        setup();
        Vec3 keg = plantKeg();
        UUID near = foe(keg.x() + 1.5, keg.z());
        UUID away = foe(keg.x() + 8, keg.z());
        t.time.advance(48);
        assertEquals(0, t.damage(near), 1e-9, "fuse still burning");
        t.time.advance(3);
        assertEquals(38 * 2.0, t.damage(near), 1e-9, "200% of 38");
        assertEquals(0, t.damage(away), 1e-9);
        assertTrue(t.engine.tags().has(near, Tags.BURNING));
        assertTrue(t.knockbackVec.get(near).x() > 0, "blown away from the keg");
        assertTrue(t.render.cues.contains("keg_blast"));
        t.time.advance(60);
        assertTrue(t.damage(near) > 38 * 2.0, "the burn ticks");
    }

    @Test
    void shootingTheKegSetsItOffEarly() throws IOException {
        setup();
        Vec3 keg = plantKeg();
        UUID near = foe(keg.x(), keg.z() + 2.5);           // beside it, off the line of fire
        t.world.look(p, keg.subtract(pos(p)));
        revolver();
        assertEquals(38 * 2.0, t.damage(near), 1e-9, "shot: it went off at once");
        assertTrue(t.engine.constructs().all().isEmpty());
        t.time.advance(60);
        assertTrue(t.damage(near) < 38 * 2.0 * 2, "once");
    }

    @Test
    void theShotgunAndBuckshotSetItOffToo() throws IOException {
        setup();
        Vec3 keg = plantKeg();
        UUID near = foe(keg.x() + 3, keg.z() + 2.5);        // behind it: past the shotgun's 7 blocks
        t.world.look(p, new Vec3(1, 0, 0.3));               // roughly at it: the cone is wide
        shotgun();
        assertEquals(38 * 2.0, t.damage(near), 1e-9, "the shotgun");

        t.engine.cooldowns().clear(p, "gunner_ult1");
        keg = plantKeg();
        UUID other = foe(keg.x() + 3.5, keg.z() - 3);       // past Buckshot's 9 blocks
        t.world.look(p, new Vec3(1, 0, 0.3));
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        assertEquals(38 * 2.0, t.damage(other), 1e-9, "and Buckshot");
    }

    @Test
    void aDirectHitExplodesAtOnce() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(4, 1, 0));
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        t.time.advance(6);
        assertEquals(38 * 2.0, t.damage(enemy), 1e-9);
        assertTrue(t.engine.constructs().all().isEmpty(), "no keg left on the ground");
    }
}

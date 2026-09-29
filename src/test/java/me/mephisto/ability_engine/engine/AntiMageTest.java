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
 * The AntiMage, the gunslinger (fixtures/antimage.yml): 210 HP, 15 armor, base damage 38. She stands at the
 * origin on a floor, looking +x, team blue; the enemy stands 2.5 blocks in front, team red (no sheet: 200 HP,
 * no armor).
 */
class AntiMageTest {

    private static final List<String> ROUNDS = List.of("gun_blind", "gun_weakness", "gun_silence");

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
        t.engine.loadouts().assign(p, "antimage");
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

    private void revolver() {
        assertTrue(t.engine.loadouts().activate(p, Slots.SECONDARY).success());
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
        assertFalse(t.engine.instances().isRunning(p, "antimage_revolver"), "over");
    }

    /** RMB held: it repeats every 4 ticks, {@code repeats} times. */
    private void holdRmb(int repeats) {
        for (int i = 0; i < repeats; i++) {
            t.time.advance(4);
            assertTrue(t.engine.loadouts().activate(p, Slots.SECONDARY, false).success());
        }
    }

    @Test
    void holdingLoadsBulletsOutOfTheCylinderThenFiresThemWhenYouLetGo() throws IOException {
        setup();
        revolver();                                         // the first shot, at once
        assertEquals(5, ammo("bullets"));
        holdRmb(3);                                         // held 12 ticks: a bullet every 5 = 2 loaded
        assertEquals(3, ammo("bullets"), "the loaded bullets come out of the cylinder: you see the count");
        holdRmb(4);                                         // held 28 ticks: all 5
        assertEquals(0, ammo("bullets"));
        assertTrue(t.engine.instances().charging(p), "still loading (waiting for the let-go)");
        assertEquals(38 * 0.55, t.damage(enemy), 1e-9, "nothing more fired yet");
        t.time.advance(7);                                  // let go (no more repeats)
        assertFalse(t.engine.instances().charging(p));
        t.time.advance(15);                                 // the volley: a bullet every 3 ticks
        assertEquals(38 * 0.55 + 5 * 38 * 0.45, t.damage(enemy), 1e-6, "5 more bullets");
        t.time.advance(36);                                 // the last volley shot was 3 ticks ago + 36 = 39
        assertEquals(0, ammo("bullets"), "reloading counts from the last shot of the volley...");
        t.time.advance(2);
        assertEquals(6, ammo("bullets"), "...2s after it");
    }

    @Test
    void holdingDoesntReloadTheLoadedBulletsAway() throws IOException {
        setup();
        revolver();
        holdRmb(20);                                        // 5 loaded (gun empty), then held 2.8s more
        assertEquals(0, ammo("bullets"), "no reload while the volley is still in your hand");
        assertEquals(0, t.engine.resources().reloadRemaining(p, "bullets"), "and it doesn't say reloading");
        t.time.advance(7);
        t.time.advance(15);
        assertEquals(38 * 0.55 + 5 * 38 * 0.45, t.damage(enemy), 1e-6);
        assertEquals(0, ammo("bullets"));
    }

    @Test
    void aShortHoldLoadsFewerAndLoadingStopsWhenTheGunIsEmpty() throws IOException {
        setup();
        revolver();
        holdRmb(2);                                         // held 8 ticks: 1 bullet
        assertEquals(4, ammo("bullets"));
        t.time.advance(20);
        assertEquals(38 * 0.55 + 38 * 0.45, t.damage(enemy), 1e-6);

        t.engine.resources().set(p, "bullets", 3);          // 3 left: the first shot and 2 to load
        t.time.advance(10);
        double before = t.damage(enemy);
        revolver();
        holdRmb(7);
        assertEquals(0, ammo("bullets"));
        t.time.advance(30);
        assertEquals(38 * 0.55 + 2 * 38 * 0.45, t.damage(enemy) - before, 1e-6, "only 2 were left to load");
    }

    @Test
    void aTapLoadsNothing() throws IOException {
        setup();
        revolver();
        holdRmb(1);                                         // held 4 ticks: not a bullet yet
        t.time.advance(30);
        assertEquals(38 * 0.55, t.damage(enemy), 1e-9);
        assertEquals(5, ammo("bullets"), "nothing taken");
    }

    @Test
    void anotherClickWhileLoadingEndsTheHoldAndShootsAgain() throws IOException {
        setup();
        revolver();
        t.time.advance(10);                                 // (no repeats: that was a click)
        revolver();                                         // a fresh click
        t.time.advance(30);
        assertEquals(2 * 38 * 0.55, t.damage(enemy), 1e-6, "two plain shots, no volley");
        assertEquals(4, ammo("bullets"));
    }

    // ---- Magic rounds ------------------------------------------------------------------------------

    private void loadRounds(String status) {
        for (int i = 0; i < 3; i++) t.engine.statuses().apply(p, status, p);
    }

    @Test
    void magicRoundsApplyOnHitAndEveryShotUsesOne() throws IOException {
        setup();
        loadRounds("gun_silence");
        assertEquals(3, stacks("gun_silence"));
        shotgun();
        assertTrue(t.engine.tags().has(enemy, Tags.SILENCED), "the shotgun carries it");
        assertEquals(2, stacks("gun_silence"));
        t.engine.statuses().remove(enemy, "silence");
        t.time.advance(14);
        revolver();
        assertTrue(t.engine.tags().has(enemy, Tags.SILENCED), "and the revolver");
        assertEquals(1, stacks("gun_silence"));
        t.time.advance(20);
        t.world.look(p, new Vec3(0, 0, 1));                 // a miss still uses one up
        revolver();
        assertFalse(t.engine.statuses().has(p, "gun_silence"), "3 shots: used up");
    }

    @Test
    void sappingRoundsWeakenDamageAndAttackSpeed() throws IOException {
        setup();
        loadRounds("gun_weakness");
        shotgun();
        assertTrue(t.engine.statuses().has(enemy, "weakened"));
        assertEquals(0.7, t.engine.stats().attackSpeedMultiplier(enemy), 1e-9);
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
    void buckshotUsesAMagicRoundAndPutsTheShotgunBackInHand() throws IOException {
        setup();
        revolver();
        loadRounds("gun_blind");
        t.time.advance(20);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        assertTrue(t.engine.statuses().has(enemy, "blinded"));
        assertEquals(2, stacks("gun_blind"));
        assertEquals("NETHERITE_HOE", weapon());
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

        UUID late = foe(20, 0);
        t.time.advance(45);
        t.world.move(late, pos(enemy));                   // walks into the pool
        t.time.advance(11);
        assertTrue(t.engine.tags().has(late, Tags.SILENCED), "standing in the pool");
        t.world.move(late, new Vec3(20, 1, 0));
        t.time.advance(40);
        assertFalse(t.engine.tags().has(late, Tags.SILENCED), "left it");
    }

    @Test
    void thePoolStaysWhereItBurstNotOnWhoeverItHit() throws IOException {
        setup();
        t.world.move(enemy, new Vec3(4, 1, 0));             // a direct hit
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        t.time.advance(10);
        assertTrue(t.engine.tags().has(enemy, Tags.SILENCED));
        t.world.move(enemy, new Vec3(20, 1, 0));            // they run
        UUID late = foe(4, 0);                              // someone walks onto the spot
        t.time.advance(50);                                 // past the burst's 2s silence
        assertFalse(t.engine.tags().has(enemy, Tags.SILENCED), "the pool didn't follow them");
        assertTrue(t.engine.tags().has(late, Tags.SILENCED), "it's still on the ground where it burst");
    }

    @Test
    void caughtInYourOwnFlaskFasterWardReadyAndThreeMagicRounds() throws IOException {
        setup();
        t.engine.statuses().apply(p, "slow", 20, enemy); // the ward is used up
        assertFalse(wardReady());
        t.world.look(p, new Vec3(0.05, -1, 0));          // at her feet
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        t.time.advance(40);                               // a bounce, then the burst
        assertTrue(t.engine.statuses().has(p, "null_rush"), "faster");
        assertTrue(wardReady(), "Null Ward ready again at once");
        List<String> loaded = ROUNDS.stream().filter(id -> t.engine.statuses().has(p, id)).toList();
        assertEquals(1, loaded.size(), "one kind");
        assertEquals(3, stacks(loaded.get(0)), "3 shots");
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
        List<String> loaded = ROUNDS.stream().filter(id -> t.engine.statuses().has(p, id)).toList();
        assertEquals(1, loaded.size());
        assertEquals(3, stacks(loaded.get(0)));
        assertFalse(t.engine.instances().isRunning(p, "antimage_ab3"), "one spell, then it's over");

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
        assertFalse(t.engine.instances().isRunning(p, "antimage_ab3"));
        t.time.advance(11);
        enemyUses("hex", true);
        assertTrue(t.engine.tags().has(p, Tags.SILENCED));
        assertTrue(ROUNDS.stream().noneMatch(id -> t.engine.statuses().has(p, id)), "nothing blocked, no rounds");
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

        t.engine.cooldowns().clear(p, "antimage_ult1");
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

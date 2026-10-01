package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.combat.DamageModifiers;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.FakeWorld;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Fae (fixtures/fae.yml): 160 HP, 5 armor, base damage 35. She stands at the origin on a floor, looking
 * +x, team blue. Enemies are team red (no sheet: 200 HP, no armor), allies team blue.
 */
class FaeTest {

    private static final double BASE = 35;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "fae");
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

    private void lookAt(UUID id) { t.world.look(p, pos(id).subtract(pos(p))); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private int running(String ability) {
        return (int) t.engine.instances().of(p).stream().filter(i -> i.ability().id().equals(ability)).count();
    }

    // ---- Passives ---------------------------------------------------------------------------------------

    @Test
    void sheIsSmallAndSitsInHerBlossom() throws IOException {
        setup();
        assertEquals(0.5, t.engine.stats().of(p).scale(), 1e-9);
        var hover = t.engine.loadouts().characterOf(p).orElseThrow().hover();
        assertEquals(0, hover.height(), 1e-9, "no hover: she walks as usual");
        assertEquals("SPORE_BLOSSOM", hover.visual());
    }

    @Test
    void anEnemyHitSetsOffTheSporeBurstThenItRecharges() throws IOException {
        setup();
        UUID near = foe(2, 0);
        UUID far = foe(8, 0);
        UUID pal = friend(-2, 0);
        assertTrue(t.engine.wards().state(p).orElseThrow().ready());
        DamageModifiers.apply(t.engine, near, p, 30);
        assertFalse(t.engine.wards().state(p).orElseThrow().ready(), "used up");
        assertFalse(t.knockback.containsKey(near), "the next tick");
        t.time.advance(1);
        assertTrue(t.knockback.containsKey(near), "knocked back");
        assertFalse(t.knockback.containsKey(far), "out of reach");
        assertFalse(t.knockback.containsKey(pal), "allies aren't");
        assertTrue(t.render.cues.contains("fae_spore_burst"));

        t.knockback.clear();
        DamageModifiers.apply(t.engine, near, p, 30);
        t.time.advance(1);
        assertFalse(t.knockback.containsKey(near), "not ready yet");

        t.time.advance(78);
        assertFalse(t.engine.wards().state(p).orElseThrow().ready());
        t.time.advance(2);
        assertTrue(t.engine.wards().state(p).orElseThrow().ready(), "4s out of combat");
    }

    @Test
    void inAFightTheSporeBurstComesBackAfterItsCooldown() throws IOException {
        setup();
        UUID near = foe(2, 0);
        DamageModifiers.apply(t.engine, near, p, 30);
        for (int i = 0; i < 9; i++) { // hit every 2s: never out of combat
            t.time.advance(20);
            DamageModifiers.apply(t.engine, p, near, 10);
        }
        t.time.advance(19);
        assertFalse(t.engine.wards().state(p).orElseThrow().ready());
        t.time.advance(2);
        assertTrue(t.engine.wards().state(p).orElseThrow().ready(), "10s after it went off");
    }

    @Test
    void fallsAndAlliesDontSetOffTheSporeBurst() throws IOException {
        setup();
        UUID pal = friend(2, 0);
        DamageModifiers.apply(t.engine, null, p, 30);
        DamageModifiers.apply(t.engine, pal, p, 30);
        assertTrue(t.engine.wards().state(p).orElseThrow().ready());
    }

    // ---- Primary: Blossom Shot ------------------------------------------------------------------------

    @Test
    void theBlossomBurstsOnWhoItHits() throws IOException {
        setup();
        UUID target = foe(5, 0);
        UUID beside = foe(5, 1.5);
        UUID pal = friend(5, -1.5);
        use(Slots.PRIMARY);
        t.time.advance(10);
        assertEquals(0.9 * BASE, t.damage(target), 1e-9, "90% base damage");
        assertEquals(0.9 * BASE, t.damage(beside), 1e-9, "within 2.2 blocks");
        assertEquals(0, t.damage(pal), 1e-9);
    }

    // ---- 1: Seed Bomb ------------------------------------------------------------------------------------

    @Test
    void aSeedLatchesOntoAnEnemyAndBurstsTwoSecondsLater() throws IOException {
        setup();
        UUID target = foe(3, 0);
        UUID beside = foe(3, 3);
        use(Slots.ABILITY_1);
        t.time.advance(10);
        assertEquals(0, t.damage(target), 1e-9, "latched, not yet");
        assertTrue(t.render.loops.contains("fae_seed_latched"));
        t.time.advance(40);
        assertEquals(1.1 * BASE, t.damage(target), 1e-9, "110% base damage");
        assertEquals(1.1 * BASE, t.damage(beside), 1e-9, "within 3.5 blocks of them");
        assertFalse(t.render.loops.contains("fae_seed_latched"), "gone with it");
    }

    @Test
    void aSeedLatchesOntoAnAllyAndHealsAroundThem() throws IOException {
        setup();
        UUID pal = friend(3, 0);
        UUID foeNear = foe(3, 2);
        use(Slots.ABILITY_1);
        t.time.advance(50);
        assertEquals(50, t.healed.getOrDefault(pal, 0.0), 1e-9, "5 HP");
        assertEquals(50, t.healed.getOrDefault(p, 0.0), 1e-9, "her too (within 3.5 blocks)");
        assertEquals(0, t.healed.getOrDefault(foeNear, 0.0), 1e-9);
        assertEquals(0, t.damage(foeNear), 1e-9, "an ally's seed doesn't burst");
    }

    @Test
    void aSeedOnTheGroundLatchesOntoWhoeverComesNear() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -1, 0));
        use(Slots.ABILITY_1);
        t.time.advance(10);
        assertEquals(1, t.engine.constructs().activeCount(), "lying there");
        assertTrue(t.render.cues.contains("fae_seed_idle"), "shimmering green while it lies there");
        Vec3 seed = t.engine.constructs().all().get(0).position();
        UUID pal = friend(seed.x(), seed.z());
        t.time.advance(2);
        assertEquals(0, t.engine.constructs().activeCount(), "an ally picked it up");
        t.time.advance(40);
        assertEquals(50, t.healed.getOrDefault(pal, 0.0), 1e-9);

        t.world.move(pal, new Vec3(30, 1, 0));
        use(Slots.ABILITY_1); // the second charge
        t.time.advance(10);
        seed = t.engine.constructs().all().get(0).position();
        UUID target = foe(seed.x(), seed.z());
        t.time.advance(45);
        assertEquals(1.1 * BASE, t.damage(target), 1e-9, "an enemy's bursts");
    }

    @Test
    void herOwnSeedLatchesOntoHerToo() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -1, 0));
        use(Slots.ABILITY_1);
        t.time.advance(10);
        Vec3 seed = t.engine.constructs().all().get(0).position();
        t.world.move(p, new Vec3(seed.x(), 1, seed.z()));
        t.time.advance(2);
        assertEquals(0, t.engine.constructs().activeCount(), "she picked it up");
        t.time.advance(40);
        assertEquals(50, t.healed.getOrDefault(p, 0.0), 1e-9, "it blooms on her: 5 HP");
    }

    @Test
    void threeSeedsAtATime() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -1, 0));
        for (int i = 0; i < 3; i++) use(Slots.ABILITY_1);
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_1).success(), "out of charges");
        t.time.advance(10);
        assertEquals(3, t.engine.constructs().activeCount());
    }

    // ---- 2: Perch -----------------------------------------------------------------------------------------

    /** Cast Perch at an ally and wait until she's sitting on them. */
    private UUID perchOnNewFriend() {
        UUID pal = friend(6, 0);
        use(Slots.ABILITY_2);
        t.time.advance(10);
        assertEquals(pal, t.world.riding.get(p), "she flew over and sat on them");
        return pal;
    }

    @Test
    void sheFliesToAnAllyAndPerchesOnTheirHead() throws IOException {
        setup();
        UUID pal = perchOnNewFriend();
        assertTrue(has(pal, "fae_blessing"));
        assertEquals(1.2, t.engine.stats().moveSpeedMultiplier(pal), 1e-9, "20% faster");
        assertTrue(t.engine.tags().has(p, Tags.UNTARGETABLE), "untouchable");
        assertTrue(t.engine.tags().has(p, Tags.HIDDEN), "hidden inside her blossom");
        assertEquals(new Vec3(6, 1 + FakeWorld.RIDE_HEIGHT, 0), pos(p));
        t.world.move(pal, new Vec3(10, 1, 4));
        assertEquals(new Vec3(10, 1 + FakeWorld.RIDE_HEIGHT, 4), pos(p), "she goes where they go");
        assertTrue(t.engine.cooldowns().remainingTicks(p, "fae_ab2") > 90, "the cooldown started as she sat down");
        assertTrue(t.engine.instances().awaitingRecast(p, "fae_ab2"));
    }

    @Test
    void nothingHurtsHerUpThere() throws IOException {
        setup();
        perchOnNewFriend();
        UUID enemy = foe(3, 0);
        var hit = DamageModifiers.apply(t.engine, enemy, p, 100);
        assertEquals(0, hit.amount(), 1e-9);
        assertTrue(t.engine.wards().state(p).orElseThrow().ready(), "nothing got through: no spore burst");
    }

    @Test
    void herAllysBasicAttacksHitHarder() throws IOException {
        setup();
        UUID pal = perchOnNewFriend();
        assertEquals(1, t.engine.statusDefs().require("fae_blessing").onHit().size(), "an on-hit buff");
        assertTrue(t.engine.statusDefs().require("fae_blessing").positive());
        assertTrue(has(pal, "fae_blessing"));
    }

    @Test
    void perchReachesTwentyBlocks() throws IOException {
        setup();
        friend(22, 0);
        use(Slots.ABILITY_2);
        assertEquals(0, running("fae_ab2"), "22 blocks: too far");
    }

    @Test
    void perchForgivesAnAimSlightlyOff() throws IOException {
        setup();
        UUID pal = friend(10, 1); // a block to the side of where she looks (+x)
        use(Slots.ABILITY_2);
        t.time.advance(15);
        assertEquals(pal, t.world.riding.get(p), "the thick ray caught them");
    }

    @Test
    void noAllyInSightNothingHappens() throws IOException {
        setup();
        foe(6, 0);
        use(Slots.ABILITY_2);
        t.time.advance(10);
        assertTrue(t.world.riding.isEmpty());
        assertEquals(0, running("fae_ab2"));
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "fae_ab2"), "nothing spent");
    }

    @Test
    void pressingAgainGivesHerAllyAGust() throws IOException {
        setup();
        UUID pal = perchOnNewFriend();
        t.world.look(p, new Vec3(1, 0, 0)); // nobody in sight
        t.time.advance(200); // the cooldown's over
        use(Slots.ABILITY_2);
        assertTrue(has(pal, "fae_gust"), "a burst of speed");
        assertEquals(1.2 * 1.35, t.engine.stats().moveSpeedMultiplier(pal), 1e-9);
        assertEquals(100, t.engine.cooldowns().remainingTicks(p, "fae_ab2"), "a gust starts the cooldown again");
        t.time.advance(41);
        assertFalse(has(pal, "fae_gust"), "2s");
        use(Slots.ABILITY_2);
        assertFalse(has(pal, "fae_gust"), "on cooldown: no second gust");
        assertEquals(pal, t.world.riding.get(p), "still up there");
        t.time.advance(60);
        use(Slots.ABILITY_2);
        assertTrue(has(pal, "fae_gust"), "5s after the first");
    }

    @Test
    void shiftHopsHerOff() throws IOException {
        setup();
        UUID pal = perchOnNewFriend();
        t.engine.rides().hopOff(p); // SHIFT (Bukkit's RideGuard)
        t.time.advance(1);
        assertNull(t.world.riding.get(p), "off");
        assertFalse(has(pal, "fae_blessing"));
        assertFalse(t.engine.tags().has(p, Tags.UNTARGETABLE));
        assertFalse(t.engine.tags().has(p, Tags.HIDDEN), "out of her blossom: seen again");
        assertEquals(0, running("fae_ab2"));
        assertTrue(t.render.cues.contains("fae_unperch"));
        assertTrue(t.engine.cooldowns().remainingTicks(p, "fae_ab2") > 90, "the cooldown from sitting down runs on");
    }

    @Test
    void aTapDoesntHopHerOff() throws IOException {
        setup();
        UUID pal = perchOnNewFriend();
        use(Slots.ABILITY_2);
        t.time.advance(60);
        assertEquals(pal, t.world.riding.get(p));
    }

    @Test
    void aimingAtAnotherAllySheFliesOverToThem() throws IOException {
        setup();
        UUID first = perchOnNewFriend();
        UUID second = friend(6, 8);
        t.time.advance(200); // the cooldown's over
        lookAt(second);
        use(Slots.ABILITY_2);
        assertNull(t.world.riding.get(p), "off the first...");
        assertFalse(has(first, "fae_blessing"));
        assertFalse(t.engine.tags().has(p, Tags.UNTARGETABLE), "...and vulnerable on the way");
        t.time.advance(15);
        assertEquals(second, t.world.riding.get(p), "...onto the second");
        assertTrue(has(second, "fae_blessing"));
        assertTrue(t.engine.tags().has(p, Tags.UNTARGETABLE));
        assertEquals(1, running("fae_ab2"));
        assertTrue(t.engine.cooldowns().remainingTicks(p, "fae_ab2") > 90, "sitting down again: the cooldown again");
    }

    @Test
    void pressingAgainWaitsForTheCooldown() throws IOException {
        setup();
        UUID first = perchOnNewFriend();
        UUID second = friend(6, 8);
        lookAt(second);
        use(Slots.ABILITY_2);
        t.time.advance(15);
        assertEquals(first, t.world.riding.get(p), "on cooldown: no hopping over (no spamming)");
        assertFalse(has(first, "fae_gust"), "and no gust");
        t.time.advance(180);
        use(Slots.ABILITY_2);
        t.time.advance(15);
        assertEquals(second, t.world.riding.get(p), "the cooldown's over: off to the second");
    }

    @Test
    void whenHerAllyDiesSheFallsOff() throws IOException {
        setup();
        UUID pal = perchOnNewFriend();
        t.world.kill(pal);
        t.time.advance(2);
        assertNull(t.world.riding.get(p));
        assertFalse(t.engine.tags().has(p, Tags.UNTARGETABLE));
        assertEquals(0, running("fae_ab2"));
        assertTrue(t.engine.cooldowns().remainingTicks(p, "fae_ab2") > 90);
    }

    @Test
    void ifTheGameTakesHerOffTheRideEnds() throws IOException {
        setup();
        UUID pal = perchOnNewFriend();
        t.world.eject(p);
        t.time.advance(2);
        assertFalse(has(pal, "fae_blessing"));
        assertEquals(0, running("fae_ab2"));
    }

    @Test
    void holdingTheKeyOnTheWayDoesntStartASecondPerch() throws IOException {
        setup();
        friend(12, 0);
        use(Slots.ABILITY_2);
        t.time.advance(1);
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_2, false).success(), "a held key's repeat");
        assertEquals(1, running("fae_ab2"));
    }

    // ---- 3: Deathcap Snare -------------------------------------------------------------------------------

    @Test
    void theSnareIsHiddenAndSpringsOnAnEnemy() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -1, 0));
        use(Slots.ABILITY_3);
        t.time.advance(10);
        var trap = t.engine.constructs().all().get(0);
        assertTrue(trap.hidden(), "enemies can't see it");
        Vec3 at = trap.position();
        UUID pal = friend(at.x(), at.z());
        t.time.advance(30);
        assertEquals(1, t.engine.constructs().activeCount(), "allies don't set it off");
        t.world.move(pal, new Vec3(30, 1, 0));
        UUID target = foe(at.x(), at.z());
        UUID beside = foe(at.x() + 2, at.z());
        t.time.advance(1);
        assertEquals(0, t.engine.constructs().activeCount(), "sprung");
        assertEquals(BASE, t.damage(target), 1e-9, "100% base damage");
        assertEquals(BASE, t.damage(beside), 1e-9, "within 3 blocks");
        assertTrue(has(target, "fae_toxin"));
        assertTrue(t.engine.tags().has(target, Tags.POISONED));
        assertTrue(t.engine.tags().has(target, "state.poison_hearts"), "green hearts");
        assertTrue(t.engine.tags().has(target, Tags.NAUSEOUS));
        assertEquals(0.65, t.engine.stats().moveSpeedMultiplier(target), 1e-9, "35% slower");
        t.time.advance(80);
        assertEquals(BASE + 4 * 0.02 * 200, t.damage(target), 1e-9, "poison: 2% max HP a second for 4s");
    }

    @Test
    void theSnareArmsAfterASecond() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -1, 0));
        use(Slots.ABILITY_3);
        t.time.advance(4);
        Vec3 at = t.engine.constructs().all().get(0).position();
        UUID target = foe(at.x(), at.z());
        t.time.advance(5);
        assertEquals(0, t.damage(target), 1e-9, "not armed yet");
        t.time.advance(20);
        assertEquals(BASE, t.damage(target), 1e-9);
    }

    @Test
    void aSnareLastsTwoMinutes() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -1, 0));
        use(Slots.ABILITY_3);
        t.time.advance(2390);
        assertEquals(1, t.engine.constructs().activeCount(), "still there after a long while");
        t.time.advance(20);
        assertEquals(0, t.engine.constructs().activeCount(), "wilted after 2 min");
    }

    @Test
    void aSnareThrownAtOneAlreadyPlantedBouncesOffIt() throws IOException {
        setup();
        t.world.look(p, new Vec3(1, -1, 0));
        use(Slots.ABILITY_3);
        t.time.advance(250); // planted, and the cooldown's over
        Vec3 first = t.engine.constructs().all().get(0).position();
        use(Slots.ABILITY_3); // the same throw: right at it
        t.time.advance(40);
        assertEquals(2, t.engine.constructs().activeCount());
        Vec3 second = t.engine.constructs().all().get(1).position();
        assertTrue(second.distance(first) > 0.5, "it glanced off the first and landed elsewhere");
    }

    // ---- Ultimate: Wild Hunt ---------------------------------------------------------------------------

    @Test
    void wildHuntGivesFreeFlightThenLatchesOntoAnEnemyAndDragsThem() throws IOException {
        setup();
        UUID target = foe(6, 0);
        t.world.look(p, new Vec3(0, 1, 0)); // nobody in sight yet
        use(Slots.ULTIMATE);
        assertTrue(t.engine.tags().has(p, Tags.FLYING), "free flight");
        lookAt(target);
        use(Slots.ULTIMATE);
        assertTrue(t.render.cues.contains("fae_vine_shot"), "the vine shoots out");
        assertFalse(t.engine.tags().has(target, Tags.STUNNED), "a skillshot: on its way");
        t.time.advance(4);
        assertTrue(t.engine.tags().has(target, Tags.STUNNED), "caught: stunned");
        assertTrue(has(p, "fae_dragging"));

        t.world.move(p, new Vec3(0, 20, 0)); // she flies up
        Vec3 before = pos(target);
        t.time.advance(10);
        assertTrue(pos(target).y() > before.y() + 5, "dragged up after her");
        assertTrue(pos(target).distance(pos(p)) < pos(p).distance(before), "closer to her");
        assertTrue(t.render.lines.stream().anyMatch(l -> "fae_vine".equals(l[0])), "the vine is drawn");

        t.time.advance(65);
        assertFalse(has(p, "fae_dragging"), "3.5s, then let go");
        assertEquals(0, running("fae_ult1"));
    }

    @Test
    void wildHuntMissingKeepsTheFlightGoing() throws IOException {
        setup();
        t.world.look(p, new Vec3(0, 1, 0));
        use(Slots.ULTIMATE);
        use(Slots.ULTIMATE); // nobody there
        t.time.advance(30); // the vine flew its 20 blocks
        assertEquals(1, running("fae_ult1"), "shoot again");
        t.time.advance(131);
        assertEquals(0, running("fae_ult1"), "8s");
        assertFalse(t.engine.tags().has(p, Tags.FLYING));
    }

    @Test
    void wildHuntsVineHasASmallHitbox() throws IOException {
        setup();
        UUID target = foe(10, 1.8);
        t.world.look(p, new Vec3(0, 1, 0));
        use(Slots.ULTIMATE);
        t.world.look(p, new Vec3(1, 0, 0)); // 1.8 blocks past them
        use(Slots.ULTIMATE);
        t.time.advance(8);
        assertFalse(t.engine.tags().has(target, Tags.STUNNED), "a 1x1 hitbox: it has to be aimed");
    }

    @Test
    void wildHuntCanShootAgainAfterAMiss() throws IOException {
        setup();
        UUID target = foe(8, 0);
        t.world.look(p, new Vec3(0, 1, 0));
        use(Slots.ULTIMATE);
        use(Slots.ULTIMATE); // up into the sky: a miss
        t.time.advance(25);
        assertFalse(t.engine.tags().has(target, Tags.STUNNED));
        lookAt(target);
        use(Slots.ULTIMATE);
        t.time.advance(6);
        assertTrue(t.engine.tags().has(target, Tags.STUNNED), "the second vine caught them");
    }

    @Test
    void wildHuntTakesHerOffHerAlly() throws IOException {
        setup();
        UUID pal = perchOnNewFriend();
        use(Slots.ULTIMATE);
        assertNull(t.world.riding.get(p));
        assertFalse(has(pal, "fae_blessing"));
        assertTrue(t.engine.tags().has(p, Tags.FLYING));
    }
}

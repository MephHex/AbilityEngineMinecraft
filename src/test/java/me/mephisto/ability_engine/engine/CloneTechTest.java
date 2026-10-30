package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clone and steal tech (Changeling's mirroring clone and its modes, Stolen Fable), through the test-only kit that
 * still uses them (fixtures/changeling_kit.yml: the Fae Trickster's first kit, under its own ids). The caster stands at
 * x=0 on a floor at y=0, facing +x; enemies are on team red.
 */
class CloneTechTest {

    private TestEngine t;
    private UUID p;
    private final List<String> told = new ArrayList<>();

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.world.players.add(p);
        t.engine.loadouts().assign(p, "changeling_kit");
        t.engine.setNotifier((who, text) -> told.add(text));
    }

    private UUID enemyAt(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        t.world.players.add(e);
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private void untilDone() {
        for (int i = 0; i < 200 && t.engine.instances().of(p).stream().anyMatch(x -> !x.ability().id().equals("ck_ab2")
                && !x.ability().id().equals("ck_ab3")); i++) {
            t.time.advance(1);
        }
    }

    /** How many times a looping cue was started (on anyone). */
    private long started(String id) { return t.render.started.stream().filter(l -> id.equals(l[0])).count(); }

    /** Time goes on until {@code target} has taken some damage (at most 20 ticks). */
    private void untilHurt(UUID target) {
        for (int i = 0; i < 20 && t.damage(target) == 0; i++) t.time.advance(1);
    }

    /** How many times a line cue was drawn. */
    private long lineCue(String id) { return t.render.lines.stream().filter(l -> id.equals(l[0])).count(); }

    private boolean silenced(UUID id) { return t.engine.tags().has(id, Tags.SILENCED); }

    private void swipe() {
        t.time.advance(15); // (past the swipe's cooldown)
        t.engine.loadouts().activate(p, Slots.PRIMARY);
    }

    // ---- (what the clone needs from the other abilities) ----------------------------------------

    @Test
    void swipesOnAnEnemyClonesDontTakeTimeOffRoughPlay() throws IOException {
        setup();
        UUID rival = enemyAt(20, 0);
        t.engine.loadouts().assign(rival, "changeling_kit");
        t.engine.loadouts().activate(rival, Slots.ABILITY_3);
        t.time.advance(11);
        UUID clone = t.engine.summons().find(rival, "changeling").orElseThrow();
        t.world.move(rival, new Vec3(40, 1, 0));       // (the rival itself out of reach)
        t.world.move(p, pos(clone).add(-2, 0.1, 0));   // the clone right in front of her
        t.engine.cooldowns().start(p, "ck_ab1", 120);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertTrue(t.damage(clone) > 0, "she hit the clone");
        assertEquals(120, t.engine.cooldowns().remainingTicks(p, "ck_ab1"), "no time off");
    }

    // ---- 2: Trick Room ---------------------------------------------------------------------------

    /**
     * An 11x11 room right where she stands: looking straight down (a level dash has nowhere to go), and her put back
     * where she was. On the block grid: centred on (0.5, 0.5), so x and z run from -5 to 6.
     */
    private void room() {
        t.world.look(p, new Vec3(0, -1, 0));
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
        t.time.advance(3);
        t.world.move(p, new Vec3(0, 1, 0));
        t.world.look(p, new Vec3(1, 0, 0));
        t.time.advance(2); // the floor's first check puts its status on her
    }

    // ---- 3: Changeling ---------------------------------------------------------------------------

    /** Cast it and wait out the vanish: the clone, right where she is, facing the other way. */
    private UUID castClone() {
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.time.advance(11);
        return t.engine.summons().find(p, "changeling").orElseThrow();
    }

    @Test
    void sheVanishesThenReappearsWithHerCloneRightWhereSheIs() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        assertTrue(t.engine.tags().has(p, Tags.HIDDEN), "gone from sight...");
        assertTrue(t.engine.tags().has(p, Tags.UNTARGETABLE));
        assertFalse(t.engine.loadouts().activate(p, Slots.ABILITY_1).success(), "...and can't act meanwhile");
        assertTrue(t.engine.summons().find(p, "changeling").isEmpty(), "no clone yet");
        t.time.advance(11);
        assertFalse(t.engine.tags().has(p, Tags.HIDDEN), "back after 0.5s");
        assertFalse(t.engine.tags().has(p, Tags.UNTARGETABLE));
        UUID clone = t.engine.summons().find(p, "changeling").orElseThrow();
        assertEquals(0, pos(clone).distance(pos(p)), 1e-6, "right inside her: no telling which is which");
        assertEquals(0, new Vec3(-1, 0, 0).distance(t.world.facing.get(clone)), 1e-9, "facing the other way");
        assertEquals(2, t.render.cues.stream().filter("trickster_clone_place"::equals).count(), "both appear together");
    }

    /** She holds keys asking for this walk (blocks a tick) for {@code ticks} ticks, moving that much, then lets go. */
    private void walk(Vec3 move, int ticks) {
        t.world.input(p, move, false);
        for (int i = 0; i < ticks; i++) {
            t.world.move(p, pos(p).add(move));
            t.time.advance(1);
        }
        t.world.input(p, Vec3.ZERO, false);
    }

    @Test
    void theCloneDoesEverythingTurnedRound() throws IOException {
        setup();
        UUID clone = castClone();
        Vec3 start = pos(clone);
        walk(new Vec3(0.2, 0, 0), 1);                  // she walks ahead...
        assertEquals(0, start.add(-0.2, 0, 0).distance(pos(clone)), 1e-9, "...so does it, its way");
        walk(new Vec3(0, 0, 0.2), 1);                  // a step to her left...
        assertEquals(0, start.add(-0.2, 0, -0.2).distance(pos(clone)), 1e-9, "...it steps to its left");
        t.world.input(p, Vec3.ZERO, true);             // a jump
        t.time.advance(1);
        t.world.input(p, Vec3.ZERO, false);
        assertEquals(start.y() + 0.42, pos(clone).y(), 1e-9, "jumps as they are");
        t.world.look(p, new Vec3(1, 0.5, 0.3));
        t.time.advance(1);
        assertEquals(0, new Vec3(-1, 0.5, -0.3).normalize().distance(t.world.facing.get(clone)), 1e-9, "looks its way");
    }

    @Test
    void walkingTowardEachOtherTheyCross() throws IOException {
        setup();
        UUID clone = castClone();
        walk(new Vec3(0.5, 0, 0), 6);                  // apart: 3 blocks each way
        assertEquals(3, pos(p).x(), 1e-9);
        assertEquals(-3, pos(clone).x(), 1e-9);
        walk(new Vec3(-0.5, 0, 0), 12);                // she walks at it: it walks at her
        assertEquals(-3, pos(p).x(), 1e-9);
        assertEquals(3, pos(clone).x(), 1e-9, "they crossed");
    }

    @Test
    void beingKnockedAroundIsntCopied() throws IOException {
        setup();
        UUID clone = castClone();
        Vec3 start = pos(clone);
        for (int i = 0; i < 4; i++) {                  // shoved 0.5 a tick, no keys held
            t.world.move(p, pos(p).add(-0.5, 0.2, 0.3));
            t.time.advance(1);
        }
        assertEquals(start, pos(clone), "she didn't move herself: neither does it");
    }

    @Test
    void rootedHerKeysMoveNothing() throws IOException {
        setup();
        UUID clone = castClone();
        Vec3 start = pos(clone);
        t.engine.statuses().apply(p, "root", 40, null);
        t.world.input(p, new Vec3(0.2, 0, 0), true);   // holding W and jump, going nowhere
        t.time.advance(5);
        assertEquals(start, pos(clone));
    }

    @Test
    void theCloneCrouchesWithHer() throws IOException {
        setup();
        UUID clone = castClone();
        t.world.input(p, Vec3.ZERO, false, true);      // SHIFT
        t.time.advance(1);
        assertTrue(t.world.crouching.contains(clone));
        t.world.input(p, Vec3.ZERO, false, false);
        t.time.advance(1);
        assertFalse(t.world.crouching.contains(clone));
    }

    @Test
    void theCloneDoesntCopyTeleports() throws IOException {
        setup();
        UUID clone = castClone();
        Vec3 start = pos(clone);
        t.time.advance(1);
        t.world.move(p, pos(p).add(10, 0, 0));         // a blink of some kind
        t.time.advance(1);
        assertEquals(start, pos(clone));
    }

    @Test
    void theCloneSwingsWithHer() throws IOException {
        setup();
        UUID clone = castClone();
        t.engine.mirrors().swung(p);                    // her arm swing (the platform saw it)
        assertEquals(List.of(clone), t.world.swings);
    }

    @Test
    void herBasicAttacksMakeTheCloneSwingInEveryModeOnceEach() throws IOException {
        setup();
        UUID clone = castClone();
        t.engine.loadouts().activate(p, Slots.PRIMARY); // a swipe (at nothing)
        t.engine.mirrors().swung(p);                    // ...and the arm swing it came with: the same swing
        assertEquals(List.of(clone), t.world.swings, "copied, once");
        for (int mode = 0; mode < 2; mode++) {          // still, then puppet
            nextMode();
            t.time.advance(15);
            t.engine.loadouts().activate(p, Slots.PRIMARY);
        }
        assertEquals(3, t.world.swings.size(), "still and puppet swing along too");
    }

    @Test
    void theCloneFakesRoughPlayWithoutDamage() throws IOException {
        setup();
        UUID clone = castClone();
        t.world.move(clone, new Vec3(0, 1, 10));       // off to the side, on its own
        t.time.advance(1);
        UUID nearClone = enemyAt(-2, 10);              // in front of it (it faces -x)
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        untilDone();
        assertEquals(0, t.damage(nearClone), 1e-9, "for show");
        assertFalse(silenced(nearClone));
        assertEquals(4, started("trickster_rough_play"), "both of hers and both of its");
    }

    @Test
    void theClonesFakeLungesGoItsWay() throws IOException {
        setup();
        UUID clone = castClone();
        t.world.move(clone, new Vec3(0, 1, 6));
        t.time.advance(1);
        t.world.look(p, new Vec3(0, 0, 1));            // she lunges toward it...
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        untilDone();
        assertEquals(4, pos(clone).z(), 0.05, "...it lunges toward her (2 blocks)");
        assertTrue(t.render.started.stream().anyMatch(l -> "trickster_rough_play".equals(l[0]) && clone.equals(l[1])),
                "its slashes drawn on it");
    }

    @Test
    void inTheRoomTheCloneTeleportsTurnedRoundAndStopsAtTheEdge() throws IOException {
        setup();
        room();                                        // x and z from -5 to 6
        UUID clone = castClone();
        t.time.advance(1);
        assertTrue(t.engine.floors().glide(p, new Vec3(1, 0, 0)));
        assertEquals(5.65, pos(p).x(), 0.11, "her: 6 blocks ahead, stopped inside the edge");
        assertEquals(-4.65, pos(clone).x(), 0.11, "it: its way, stopped inside the room's edge (not 6)");
        t.time.advance(1);
        assertEquals(-4.65, pos(clone).x(), 0.11, "and her teleport isn't copied as a step");
    }

    @Test
    void outsideTheRoomTheCloneDoesntTeleport() throws IOException {
        setup();
        room();
        UUID clone = castClone();
        t.world.move(clone, new Vec3(20, 1, 0));       // out of the room
        t.time.advance(1);
        Vec3 at = pos(clone);
        assertTrue(t.engine.floors().glide(p, new Vec3(1, 0, 0)));
        t.time.advance(1);
        assertEquals(at, pos(clone));
    }

    /** 3 again while the clone is out: its next mode. */
    private void nextMode() {
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
    }

    @Test
    void pressingThreeAgainCyclesTheClonesModeAndSaysWhich() throws IOException {
        setup();
        UUID clone = castClone();
        assertEquals(me.mephisto.ability_engine.engine.summon.Mirrors.Mode.COPY, t.engine.mirrors().mode(p));
        nextMode();
        assertEquals(me.mephisto.ability_engine.engine.summon.Mirrors.Mode.STILL, t.engine.mirrors().mode(p));
        nextMode();
        assertEquals(me.mephisto.ability_engine.engine.summon.Mirrors.Mode.PUPPET, t.engine.mirrors().mode(p));
        nextMode();
        assertEquals(me.mephisto.ability_engine.engine.summon.Mirrors.Mode.COPY, t.engine.mirrors().mode(p));
        assertEquals(List.of("Changeling: standing still", "Changeling: puppet (your keys move only the clone)",
                "Changeling: copying you"), told);
        assertTrue(t.render.privateCues.stream().anyMatch(c -> "trickster_clone_mode".equals(c[0])), "only she hears it");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "ck_ab3"), "no cooldown while the clone is out");
        assertTrue(t.engine.summons().find(p, "changeling").isPresent(), "the clone stays");
        t.time.advance(162);
        assertTrue(t.engine.summons().find(p, "changeling").isEmpty());
        assertEquals(280, t.engine.cooldowns().remainingTicks(p, "ck_ab3"), 10, "its cooldown starts when it goes");
    }

    @Test
    void stillTheCloneStopsCopyingMovement() throws IOException {
        setup();
        UUID clone = castClone();
        nextMode();
        Vec3 start = pos(clone);
        walk(new Vec3(0.3, 0, 0), 5);
        t.world.input(p, Vec3.ZERO, true);
        t.time.advance(1);
        assertEquals(start, pos(clone), "it stays put");
        Vec3 looking = t.world.facing.get(clone);
        t.world.look(p, new Vec3(0, 0, 1));
        t.time.advance(1);
        assertEquals(looking, t.world.facing.get(clone), "nor turns with her");
    }

    @Test
    void puppetSheStaysAndHerKeysMoveOnlyTheClone() throws IOException {
        setup();
        UUID clone = castClone();
        nextMode();
        nextMode();
        assertTrue(t.engine.tags().has(p, Tags.PUPPETEER), "she can't walk");
        assertTrue(t.engine.tags().has(p, Tags.HIDDEN), "and is hidden behind her decoy (she can turn unseen)");
        Vec3 start = pos(clone);
        t.world.input(p, new Vec3(0.25, 0, 0), false);  // W (she can't move: the clone does)
        t.time.advance(4);
        assertEquals(0, start.add(-1, 0, 0).distance(pos(clone)), 1e-9, "the puppet walks (its way)");
        t.world.input(p, Vec3.ZERO, true);              // the jump key: she can't jump, the puppet does
        t.time.advance(1);
        assertEquals(start.y() + 0.42, pos(clone).y(), 1e-9);
        t.world.input(p, Vec3.ZERO, false);
        nextMode();
        assertFalse(t.engine.tags().has(p, Tags.PUPPETEER), "back to copying: free again");
        assertFalse(t.engine.tags().has(p, Tags.HIDDEN), "and seen again");
    }

    @Test
    void puppetTrickRoomTeleportsMoveOnlyTheClone() throws IOException {
        setup();
        room();
        UUID clone = castClone();
        nextMode();
        nextMode();
        assertTrue(t.engine.floors().glide(p, new Vec3(1, 0, 0)));
        assertEquals(0, pos(p).x(), 1e-9, "she stays");
        assertEquals(-4.65, pos(clone).x(), 0.11, "the puppet blinks (its way, to inside the room's edge)");
    }

    @Test
    void thePuppetGoingLetsHerGo() throws IOException {
        setup();
        castClone();
        nextMode();
        nextMode();
        t.engine.summons().dismiss(p, "changeling");
        t.time.advance(2);
        assertFalse(t.engine.tags().has(p, Tags.PUPPETEER));
        assertEquals(me.mephisto.ability_engine.engine.summon.Mirrors.Mode.COPY, t.engine.mirrors().mode(p));
    }

    @Test
    void twoEnemyBasicAttacksBreakTheClone() throws IOException {
        setup();
        UUID clone = castClone();
        UUID enemy = enemyAt(pos(clone).x() + 2, pos(clone).z());
        t.engine.loadouts().assign(enemy, "changeling_kit");
        t.world.look(enemy, new Vec3(-1, 0, 0));
        t.world.move(p, new Vec3(-10, 1, -10));        // (out of the way)
        t.engine.loadouts().activate(enemy, Slots.PRIMARY);
        assertTrue(t.engine.summons().find(p, "changeling").isPresent(), "one hit: still there");
        t.time.advance(15);
        t.engine.loadouts().activate(enemy, Slots.PRIMARY);
        assertTrue(t.engine.summons().find(p, "changeling").isEmpty(), "two: broken");
        assertTrue(t.render.cues.contains("trickster_clone_poof"));
        t.time.advance(2);
        assertEquals(280, t.engine.cooldowns().remainingTicks(p, "ck_ab3"), 3, "its cooldown starts when it goes");
    }

    @Test
    void anyAbilityHitBreaksTheClone() throws IOException {
        setup();
        UUID clone = castClone();
        UUID enemy = enemyAt(pos(clone).x() + 5, pos(clone).z());
        t.world.look(enemy, new Vec3(-1, 0, 0));
        t.world.move(p, new Vec3(-10, 1, -10));
        t.engine.activator().activate(enemy, "test_blast");
        assertTrue(t.engine.summons().find(p, "changeling").isEmpty());
    }

    @Test
    void breakingTheCloneLeavesYouOnFriendlyFire() throws IOException {
        setup();
        UUID clone = castClone();
        UUID fooled = enemyAt(pos(clone).x() + 5, pos(clone).z());
        UUID buddy = enemyAt(pos(clone).x() + 5, pos(clone).z() + 3);   // their teammate
        t.world.look(fooled, new Vec3(-1, 0, 0));
        t.world.move(p, new Vec3(-10, 1, -10));
        assertTrue(t.engine.teams().allies(fooled, buddy));
        t.engine.activator().activate(fooled, "test_blast");          // breaks it
        assertTrue(t.engine.summons().find(p, "changeling").isEmpty());
        assertTrue(t.engine.tags().has(fooled, Tags.FRIENDLY_FIRE), "fooled");
        assertTrue(t.render.loops.contains("trickster_betrayed"), "for everyone to see");
        assertFalse(t.engine.teams().allies(fooled, buddy), "their own team's enemy for now");
        t.world.look(buddy, new Vec3(0, 0, -1));
        t.engine.activator().activate(buddy, "test_blast");           // their teammate's shot hits them
        assertTrue(t.damage(fooled) > 0, "their ally can hurt them");
        t.time.advance(81);
        assertFalse(t.engine.tags().has(fooled, Tags.FRIENDLY_FIRE), "4s");
        assertTrue(t.engine.teams().allies(fooled, buddy), "friends again");
    }

    @Test
    void theCloneGoesWhenTheFaeDiesAndAfterEightSeconds() throws IOException {
        setup();
        castClone();
        t.engine.resetOnDeath(p);
        assertTrue(t.engine.summons().find(p, "changeling").isEmpty(), "she died");
        t.engine.resetOnRespawn(p);
        t.engine.cooldowns().clear(p, "ck_ab3");
        castClone();
        t.time.advance(155);
        assertTrue(t.engine.summons().find(p, "changeling").isPresent(), "still there at 7.75s");
        t.time.advance(6);
        assertTrue(t.engine.summons().find(p, "changeling").isEmpty(), "8s");
    }

    // ---- Ultimate: Stolen Fable ------------------------------------------------------------------

    private void charged() {
        t.engine.ultCharge().configure(true, 5, 2, 0);
        t.engine.ultCharge().set(p, 100);
    }

    private String ultSlot() { return t.engine.loadouts().abilityIn(p, Slots.ULTIMATE).orElse(null); }

    @Test
    void withNobodyInSightNothingHappensAndTheChargeIsKept() throws IOException {
        setup();
        charged();
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        assertEquals(100, t.engine.ultCharge().of(p), 1e-9);
        assertEquals("ck_ult", ultSlot());
    }

    @Test
    void theStolenUltimateCanBeCastOnceThenTheSlotIsStolenFableAgain() throws IOException {
        setup();
        charged();
        UUID dreamer = enemyAt(6, 0);
        t.engine.loadouts().assign(dreamer, "dreamer");
        t.engine.ultCharge().set(dreamer, 40);
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        assertEquals("dreamer_ult1", ultSlot(), "Dream Tempest is hers for now");
        assertEquals(0, t.engine.ultCharge().of(p), 1e-9, "her own charge was spent on the steal");
        assertEquals(40, t.engine.ultCharge().of(dreamer), 1e-9, "they keep theirs");
        assertEquals(1, lineCue("trickster_snatch"));
        t.time.advance(5);
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success(), "free: no charge needed");
        assertFalse(t.engine.loadouts().activate(p, Slots.ULTIMATE).success(), "once");
        for (int i = 0; i < 100 && t.engine.instances().isRunning(p, "dreamer_ult1"); i++) t.time.advance(1);
        assertEquals("ck_ult", ultSlot(), "used: back to Stolen Fable");
        assertFalse(t.engine.loadouts().activate(p, Slots.ULTIMATE).success(), "which charges up as usual");
    }

    @Test
    void unusedForTwentySecondsItsLost() throws IOException {
        setup();
        charged();
        UUID dreamer = enemyAt(6, 0);
        t.engine.loadouts().assign(dreamer, "dreamer");
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        t.time.advance(399);
        assertEquals("dreamer_ult1", ultSlot());
        t.time.advance(2);
        assertEquals("ck_ult", ultSlot());
    }

    @Test
    void anUltimateThatOnlyWorksForItsOwnerCantBeStolen() throws IOException {
        setup();
        charged();
        UUID valkyrie = enemyAt(6, 0);
        t.engine.loadouts().assign(valkyrie, "valkyrie");
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        assertEquals("ck_ult", ultSlot());
        assertEquals(100, t.engine.ultCharge().of(p), 1e-9, "the charge is given back");
        assertEquals(List.of("That ultimate can't be stolen."), told);
        assertFalse(t.render.cues.contains("trickster_steal_fail"), "the fizzle isn't for everyone...");
        assertTrue(t.render.privateCues.stream().anyMatch(c -> "trickster_steal_fail".equals(c[0])
                && java.util.Set.of(p).equals(c[1])), "...only for her");
    }

    @Test
    void fromAnotherTricksterYouStealTheUltimateTheyStole() throws IOException {
        setup();
        charged();
        UUID rival = enemyAt(6, 0);
        t.engine.loadouts().assign(rival, "changeling_kit");
        t.engine.stolen().lend(rival, "dreamer_ult1", 400); // they're holding a stolen Dream Tempest
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        assertEquals("dreamer_ult1", ultSlot(), "that's what she takes");
        assertTrue(t.engine.stolen().isFree(p, "dreamer_ult1"));
        assertTrue(t.engine.stolen().isFree(rival, "dreamer_ult1"), "a copy: they keep it");
    }

    @Test
    void aTricksterHoldingNothingHasNothingToSteal() throws IOException {
        setup();
        charged();
        UUID rival = enemyAt(6, 0);
        t.engine.loadouts().assign(rival, "changeling_kit");
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        assertEquals("ck_ult", ultSlot(), "Stolen Fable itself can't be stolen");
        assertFalse(t.engine.stolen().heldBy(p).isPresent());
        assertEquals(100, t.engine.ultCharge().of(p), 1e-9, "the charge is given back");
        assertEquals(List.of("That ultimate can't be stolen."), told);
    }

    @Test
    void silenceBlocksTheStealAndTheStolenUltimate() throws IOException {
        setup();
        charged();
        UUID dreamer = enemyAt(6, 0);
        t.engine.loadouts().assign(dreamer, "dreamer");
        t.engine.statuses().apply(p, "silence", 20, null);
        assertFalse(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        t.time.advance(21);
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        t.engine.statuses().apply(p, "silence", 20, null);
        assertFalse(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        assertTrue(t.engine.stolen().isFree(p, "dreamer_ult1"), "still there for later");
    }

    @Test
    void dyingLosesTheStolenUltimate() throws IOException {
        setup();
        charged();
        UUID dreamer = enemyAt(6, 0);
        t.engine.loadouts().assign(dreamer, "dreamer");
        t.engine.loadouts().activate(p, Slots.ULTIMATE);
        t.engine.resetOnDeath(p);
        assertEquals("ck_ult", ultSlot());
    }
}

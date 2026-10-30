package me.mephisto.ability_engine.engine;

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
 * Brains (PvE), with the Bramble Knight (fixture snapshot): a non-player at x=0 facing +x (the fake world turns nobody's
 * aim, so its enemies stand along +x), on team "monsters".
 */
class BrainTest {

    private TestEngine t;
    private UUID boss;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        boss = t.spawn(0, 1, 0);
        t.world.team(boss, "monsters");
        t.engine.loadouts().assign(boss, "bramble_knight");
    }

    private UUID playerAt(double x, double z) {
        UUID p = t.spawn(x, 1, z);
        t.world.team(p, "blue");
        t.world.players.add(p);
        return p;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    @Test
    void aNonPlayerPlayingABrainCharacterIsDrivenAndPicksTheNearestEnemy() throws IOException {
        setup();
        UUID far = playerAt(15, 0);
        UUID near = playerAt(3, 0);
        UUID friend = t.spawn(1, 1, 0);
        t.world.team(friend, "monsters");
        assertTrue(t.engine.brains().drives(boss));
        t.time.advance(1);
        assertEquals(near, t.engine.brains().targetOf(boss).orElseThrow(), "the nearest enemy, not its friend");
        assertFalse(t.engine.brains().targetOf(boss).get().equals(far));
    }

    @Test
    void playersAreNeverDriven() throws IOException {
        setup();
        UUID p = playerAt(5, 0);
        t.engine.loadouts().assign(p, "bramble_knight");
        assertFalse(t.engine.brains().drives(p));
    }

    @Test
    void theFirstRuleThatHoldsCasts() throws IOException {
        setup();
        UUID p = playerAt(2, 0);                          // close: the slam's rule comes before the swing's
        t.time.advance(1);
        assertEquals(0, t.damage(p), 1e-9, "the slam is telegraphed first...");
        assertTrue(t.render.cues.contains("shockwave_4"), "...with its ring");
        t.time.advance(21);
        assertEquals(45, t.damage(p), 1e-6, "150% of 30");
        assertTrue(t.engine.tags().has(p, Tags.ROOTED), "rooted");
        t.time.advance(10);                               // the slam's on cooldown: the next rule down, the swing
        assertTrue(t.damage(p) > 45, "then it swings");
    }

    @Test
    void itChargesWhoeverKeepsTheirDistance() throws IOException {
        setup();
        UUID p = playerAt(9, 0);
        t.time.advance(15);
        assertEquals(36, t.damage(p), 1e-6, "ran through them: 120% of 30");
        assertTrue(t.engine.statuses().has(p, "slow"));
        assertTrue(pos(boss).x() > 8, "charged");
    }

    @Test
    void itWalksAfterItsTarget() throws IOException {
        setup();
        playerAt(16, 0);                                  // out of the charge's reach (12)
        t.time.advance(10);
        assertTrue(pos(boss).x() > 1.5, "walking at 0.22 a tick: " + pos(boss));
    }

    @Test
    void confusedItGoesAfterItsOwnTeammateNotItsTarget() throws IOException {
        setup();
        UUID p = playerAt(5, 0);
        UUID friend = t.spawn(-6, 1, 0);
        t.world.team(friend, "monsters");
        t.time.advance(1);
        t.engine.statuses().apply(boss, "trickster_confused", 60, p); // (the Trickster's Confuse Ray)
        t.time.advance(10);
        assertTrue(pos(boss).x() < -1, "running at its friend, away from the player: " + pos(boss));
        assertEquals(0, t.damage(p), 1e-9, "the player is left alone");
    }

    @Test
    void withItsControlsInvertedItWalksAwayFromItsTarget() throws IOException {
        setup();
        UUID p = playerAt(16, 0);
        t.engine.statuses().apply(boss, "topsy_turvy_room", 60, p); // (the Trickster's topsy-turvy Trick Room)
        t.time.advance(10);
        assertTrue(pos(boss).x() < -1.5, "walking the other way: " + pos(boss));
    }

    @Test
    void itEnragesOnceBelowFortyPercent() throws IOException {
        setup();
        playerAt(16, 0);
        t.world.healthFraction.put(boss, 0.3);
        t.time.advance(1);
        assertTrue(t.engine.statuses().has(boss, "bramble_enraged"));
        t.time.advance(250);                              // over, and its cooldown too
        assertFalse(t.engine.statuses().has(boss, "bramble_enraged"));
        t.time.advance(400);
        assertFalse(t.engine.statuses().has(boss, "bramble_enraged"), "once");
    }

    @Test
    void itDoesntSeeThroughWallsNorTheUntargetable() throws IOException {
        setup();
        UUID p = playerAt(5, 0);
        t.world.box(2, 3, -3, 3, 10);                     // a wall between them
        t.time.advance(1);
        assertTrue(t.engine.brains().targetOf(boss).isEmpty(), "out of sight");
        UUID q = playerAt(-4, 0);                         // behind it, in plain sight...
        t.engine.tags().grant(q, Tags.UNTARGETABLE);      // ...but untargetable
        t.time.advance(6);
        assertTrue(t.engine.brains().targetOf(boss).isEmpty());
        assertEquals(0, t.damage(p) + t.damage(q), 1e-9);
    }

    @Test
    void enemiesOnlyKitsArentPlayable() throws IOException {
        setup();
        assertFalse(t.engine.characters().find("bramble_knight").orElseThrow().playable());
        assertTrue(t.engine.characters().find("trickster").orElseThrow().playable());
    }
}

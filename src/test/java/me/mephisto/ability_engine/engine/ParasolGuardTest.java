package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.abilities;
import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * parasol_guard from the shipped content. The guard stands at x=0 facing +x (its front);
 * the enemy stands in front at x=10 facing the guard.
 */
class ParasolGuardTest {

    private TestEngine t;
    private UUID guard;
    private UUID enemy;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.load(abilities(
                "punch", map("nodes", map("hit", map("type", "apply_effects", "targets", map("type", "key", "key", "target"),
                        "effects", list(map("id", "damage", "amount", 10))))),
                "shove", map("nodes", map("push", map("type", "apply_effects", "targets", map("type", "radius", "radius", 6),
                        "effects", list(map("id", "knockback", "from", "caster", "radius", 6)))))));
        guard = t.spawn(0, 1, 0);                    // faces +x by default
        enemy = t.spawn(10, 1, 0);
        t.world.look(enemy, new Vec3(-1, 0, 0));
        t.world.team(guard, "blue");
        t.world.team(enemy, "red");
    }

    private void raise() {
        assertTrue(t.engine.activator().activate(guard, "parasol_guard").success());
        assertTrue(t.engine.barriers().has(guard));
    }

    @Test
    void projectilesFromTheFrontAreAbsorbed() throws IOException {
        setup();
        raise();
        t.engine.activator().activate(enemy, "arcane_bolt");
        t.time.advance(10);
        assertEquals(0, t.damage(guard), 1e-9);
        assertEquals(0, t.engine.projectiles().activeCount(), "absorbed, not flying on");
        assertTrue(t.render.cues.contains("barrier_block"), "splash");
    }

    @Test
    void projectilesFromBehindStillHit() throws IOException {
        setup();
        raise();
        UUID behind = t.spawn(-10, 1, 0);
        t.world.team(behind, "red");
        t.engine.activator().activate(behind, "arcane_bolt"); // faces +x: into the guard's back
        t.time.advance(10);
        assertEquals(30, t.damage(guard), 1e-9);
    }

    @Test
    void hitscansStopLikeAtAWall() throws IOException {
        setup();
        raise();
        t.engine.activator().activate(enemy, "test_blast");
        assertEquals(0, t.damage(guard), 1e-9);
    }

    @Test
    void anAllyRightBehindTheGuardIsCoveredToo() throws IOException {
        setup();
        UUID ally = t.spawn(-1.5, 1, 0);
        t.world.team(ally, "blue");
        raise();
        t.engine.activator().activate(enemy, "test_blast");
        t.engine.activator().activate(enemy, "arcane_bolt");
        t.time.advance(10);
        assertEquals(0, t.damage(ally), 1e-9);
    }

    @Test
    void alliesShootThroughIt() throws IOException {
        setup();
        UUID ally = t.spawn(-3, 1, 0);
        t.world.team(ally, "blue");
        raise();
        t.engine.activator().activate(ally, "arcane_bolt"); // from behind the guard, toward the enemy
        t.time.advance(10);
        assertEquals(30, t.damage(enemy), 1e-9);
    }

    @Test
    void dashesStopAtIt() throws IOException {
        setup();
        raise();
        t.engine.activator().activate(enemy, "royal_lunge");
        t.time.advance(15);
        assertEquals(0, t.damage(guard), 1e-9);
        assertFalse(t.engine.tags().has(guard, Tags.STUNNED));
    }

    @Test
    void meleeFromTheFrontIsBlockedButNotFromBehind() throws IOException {
        setup();
        raise();
        UUID front = t.spawn(1.2, 1, 0.3);
        t.world.team(front, "red");
        t.engine.activator().activateOnId(front, "punch", new EntityTarget(guard), java.util.Map.of());
        assertEquals(0, t.damage(guard), 1e-9, "blocked from the front");

        UUID back = t.spawn(-1.2, 1, 0);
        t.world.team(back, "red");
        t.engine.activator().activateOnId(back, "punch", new EntityTarget(guard), java.util.Map.of());
        assertEquals(10, t.damage(guard), 1e-9, "lands from behind");
    }

    @Test
    void knockbackImmuneWhileGuarding() throws IOException {
        setup();
        UUID bystander = t.spawn(2, 1, 2);             // unguarded, no team
        raise();
        UUID pusher = t.spawn(3, 1, 0);
        t.world.team(pusher, "red");
        t.engine.activator().activate(pusher, "shove");
        assertFalse(t.knockback.containsKey(guard), "guard doesn't budge");
        assertTrue(t.knockback.containsKey(bystander), "others are pushed");
    }

    @Test
    void lastsTwoSecondsThenHitsLandAgain() throws IOException {
        setup();
        raise();
        assertTrue(t.engine.tags().has(guard, Tags.SLOWED));
        t.time.advance(40);
        assertFalse(t.engine.barriers().has(guard));
        assertFalse(t.engine.tags().has(guard, Tags.BLOCK_KNOCKBACK));
        t.engine.activator().activate(enemy, "test_blast");
        assertEquals(40, t.damage(guard), 1e-9);
    }

    @Test
    void noOtherActionsWhileGuarding() throws IOException {
        setup();
        t.engine.loadouts().assign(guard, "duelist");
        raise();
        for (String slot : java.util.List.of("primary", "ability_1")) {
            assertEquals("blocked:" + Tags.BLOCK_ABILITY, t.engine.loadouts().activate(guard, slot).reason(), slot);
        }
        assertTrue(t.engine.barriers().has(guard), "still guarding");

        t.time.advance(40);
        assertTrue(t.engine.loadouts().activate(guard, "primary").success(), "free again when it ends");
    }

    @Test
    void theGuardItselfIsntCancelledByItsOwnLock() throws IOException {
        setup();
        raise();
        t.time.advance(20);
        assertTrue(t.engine.barriers().has(guard), "block.ability is its own tag: it doesn't interrupt itself");
    }
}

package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.testkit.FakeRenderer;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Projectiles flown by the game itself (FlyingVisual: a real arrow). The engine never moves them; it
 * reads them each tick and checks the stretch they're about to fly. The Hunter's bolt is one.
 */
class RealArrowTest {

    private TestEngine t;
    private UUID hunter;

    private void setup() throws IOException {
        t = new TestEngine();
        t.render.realArrows = true;
        ShippedContent.loadClean(t.engine);
        hunter = t.spawn(0, 1, 0); // looks +x
        t.engine.loadouts().assign(hunter, "hunter");
        t.engine.quivers().tryLoad(hunter);
    }

    private FakeRenderer.FakeArrow shoot() {
        assertTrue(t.engine.loadouts().activate(hunter, "primary").success());
        assertEquals(1, t.render.arrows.size(), "a real arrow, not an engine-drawn visual");
        return t.render.arrows.get(0);
    }

    @Test
    void itHitsAndIsRemovedBeforeFlyingThroughTheTarget() throws IOException {
        setup();
        UUID enemy = t.spawn(10, 1, 0);
        FakeRenderer.FakeArrow arrow = shoot();
        t.time.advance(10);
        assertEquals(45, t.damage(enemy), 1e-9);
        assertTrue(arrow.removed);
        assertFalse(arrow.path.isEmpty(), "the game flew it");
        double furthest = arrow.path.stream().mapToDouble(Vec3::x).max().orElseThrow();
        assertTrue(furthest < 10, "never drawn past the target: x=" + furthest);
    }

    @Test
    void itDropsLikeAVanillaArrowNotLikeTheEngineSpec() throws IOException {
        setup();
        FakeRenderer.FakeArrow arrow = shoot();
        t.time.advance(6);
        // After 6 ticks from y=1: vanilla gravity (0.05) drops it ~0.74, the spec's 0.02 would be ~0.42.
        assertTrue(arrow.pos.y() < 0.4, "vanilla drop, not the spec's: y=" + arrow.pos.y());
    }

    @Test
    void itFliesThroughAlliesToTheEnemyBehind() throws IOException {
        setup();
        UUID ally = t.spawn(5, 1, 0);
        UUID enemy = t.spawn(9, 1, 0);
        t.world.team(hunter, "blue");
        t.world.team(ally, "blue");
        shoot();
        t.time.advance(10);
        assertEquals(0, t.damage(ally), 1e-9);
        assertEquals(45, t.damage(enemy), 1e-9);
    }

    @Test
    void aParasolGuardAbsorbsIt() throws IOException {
        setup();
        UUID guard = t.spawn(8, 1, 0);
        t.world.look(guard, new Vec3(-1, 0, 0)); // facing the hunter
        t.world.team(hunter, "blue");
        t.world.team(guard, "red");
        assertTrue(t.engine.activator().activate(guard, "parasol_guard").success());
        FakeRenderer.FakeArrow arrow = shoot();
        t.time.advance(10);
        assertEquals(0, t.damage(guard), 1e-9);
        assertTrue(arrow.removed, "absorbed");
        assertTrue(t.render.cues.contains("barrier_block"));
        assertEquals(0, t.engine.projectiles().activeCount());
    }

    @Test
    void aMissExpiresAndRemovesTheArrow() throws IOException {
        setup();
        FakeRenderer.FakeArrow arrow = shoot();
        t.time.advance(45); // lifetime 40
        assertTrue(arrow.removed);
        assertEquals(0, t.engine.projectiles().activeCount());
    }
}

package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** sovereign_geyser from the shipped content. Caster at x=0 facing +x. */
class SovereignGeyserTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
    }

    private UUID enemyAt(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private void cast() {
        assertTrue(t.engine.activator().activate(p, "sovereign_geyser").success());
    }

    @Test
    void chargesThenHitsEveryEnemyInTheBeam() throws IOException {
        setup();
        UUID near = enemyAt(5, 0);
        UUID far = enemyAt(15, 0.8);             // inside the 2-wide beam
        UUID aside = enemyAt(8, 4);              // well outside it
        cast();
        t.time.advance(11);
        assertEquals(0, t.damage(near), 1e-9, "still charging");
        t.time.advance(1);

        assertEquals(180, t.damage(near), 1e-9);
        assertEquals(180, t.damage(far), 1e-9, "a beam hits everyone in line");
        assertEquals(0, t.damage(aside), 1e-9);
        assertTrue(t.knockback.get(near) > 2, "huge shove");
        assertEquals("geyser_beam", t.render.lines.get(0)[0]);
    }

    @Test
    void doesNotGoThroughWalls() throws IOException {
        setup();
        UUID before = enemyAt(5, 0);
        t.world.box(7, 8, -5, 5, 10);            // a wall between
        UUID behind = enemyAt(12, 0);
        cast();
        t.time.advance(12);
        assertEquals(180, t.damage(before), 1e-9);
        assertEquals(0, t.damage(behind), 1e-9, "line of sight matters");
        Vec3 end = (Vec3) t.render.lines.get(0)[2];
        assertEquals(7.0, end.x(), 1e-6, "the beam visual ends at the wall");
    }

    @Test
    void anEnemyParasolGuardStopsIt() throws IOException {
        setup();
        UUID guard = enemyAt(5, 0);
        t.world.look(guard, new Vec3(-1, 0, 0)); // facing the caster
        UUID behind = enemyAt(10, 0);
        t.engine.activator().activate(guard, "parasol_guard");
        cast();
        t.time.advance(12);
        assertEquals(0, t.damage(guard), 1e-9);
        assertEquals(0, t.damage(behind), 1e-9);
    }

    @Test
    void alliesAreSpared() throws IOException {
        setup();
        UUID ally = t.spawn(5, 1, 0);
        t.world.team(ally, "blue");
        cast();
        t.time.advance(12);
        assertEquals(0, t.damage(ally), 1e-9);
        assertFalse(t.knockback.containsKey(ally));
    }

    @Test
    void airAnchorRootAndLockForTheWholeCast() throws IOException {
        setup();
        cast();
        for (String tag : new String[]{Tags.ANCHORED, Tags.BLOCK_MOVE, Tags.BLOCK_ABILITY}) {
            assertTrue(t.engine.tags().has(p, tag), tag);
        }
        t.time.advance(21);
        assertTrue(t.engine.tags().has(p, Tags.ANCHORED), "still anchored while the beam lingers");
        t.time.advance(1);
        assertFalse(t.engine.tags().has(p, Tags.ANCHORED), "released after 0.6s charge + 0.5s beam");
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_MOVE));
    }

    @Test
    void aStunDuringTheChargeCancelsIt() throws IOException {
        setup();
        UUID near = enemyAt(5, 0);
        cast();
        t.time.advance(5);
        t.engine.statuses().apply(p, "stun", 20, null);
        t.time.advance(20);
        assertEquals(0, t.damage(near), 1e-9);
        assertFalse(t.engine.tags().has(p, Tags.ANCHORED), "anchor released");
    }

    @Test
    void aimIsTakenWhenItFires() throws IOException {
        setup();
        UUID side = enemyAt(0, 8);
        cast();
        t.time.advance(6);
        t.world.look(p, new Vec3(0, 0, 1));      // turned during the charge
        t.time.advance(6);
        assertEquals(180, t.damage(side), 1e-9);
    }
}

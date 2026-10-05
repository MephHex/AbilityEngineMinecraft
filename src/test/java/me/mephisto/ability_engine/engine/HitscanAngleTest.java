package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import me.mephisto.ability_engine.engine.testkit.Yml;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hitscan's {@code angle}: missed with the ray, whoever is nearest the crosshair within the angle, in sight. The
 * caster at the origin looking +x; a "strict" shot (the ray only) and a "forgiving" one (angle 50), both 6 blocks.
 */
class HitscanAngleTest {

    private TestEngine t;
    private UUID me;

    private void setup() {
        t = new TestEngine();
        t.load(Map.of("abilities", Map.of(
                "strict", shot(0),
                "forgiving", shot(50))));
        me = t.spawn(0, 1, 0);
        t.world.team(me, "blue");
    }

    private static Map<String, Object> shot(double angle) {
        return Map.of("nodes", Yml.map( // (in order: aim first)
                "aim", Map.of("type", "acquire_target", "query", Map.of("type", "hitscan", "range", 6, "angle", angle),
                        "on", Map.of("hit", "hit")),
                "hit", Map.of("type", "apply_effects", "targets", Map.of("type", "key", "key", "target"),
                        "effects", List.of(Map.of("id", "damage", "amount", 10)))));
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    @Test
    void aBitOffTheCrosshairTheRayMissesTheAngleDoesnt() {
        setup();
        UUID off = foe(4, 1.5);                 // ~21 degrees off: past the ray
        assertTrue(t.engine.activator().activate(me, "strict").success());
        assertEquals(0, t.damage(off), 1e-9, "the ray alone misses");
        assertTrue(t.engine.activator().activate(me, "forgiving").success());
        assertEquals(10, t.damage(off), 1e-9, "within 25 degrees of the crosshair");
    }

    @Test
    void theRayStillComesFirstAndTheAngleNeedsSightAndRange() {
        setup();
        UUID aimed = foe(5, 0);                 // right on the crosshair
        UUID closer = foe(2, 0.9);              // nearer, but off to the side
        assertTrue(t.engine.activator().activate(me, "forgiving").success());
        assertEquals(10, t.damage(aimed), 1e-9, "the one aimed at");
        assertEquals(0, t.damage(closer), 1e-9);

        setup();
        UUID walled = foe(4, -1.3);              // nearest the crosshair, but behind a pillar
        UUID seen = foe(4, 1.5);
        UUID wide = foe(3, 3);                   // 45 degrees off: outside
        UUID far = foe(8, 0.5);                  // past the range
        t.world.box(1.8, 2.4, -1.2, -0.3, 10);
        assertTrue(t.engine.activator().activate(me, "forgiving").success());
        assertEquals(0, t.damage(walled), 1e-9, "a wall in between");
        assertEquals(10, t.damage(seen), 1e-9, "the next nearest the crosshair, in sight");
        assertEquals(0, t.damage(wide), 1e-9);
        assertEquals(0, t.damage(far), 1e-9);
    }

    @Test
    void theAngleIsInDegreesUpTo180() {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(Map.of("abilities", Map.of("bad", Map.of("nodes", Map.of(
                "aim", Map.of("type", "acquire_target", "query", Map.of("type", "hitscan", "range", 6, "angle", 200)))))),
                "src/test");
        assertTrue(String.join("\n", report.errors()).contains("angle"), String.join("\n", report.errors()));
    }
}

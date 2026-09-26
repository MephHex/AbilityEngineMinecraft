package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.abilities;
import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetQueryTest {

    @Test
    void coneHitsOnlyWhatIsInFront() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        UUID front = t.spawn(3, 1, 0.5);
        UUID behind = t.spawn(-3, 1, 0);
        UUID side = t.spawn(0, 1, 3);
        UUID far = t.spawn(20, 1, 0);
        t.load(abilities("cone", map("nodes", map("burn", map("type", "apply_effects",
                "targets", map("type", "cone", "range", 6, "angle", 60),
                "effects", list(map("id", "damage", "amount", 1)))))));

        t.engine.activator().activate(caster, "cone");
        assertEquals(1.0, t.damage(front), 1e-9);
        assertEquals(0.0, t.damage(behind), 1e-9);
        assertEquals(0.0, t.damage(side), 1e-9);
        assertEquals(0.0, t.damage(far), 1e-9);
        assertEquals(0.0, t.damage(caster), 1e-9);
    }

    @Test
    void radiusRespectsMaxTargetsNearestFirst() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        UUID a = t.spawn(1, 1, 0);
        UUID b = t.spawn(0, 1, 2);
        UUID c = t.spawn(-3, 1, 0);
        t.load(abilities("nova", map("nodes", map("nova", map("type", "apply_effects",
                "targets", map("type", "radius", "radius", 5, "max", 2),
                "effects", list(map("id", "damage", "amount", 1)))))));

        t.engine.activator().activate(caster, "nova");
        assertEquals(1.0, t.damage(a), 1e-9);
        assertEquals(1.0, t.damage(b), 1e-9);
        assertEquals(0.0, t.damage(c), 1e-9);
        assertEquals(0.0, t.damage(caster), 1e-9);
    }

    @Test
    void switchBranchesOnTargetKind() {
        TestEngine t = new TestEngine();
        t.world.floor(0);
        UUID caster = t.spawn(0, 2, 0);
        t.world.look(caster, new me.mephisto.ability_engine.engine.math.Vec3(1, -1, 0));
        t.load(abilities("mark", map("nodes", map(
                "aim", map("type", "acquire_target", "query", map("type", "hitscan", "range", 10, "blocks", true),
                        "on", map("hit", "which")),
                "which", map("type", "switch", "key", "target", "on", map("entity", "e", "point", "p")),
                "e", map("type", "play_cue", "cue", "entity"),
                "p", map("type", "play_cue", "cue", "point")))));

        t.engine.activator().activate(caster, "mark");
        assertEquals(list("point"), t.render.cues);
    }
}

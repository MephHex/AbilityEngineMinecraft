package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.abilities;
import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphTest {

    private static Object blast() {
        return map("cooldown", 60, "nodes", map(
                "aim", map("type", "acquire_target", "query", map("type", "hitscan", "range", 20),
                        "on", map("hit", "dmg", "miss", "missed")),
                "dmg", map("type", "apply_effects", "targets", map("type", "key", "key", "target"),
                        "effects", list(map("id", "damage", "amount", 4))),
                "missed", map("type", "print", "message", "miss")));
    }

    @Test
    void hitscanHitFollowsHitPortAndInstanceCompletes() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        UUID mob = t.spawn(5, 1, 0);
        t.load(abilities("blast", blast()));

        assertTrue(t.engine.activator().activate(caster, "blast").success());
        assertEquals(4.0, t.damage(mob), 1e-9);
        assertEquals(0, t.engine.instances().count());
    }

    @Test
    void hitscanMissFollowsMissPort() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.world.look(caster, new Vec3(-1, 0, 0));
        UUID mob = t.spawn(5, 1, 0);
        t.load(abilities("blast", blast()));

        t.engine.activator().activate(caster, "blast");
        assertEquals(0.0, t.damage(mob), 1e-9);
        assertEquals(0, t.engine.instances().count());
    }

    @Test
    void wiringTyposAreReportedAtLoadTime() {
        TestEngine t = new TestEngine();
        LoadReport report = new AbilityLoader(t.engine).load(abilities(
                "broken", map("nodes", map(
                        "aim", map("type", "acquire_target", "query", map("type", "self"),
                                "on", map("hti", "dmg", "miss", "nowhere")),
                        "dmg", map("type", "print", "message", "x"))),
                "fine", map("nodes", map("p", map("type", "print", "message", "ok")))
        ), "test");

        assertEquals(1, report.abilities(), "the valid ability still loads");
        assertEquals(1, report.errors().size());
        String err = report.errors().get(0);
        assertTrue(err.contains("no port 'hti'"), err);
        assertTrue(err.contains("unknown node 'nowhere'"), err);
    }

    @Test
    void synchronousLoopIsCancelledInsteadOfHanging() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("loop", map("nodes", map(
                "a", map("type", "print", "message", "a", "next", "b"),
                "b", map("type", "print", "message", "b", "next", "a")))));

        t.engine.activator().activate(caster, "loop");
        assertEquals(0, t.engine.instances().count());
    }

    @Test
    void delayResumesOnTheSchedulerAfterExactTicks() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("later", map("nodes", map(
                "wait", map("type", "delay", "ticks", 20, "next", "hurt"),
                "hurt", map("type", "apply_effects", "targets", map("type", "self"),
                        "effects", list(map("id", "damage", "amount", 1)))))));

        t.engine.activator().activate(caster, "later");
        assertEquals(1, t.engine.instances().count(), "instance stays alive while suspended");
        t.time.advance(19);
        assertEquals(0.0, t.damage(caster), 1e-9);
        t.time.advance(1);
        assertEquals(1.0, t.damage(caster), 1e-9);
        assertEquals(0, t.engine.instances().count());
    }

    @Test
    void cancellingAnInstanceKillsItsPendingDelay() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("later", map("nodes", map(
                "wait", map("type", "delay", "ticks", 20, "next", "hurt"),
                "hurt", map("type", "apply_effects", "targets", map("type", "self"),
                        "effects", list(map("id", "damage", "amount", 1)))))));

        t.engine.activator().activate(caster, "later");
        AbilityInstance instance = t.engine.instances().of(caster).get(0);
        t.engine.instances().cancelAll(caster, "test");
        t.time.advance(40);

        assertFalse(instance.isActive());
        assertEquals(0.0, t.damage(caster), 1e-9);
        assertEquals(0, t.time.pendingTasks());
    }
}

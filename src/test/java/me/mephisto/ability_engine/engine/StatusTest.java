package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.ability.activation.ActivationResult;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.abilities;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusTest {

    private static Object noop(int cooldown) {
        return map("cooldown", cooldown, "nodes", map("p", map("type", "print", "message", "hi")));
    }

    @Test
    void stunBlocksActivationUntilItExpires() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("noop", noop(0)));

        t.engine.statuses().apply(caster, "stun", null);
        ActivationResult blocked = t.engine.activator().activate(caster, "noop");
        assertFalse(blocked.success());
        assertEquals("blocked:" + Tags.BLOCK_ABILITY, blocked.reason());

        t.time.advance(40);
        assertFalse(t.engine.tags().has(caster, Tags.STUNNED));
        assertTrue(t.engine.activator().activate(caster, "noop").success());
    }

    @Test
    void refreshKeepsTheLongerDuration() {
        TestEngine t = new TestEngine();
        UUID mob = t.spawn(0, 1, 0);

        t.engine.statuses().apply(mob, "stun", 40, null);
        t.time.advance(10);
        t.engine.statuses().apply(mob, "stun", 10, null); // shorter: must not cut the first one short
        assertEquals(30, t.engine.statuses().remainingTicks(mob, "stun"));

        t.time.advance(29);
        assertTrue(t.engine.tags().has(mob, Tags.STUNNED));
        t.time.advance(1);
        assertFalse(t.engine.tags().has(mob, Tags.STUNNED));
    }

    @Test
    void tagsAreReferenceCountedAcrossStatuses() {
        TestEngine t = new TestEngine();
        UUID mob = t.spawn(0, 1, 0);

        t.engine.statuses().apply(mob, "stun", 40, null);
        t.engine.statuses().apply(mob, "root", 80, null);
        t.time.advance(40);
        assertFalse(t.engine.tags().has(mob, Tags.STUNNED));
        assertTrue(t.engine.tags().has(mob, Tags.BLOCK_MOVE), "root still holds block.move");
        t.time.advance(40);
        assertFalse(t.engine.tags().has(mob, Tags.BLOCK_MOVE));
    }

    @Test
    void cooldownIsMeasuredInServerTicks() {
        TestEngine t = new TestEngine();
        UUID caster = t.spawn(0, 1, 0);
        t.load(abilities("noop", noop(60)));

        assertTrue(t.engine.activator().activate(caster, "noop").success());
        ActivationResult again = t.engine.activator().activate(caster, "noop");
        assertEquals("on_cooldown:3.0s", again.reason());
        t.time.advance(60);
        assertTrue(t.engine.activator().activate(caster, "noop").success());
    }
}

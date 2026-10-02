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

    @Test
    void nothingLandsOnTheDead() {
        TestEngine t = new TestEngine();
        UUID attacker = t.spawn(0, 1, 0);
        UUID victim = t.spawn(2, 1, 0);
        t.world.kill(victim); // e.g. the damage of a hit that also slows: it killed them first
        t.engine.resetOnDeath(victim);
        t.engine.statuses().apply(victim, "root", attacker);
        assertFalse(t.engine.statuses().has(victim, "root"), "not on the corpse: it would still be on them after respawning");
        assertFalse(t.engine.tags().has(victim, Tags.BLOCK_MOVE));
    }

    @Test
    void aRespawnStartsClean() {
        TestEngine t = new TestEngine();
        UUID player = t.spawn(0, 1, 0);
        t.engine.statuses().apply(player, "root", 200, null);
        t.engine.resetOnRespawn(player);
        assertFalse(t.engine.statuses().has(player, "root"));
        assertFalse(t.engine.tags().has(player, Tags.BLOCK_MOVE));
    }

    @Test
    void jumpBoostIsAStatusLevelAndTheHighestCounts() {
        TestEngine t = new TestEngine();
        UUID player = t.spawn(0, 1, 0);
        t.load(map("statuses", map(
                "springy", map("duration", 40, "jump_boost", 3),
                "hoppy", map("duration", 80, "jump_boost", 1))));
        assertEquals(0, t.engine.stats().jumpBoost(player));
        t.engine.statuses().apply(player, "hoppy", player);
        t.engine.statuses().apply(player, "springy", player);
        assertEquals(3, t.engine.stats().jumpBoost(player), "Jump Boost III");
        t.time.advance(40);
        assertEquals(1, t.engine.stats().jumpBoost(player), "springy over: hoppy's I");
        t.time.advance(40);
        assertEquals(0, t.engine.stats().jumpBoost(player));
    }

    @Test
    void poisonedTheyHealLess() {
        TestEngine t = new TestEngine();
        UUID player = t.spawn(0, 1, 0);
        t.load(map("statuses", map(
                "venom", map("duration", 40, "healing_taken", 0.6),
                "rot", map("duration", 40, "healing_taken", 0.5))));
        assertEquals(1, t.engine.stats().healingMultiplier(player), 1e-9);
        t.engine.statuses().apply(player, "venom", null);
        assertEquals(0.6, t.engine.stats().healingMultiplier(player), 1e-9, "40% less healing");
        t.engine.statuses().apply(player, "rot", null);
        assertEquals(0.3, t.engine.stats().healingMultiplier(player), 1e-9, "several multiply");
        t.time.advance(40);
        assertEquals(1, t.engine.stats().healingMultiplier(player), 1e-9, "over: all of it again");
    }
}

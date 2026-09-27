package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.abilities;
import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Essence Reaver's Soul Rend (fixtures/essence_reaver.yml). He stands at the origin looking +x. */
class SoulRendTest {

    private TestEngine t;
    private UUID p;
    private UUID victim;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        // A test attack: 100 damage to whoever it's aimed at.
        t.load(abilities("punch", map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "key", "key", "target"),
                "effects", list(map("id", "damage", "amount", 100)))))));
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "essence_reaver");
        victim = t.spawn(3, 1, 0);
        t.world.team(victim, "red");
    }

    private void punch(UUID target) {
        assertTrue(t.engine.activator().activateOnId(p, "punch", new EntityTarget(target), Map.of()).success());
    }

    private UUID soul() { return t.engine.summons().find(p, "rent_soul").orElseThrow(); }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    /** Ult, then the basic attack that rends. */
    private void rend() {
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        assertEquals("essence_reaver_rend", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow());
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
    }

    @Test
    void theRendTearsOutTheirSoulKnocksThemBackAndStuns() throws IOException {
        setup();
        rend();
        UUID soul = soul();
        assertEquals(new Vec3(3, 1, 0), pos(soul), "left where they stood");
        assertTrue(t.world.glowing.contains(soul));
        assertEquals("red", t.world.teamOf(soul).orElseThrow(), "on their side: their allies can't hit it");
        assertEquals(40, t.damage(victim), 1e-9);
        assertTrue(t.engine.tags().has(victim, Tags.STUNNED));
        assertTrue(t.knockback.getOrDefault(victim, 0.0) > 1, "knocked back");
        assertEquals("reaver_strike", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow(),
                "used up: back to normal swings");
    }

    @Test
    void aMissKeepsTheRendReady() throws IOException {
        setup();
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        t.world.look(p, new Vec3(-1, 0, 0));
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        assertTrue(t.engine.summons().find(p, "rent_soul").isEmpty());
        assertEquals("essence_reaver_rend", t.engine.loadouts().abilityIn(p, Slots.PRIMARY).orElseThrow(), "still ready");
    }

    @Test
    void hurtingTheSoulHurtsItsOwnerByHalf() throws IOException {
        setup();
        rend();
        double before = t.damage(victim);
        punch(soul());
        assertEquals(100, t.damage(soul()), 1e-9);
        assertEquals(50, t.damage(victim) - before, 1e-9, "half mirrored");
    }

    @Test
    void destroyingTheSoulSlamsItBackIntoThemAndHealsYou() throws IOException {
        setup();
        rend();
        UUID soul = soul();
        punch(soul);
        double before = t.damage(victim);
        punch(soul); // 200 > its 150 health
        t.time.advance(2);
        assertFalse(t.world.isAlive(soul));
        assertTrue(t.engine.summons().find(p, "rent_soul").isEmpty(), "cleared away");
        assertEquals(50 + 90, t.damage(victim) - before, 1e-9, "the mirrored half, then the backlash");
        assertEquals(150, t.healed.getOrDefault(p, 0.0), 1e-9, "a large heal");
        assertTrue(t.render.lines.stream().anyMatch(l -> l[0].equals("soul_return")), "it flies back to them");
    }

    @Test
    void fleeingSnapsTheTetherTheSoulVanishesAndTheyreSlowed() throws IOException {
        setup();
        rend();
        UUID soul = soul();
        t.world.move(victim, new Vec3(20, 1, 0));
        t.time.advance(6);
        assertFalse(t.world.isAlive(soul), "gone");
        assertTrue(t.engine.tags().has(victim, Tags.SLOWED));
        double before = t.damage(victim);
        t.time.advance(10);
        assertEquals(before, t.damage(victim), 1e-9, "no backlash for fleeing");
        assertEquals(0, t.healed.getOrDefault(p, 0.0), 1e-9);
    }

    @Test
    void stayingNearItIsFineAndItFadesAfterSixSeconds() throws IOException {
        setup();
        rend();
        UUID soul = soul();
        t.world.move(victim, new Vec3(8, 1, 0));
        t.time.advance(100);
        assertTrue(t.world.isAlive(soul), "still there at 5s");
        assertFalse(t.engine.tags().has(victim, Tags.SLOWED), "not slowed: they stayed");
        t.time.advance(25);
        assertFalse(t.world.isAlive(soul), "faded after 6s");
        assertTrue(t.engine.links().onTarget(soul).isEmpty(), "tether gone too");
        assertEquals(0, t.healed.getOrDefault(p, 0.0), 1e-9);
    }
}

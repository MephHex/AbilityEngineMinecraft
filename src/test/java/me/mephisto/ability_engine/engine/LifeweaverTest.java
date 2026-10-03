package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.combat.DamageModifiers;
import me.mephisto.ability_engine.engine.loadout.Slots;
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
 * The Lifeweaver (fixtures/lifeweaver.yml): 200 HP, 5 armor, base damage 32. She stands at the origin on a floor, looking
 * +x, team blue. Allies are blue, enemies red (no sheet: 200 HP, no armor). The fake world has no health: a killing blow
 * is {@code engine.preventDeath} (what the platform asks before one lands), and dying is leaving the world.
 */
class LifeweaverTest {

    private static final double BASE = 32;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "lifeweaver");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private UUID ally(double x, double z) {
        UUID a = t.spawn(x, 1, z);
        t.world.team(a, "blue");
        return a;
    }

    private boolean use(String slot) { return t.engine.loadouts().activate(p, slot).success(); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private boolean alive(UUID id) { return t.world.positionOf(new EntityTarget(id)).isPresent(); }

    // ---- Lingering Soul -----------------------------------------------------------------------------------------

    @Test
    void aKillingBlowMakesHerASpectralRemnantFor5sThenSheDies() throws IOException {
        setup();
        var saved = t.engine.preventDeath(p);
        assertTrue(saved.isPresent(), "not dead yet");
        assertTrue(has(p, "lw_remnant"), "a Spectral Remnant");
        assertTrue(t.engine.tags().has(p, Tags.UNTARGETABLE));
        assertTrue(t.engine.tags().has(p, Tags.PHASING), "through units");
        assertFalse(use(Slots.ABILITY_1), "her abilities don't work...");
        assertTrue(t.engine.preventDeath(p).isEmpty(), "...and it only saves her once");

        t.time.advance(101);
        assertFalse(alive(p), "5s: she dies");
    }

    @Test
    void apotheosisAsARemnantRevivesHerAtHalfHealth() throws IOException {
        setup();
        t.engine.preventDeath(p);
        assertTrue(use(Slots.ULTIMATE), "her ultimate still works");
        assertFalse(has(p, "lw_remnant"), "revived");
        assertEquals(0.5, t.world.healthFraction.get(p), 1e-9, "at 50% HP");
        assertTrue(has(p, "lw_apotheosis"), "and it starts at once");
        t.time.advance(130);
        assertTrue(alive(p), "she lives on");
    }

    // ---- Life Drain ---------------------------------------------------------------------------------------------

    @Test
    void holdingRmbDrainsTheEnemyInHerSights() throws IOException {
        setup();
        UUID enemy = foe(8, 0);
        assertTrue(use(Slots.SECONDARY));
        for (int i = 0; i < 4; i++) { // held: RMB repeats every 4 ticks
            t.time.advance(4);
            t.engine.loadouts().activate(p, Slots.SECONDARY, false);
        }
        double held = t.damage(enemy);
        assertTrue(held >= BASE * 0.3 * 4 - 1e-6, "every 0.2s while held: " + held);
        assertEquals(held * 0.5, t.lifesteal.getOrDefault(p, 0.0), 1e-6, "half of it heals her");
        t.time.advance(20); // let go
        double after = t.damage(enemy);
        t.time.advance(20);
        assertEquals(after, t.damage(enemy), 1e-9, "let go: it stops");
    }

    // ---- Harmonic Strike ----------------------------------------------------------------------------------------

    @Test
    void theOrbStealsFromEnemiesOnTheWayOutAndHealsAlliesOnTheWayBack() throws IOException {
        setup();
        UUID first = foe(4, 0);
        UUID second = foe(7, 0);
        UUID friend = ally(10, 0);
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(60);
        assertEquals(BASE * 0.6, t.damage(first), 1e-6, "out: 60%");
        assertEquals(BASE * 0.6, t.damage(second), 1e-6, "through both");
        assertEquals(30 + 2 * 15, t.healed.getOrDefault(friend, 0.0), 1e-6, "back: 30 HP, +15 per enemy it hit");
        assertEquals(0, t.healed.getOrDefault(first, 0.0), 1e-9, "enemies aren't healed");
    }

    @Test
    void theOrbComesBackToWhereSheIsNowNotWhereSheThrewIt() throws IOException {
        setup();
        UUID oldSpot = ally(1.5, 0);   // on the straight way back to where she threw it
        assertTrue(use(Slots.ABILITY_1));
        t.time.advance(16);            // out and turned back
        t.world.move(p, new Vec3(3, 1, 10)); // she's moved off to the side
        t.time.advance(30);
        assertEquals(0, t.engine.projectiles().activeCount(), "it followed her and she caught it");
        assertEquals(0, t.healed.getOrDefault(oldSpot, 0.0), 1e-9, "not back to where she was");
        assertEquals(0, t.healed.getOrDefault(p, 0.0), 1e-9, "catching it doesn't heal her");
    }

    // ---- Beyond Life and Death ----------------------------------------------------------------------------------

    @Test
    void anAllyKilledUnderItDecaysAndAKillRevivesThemAtAQuarter() throws IOException {
        setup();
        UUID friend = ally(6, 0);
        UUID enemy = foe(12, 6);
        assertTrue(use(Slots.ABILITY_2));
        t.time.advance(2);
        assertTrue(has(friend, "lw_marked"), "the ally in her sights glows");
        assertTrue(use(Slots.PRIMARY)); // LMB: them
        assertTrue(has(friend, "lw_beyond"));
        assertEquals(1.25, t.engine.stats().moveSpeedMultiplier(friend), 1e-9, "faster");

        assertTrue(t.engine.preventDeath(friend).isPresent(), "a killing blow doesn't kill them...");
        assertTrue(has(friend, "lw_decaying"), "...they decay instead");
        assertFalse(has(friend, "lw_beyond"));
        t.time.advance(20);
        assertTrue(t.damage(friend) > 0, "draining");

        t.engine.notifyKill(friend, enemy, true); // they get a kill in time
        assertFalse(has(friend, "lw_decaying"), "revived");
        assertEquals(0.25, t.world.healthFraction.get(friend), 1e-9, "at 25% HP");
        t.time.advance(120);
        assertTrue(alive(friend));
    }

    @Test
    void withoutAKillTheDecayRunsOutAndTheyDie() throws IOException {
        setup();
        UUID friend = ally(6, 0);
        use(Slots.ABILITY_2);
        t.time.advance(2);
        use(Slots.PRIMARY);
        t.engine.preventDeath(friend);
        t.time.advance(101);
        assertFalse(alive(friend), "5s of decay: dead");
    }

    @Test
    void onHerselfLingeringSoulStillSavesHerAfterTheDecay() throws IOException {
        setup();
        use(Slots.ABILITY_2);
        assertTrue(use(Slots.SECONDARY)); // RMB: herself
        assertTrue(has(p, "lw_beyond"));
        t.engine.preventDeath(p);
        assertTrue(has(p, "lw_decaying"), "decaying first");
        assertFalse(has(p, "lw_remnant"));
        t.time.advance(101);
        assertTrue(alive(p), "the decay ran out...");
        assertTrue(has(p, "lw_remnant"), "...and her passive caught her");
        t.time.advance(101);
        assertFalse(alive(p));
    }

    // ---- Lifeline -----------------------------------------------------------------------------------------------

    @Test
    void aFullLifelineHealsThenShieldsThemBoth() throws IOException {
        setup();
        UUID friend = ally(6, 0);
        assertTrue(use(Slots.ABILITY_3));
        assertTrue(t.engine.cooldowns().remainingTicks(p, "lw_ab3") > 0);
        t.time.advance(70);
        assertEquals(6 * 200 * 0.04, t.healed.getOrDefault(friend, 0.0), 1e-6, "4% of their max HP every 0.5s");
        assertEquals(60, t.shields.getOrDefault(friend, 0.0), 1e-6, "held all the way: a shield");
        assertEquals(60, t.shields.getOrDefault(p, 0.0), 1e-6, "for her too");
    }

    @Test
    void aBrokenLifelineGivesNoShield() throws IOException {
        setup();
        UUID friend = ally(6, 0);
        use(Slots.ABILITY_3);
        t.time.advance(20);
        t.world.move(friend, new Vec3(40, 1, 0));
        t.time.advance(60);
        assertEquals(0, t.shields.getOrDefault(friend, 0.0), 1e-9);
    }

    // ---- Apotheosis ---------------------------------------------------------------------------------------------

    @Test
    void apotheosisShieldsAndMendsAlliesAroundHer() throws IOException {
        setup();
        UUID near = ally(4, 0);
        UUID far = ally(15, 0);
        UUID enemy = foe(3, 3);
        assertTrue(use(Slots.ULTIMATE));
        assertTrue(t.engine.tags().has(p, Tags.UNTARGETABLE), "a lattice: untargetable");
        assertTrue(t.engine.tags().has(p, Tags.PHASING));
        assertEquals(1.3, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "30% faster");
        assertFalse(use(Slots.ABILITY_1), "no abilities");
        assertTrue(has(near, "lw_sanctuary") && has(p, "lw_sanctuary"), "allies around her, her too");
        assertFalse(has(far, "lw_sanctuary"));
        assertFalse(has(enemy, "lw_sanctuary"));
        assertEquals(40, DamageModifiers.apply(t.engine, enemy, near, 100, 0).amount(), 1e-9, "60% less damage");
        t.time.advance(20);
        assertTrue(t.healed.getOrDefault(near, 0.0) > 0, "regenerating");
        t.time.advance(110);
        assertFalse(has(p, "lw_apotheosis"), "6s");
    }
}

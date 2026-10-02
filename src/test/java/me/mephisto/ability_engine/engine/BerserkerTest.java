package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.ability.activation.AbilityActivator;
import me.mephisto.ability_engine.engine.ability.activation.ActivationResult;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Berserker (fixtures/berserker.yml): 260 HP, 20 armor, base damage 42. He stands at the origin on a floor, looking
 * +x, team blue. Enemies are team red (no sheet: 200 HP, no armor).
 */
class BerserkerTest {

    private static final double BASE = 42;

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.world.players.add(p);
        t.engine.loadouts().assign(p, "berserker");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private UUID player(double x, double z) {
        UUID e = foe(x, z);
        t.world.players.add(e);
        return e;
    }

    private void use(String slot) { assertTrue(t.engine.loadouts().activate(p, slot).success(), slot); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    private long leapCooldown() { return t.engine.cooldowns().remainingTicks(p, "berserker_ab1"); }

    // ---- Axe Chop -------------------------------------------------------------------------------------------

    @Test
    void anAxeChopHitsWhoeverIsInFront() throws IOException {
        setup();
        UUID ahead = foe(2, 0);
        UUID behind = foe(-2, 0);
        use(Slots.PRIMARY);
        assertEquals(BASE, t.damage(ahead), 1e-6, "100% base damage");
        assertEquals(0, t.damage(behind), 1e-6);
    }

    @Test
    void aChopThatLandsOnAPlayerTakesTimeOffWhirlingLeap() throws IOException {
        setup();
        t.engine.cooldowns().start(p, "berserker_ab1", 160);
        UUID mob = foe(2, 0);
        use(Slots.PRIMARY);
        assertEquals(160, leapCooldown(), "a mob: nothing off");

        t.time.advance(20);
        t.world.kill(mob);
        player(2, 0);
        use(Slots.PRIMARY);
        assertEquals(160 - 20 - 5, leapCooldown(), "a player: 0.25s off");

        t.time.advance(20);
        use(Slots.PRIMARY); // ...and again (once per chop, not per player hit)
        assertEquals(160 - 40 - 10, leapCooldown());
    }

    @Test
    void aChopThatMissesTakesNothingOff() throws IOException {
        setup();
        t.engine.cooldowns().start(p, "berserker_ab1", 160);
        player(-3, 0); // behind him
        use(Slots.PRIMARY);
        assertEquals(160, leapCooldown());
    }

    // ---- Whirling Leap --------------------------------------------------------------------------------------

    @Test
    void whirlingLeapHopsAShortWayAndSlowsWhoeverItSpinsInto() throws IOException {
        setup();
        UUID enemy = foe(4, 0);
        use(Slots.ABILITY_1);
        t.time.advance(30);
        double x = t.world.positionOf(new me.mephisto.ability_engine.engine.target.EntityTarget(p)).orElseThrow().position().x();
        assertTrue(x > 1.8 && x < 3.5, "a short hop: " + x);
        assertEquals(BASE * 0.9, t.damage(enemy), 1e-6, "90% base damage");
        assertTrue(has(enemy, "berserker_hamstring"), "slowed");
    }

    // ---- War Cry --------------------------------------------------------------------------------------------

    @Test
    void warCryChargesUpThenItsChopsHealHim() throws IOException {
        setup();
        UUID enemy = foe(2, 0);
        use(Slots.ABILITY_2);
        t.time.advance(9);
        assertFalse(has(p, "berserker_war_cry"), "still drawing breath");
        t.time.advance(1);
        assertTrue(has(p, "berserker_war_cry"));

        use(Slots.PRIMARY);
        assertEquals(BASE, t.damage(enemy), 1e-6);
        assertEquals(BASE * 0.3, t.lifesteal.getOrDefault(p, 0.0), 1e-6, "30% of it back");
    }

    @Test
    void withoutWarCryAChopDoesntHeal() throws IOException {
        setup();
        foe(2, 0);
        use(Slots.PRIMARY);
        assertEquals(0, t.lifesteal.getOrDefault(p, 0.0), 1e-6);
    }

    // ---- Reaping Cleave -------------------------------------------------------------------------------------

    @Test
    void reapingCleaveDealsTrueDamageOnItsEdgeAndHealsIt() throws IOException {
        setup();
        UUID close = foe(1.5, 0);
        UUID edge = foe(0, 4);
        UUID far = foe(-6, 0);
        use(Slots.ABILITY_3);
        t.time.advance(14);
        assertEquals(0, t.damage(edge), 1e-6, "still winding up");
        t.time.advance(1);
        assertEquals(BASE * 0.9, t.damage(close), 1e-6, "the handle: just the swing");
        assertEquals(BASE * 0.9 + 200 * 0.08, t.damage(edge), 1e-6, "the blade: + 8% max HP");
        assertEquals(0, t.damage(far), 1e-6);
        assertEquals(200 * 0.08, t.lifesteal.getOrDefault(p, 0.0), 1e-6, "the true damage, all of it healed");
    }

    // ---- Bloodlust (passive) --------------------------------------------------------------------------------

    @Test
    void lowOnHealthHeHitsHarderAndSwingsFaster() throws IOException {
        setup();
        int normal = t.engine.stats().cooldownTicks(p, "berserker_primary", 17);
        t.world.healthFraction.put(p, 0.5);
        t.time.advance(10);
        assertFalse(has(p, "berserker_bloodlust"), "50%: not yet");

        t.world.healthFraction.put(p, 0.3);
        t.time.advance(5);
        assertTrue(has(p, "berserker_bloodlust"), "below 40%");
        assertTrue(t.engine.stats().cooldownTicks(p, "berserker_primary", 17) < normal, "faster swings");
        UUID enemy = foe(2, 0);
        use(Slots.PRIMARY);
        assertEquals(BASE * 1.25, t.damage(enemy), 1e-6, "+25% damage");

        t.time.advance(100);
        assertTrue(has(p, "berserker_bloodlust"), "kept on while he stays low");
        t.world.healthFraction.put(p, 0.8);
        t.time.advance(15);
        assertFalse(has(p, "berserker_bloodlust"), "healed back up: gone");
    }

    // ---- Rampage --------------------------------------------------------------------------------------------

    @Test
    void rampageShrugsOffCrowdControlButNotDamageOverTime() throws IOException {
        setup();
        UUID enemy = foe(10, 0);
        t.engine.statuses().apply(p, "root", enemy);
        t.engine.statuses().apply(p, "berserker_hamstring", enemy);
        use(Slots.ULTIMATE);
        assertFalse(has(p, "root"), "the root on him ends");
        assertFalse(has(p, "berserker_hamstring"), "and the slow");
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_KNOCKBACK));
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_DISPLACE));

        t.engine.statuses().apply(p, "stun", enemy);
        t.engine.statuses().apply(p, "silence", enemy);
        assertFalse(has(p, "stun"), "unstoppable");
        assertFalse(has(p, "silence"));
        t.engine.statuses().apply(p, "burn", enemy);
        assertTrue(has(p, "burn"), "damage over time still lands");

        t.time.advance(160);
        assertFalse(has(p, "berserker_rampage"));
        t.engine.statuses().apply(p, "stun", enemy);
        assertTrue(has(p, "stun"), "over: stoppable again");
    }

    @Test
    void rampageStopsTheToxinsSlowButNotItsPoison() throws IOException {
        setup();
        UUID fae = foe(10, 0);
        use(Slots.ULTIMATE);
        double rampaging = t.engine.stats().moveSpeedMultiplier(p); // Rampage x War Cry
        t.time.advance(100);
        t.engine.statuses().apply(p, "fae_toxin", fae); // 4s: past the end of Rampage
        assertTrue(has(p, "fae_toxin"), "it lands");
        assertTrue(t.engine.tags().has(p, Tags.POISONED), "poisoned");
        assertEquals(rampaging, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "but not slowed");
        t.time.advance(20);
        assertEquals(260 * 0.02, t.damage(p), 1e-6, "the poison still hurts (2% max HP a second)");

        t.time.advance(40); // Rampage (and its War Cry) over, 1s of toxin left
        assertFalse(has(p, "berserker_rampage"));
        assertTrue(has(p, "fae_toxin"));
        assertEquals(0.65, t.engine.stats().moveSpeedMultiplier(p), 1e-9, "slowed again for the time it has left");
    }

    @Test
    void aRootThatAlsoPoisonsLetsGoButKeepsPoisoning() throws IOException {
        setup();
        t.load(java.util.Map.of("statuses", java.util.Map.of("venom_snare", java.util.Map.of(
                "duration", 200, "tags", java.util.List.of("state.rooted", "block.move", "state.poisoned"),
                "tick", java.util.Map.of("every", 20, "effects",
                        java.util.List.of(java.util.Map.of("id", "damage", "amount", 10, "knockback", false)))))));
        UUID enemy = foe(10, 0);
        t.engine.statuses().apply(p, "venom_snare", enemy);
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE), "rooted");

        use(Slots.ULTIMATE);
        assertTrue(has(p, "venom_snare"), "it stays: not just crowd control");
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_MOVE), "but the root lets go");
        assertTrue(t.engine.tags().has(p, Tags.POISONED), "and the poison doesn't");
        t.time.advance(20);
        assertTrue(t.damage(p) > 0, "it still ticks");

        t.time.advance(140); // Rampage over, the snare still on
        assertTrue(t.engine.tags().has(p, Tags.BLOCK_MOVE), "rooted again");
        t.time.advance(40);
        assertFalse(has(p, "venom_snare"));
        assertFalse(t.engine.tags().has(p, Tags.BLOCK_MOVE), "and free when it ends");
    }

    @Test
    void warCryIsPassiveForTheWholeRampage() throws IOException {
        setup();
        use(Slots.ULTIMATE);
        assertTrue(has(p, "berserker_war_cry"), "War Cry on at once");
        assertEquals(160, t.engine.statuses().remainingTicks(p, "berserker_war_cry"), "for the whole 8s");
        var warCry = t.engine.abilities().find("berserker_ab2").orElseThrow();
        assertTrue(t.engine.activator().isPassive(p, warCry), "its icon glints");

        ActivationResult pressed = t.engine.loadouts().activate(p, Slots.ABILITY_2);
        assertFalse(pressed.success());
        assertEquals(AbilityActivator.PASSIVE, pressed.reason(), "the key does nothing");
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "berserker_ab2"), "and costs nothing");

        foe(2, 0);
        use(Slots.PRIMARY);
        assertEquals(BASE * 0.3, t.lifesteal.getOrDefault(p, 0.0), 1e-6, "its chops heal");

        t.time.advance(160);
        assertFalse(t.engine.activator().isPassive(p, warCry));
        use(Slots.ABILITY_2); // usable again
    }

    @Test
    void theXpBarCountsDownWarCry() throws IOException {
        setup();
        var bar = t.engine.loadouts().characterOf(p).orElseThrow().statusBar();
        assertEquals("berserker_war_cry", bar.status());
        assertEquals(me.mephisto.ability_engine.engine.loadout.CharacterDef.StatusBar.Level.SECONDS, bar.level());

        use(Slots.ULTIMATE); // 8s of War Cry, longer than its own 5s
        assertEquals(1, t.engine.statuses().gauge(p, "berserker_war_cry").orElseThrow().fraction(), 1e-9, "full");
        t.time.advance(80);
        assertEquals(0.5, t.engine.statuses().gauge(p, "berserker_war_cry").orElseThrow().fraction(), 1e-9,
                "halfway through the 8s, not still full");
    }
}

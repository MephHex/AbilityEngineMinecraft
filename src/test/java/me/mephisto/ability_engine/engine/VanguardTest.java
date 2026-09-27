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

/** The Vanguard (fixtures/vanguard.yml). He stands at the origin on a floor, looking +x, team blue. */
class VanguardTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        // A test attack: 100 damage to whoever it's aimed at (like a melee click).
        t.load(abilities("punch", map("nodes", map("hit", map("type", "apply_effects",
                "targets", map("type", "key", "key", "target"),
                "effects", list(map("id", "damage", "amount", 100)))))));
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "vanguard");
    }

    private UUID ally(double x, double z) {
        UUID a = t.spawn(x, 1, z);
        t.world.team(a, "blue");
        return a;
    }

    private UUID enemy(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private Vec3 pos(UUID id) { return t.world.positionOf(new EntityTarget(id)).orElseThrow().position(); }

    private void punch(UUID attacker, UUID victim) {
        assertTrue(t.engine.activator().activateOnId(attacker, "punch", new EntityTarget(victim), Map.of()).success());
    }

    private double shield(UUID id) { return t.shields.getOrDefault(id, 0.0); }

    /** Look at an entity (from the eye, which is the centre in the fake world). */
    private void lookAt(UUID target) { t.world.look(p, pos(target).subtract(pos(p))); }

    private void bond(UUID ally) {
        lookAt(ally);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
        t.world.look(p, new Vec3(1, 0, 0));
        assertTrue(t.engine.links().find(p, "radiant_bond").isPresent(), "bonded");
    }

    // ---- Passive: Inspiration -------------------------------------------------------------------

    @Test
    void abilitiesInspireNearbyAlliesButNotHimOrEnemies() throws IOException {
        setup();
        UUID near = ally(3, 3);
        UUID far = ally(20, 0);
        UUID foe = enemy(0, 4);
        t.engine.loadouts().activate(p, Slots.ABILITY_3); // Bulwark
        assertTrue(t.engine.statuses().has(near, "inspiration"));
        assertTrue(t.engine.tags().has(near, Tags.HASTED), "+30% speed");
        assertFalse(t.engine.statuses().has(far, "inspiration"), "8 blocks");
        assertFalse(t.engine.statuses().has(foe, "inspiration"));
        assertFalse(t.engine.statuses().has(p, "inspiration"), "his allies, not himself");
        t.time.advance(41);
        assertFalse(t.engine.statuses().has(near, "inspiration"), "2s");
    }

    @Test
    void theBasicAttackDoesntInspire() throws IOException {
        setup();
        UUID near = ally(3, 3);
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        assertFalse(t.engine.statuses().has(near, "inspiration"));
    }

    // ---- 1: Heroic Leap ----------------------------------------------------------------------------

    @Test
    void heroicLeapLandsAheadSlamsAndShieldsPerEnemyHit() throws IOException {
        setup();
        UUID a = enemy(11, 0);
        UUID b = enemy(10, 1.5);
        UUID far = enemy(25, 0);
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_1).success());
        assertTrue(pos(p).y() > 1.5, "up in the air");
        t.time.advance(40);
        assertTrue(pos(p).x() > 7 && pos(p).x() < 14, "landed ~10 blocks ahead: " + pos(p));
        assertEquals(50, t.damage(a), 1e-9);
        assertEquals(50, t.damage(b), 1e-9);
        assertEquals(0, t.damage(far), 1e-9);
        assertTrue(t.knockbackVec.get(a).y() > 0.5, "knocked up");
        assertEquals(40, shield(p), 1e-9, "20 absorption per enemy hit (2)");
    }

    @Test
    void heroicLeapWithNoOneAroundGivesNoShield() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        t.time.advance(40);
        assertEquals(0, shield(p), 1e-9);
    }

    @Test
    void theBondedAllyGetsTheSameAbsorption() throws IOException {
        setup();
        UUID friend = ally(4, 0);
        bond(friend);
        enemy(11, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_1);
        t.time.advance(40);
        assertEquals(20, shield(p), 1e-9);
        assertEquals(20, shield(friend), 1e-9, "copied to the tethered ally");
    }

    @Test
    void cantLeapWhileRooted() throws IOException {
        setup();
        t.engine.statuses().apply(p, "root", 40, null);
        assertEquals("blocked:" + Tags.BLOCK_MOVE, t.engine.loadouts().activate(p, Slots.ABILITY_1).reason());
    }

    // ---- 2: Radiant Bond --------------------------------------------------------------------------

    @Test
    void bondingAnAllyEmpowersThemAndStartsTheCooldown() throws IOException {
        setup();
        UUID friend = ally(6, 0.6); // not dead centre: the hitbox is generous
        t.world.look(p, new Vec3(1, 0, 0));
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success());
        assertEquals(friend, t.engine.links().find(p, "radiant_bond").orElseThrow().target());
        assertTrue(t.engine.statuses().has(friend, "strength"));
        assertTrue(t.engine.cooldowns().remainingTicks(p, "vanguard_ab2") > 0);
    }

    @Test
    void aMissWithNoBondIsFree() throws IOException {
        setup();
        enemy(6, 0); // enemies don't count
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        assertTrue(t.engine.links().find(p, "radiant_bond").isEmpty());
        assertEquals(0, t.engine.cooldowns().remainingTicks(p, "vanguard_ab2"), "no cooldown for a whiff");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_2).success(), "try again right away");
    }

    @Test
    void bondedAimingAtNothingReEmpowersAndAtAnotherAllySwaps() throws IOException {
        setup();
        UUID first = ally(0, 6);
        UUID second = ally(0, -6);
        bond(first);
        t.engine.statuses().remove(first, "strength");
        t.engine.cooldowns().clear(p, "vanguard_ab2");

        t.world.look(p, new Vec3(1, 0, 0)); // at nothing
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        assertTrue(t.engine.statuses().has(first, "strength"), "empowered again");
        assertEquals(first, t.engine.links().find(p, "radiant_bond").orElseThrow().target(), "same bond");
        assertTrue(t.engine.cooldowns().remainingTicks(p, "vanguard_ab2") > 0, "that's a use: cooldown");

        t.engine.cooldowns().clear(p, "vanguard_ab2");
        lookAt(second);
        t.engine.loadouts().activate(p, Slots.ABILITY_2);
        assertEquals(second, t.engine.links().find(p, "radiant_bond").orElseThrow().target(), "moved to the other ally");
    }

    @Test
    void theBondedAllyTakesLessAndHalfOfThePreventedHitsHim() throws IOException {
        setup();
        UUID friend = ally(4, 0);
        UUID foe = enemy(4, 2);
        bond(friend);
        punch(foe, friend);
        assertEquals(70, t.damage(friend), 1e-9, "30% less");
        assertEquals(15, t.damage(p), 1e-9, "half of the 30 prevented");
    }

    @Test
    void strengthAmpsTheirDamage() throws IOException {
        setup();
        UUID friend = ally(4, 0);
        UUID foe = enemy(6, 0);
        bond(friend);
        punch(friend, foe);
        assertEquals(125, t.damage(foe), 1e-9, "+25%");
    }

    @Test
    void positiveEffectsOnHimAreCopiedToTheAllyNegativeOnesArent() throws IOException {
        setup();
        UUID friend = ally(4, 0);
        bond(friend);
        t.engine.statuses().remove(friend, "strength");
        t.engine.statuses().apply(p, "strength", p);
        assertTrue(t.engine.statuses().has(friend, "strength"), "a buff on him lands on them too");
        t.engine.statuses().apply(p, "stun", 20, null);
        assertFalse(t.engine.tags().has(friend, Tags.STUNNED), "a stun isn't copied");
    }

    @Test
    void theBondBreaksWhenTheyGetTooFarApart() throws IOException {
        setup();
        UUID friend = ally(4, 0);
        bond(friend);
        t.world.move(friend, new Vec3(25, 1, 0));
        t.time.advance(1);
        assertTrue(t.engine.links().find(p, "radiant_bond").isEmpty());
        assertTrue(t.render.cues.contains("tether_break"));
        UUID foe = enemy(25, 2);
        punch(foe, friend);
        assertEquals(100, t.damage(friend), 1e-9, "no more protection");
    }

    // ---- 3: Bulwark ---------------------------------------------------------------------------------

    @Test
    void bulwarkDestroysProjectilesFromTheFrontButNotRays() throws IOException {
        setup();
        UUID shooter = enemy(10, 0);
        t.world.look(shooter, new Vec3(-1, 0, 0));
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        assertTrue(t.engine.tags().has(p, Tags.STURDY) && t.engine.tags().has(p, Tags.SLOWED));

        assertTrue(t.engine.activator().activate(shooter, "arcane_bolt").success());
        t.time.advance(10);
        assertEquals(0, t.damage(p), 1e-9, "the projectile was destroyed");
        assertTrue(t.render.cues.contains("barrier_block"));

        assertTrue(t.engine.activator().activate(shooter, "test_blast").success()); // a hitscan
        assertEquals(40, t.damage(p), 1e-9, "only projectiles are stopped");
    }

    @Test
    void bulwarkHalvesKnockbackAndLastsFourSeconds() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.time.advance(81);
        assertFalse(t.engine.tags().has(p, Tags.STURDY), "down after 4s");
        assertFalse(t.engine.barriers().has(p));
        assertTrue(t.engine.cooldowns().remainingTicks(p, "vanguard_ab3") > 0, "cooldown once it's down");
    }

    @Test
    void shieldRushChargesStunsTheFirstEnemyAndLowersTheShield() throws IOException {
        setup();
        UUID first = enemy(5, 0);
        UUID behind = enemy(7, 0);
        t.engine.loadouts().activate(p, Slots.ABILITY_3);
        t.time.advance(10);
        t.engine.loadouts().activate(p, Slots.ABILITY_3); // recast
        t.time.advance(10);
        assertTrue(t.engine.tags().has(first, Tags.STUNNED));
        assertFalse(t.engine.tags().has(behind, Tags.STUNNED), "only the first");
        assertTrue(pos(p).x() > 2 && pos(p).x() < 5, "charged up to them: " + pos(p));
        assertFalse(t.engine.barriers().has(p), "shield down");
        assertFalse(t.engine.tags().has(p, Tags.SLOWED));
    }

    // ---- Ultimate: Hero's Descent -------------------------------------------------------------------

    /** Launch, wait for the top of the arc: the landing preview opens. */
    private void launch() {
        assertTrue(t.engine.loadouts().activate(p, Slots.ULTIMATE).success());
        for (int i = 0; i < 40 && !t.engine.targeting().isTargeting(p); i++) t.time.advance(1);
        assertTrue(t.engine.targeting().isTargeting(p), "choosing a landing spot");
        assertTrue(pos(p).y() > 8, "high in the sky: " + pos(p));
        assertTrue(t.engine.tags().has(p, Tags.ANCHORED), "hovering");
    }

    @Test
    void heroesDescentLandsWhereYouConfirmThrowsEnemiesAndShieldsAllies() throws IOException {
        setup();
        UUID nearFoe = enemy(13, 0);
        UUID edgeFoe = enemy(16, 0);
        UUID friend = ally(12, 2);
        launch();
        // Look at the floor around x=12 from up there, and confirm (LMB).
        t.world.look(p, new Vec3(12, 0, 0).subtract(pos(p)));
        assertTrue(t.engine.targeting().confirm(p).success());
        assertFalse(t.engine.tags().has(p, Tags.ANCHORED), "falling");
        t.time.advance(20);
        assertTrue(Math.abs(pos(p).x() - 12) < 1.5 && pos(p).y() < 2, "landed on the spot: " + pos(p));
        assertEquals(60, t.damage(nearFoe), 1e-9);
        assertTrue(t.knockback.get(nearFoe) > t.knockback.get(edgeFoe), "closer = thrown harder");
        assertEquals(120, shield(friend), 1e-9);
        assertEquals(120, shield(p), 1e-9, "himself too");
        assertEquals(0, shield(nearFoe), 1e-9);
    }

    @Test
    void notConfirmingLandsWhereYouLookAfterThreeSeconds() throws IOException {
        setup();
        launch();
        t.world.look(p, new Vec3(8, 0, 0).subtract(pos(p)));
        t.time.advance(80);
        assertFalse(t.engine.targeting().isTargeting(p));
        assertTrue(Math.abs(pos(p).x() - 8) < 1.5 && pos(p).y() < 2, "landed where he looked: " + pos(p));
    }
}

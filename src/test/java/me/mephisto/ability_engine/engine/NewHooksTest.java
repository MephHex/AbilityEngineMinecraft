package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.effect.Knockback;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * on_damaged, ally_healing_dealt, damage falloff, vertical knockback, release_charge (and pressing a charging ability's
 * key again), projectile fans / count_bonus / heading, and set's stacks:. The caster at the origin looking +x, team blue.
 */
class NewHooksTest {

    private TestEngine t;
    private UUID me;

    private void setup(Map<String, Object> content) {
        t = new TestEngine();
        t.load(content);
        me = t.spawn(0, 1, 0);
        t.world.team(me, "blue");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private static Map<String, Object> hitAll(double amount) {
        return map("nodes", map("hit", map("type", "apply_effects", "targets", map("type", "radius", "radius", 8),
                "effects", list(map("id", "damage", "amount", amount)))));
    }

    @Test
    void onDamagedLandsOnTheHolderForEveryHitFromAnEnemy() {
        setup(map(
                "statuses", map(
                        "bracing", map("duration", 100, "on_damaged", list(map("id", "status", "status", "grit"))),
                        "grit", map("duration", 100, "stacking", "stack", "max_stacks", 10)),
                "abilities", map("hit", hitAll(5))));
        t.engine.statuses().apply(me, "bracing", 100, me);
        UUID enemy = foe(3, 0);
        t.engine.activator().activate(enemy, "hit");
        t.engine.activator().activate(enemy, "hit");
        assertEquals(2, t.engine.statuses().find(me, "grit").orElseThrow().stacks());

        UUID friend = t.spawn(3, 1, 2);
        t.world.team(friend, "blue");
        t.engine.activator().activate(friend, "hit"); // (their area doesn't hurt allies anyway; and an ally's hit wouldn't count)
        t.engine.notifyDamaged(me, friend);
        t.engine.notifyDamaged(me, null);              // a fall: nobody
        assertEquals(2, t.engine.statuses().find(me, "grit").orElseThrow().stacks());
    }

    @Test
    void allyHealingDealtStrengthensHealsOnOthersNotOnYourselfOrShields() {
        setup(map(
                "statuses", map("amulet", map("ally_healing_dealt", 1.4)),
                "abilities", map("mend", map("nodes", map("h", map("type", "apply_effects",
                        "targets", map("type", "radius", "radius", 8, "include_caster", true), "affects", "allies",
                        "effects", list(map("id", "heal", "amount", 50), map("id", "shield", "amount", 20))))))));
        UUID friend = t.spawn(2, 1, 0);
        t.world.team(friend, "blue");
        t.engine.statuses().apply(me, "amulet", 0, me);
        t.engine.activator().activate(me, "mend");
        assertEquals(70, t.healed.get(friend), 1e-9, "50 x 1.4");
        assertEquals(50, t.healed.get(me), 1e-9, "on yourself: as it is");
        assertEquals(20, t.shields.get(friend), 1e-9, "shields: as they are");
    }

    @Test
    void falloffEachHitInARowDoesLessDownToTheMinimum() {
        setup(map(
                "statuses", map("hit_by", map("duration", 40, "stacking", "stack", "max_stacks", 50)),
                "abilities", map("shard", map("nodes", map("h", map("type", "apply_effects",
                        "targets", map("type", "radius", "radius", 8),
                        "effects", list(map("id", "damage", "amount", 100,
                                        "falloff", map("status", "hit_by", "per_stack", 0.05, "min", 0.1)),
                                map("id", "status", "status", "hit_by"))))))));
        UUID enemy = foe(3, 0);
        double[] want = {100, 95, 90, 85, 80};
        double total = 0;
        for (double w : want) {
            t.engine.activator().activate(me, "shard");
            total += w;
            assertEquals(total, t.damage(enemy), 1e-6);
        }
        for (int i = 0; i < 30; i++) t.engine.activator().activate(me, "shard"); // way down
        double before = t.damage(enemy);
        t.engine.activator().activate(me, "shard");
        assertEquals(10, t.damage(enemy) - before, 1e-6, "never below 10%");
        t.time.advance(41);                              // a new run (the status ran out): full again
        before = t.damage(enemy);
        t.engine.activator().activate(me, "shard");
        assertEquals(100, t.damage(enemy) - before, 1e-6);
    }

    @Test
    void verticalKnockbackThrowsUpOffABlastBelow() {
        Vec3 flat = Knockback.impulse(new Vec3(0, 0, 0), new Vec3(0.1, 1, 0), 1,
                1.3, 1.3, 0.35);
        Vec3 up = Knockback.impulse(new Vec3(0, 0, 0), new Vec3(0.1, 1, 0), 1,
                1.3, 1.3, 0.35, true);
        assertEquals(0.35, flat.y(), 1e-9, "sideways only: just the lift");
        assertTrue(up.y() > 1.5, "straight away from it: up, " + up);
        assertTrue(Math.abs(up.x()) < 0.2);
    }

    @Test
    void releaseChargeAndPressingItsKeyAgainLetAChargeGo() {
        setup(map("abilities", map(
                "volley", map("nodes", map(
                        "charge", map("type", "charge", "ticks", 40, "on", map("out", "boom", "early", "boom")),
                        "boom", map("type", "apply_effects", "targets", map("type", "radius", "radius", 8),
                                "effects", list(map("id", "damage", "amount", 10))))),
                "loose", map("nodes", map("go", map("type", "release_charge", "ability", "volley"))))));
        UUID enemy = foe(3, 0);
        t.engine.activator().activate(me, "volley");
        t.time.advance(5);
        assertEquals(0, t.damage(enemy), 1e-9, "still charging");
        t.engine.activator().activate(me, "loose");
        assertEquals(10, t.damage(enemy), 1e-9, "let go");

        t.engine.cooldowns().clearAll(me);
        t.engine.activator().activate(me, "volley");
        t.time.advance(5);
        t.engine.activator().activate(me, "volley", false); // held: nothing
        assertEquals(10, t.damage(enemy), 1e-9);
        t.engine.activator().activate(me, "volley", true);  // pressed again: let go
        assertEquals(20, t.damage(enemy), 1e-9);
        assertEquals(0, t.engine.instances().count(), "no second cast started");
    }

    @Test
    void aFanSpreadsEvenlyCountBonusAddsAndHeadingFollowsTheShotThatHit() {
        setup(map(
                "statuses", map("loaded", map("duration", 100, "stacking", "stack", "max_stacks", 10)),
                "abilities", map("burst", map("nodes", map(
                        "count", map("type", "set", "key", "shards", "value", "stacks:loaded", "next", "shot"),
                        "shot", map("type", "projectile", "speed", 1.0, "size", 0.4, "range", 20,
                                "on", map("hit_entity", "spray")),
                        "spray", map("type", "projectile", "from", "hit", "heading", "flight", "count_bonus", "shards",
                                "fan", 90, "speed", 1.0, "size", 0.3, "range", 6, "through_blocks", true,
                                "on", map("hit_entity", "dmg")),
                        "dmg", map("type", "apply_effects", "targets", map("type", "key", "key", "hit"),
                                "effects", list(map("id", "damage", "amount", 1))))))));
        for (int i = 0; i < 3; i++) t.engine.statuses().apply(me, "loaded", 100, me);
        UUID first = foe(4, 0);
        // behind it, along its flight (+x): one straight on, two at 45 degrees either side; nothing behind the caster
        UUID ahead = foe(8, 0), left = foe(7, 3), right = foe(7, -3), back = foe(-2, 0);
        t.engine.activator().activate(me, "burst");
        t.time.advance(20);
        assertEquals(1, t.damage(ahead), 1e-9);
        assertEquals(1, t.damage(left), 1e-9);
        assertEquals(1, t.damage(right), 1e-9);
        assertEquals(0, t.damage(back), 1e-9);
        assertEquals(0, t.damage(first), 1e-9, "the shards burst out of the first one: not into it again");
    }
}

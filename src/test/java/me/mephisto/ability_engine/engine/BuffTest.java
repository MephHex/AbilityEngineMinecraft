package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Buffs (statuses with on_hit effects) and damage-over-time, from the shipped abilities.yml. */
class BuffTest {

    private TestEngine t;
    private UUID p;
    private UUID mob;

    private void setup() throws IOException {
        t = new TestEngine();
        me.mephisto.ability_engine.engine.testkit.ShippedContent.loadClean(t.engine);
        p = t.spawn(0, 1, 0);          // looking +x
        mob = t.spawn(6, 1, 0);
        t.engine.loadouts().assign(p, "archmage");
    }

    private void buff(String ability) {
        assertTrue(t.engine.activator().activate(p, ability).success());
    }

    private void advanceUntilHit(UUID who) {
        for (int i = 0; i < 60 && t.damage(who) == 0; i++) t.time.advance(1);
    }

    @Test
    void primaryFireAppliesOnHitsByDefault() throws IOException {
        setup();
        buff("buff_damage_test");
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        advanceUntilHit(mob);
        assertEquals(30 + 20, t.damage(mob), 1e-9, "bolt burst + empowered on-hit");
    }

    @Test
    void noBuffNoBonus() throws IOException {
        setup();
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        advanceUntilHit(mob);
        assertEquals(30, t.damage(mob), 1e-9);
    }

    @Test
    void theSameAbilityOutsideThePrimarySlotDoesnt() throws IOException {
        setup();
        buff("buff_damage_test");
        t.engine.activator().activate(p, "arcane_bolt"); // e.g. /ae cast: not from the primary slot
        advanceUntilHit(mob);
        assertEquals(30, t.damage(mob), 1e-9);
    }

    @Test
    void burningTouchSetsTargetsOnFireForFourTicks() throws IOException {
        setup();
        buff("buff_burn_test");
        t.engine.loadouts().activate(p, Slots.PRIMARY);
        advanceUntilHit(mob);
        assertTrue(t.engine.tags().has(mob, Tags.BURNING), "burning");
        assertEquals(30, t.damage(mob), 1e-9, "no burn damage yet");

        t.time.advance(40);
        assertEquals(30 + 4 * 5, t.damage(mob), 1e-9, "4 burn ticks of 5 over 2s");
        assertFalse(t.engine.tags().has(mob, Tags.BURNING), "burned out");
        t.time.advance(40);
        assertEquals(50, t.damage(mob), 1e-9, "and it stops");
    }

    @Test
    void arcaneMissileOptsInOnItsDirectHit() throws IOException {
        setup();
        buff("buff_damage_test");
        t.engine.loadouts().activate(p, Slots.ABILITY_1); // not primary: the node's on_hit: true does it
        advanceUntilHit(mob);
        assertEquals(25 + 20, t.damage(mob), 1e-9);
    }

    @Test
    void onHitsNeverLandOnYourself() throws IOException {
        setup();
        buff("buff_damage_test");
        buff("buff_damage_test"); // a self-targeted ability while buffed
        assertEquals(0, t.damage(p), 1e-9);
    }

    @Test
    void bindingStunShatterCarriesBuffsToEveryoneButThePlainShatterDoesnt() throws IOException {
        t = new TestEngine();
        me.mephisto.ability_engine.engine.testkit.ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        UUID near = t.spawn(5.2, 1, 0.8);
        UUID edge = t.spawn(4.7, 1, 3.0);
        buff("buff_damage_test");

        // recast Missile into the Binding: stun shatter, buffs apply to both
        t.world.look(p, new Vec3(1, -0.2, 0));
        t.engine.activator().activate(p, "unstable_binding");
        assertTrue(t.engine.targeting().confirm(p).success());
        t.world.look(p, new Vec3(1, 0, 0));
        t.time.advance(2);
        t.engine.activator().activate(p, "arcane_missile");
        t.time.advance(2);
        t.engine.activator().activate(p, "arcane_missile");
        advanceUntilHit(near);
        assertEquals(30 + 20, t.damage(near), 1e-9);
        assertEquals(30 + 20, t.damage(edge), 1e-9);

        // a Binding that just runs out: plain shatter, no on-hits
        t.engine.cooldowns().clearAll(p);
        t.world.look(p, new Vec3(1, -0.2, 0));
        t.engine.activator().activate(p, "unstable_binding");
        assertTrue(t.engine.targeting().confirm(p).success());
        t.time.advance(60);
        assertEquals(50 + 30, t.damage(near), 1e-9, "second shatter added 30, no +20");
    }
}

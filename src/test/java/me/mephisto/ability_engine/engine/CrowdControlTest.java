package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What each crowd control stops, per slot (the HUD shows a barrier on those): silence = everything but
 * basic attacks, disarm = only basic attacks, stun = everything, root = movement abilities.
 * The Umbrella test kit: primary test_blast, 1 Royal Lunge (a dash), 2 Parasol Guard, 3 Piercing Thrust.
 */
class CrowdControlTest {

    private TestEngine t;
    private UUID p;

    private void setup() throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(p, "duelist");
    }

    private Optional<String> cc(String slot) { return t.engine.loadouts().crowdControl(p, slot); }

    private boolean works(String slot) {
        boolean ok = t.engine.loadouts().activate(p, slot).success();
        t.engine.instances().cancelAll(p, "src/test");
        t.engine.cooldowns().clearAll(p);
        return ok;
    }

    @Test
    void silenceStopsAbilitiesButNotPrimaryFire() throws IOException {
        setup();
        t.engine.statuses().apply(p, "silence", null);
        assertEquals(Optional.empty(), cc(Slots.PRIMARY));
        assertEquals(Optional.of(Tags.SILENCED), cc(Slots.ABILITY_1));
        assertEquals(Optional.of(Tags.SILENCED), cc(Slots.ULTIMATE));
        assertTrue(works(Slots.PRIMARY), "primary fire still works");
        assertFalse(works(Slots.ABILITY_2));
        assertEquals("blocked:state.silenced", t.engine.loadouts().activate(p, Slots.ABILITY_3).reason());
    }

    @Test
    void disarmStopsOnlyPrimaryFire() throws IOException {
        setup();
        t.engine.statuses().apply(p, "disarm", null);
        assertEquals(Optional.of(Tags.DISARMED), cc(Slots.PRIMARY));
        assertEquals(Optional.empty(), cc(Slots.ABILITY_2));
        assertFalse(works(Slots.PRIMARY));
        assertTrue(works(Slots.ABILITY_2), "abilities still work");
    }

    @Test
    void stunStopsEverything() throws IOException {
        setup();
        t.engine.statuses().apply(p, "stun", null);
        for (String slot : new String[]{Slots.PRIMARY, Slots.ABILITY_1, Slots.ABILITY_2, Slots.ABILITY_3, Slots.ULTIMATE}) {
            assertEquals(Optional.of(Tags.STUNNED), cc(slot), slot);
            assertFalse(works(slot), slot);
        }
    }

    @Test
    void rootStopsOnlyMovementAbilities() throws IOException {
        setup();
        t.engine.statuses().apply(p, "root", null);
        assertEquals(Optional.of(Tags.ROOTED), cc(Slots.ABILITY_1), "Royal Lunge is a dash");
        assertEquals(Optional.empty(), cc(Slots.ABILITY_3), "Piercing Thrust isn't");
        assertEquals(Optional.empty(), cc(Slots.PRIMARY));
        assertFalse(works(Slots.ABILITY_1));
        assertTrue(works(Slots.ABILITY_3));
    }

    @Test
    void nothingAppliedNothingBlocked() throws IOException {
        setup();
        for (String slot : Slots.ALL) assertEquals(Optional.empty(), cc(slot), slot);
    }
}

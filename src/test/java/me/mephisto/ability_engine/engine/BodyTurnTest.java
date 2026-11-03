package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.BodyTurn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A model's body turns as a vanilla player's: the head at most 50 degrees from it, toward where they walk. */
class BodyTurnTest {

    private static float settle(float body, float look, double dx, double dz, boolean attacking, int ticks) {
        for (int i = 0; i < ticks; i++) body = BodyTurn.next(body, look, dx, dz, attacking);
        return body;
    }

    @Test
    void standingStillALittleAsideTheBodyStays() {
        assertEquals(0, BodyTurn.next(0, 30, 0, 0, false), 1e-4);
    }

    @Test
    void lookingFurtherRoundDragsTheBodyTo50Degrees() {
        assertEquals(40, BodyTurn.next(0, 90, 0, 0, false), 1e-4, "a 90 degree look: the head 50 from the body");
        assertEquals(-50, BodyTurn.next(0, -100, 0, 0, false), 1e-4);
        assertEquals(-130, BodyTurn.next(180, -80, 0, 0, false), 1e-4, "across the -180/180 seam");
    }

    @Test
    void walkingTheBodyTurnsToWhereTheyGo() {
        // yaw 0 = +z (south): walking south while looking 40 to the side
        float body = settle(40, 40, 0, 0.2, false, 40);
        assertEquals(0, body, 0.01);
    }

    @Test
    void backingUpTheBodyFacesAwayFromTheWayTheyGo() {
        float body = settle(0, 0, 0, -0.2, false, 40); // looking south, moving north
        assertEquals(0, body, 0.01);
    }

    @Test
    void attackingTheBodyTurnsToTheAim() {
        float body = settle(0, 45, 0, 0, true, 40);
        assertEquals(45, body, 0.01);
        assertTrue(Math.abs(BodyTurn.next(0, 45, 0, 0, true) - 13.5f) < 1e-4, "a third of the way a tick");
    }

    @Test
    void wrapStaysInRange() {
        assertEquals(-170, BodyTurn.wrap(190), 1e-4);
        assertEquals(170, BodyTurn.wrap(-190), 1e-4);
        assertEquals(-180, BodyTurn.wrap(180), 1e-4);
    }
}

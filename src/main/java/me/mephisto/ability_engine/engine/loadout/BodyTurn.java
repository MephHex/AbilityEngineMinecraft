package me.mephisto.ability_engine.engine.loadout;

/**
 * Where a body faces, tick by tick, as vanilla turns a player's (LivingEntity.tick / tickHeadTurn): walking, it turns
 * toward where they go (backward when they back up); attacking, toward where they look; and it never lets the head
 * turn more than 50 degrees away from it, dragging along when they look further round. Standing still and looking a
 * little aside, it stays put. Degrees, Minecraft yaw.
 */
public final class BodyTurn {

    /** Vanilla: the most a head turns away from its body (LivingEntity.getMaxHeadRotationRelativeToBody). */
    public static final float MAX_HEAD_TURN = 50;

    private BodyTurn() {}

    /**
     * @param body      where the body faced last tick
     * @param look      where they look now
     * @param dx        how far they moved along x this tick (blocks)
     * @param dz        along z
     * @param attacking swinging / attacking: the body turns to their aim
     * @return where the body faces now
     */
    public static float next(float body, float look, double dx, double dz, boolean attacking) {
        float target = body;
        if (dx * dx + dz * dz > 0.0025) { // moving (vanilla: over 0.05 blocks a tick)
            float heading = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            float off = Math.abs(wrap(look) - heading);
            target = off > 95 && off < 265 ? heading - 180 : heading; // backing up: facing away from the way they go
        }
        if (attacking) target = look;
        body += wrap(target - body) * 0.3f;
        float head = wrap(look - body);
        if (Math.abs(head) > MAX_HEAD_TURN) body += head - Math.signum(head) * MAX_HEAD_TURN;
        return wrap(body);
    }

    /** Into -180..180. */
    public static float wrap(float degrees) {
        float d = degrees % 360;
        if (d >= 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }
}

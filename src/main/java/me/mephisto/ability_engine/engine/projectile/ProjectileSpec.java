package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.List;

/**
 * Everything about a projectile type, as data. Values that were hardcoded in ProjectileCast
 * (speed 1.4, lifetime 1000, bounce damping 0.6, DIAMOND_BLOCK) live here now.
 *
 * @param speed         blocks per tick at launch
 * @param size          hitbox diameter in blocks (also visual scale)
 * @param visual        platform-specific visual id; on Bukkit a Material name
 * @param count         projectiles per cast (shotguns); each is its own branch
 * @param spreadDegrees max random deviation from the crosshair, per projectile
 * @param restitution    share of the "into the surface" speed that comes back out (0 = dead, 1 = superball)
 * @param friction       how much a hard impact kills speed along the surface (Coulomb-style, see {@link #bounce})
 * @param minBounceSpeed rebounds slower than this don't happen: the projectile lands (exits hit_block)
 * @param range          blocks it may travel UNGUIDED before expiring (0 = only lifetime limits it).
 *                       Distance flown while guided doesn't count; lifetimeTicks stays a hard cap.
 * @param pierce         enemies it passes through, each hit on its own (like vanilla Piercing); it stops
 *                       at the next one after that
 * @param slide          out of bounces, landing on the ground doesn't stop it: it slides, keeping this
 *                       share (0..1) of its speed each tick on the ground, and stops (hit_block) when it's
 *                       nearly still or hits a wall. 0 = it stops where it lands
 * @param visualSize     the visual's scale when it should differ from the hitbox (0 = {@code size})
 * @param faceFlight     the visual turns to point along its flight (a thrown pickaxe, head first)
 * @param health         above 0: the projectile has a body enemies can hit and kill (design HP); when it's
 *                       killed the projectile ends ("destroyed")
 * @param throughBlocks  it flies through terrain (only entities stop it)
 * @param hitsCaster     it can hit its own caster too (exits hit_entity), once it has flown clear of them
 * @param hitsAllies     allies don't let it through: it hits friend and foe alike (exits hit_entity either way)
 * @param bouncesOffOwn  it bounces off the caster's own constructs from the same ability (solid or not), e.g. a
 *                       thrown trap glancing off one already planted
 */
public record ProjectileSpec(
        double speed,
        double size,
        int lifetimeTicks,
        int maxBounces,
        double restitution,
        double friction,
        double minBounceSpeed,
        List<MotionModifier> motion,
        String visual,
        int count,
        double spreadDegrees,
        double range,
        int pierce,
        double slide,
        double visualSize,
        boolean faceFlight,
        double health,
        boolean throughBlocks,
        boolean hitsCaster,
        boolean hitsAllies,
        boolean bouncesOffOwn
) {
    public ProjectileSpec {
        motion = List.copyOf(motion);
        if (count < 1) count = 1;
    }

    public static Builder builder() { return new Builder(); }

    /** The visual's scale: {@code visual_size}, or the hitbox size. */
    public double shownSize() { return visualSize > 0 ? visualSize : size; }

    /**
     * Velocity after hitting a surface with unit normal {@code n}, or null if it's too slow to bounce.
     * The velocity is split into the part going INTO the surface and the part sliding ALONG it:
     * <ul>
     *   <li>into: reversed and scaled by restitution</li>
     *   <li>along: loses {@code friction * impulse}, so the loss grows with how hard it hit. A throw at
     *       your feet is almost all "into" and thuds nearly in place; a low skim keeps rolling.</li>
     * </ul>
     */
    public Vec3 bounce(Vec3 v, Vec3 n) {
        double into = -v.dot(n);                    // speed toward the surface
        double rebound = into * restitution;
        if (rebound < minBounceSpeed) return null;  // also covers grazing / moving away
        Vec3 along = v.add(n.multiply(into));       // v minus its normal component
        double alongSpeed = along.length();
        double keep = alongSpeed < 1e-9 ? 0 : Math.max(0, 1 - friction * (1 + restitution) * into / alongSpeed);
        return along.multiply(keep).add(n.multiply(rebound));
    }

    public static final class Builder {
        private double speed = 1.4;
        private double size = 0.5;
        private int lifetimeTicks = 200;
        private int maxBounces = 0;
        private double restitution = 0.35;
        private double friction = 0.4;
        private double minBounceSpeed = 0.05;
        private List<MotionModifier> motion = List.of();
        private String visual = "DIAMOND_BLOCK";
        private int count = 1;
        private double spreadDegrees = 0;
        private double range = 0;
        private int pierce = 0;
        private double slide = 0;
        private double visualSize = 0;
        private boolean faceFlight = false;
        private double health = 0;
        private boolean throughBlocks = false;
        private boolean hitsCaster = false;
        private boolean hitsAllies = false;
        private boolean bouncesOffOwn = false;

        public Builder speed(double v) { speed = v; return this; }
        public Builder size(double v) { size = v; return this; }
        public Builder lifetimeTicks(int v) { lifetimeTicks = v; return this; }
        public Builder maxBounces(int v) { maxBounces = v; return this; }
        public Builder restitution(double v) { restitution = v; return this; }
        public Builder friction(double v) { friction = v; return this; }
        public Builder minBounceSpeed(double v) { minBounceSpeed = v; return this; }
        public Builder motion(List<MotionModifier> v) { motion = v; return this; }
        public Builder visual(String v) { visual = v; return this; }
        public Builder count(int v) { count = v; return this; }
        public Builder spreadDegrees(double v) { spreadDegrees = v; return this; }
        public Builder range(double v) { range = v; return this; }
        public Builder pierce(int v) { pierce = v; return this; }
        public Builder slide(double v) { slide = v; return this; }
        public Builder visualSize(double v) { visualSize = v; return this; }
        public Builder faceFlight(boolean v) { faceFlight = v; return this; }
        public Builder health(double v) { health = v; return this; }
        public Builder throughBlocks(boolean v) { throughBlocks = v; return this; }
        public Builder hitsCaster(boolean v) { hitsCaster = v; return this; }
        public Builder hitsAllies(boolean v) { hitsAllies = v; return this; }
        public Builder bouncesOffOwn(boolean v) { bouncesOffOwn = v; return this; }

        public ProjectileSpec build() {
            return new ProjectileSpec(speed, size, lifetimeTicks, maxBounces, restitution, friction, minBounceSpeed,
                    motion, visual, count, spreadDegrees, range, pierce, slide, visualSize, faceFlight, health, throughBlocks, hitsCaster,
                    hitsAllies, bouncesOffOwn);
        }
    }
}

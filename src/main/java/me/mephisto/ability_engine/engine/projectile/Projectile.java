package me.mephisto.ability_engine.engine.projectile;

import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.ProjectileVisual;

import java.util.List;

/** One projectile in flight. Mutable, owned by ProjectileSystem; exposed to graphs as a ProjectileHandle. */
final class Projectile implements ProjectileHandle {
    final ProjectileSpec spec;
    final String world;
    final Resumer resumer;
    final ProjectileVisual visual;
    Vec3 position;
    Vec3 velocity;
    List<MotionModifier> motion;
    int ticksLived;
    int guidedUntilTick = -1;   // guided while ticksLived <= this
    double unguidedDistance;
    Vec3 sweepFrom;             // first tick only: check collisions from here (the eye)
    Vec3 lastPosition;          // where it was the tick before (its approach, for hit direction)
    int bouncesLeft;
    boolean done;
    boolean redirected;         // velocity was set from outside: a self-flying visual must be told
    int piercesLeft;
    boolean clearOfCaster;      // hits_caster: it has flown clear of the caster, who can be hit from now on
    int hoverTicks;             // seek: time spent hovering at its max distance
    Vec3 heading;               // seek: the way it flies when it isn't homing
    final java.util.Set<java.util.UUID> pierced = new java.util.HashSet<>(); // already hit: fly through them

    Projectile(ProjectileSpec spec, String world, Vec3 position, Vec3 velocity, Resumer resumer, ProjectileVisual visual) {
        this.spec = spec;
        this.world = world;
        this.position = position;
        this.velocity = velocity;
        this.resumer = resumer;
        this.visual = visual;
        this.motion = spec.motion();
        this.lastPosition = position;
        this.bouncesLeft = spec.maxBounces();
        this.piercesLeft = Math.max(0, spec.pierce());
    }

    @Override public boolean isAlive() { return !done; }
    @Override public String world() { return world; }
    @Override public Vec3 position() { return position; }
    @Override public Vec3 velocity() { return velocity; }

    @Override
    public void redirect(Vec3 newVelocity) {
        if (done) return;
        velocity = newVelocity;
        redirected = true;
    }

    @Override
    public void setMotion(List<MotionModifier> newMotion) {
        if (!done) motion = List.copyOf(newMotion);
    }

    /** Lease of two ticks: steering runs on its own tick schedule, so don't lapse between calls. */
    @Override
    public void markGuided() { guidedUntilTick = ticksLived + 2; }

    boolean isGuided() { return ticksLived <= guidedUntilTick; }

    @Override
    public double unguidedDistance() { return unguidedDistance; }
}

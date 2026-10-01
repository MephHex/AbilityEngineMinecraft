package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Leash the entity in {@code target} to the caster for {@code duration} ticks: wherever the caster goes
 * (running, flying), they're dragged along. Within {@code length} blocks the leash is slack; beyond it they're
 * pulled toward the caster every tick, harder the farther out they are ({@code pull} x the excess, at most
 * {@code max_speed} blocks per tick). {@code cue} is a line drawn between the two every other tick.
 * Exits "out" when the time is up, or "broken" early: they're gone or dead, in another world, or (teleported
 * away) more than {@code range} blocks off. The cast ending lets go too.
 */
public final class LeashNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.BROKEN);
    private static final int DRAW_EVERY = 2;

    private final String targetKey;
    private final double length;
    private final int duration;
    private final double pull;
    private final double maxSpeed;
    private final double range;
    private final String cue;

    public LeashNode(String targetKey, double length, int duration, double pull, double maxSpeed, double range, String cue) {
        this.targetKey = targetKey;
        this.length = length;
        this.duration = duration;
        this.pull = pull;
        this.maxSpeed = maxSpeed;
        this.range = range;
        this.cue = cue;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        if (!(KeyQuery.read(ctx, targetKey).orElse(null) instanceof EntityTarget e) || e.id().equals(ctx.caster())) {
            return NodeResult.out(Ports.BROKEN);
        }
        new Leash(ctx, ctx.suspend(), e.id()).begin();
        return NodeResult.SUSPENDED;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    private final class Leash {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private final UUID held;
        private TaskHandle task;
        private int ticks;
        private boolean done;

        Leash(ExecutionContext ctx, Resumer resumer, UUID held) {
            this.ctx = ctx;
            this.resumer = resumer;
            this.held = held;
        }

        void begin() {
            ctx.instance().onEnd(() -> {
                done = true;
                if (task != null) task.cancel();
            });
            tick();
            if (!done) task = ctx.engine().scheduler().every(1, 1, this::tick);
        }

        private void tick() {
            if (done || !ctx.instance().isActive()) return;
            var world = ctx.engine().world();
            Optional<PointTarget> anchor = world.positionOf(new EntityTarget(ctx.caster()));
            Optional<PointTarget> them = world.isAlive(held) ? world.positionOf(new EntityTarget(held)) : Optional.empty();
            if (anchor.isEmpty() || them.isEmpty() || !anchor.get().world().equals(them.get().world())
                    || anchor.get().position().distance(them.get().position()) > range) {
                finish(Ports.BROKEN);
                return;
            }
            Vec3 toCaster = anchor.get().position().subtract(them.get().position());
            double distance = toCaster.length();
            if (distance > length) {
                double speed = Math.min(maxSpeed, (distance - length) * pull);
                ctx.engine().movement().setVelocity(held, toCaster.normalize().multiply(speed));
            }
            if (cue != null && ticks % DRAW_EVERY == 0) {
                ctx.engine().cuesFor(ctx.caster()).playLine(cue, anchor.get().world(), anchor.get().position(),
                        them.get().position());
            }
            if (++ticks >= duration) finish(Ports.OUT);
        }

        private void finish(String port) {
            if (done) return;
            done = true;
            if (task != null) task.cancel();
            resumer.resume(port);
        }
    }
}

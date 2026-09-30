package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Resumer;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Optional;
import java.util.Set;

/**
 * Hold a tether from the caster to {@code target} for {@code duration} ticks (a charge the target should
 * run from). Exits "complete" if it holds all the way, or "broken" as soon as:
 * <ul>
 *   <li>they're more than {@code range} blocks apart, or the target is gone</li>
 *   <li>a wall has blocked the line between them for more than {@code sight_grace} ticks in a row</li>
 *   <li>the caster has any of {@code break_on} (e.g. stunned or silenced)</li>
 * </ul>
 * The line is drawn every 2 ticks as {@code <cue>_<stage>}, stage 1..{@code stages} growing with the
 * charge (so the platform can make it tighten and brighten), and the charge fills the cast bar.
 */
public final class TetherNode implements GraphNode {

    public static final String COMPLETE = "complete";
    public static final String BROKEN = "broken";
    private static final Set<String> OUTPUTS = Set.of(COMPLETE, BROKEN);

    private final String targetKey;
    private final int duration;
    private final double range;
    private final int sightGrace;
    private final Set<String> breakOn;
    private final String cue;
    private final int stages;

    public TetherNode(String targetKey, int duration, double range, int sightGrace, Set<String> breakOn, String cue, int stages) {
        this.targetKey = targetKey;
        this.duration = Math.max(1, duration);
        this.range = range;
        this.sightGrace = Math.max(0, sightGrace);
        this.breakOn = Set.copyOf(breakOn);
        this.cue = cue;
        this.stages = Math.max(1, stages);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Optional<EntityTarget> target = KeyQuery.read(ctx, targetKey)
                .filter(t -> t instanceof EntityTarget).map(t -> (EntityTarget) t);
        if (target.isEmpty()) return NodeResult.out(BROKEN);
        new Hold(ctx, ctx.suspend(), target.get()).start();
        return NodeResult.SUSPENDED;
    }

    private final class Hold {
        private final ExecutionContext ctx;
        private final Resumer resumer;
        private final EntityTarget target;
        private final AbilityInstance instance;
        private AbilityInstance.CastProgress bar;
        private TaskHandle task;
        private int ticks;
        private int blindTicks; // in a row with a wall in between
        private boolean done;

        Hold(ExecutionContext ctx, Resumer resumer, EntityTarget target) {
            this.ctx = ctx;
            this.resumer = resumer;
            this.target = target;
            this.instance = ctx.instance();
        }

        void start() {
            bar = instance.showProgress(duration);
            instance.onEnd(() -> {
                done = true;
                if (task != null) task.cancel();
            });
            task = ctx.engine().scheduler().every(1, 1, this::tick);
            draw();
        }

        private void tick() {
            if (done || !instance.isActive()) return;
            var engine = ctx.engine();
            var from = engine.world().positionOf(new EntityTarget(ctx.caster()));
            var to = engine.world().positionOf(target);
            if (from.isEmpty() || to.isEmpty() || !engine.world().isAlive(target.id())
                    || !from.get().world().equals(to.get().world())
                    || from.get().position().distance(to.get().position()) > range) {
                finish(BROKEN);
                return;
            }
            if (engine.tags().firstMatch(ctx.caster(), breakOn) != null) {
                finish(BROKEN);
                return;
            }
            Optional<Aim> aim = engine.world().aimOf(ctx.caster());
            var eye = aim.map(Aim::eye).orElse(from.get().position());
            boolean wall = engine.world().sweep(from.get().world(), eye, to.get().position(), 0.05, id -> true).isPresent();
            blindTicks = wall ? blindTicks + 1 : 0;
            if (blindTicks > sightGrace) {
                finish(BROKEN);
                return;
            }
            if (++ticks >= duration) {
                finish(COMPLETE);
                return;
            }
            if (ticks % 2 == 0) draw();
        }

        private void draw() {
            var engine = ctx.engine();
            var from = engine.world().positionOf(new EntityTarget(ctx.caster()));
            var to = engine.world().positionOf(target);
            if (from.isEmpty() || to.isEmpty()) return;
            int stage = Math.min(stages, 1 + (int) ((double) ticks / duration * stages));
            engine.cues().playLine(cue + "_" + stage, from.get().world(), from.get().position(), to.get().position());
        }

        private void finish(String port) {
            if (done) return;
            done = true;
            if (task != null) task.cancel();
            instance.clearProgress(bar);
            resumer.resume(port);
        }
    }
}

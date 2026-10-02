package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.construct.ConstructSystem;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Optional;
import java.util.Set;

/**
 * Place a construct at a blackboard target (default "aim"), floating {@code height} above it, and
 * wait until it ends. Its centre is stored under {@code store} so later nodes can use it as the
 * blast centre. Exits fuse / struck / broken, or triggered for a trap (see ConstructSystem).
 * Trap options: {@code trigger: <radius>} (an enemy this close sets it off; stored as "hit"),
 * {@code arm: <ticks>} (not before this), {@code solid: false} (projectiles and punches pass through).
 * {@code limit: N}: at most N from this caster and ability at once; one more ends the oldest.
 * {@code triggered_by: enemies | allies | all | everyone}: who sets a trap off (never the caster, except with
 * everyone); {@code hidden: true}: only the caster and their allies see it. {@code cue: <id>}: played at it every
 * {@code cue_every} ticks (default 10) while it stands.
 */
public final class ConstructNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.FUSE, Ports.STRUCK, Ports.BROKEN, Ports.TRIGGERED);

    private final String atKey;
    private final String store;
    private final double height;
    private final double size;
    private final int fragileTicks;
    private final int fuseTicks;
    private final String visual;
    private final ConstructSystem.Options options;
    /** Each one's fuse is {@code fuseTicks} give or take up to this many ticks, at random (0 = all the same). */
    private int fuseSpread;

    /** @param fuseSpread each one's fuse is {@code fuseTicks} give or take up to this many ticks, at random */
    public ConstructNode withFuseSpread(int fuseSpread) {
        this.fuseSpread = Math.max(0, fuseSpread);
        return this;
    }

    public ConstructNode(String atKey, String store, double height, double size, int fragileTicks, int fuseTicks, String visual) {
        this(atKey, store, height, size, fragileTicks, fuseTicks, visual, ConstructSystem.Options.DEFAULT);
    }

    public ConstructNode(String atKey, String store, double height, double size, int fragileTicks, int fuseTicks, String visual,
                         ConstructSystem.Options options) {
        this.options = options;
        this.atKey = atKey;
        this.store = store;
        this.height = height;
        this.size = size;
        this.fragileTicks = fragileTicks;
        this.fuseTicks = fuseTicks;
        this.visual = visual;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        Optional<PointTarget> at = KeyQuery.read(ctx, atKey).flatMap(ctx.engine().world()::positionOf);
        if (at.isEmpty()) return NodeResult.out(Ports.BROKEN);

        Vec3 center = at.get().position().add(0, height, 0);
        ctx.blackboard().putRaw(store, new PointTarget(at.get().world(), center));
        int fuse = fuseSpread <= 0 ? fuseTicks
                : Math.max(1, fuseTicks + ctx.engine().random().nextInt(-fuseSpread, fuseSpread + 1)); // each its own
        ctx.engine().constructs().place(ctx.caster(), at.get().world(), center, size, fragileTicks, fuse,
                visual, ctx.suspend(), options);
        return NodeResult.SUSPENDED;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}

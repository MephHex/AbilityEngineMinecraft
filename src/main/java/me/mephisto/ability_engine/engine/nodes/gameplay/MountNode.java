package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.ride.RideManager;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Set;

/**
 * Ride the entity in {@code target} (a key): the caster sits on them (on Bukkit, on their head) and goes
 * wherever they go, until the ride ends (see RideManager). Exits:
 * <ul>
 *   <li>"out" at once: the ride is on (it lasts as long as the cast does, at most)</li>
 *   <li>"none": nobody there, or they can't be ridden</li>
 *   <li>"off", later, in a branch of its own: the ride ended by itself (the mount died or left, the game
 *       took the caster off, another cast's dismount). Ended by the cast instead (its own dismount, another
 *       mount, the cast ending): nothing more runs</li>
 * </ul>
 * {@code status}: on the mount while it's ridden (from the caster); {@code self_status}: on the caster
 * while riding. {@code store}: the mount is written there too (default "mount").
 */
public final class MountNode implements GraphNode {

    private static final Set<String> OUTPUTS = Set.of(Ports.OUT, Ports.NONE, Ports.OFF);

    private final String targetKey;
    private final String status;
    private final String selfStatus;
    private final String store;

    public MountNode(String targetKey, String status, String selfStatus, String store) {
        this.targetKey = targetKey;
        this.status = status;
        this.selfStatus = selfStatus;
        this.store = store;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        if (!(KeyQuery.read(ctx, targetKey).orElse(null) instanceof EntityTarget mount)
                || mount.id().equals(ctx.caster()) || !ctx.engine().world().isAlive(mount.id())) {
            return NodeResult.out(Ports.NONE);
        }
        ExecutionContext offBranch = ctx.fork(); // waits for the ride to end by itself
        var off = offBranch.suspend();
        boolean riding = ctx.engine().rides().mount(new RideManager.Ride(ctx.caster(), mount.id(), status, selfStatus,
                ctx.instance(), off));
        if (!riding) {
            off.abandon();
            return NodeResult.out(Ports.NONE);
        }
        if (store != null) ctx.blackboard().putRaw(store, mount);
        return NodeResult.NEXT;
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}

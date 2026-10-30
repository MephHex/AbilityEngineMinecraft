package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.ability.Ability;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.stats.UltimateCharge;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Steal a copy of an enemy's ultimate (Stolen Fable): their selected ultimate goes into the caster's ultimate slot for one
 * use within {@code window} ticks (see StolenUltimates); they keep their own, charge and all.
 * <ul>
 *   <li>{@code stolen}: done. The ability stolen is stored as {@code stolen} (its id).</li>
 *   <li>{@code unstealable}: their ultimate reads its owner's own kit ({@code stealable: false}), or they have none. The
 *       caster is told {@code message}.</li>
 *   <li>{@code none}: nobody valid in {@code target} (a key, default "target"): no enemy, or gone.</li>
 * </ul>
 * Either way it doesn't steal, the cast costs nothing: the caster's charge is given back (or, with ultimates on
 * cooldowns, this ability's cooldown). An ultimate with {@code steal_as: <ability>} is stolen as that one instead (a
 * version that works for anyone). Someone holding a stolen ultimate (another thief) has THAT one taken instead: a copy of
 * the stolen ultimate (they keep theirs), whatever their own is; a thief holding nothing is stolen from as usual (Stolen
 * Fable itself is {@code stealable: false}: nothing to take).
 */
public final class StealUltimateNode implements GraphNode {

    public static final String STOLEN = "stolen";
    public static final String UNSTEALABLE = "unstealable";
    public static final String NONE = "none";
    private static final Set<String> OUTPUTS = Set.of(STOLEN, UNSTEALABLE, NONE);

    private final String targetKey;
    private final int window;
    private final String message;

    public StealUltimateNode(String targetKey, int window, String message) {
        this.targetKey = targetKey;
        this.window = window;
        this.message = message;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        var engine = ctx.engine();
        UUID caster = ctx.caster();
        Optional<UUID> victim = KeyQuery.read(ctx, targetKey)
                .filter(t -> t instanceof EntityTarget).map(t -> ((EntityTarget) t).id())
                .filter(id -> !id.equals(caster) && engine.world().isAlive(id) && engine.teams().enemies(caster, id));
        if (victim.isEmpty()) {
            refund(ctx);
            return NodeResult.out(NONE);
        }
        // They hold a stolen one themselves (another thief): that's what's taken (a copy; they keep it)
        Optional<String> held = engine.stolen().heldBy(victim.get());
        if (held.isPresent() && engine.abilities().find(held.get()).isPresent()) {
            engine.stolen().lend(caster, held.get(), window);
            ctx.blackboard().putRaw(STOLEN, held.get());
            return NodeResult.out(STOLEN);
        }
        String theirs = selectedUltimate(ctx, victim.get());
        Optional<Ability> ult = theirs == null ? Optional.empty() : engine.abilities().find(theirs);
        if (ult.isEmpty() || !ult.get().stealable()) {
            refund(ctx);
            if (message != null) engine.notify(caster, message);
            return NodeResult.out(UNSTEALABLE);
        }
        String given = ult.get().stealAs() != null ? ult.get().stealAs() : ult.get().id();
        if (engine.abilities().find(given).isEmpty()) { // (the loader reports a missing steal_as)
            refund(ctx);
            return NodeResult.out(UNSTEALABLE);
        }
        engine.stolen().lend(caster, given, window);
        ctx.blackboard().putRaw(STOLEN, given);
        return NodeResult.out(STOLEN);
    }

    /** The ultimate they picked: their kit's own (not a copy they hold), or their form's when the kit has none. */
    private static String selectedUltimate(ExecutionContext ctx, UUID victim) {
        var loadouts = ctx.engine().loadouts();
        String own = loadouts.baseCharacterOf(victim).map(c -> c.abilityIn(Slots.ULTIMATE)).orElse(null);
        if (own != null) return own;
        return loadouts.kitOf(victim).map(c -> c.abilityIn(Slots.ULTIMATE)).orElse(null);
    }

    /** The cast didn't steal anything: it costs nothing. */
    private static void refund(ExecutionContext ctx) {
        var engine = ctx.engine();
        UUID caster = ctx.caster();
        String self = ctx.instance().ability().id();
        if (engine.ultCharge().gates(caster, self)) engine.ultCharge().set(caster, UltimateCharge.FULL);
        else engine.cooldowns().clear(caster, self);
    }

    @Override
    public Set<String> outputs() { return OUTPUTS; }
}

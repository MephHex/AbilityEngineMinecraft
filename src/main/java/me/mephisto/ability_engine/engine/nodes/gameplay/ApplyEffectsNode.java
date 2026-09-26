package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.effect.EffectConfig;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.status.ActiveStatus;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;
import java.util.Optional;
import me.mephisto.ability_engine.engine.target.Target;
import me.mephisto.ability_engine.engine.target.TargetQuery;

import java.util.List;

/** Apply a list of effects to every target a query returns (was EffectApplicationNode). */
public final class ApplyEffectsNode implements GraphNode {

    /** Who this node may affect. The caster themselves always passes (self-targeted abilities). */
    public enum Affects { ENEMIES, ALLIES, ALL }

    private final TargetQuery targets;
    private final List<EffectConfig> effects;
    private final Affects affects;
    /** null = inherit: on for casts from the primary slot, off otherwise. */
    private final Boolean onHit;

    public ApplyEffectsNode(TargetQuery targets, List<EffectConfig> effects) {
        this(targets, effects, Affects.ENEMIES, null);
    }

    public ApplyEffectsNode(TargetQuery targets, List<EffectConfig> effects, Affects affects, Boolean onHit) {
        this.targets = targets;
        this.effects = List.copyOf(effects);
        this.affects = affects;
        this.onHit = onHit;
    }

    /**
     * Direct hits (a key target: what a projectile/dash hit, what a melee click hit) are blocked by the
     * target's frontal barrier when they come from its front. Area effects aren't direct hits.
     */
    private boolean blockedFromFront(ExecutionContext ctx, Target target) {
        if (!(targets instanceof KeyQuery) || !(target instanceof EntityTarget e) || e.id().equals(ctx.caster())) return false;
        if (!ctx.engine().barriers().has(e.id())) return false;
        Optional<PointTarget> from = KeyQuery.read(ctx, Keys.HIT_FROM.name()).flatMap(ctx.engine().world()::positionOf)
                .or(() -> ctx.engine().world().positionOf(new EntityTarget(ctx.caster())));
        if (from.isEmpty() || !ctx.engine().barriers().blocksDirectHit(e.id(), from.get().position(), ctx.caster())) return false;
        ctx.engine().world().positionOf(e).ifPresent(p -> ctx.engine().barriers().blocked(p.world(), p.position()));
        return true;
    }

    private boolean appliesOnHit(ExecutionContext ctx) {
        if (onHit != null) return onHit;
        return Slots.PRIMARY.equals(ctx.blackboard().raw(Keys.SLOT.name()));
    }

    /** The caster's buffs: every on-hit effect of every status they have, applied to this target. */
    private static void applyOnHit(ExecutionContext ctx, Target target) {
        for (ActiveStatus buff : ctx.engine().statuses().on(ctx.caster())) {
            for (EffectConfig config : buff.def().onHit()) {
                ctx.engine().effects().require(config.effectId())
                        .apply(new EffectContext(ctx.engine(), ctx, ctx.caster(), target, config.params()));
            }
        }
    }

    private boolean allowed(ExecutionContext ctx, Target target) {
        if (!(target instanceof EntityTarget e) || e.id().equals(ctx.caster())) return true;
        boolean ally = ctx.engine().teams().allies(ctx.caster(), e.id());
        return switch (affects) {
            case ENEMIES -> !ally;
            case ALLIES -> ally;
            case ALL -> true;
        };
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        List<Target> found = targets.find(ctx).stream().filter(t -> allowed(ctx, t)).filter(t -> !blockedFromFront(ctx, t)).toList();
        ctx.engine().log().debug(() -> "apply_effects: " + found.size() + " target(s)");
        boolean onHitHere = appliesOnHit(ctx);
        for (Target target : found) {
            for (EffectConfig config : effects) {
                ctx.engine().effects().require(config.effectId())
                        .apply(new EffectContext(ctx.engine(), ctx, ctx.caster(), target, config.params()));
            }
            // On-hits only land on OTHER entities you hit, never on yourself.
            if (onHitHere && target instanceof EntityTarget e && !e.id().equals(ctx.caster())) applyOnHit(ctx, target);
        }
        return NodeResult.NEXT;
    }
}

package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.effect.EffectConfig;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.quiver.Bolt;
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
    /** Also apply the infusions of the bolt this cast fired (see take_bolt). */
    private final boolean infusions;

    public ApplyEffectsNode(TargetQuery targets, List<EffectConfig> effects) {
        this(targets, effects, Affects.ENEMIES, null);
    }

    public ApplyEffectsNode(TargetQuery targets, List<EffectConfig> effects, Affects affects, Boolean onHit) {
        this(targets, effects, affects, onHit, false);
    }

    public ApplyEffectsNode(TargetQuery targets, List<EffectConfig> effects, Affects affects, Boolean onHit, boolean infusions) {
        this(targets, effects, affects, onHit, infusions, null, null);
    }

    /** Where to store how many targets were affected (null = don't). */
    private String countKey;
    /** Where to store how many of them were players (null = don't). */
    private String countPlayersKey;
    /** {@code times: "stacks:<status>"}: as many times as the caster has stacks of that status. */
    static final String STACKS_OF = "stacks:";

    /** Apply the effects this many times per target: the number stored under this key (null = once). */
    private String timesKey;

    /**
     * @param countKey store how many targets it affected under this key (e.g. enemies hit by a landing)
     * @param timesKey apply the effects as many times as the number under this key (e.g. a shield per enemy hit)
     */
    public ApplyEffectsNode(TargetQuery targets, List<EffectConfig> effects, Affects affects, Boolean onHit, boolean infusions,
                            String countKey, String timesKey) {
        this(targets, effects, affects, onHit, infusions, countKey, timesKey, null);
    }

    /** @param countPlayersKey store how many of the targets it affected were players (e.g. "a hit that lands on a player") */
    public ApplyEffectsNode(TargetQuery targets, List<EffectConfig> effects, Affects affects, Boolean onHit, boolean infusions,
                            String countKey, String timesKey, String countPlayersKey) {
        this.countKey = countKey;
        this.countPlayersKey = countPlayersKey;
        this.timesKey = timesKey;
        this.targets = targets;
        this.effects = List.copyOf(effects);
        this.affects = affects;
        this.onHit = onHit;
        this.infusions = infusions;
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

    /** The caster's statuses' ability_on_hit effects, on an enemy one of their abilities hit. */
    private static void applyAbilityOnHit(ExecutionContext ctx, Target target) {
        for (ActiveStatus s : java.util.List.copyOf(ctx.engine().statuses().on(ctx.caster()))) {
            for (EffectConfig config : s.def().extras().abilityOnHit()) apply(ctx, config, target);
        }
    }

    /** The caster's buffs: every on-hit effect of every status they have, applied to this target. */
    private static void applyOnHit(ExecutionContext ctx, Target target) {
        java.util.List<String> usedUp = new java.util.ArrayList<>();
        boolean basic = ctx.engine().stats().isBasicAttack(ctx.caster(), ctx.instance().ability().id());
        for (ActiveStatus buff : java.util.List.copyOf(ctx.engine().statuses().on(ctx.caster()))) {
            for (EffectConfig config : buff.def().onHit()) apply(ctx, config, target);
            var basicOnHit = buff.def().extras().basicOnHit();
            if (basic) for (EffectConfig config : basicOnHit) apply(ctx, config, target); // basic attacks only
            if (buff.def().once() && (!buff.def().onHit().isEmpty() || (basic && !basicOnHit.isEmpty()))) usedUp.add(buff.def().id());
        }
        usedUp.forEach(id -> ctx.engine().statuses().remove(ctx.caster(), id)); // "your NEXT hit" buffs
    }

    /** The fired bolt's infusions: each one's on-hit effects, applied to this target. */
    private static void applyInfusions(ExecutionContext ctx, Target target) {
        Bolt bolt = ctx.get(Keys.BOLT);
        if (bolt == null) return;
        for (String id : bolt.infusions()) {
            // An infusion removed by /ae reload while the bolt was queued just does nothing.
            ctx.engine().infusions().find(id).ifPresent(infusion -> {
                for (EffectConfig config : infusion.onHit()) apply(ctx, config, target);
            });
        }
    }

    /**
     * One effect on one target. {@code self: true} puts it on the caster instead (e.g. an infusion or
     * on-hit that heals the shooter whenever it hits someone).
     */
    static void apply(ExecutionContext ctx, EffectConfig config, Target target) {
        Target to = config.params().getBool("self", false) ? new EntityTarget(ctx.caster()) : target;
        ctx.engine().effects().require(config.effectId())
                .apply(new EffectContext(ctx.engine(), ctx, ctx.caster(), to, config.params()));
    }

    private boolean allowed(ExecutionContext ctx, Target target) {
        if (!(target instanceof EntityTarget e) || e.id().equals(ctx.caster())) return true;
        if (ctx.engine().tags().has(e.id(), me.mephisto.ability_engine.engine.tag.Tags.UNTARGETABLE)) return false;
        if (ctx.engine().veils().blocks(ctx.caster(), e.id())) return false; // across a veil: out of reach
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
        // ability_on_hit: every enemy an ability (1, 2, 3, the ultimate: not fire) hits, unless the node says on_hit: false
        boolean abilityOnHitHere = !Boolean.FALSE.equals(onHit)
                && !ctx.engine().stats().isFire(ctx.caster(), ctx.instance().ability().id());
        if (countKey != null) ctx.blackboard().putRaw(countKey, found.size());
        if (countPlayersKey != null) {
            ctx.blackboard().putRaw(countPlayersKey, (int) found.stream()
                    .filter(t -> t instanceof EntityTarget e && ctx.engine().world().isPlayer(e.id())).count());
        }
        int times = timesKey == null ? 1
                : timesKey.startsWith(STACKS_OF) // the caster's stacks of a status (e.g. enemies hit so far)
                ? ctx.engine().statuses().find(ctx.caster(), timesKey.substring(STACKS_OF.length())).map(s -> s.stacks()).orElse(0)
                : ctx.blackboard().raw(timesKey) instanceof Number n ? n.intValue() : 0;
        for (Target target : found) {
            for (int i = 0; i < times; i++) {
                for (EffectConfig config : effects) apply(ctx, config, target);
            }
            // On-hits only land on OTHER entities you hit, never on yourself.
            boolean other = target instanceof EntityTarget e && !e.id().equals(ctx.caster());
            if (onHitHere && other) applyOnHit(ctx, target);
            if (abilityOnHitHere && other && ctx.engine().teams().enemies(ctx.caster(), ((EntityTarget) target).id())) {
                applyAbilityOnHit(ctx, target);
            }
            if (infusions && other) applyInfusions(ctx, target); // a bolt's magic is for whoever it hits
        }
        return NodeResult.NEXT;
    }
}

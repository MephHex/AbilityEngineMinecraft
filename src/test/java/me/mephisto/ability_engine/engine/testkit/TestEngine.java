package me.mephisto.ability_engine.engine.testkit;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Platform;
import me.mephisto.ability_engine.engine.status.StackPolicy;
import me.mephisto.ability_engine.engine.status.StatusDef;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Engine wired to fakes, with a "damage" effect that just records who got hit for how much. */
public final class TestEngine {

    public final FakeTime time = new FakeTime();
    public final FakeWorld world = new FakeWorld();
    public final FakeRenderer render = new FakeRenderer();
    public final AbilityEngine engine;
    public final List<Target> hits = new ArrayList<>();
    public final Map<UUID, Double> damageTaken = new HashMap<>();
    public final List<Vec3> teleports = new ArrayList<>();
    /** Horizontal knockback strength each entity received (last one wins). */
    public final Map<UUID, Double> knockback = new HashMap<>();
    /** The full knockback impulse each entity received (last one wins): direction matters for pulls. */
    public final Map<UUID, Vec3> knockbackVec = new HashMap<>();

    /** A place_blocks effect: its shape, where (a spot or an entity), and its params. */
    public record Placed(String shape, me.mephisto.ability_engine.engine.target.Target at, int duration) {}

    /** Every place_blocks (ice walls, tombs): the fake world has no blocks, so they're only recorded. */
    public final java.util.List<Placed> placed = new java.util.ArrayList<>();
    /** Total healing received per entity (design HP), and how much of it asked for overflow. */
    public final Map<UUID, Double> healed = new HashMap<>();
    /** Absorption shields received per entity (design HP, summed). */
    public final Map<UUID, Double> shields = new HashMap<>();
    /** Lifesteal requested by damage effects: caster -> summed lifesteal share of the damage. */
    public final Map<UUID, Double> lifesteal = new HashMap<>();

    public TestEngine() {
        Logger logger = Logger.getLogger("test");
        logger.setLevel(Level.WARNING);
        render.time = time;
        engine = new AbilityEngine(new Platform(time, time, world, world, render, render, render, render, world, logger));
        engine.effects().register("damage", new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                hits.add(ctx.target());
                if (ctx.target() instanceof EntityTarget e) {
                    // Same rules as the real damage effect: backstab crits, and report it (stealth breaks).
                    var parts = me.mephisto.ability_engine.engine.combat.DamageAmount.damage(ctx);
                    if (ctx.engine().spellShields().absorb(ctx, parts.total())) return; // a spell shield ate it
                    me.mephisto.ability_engine.engine.combat.HitReactions.onHit(ctx);
                    // Strength, damage taken, tethers, armor: the same pipeline as the real damage effect.
                    var result = me.mephisto.ability_engine.engine.combat.DamageModifiers.apply(ctx.engine(), ctx.caster(), e.id(),
                            parts.total(), parts.pierceShare(), true);
                    double amount = result.amount();
                    ctx.engine().notifyDamageDealt(ctx.caster(), e.id());
                    damageTaken.merge(e.id(), amount, Double::sum);
                    world.hurt(e.id(), amount); // vulnerable clones (souls) die at 0
                    for (var r : result.redirects()) damageTaken.merge(r.to(), r.amount(), Double::sum);
                    double ls = ctx.params().getDouble("lifesteal", 0)
                            + (e.id().equals(ctx.caster()) ? 0 : ctx.engine().stats().abilityLifesteal(ctx.caster()));
                    if (ls > 0) lifesteal.merge(ctx.caster(), amount * ls * ctx.engine().stats().healingMultiplier(ctx.caster()),
                            Double::sum);
                }
            }

            @Override
            public void validate(Params params) { me.mephisto.ability_engine.engine.combat.DamageAmount.validate(params, true); }
        });
        engine.effects().register("shield", new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (ctx.target() instanceof EntityTarget e) {
                    shields.merge(e.id(), me.mephisto.ability_engine.engine.combat.DamageAmount.heal(ctx), Double::sum);
                }
            }

            @Override
            public void validate(Params params) { me.mephisto.ability_engine.engine.combat.DamageAmount.validate(params, false); }
        });
        engine.effects().register("heal", new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (ctx.target() instanceof EntityTarget e) {
                    healed.merge(e.id(), me.mephisto.ability_engine.engine.combat.DamageAmount.heal(ctx)
                            * ctx.engine().stats().healingMultiplier(e.id()), Double::sum);
                }
            }

            @Override
            public void validate(Params params) { me.mephisto.ability_engine.engine.combat.DamageAmount.validate(params, false); }
        });
        engine.effects().register("knockback", new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (!(ctx.target() instanceof EntityTarget e) || ctx.execution() == null) return;
                if (ctx.engine().tags().has(e.id(), "block.knockback")) return; // same rules as the real effect
                double scale = ctx.engine().tags().has(e.id(), "state.sturdy") ? 0.5 : 1;
                var center = me.mephisto.ability_engine.engine.target.KeyQuery.read(ctx.execution(), ctx.params().requireString("from"))
                        .flatMap(world::positionOf);
                var pos = world.positionOf(e);
                if (center.isEmpty() || pos.isEmpty()) return;
                Vec3 v = me.mephisto.ability_engine.engine.effect.Knockback.impulse(center.get().position(), pos.get().position(),
                        ctx.params().requireDouble("radius"), ctx.params().getDouble("center", 1.2),
                        ctx.params().getDouble("edge", 0.3), ctx.params().getDouble("lift", 0.3));
                v = v.multiply(scale);
                knockback.put(e.id(), new Vec3(v.x(), 0, v.z()).length());
                knockbackVec.put(e.id(), v);
            }

            @Override
            public void validate(Params params) {
                params.requireString("from");
                params.requireDouble("radius");
            }
        });
        engine.effects().register("place_blocks", new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                placed.add(new Placed(ctx.params().getString("shape", "wall"), ctx.target(), ctx.params().requireInt("duration")));
            }

            @Override
            public void validate(Params params) { params.requireInt("duration"); }
        });
        engine.effects().register("teleport", new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (!(ctx.target() instanceof EntityTarget e) || ctx.execution() == null) return;
                me.mephisto.ability_engine.engine.effect.TeleportAway.destination(ctx, e)
                        .ifPresent(p -> {
                            teleports.add(p.position());
                            world.move(e.id(), p.position());
                        });
            }

            @Override
            public void validate(Params params) { me.mephisto.ability_engine.engine.effect.TeleportAway.validate(params); }
        });
        engine.statusDefs().define(new StatusDef("stun", 40, StackPolicy.REFRESH, 1,
                Set.of(Tags.STUNNED, Tags.BLOCK_MOVE, Tags.BLOCK_ABILITY)));
        engine.statusDefs().define(new StatusDef("root", 40, StackPolicy.REFRESH, 1,
                Set.of(Tags.ROOTED, Tags.BLOCK_MOVE)));
    }

    public UUID spawn(double x, double y, double z) { return world.spawn(new Vec3(x, y, z)); }

    public double damage(UUID id) { return damageTaken.getOrDefault(id, 0.0); }

    /** Load a single ability from a Map written like the YAML. Fails the test on any load error. */
    public void load(Map<String, Object> root) {
        LoadReport report = new AbilityLoader(engine).load(root, "test");
        if (!report.isClean()) throw new AssertionError("load errors: " + report.errors());
    }
}

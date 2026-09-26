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
    /** Total healing received per entity (design HP), and how much of it asked for overflow. */
    public final Map<UUID, Double> healed = new HashMap<>();
    /** Lifesteal requested by damage effects: caster -> summed lifesteal share of the damage. */
    public final Map<UUID, Double> lifesteal = new HashMap<>();

    public TestEngine() {
        Logger logger = Logger.getLogger("test");
        logger.setLevel(Level.WARNING);
        engine = new AbilityEngine(new Platform(time, time, world, world, render, render, render, render, logger));
        engine.effects().register("damage", new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                hits.add(ctx.target());
                if (ctx.target() instanceof EntityTarget e) {
                    double amount = ctx.params().requireDouble("amount");
                    damageTaken.merge(e.id(), amount, Double::sum);
                    double ls = ctx.params().getDouble("lifesteal", 0);
                    if (ls > 0) lifesteal.merge(ctx.caster(), amount * ls, Double::sum);
                }
            }

            @Override
            public void validate(Params params) { params.requireDouble("amount"); }
        });
        engine.effects().register("heal", new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (ctx.target() instanceof EntityTarget e) healed.merge(e.id(), ctx.params().requireDouble("amount"), Double::sum);
            }

            @Override
            public void validate(Params params) { params.requireDouble("amount"); }
        });
        engine.effects().register("knockback", new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (!(ctx.target() instanceof EntityTarget e) || ctx.execution() == null) return;
                if (ctx.engine().tags().has(e.id(), "block.knockback")) return; // same rule as the real effect
                var center = me.mephisto.ability_engine.engine.target.KeyQuery.read(ctx.execution(), ctx.params().requireString("from"))
                        .flatMap(world::positionOf);
                var pos = world.positionOf(e);
                if (center.isEmpty() || pos.isEmpty()) return;
                Vec3 v = me.mephisto.ability_engine.engine.effect.Knockback.impulse(center.get().position(), pos.get().position(),
                        ctx.params().requireDouble("radius"), ctx.params().getDouble("center", 1.2),
                        ctx.params().getDouble("edge", 0.3), ctx.params().getDouble("lift", 0.3));
                knockback.put(e.id(), new Vec3(v.x(), 0, v.z()).length());
                knockbackVec.put(e.id(), v);
            }

            @Override
            public void validate(Params params) {
                params.requireString("from");
                params.requireDouble("radius");
            }
        });
        engine.effects().register("teleport", new Effect() {
            @Override
            public void apply(EffectContext ctx) {
                if (!(ctx.target() instanceof EntityTarget e) || ctx.execution() == null) return;
                me.mephisto.ability_engine.engine.target.KeyQuery.read(ctx.execution(), ctx.params().requireString("to"))
                        .flatMap(world::positionOf)
                        .ifPresent(p -> {
                            teleports.add(p.position());
                            world.move(e.id(), p.position());
                        });
            }

            @Override
            public void validate(Params params) { params.requireString("to"); }
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

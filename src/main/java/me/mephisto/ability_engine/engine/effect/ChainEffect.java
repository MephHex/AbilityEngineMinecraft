package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.EffectConfig;
import me.mephisto.ability_engine.engine.platform.EntitySnapshot;
import me.mephisto.ability_engine.engine.status.ActiveStatus;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Effect id "chain": a chain lightning from whoever was hit to up to {@code max} (3) other enemies within
 * {@code radius} (5) blocks of them, nearest first. Each takes {@code base} (0.3) x the caster's base damage, and the
 * caster's on-hit effects (their statuses' on_hit and basic_on_hit, but no further chains). {@code cue}: a line cue drawn
 * from the one hit to each.
 */
public final class ChainEffect implements Effect {

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget hit)) return;
        AbilityEngine engine = ctx.engine();
        UUID caster = ctx.caster();
        var from = engine.world().positionOf(hit);
        if (from.isEmpty()) return;
        Params p = ctx.params();
        double radius = p.getDouble("radius", 5);
        int max = p.getInt("max", 3);
        String cue = p.getString("cue", null);
        List<EntitySnapshot> next = engine.world().livingEntitiesNear(from.get(), radius).stream()
                .filter(e -> !e.id().equals(hit.id()) && !e.id().equals(caster))
                .filter(e -> engine.teams().enemies(caster, e.id()) && !engine.tags().has(e.id(), Tags.UNTARGETABLE))
                .sorted(Comparator.comparingDouble(e -> e.center().distance(from.get().position())))
                .limit(max)
                .toList();
        EffectConfig damage = new EffectConfig("damage", Params.of(Map.of("base", p.getDouble("base", 0.3), "knockback", false)));
        for (EntitySnapshot e : next) {
            EntityTarget target = new EntityTarget(e.id());
            run(ctx, damage, target);
            for (ActiveStatus s : List.copyOf(engine.statuses().on(caster))) { // its on-hits: not another chain
                for (EffectConfig c : s.def().onHit()) if (!c.effectId().equals("chain")) run(ctx, c, target);
                for (EffectConfig c : s.def().extras().basicOnHit()) if (!c.effectId().equals("chain")) run(ctx, c, target);
            }
            if (cue != null) engine.cuesFor(caster).playLine(cue, from.get().world(), from.get().position(), e.center());
        }
    }

    private static void run(EffectContext ctx, EffectConfig config, EntityTarget target) {
        Params params = config.params();
        EntityTarget to = params.getBool("self", false) ? new EntityTarget(ctx.caster()) : target;
        ctx.engine().effects().require(config.effectId())
                .apply(new EffectContext(ctx.engine(), ctx.execution(), ctx.caster(), to, params));
    }

    @Override
    public void validate(Params params) {
        if (params.getDouble("radius", 5) <= 0) throw params.error("radius", "must be above 0");
        if (params.getInt("max", 3) < 1) throw params.error("max", "at least 1");
    }
}

package me.mephisto.ability_engine.bukkit.effect;

import me.mephisto.ability_engine.bukkit.platform.Convert;
import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.effect.Knockback;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.tag.Tags;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.Optional;

/**
 * Effect id "knockback". Params: {@code from} (required) = blackboard key of the centre,
 * {@code radius} (required), {@code center} = push strength at the centre (default 1.2),
 * {@code edge} = strength at the radius (default 0.3), {@code lift} = upward push (default 0.3),
 * {@code vertical: true} = away from the centre up and down too (a blast below throws them up), not only sideways.
 * The math lives in the engine (Knockback.impulse) so it's tested there.
 */
public final class KnockbackEffect implements Effect {

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target) || ctx.execution() == null) return; // needs its cast's keys
        Entity entity = Bukkit.getEntity(target.id());
        if (entity == null) return;
        if (ctx.engine().tags().has(target.id(), Tags.BLOCK_KNOCKBACK)) return; // e.g. guarding

        var world = ctx.engine().world();
        Optional<PointTarget> center = KeyQuery.read(ctx.execution(), ctx.params().requireString("from")).flatMap(world::positionOf);
        Optional<PointTarget> pos = world.positionOf(target);
        if (center.isEmpty() || pos.isEmpty()) return;

        Params p = ctx.params();
        Vec3 impulse = Knockback.impulse(center.get().position(), pos.get().position(), p.requireDouble("radius"),
                p.getDouble("center", 1.2), p.getDouble("edge", 0.3), p.getDouble("lift", 0.3), p.getBool("vertical", false));
        if (ctx.engine().tags().has(target.id(), Tags.STURDY)) impulse = impulse.multiply(0.5); // e.g. Bulwark
        entity.setVelocity(entity.getVelocity().add(Convert.bukkit(impulse)));
    }

    @Override
    public void validate(Params params) {
        params.requireString("from");
        params.requireDouble("radius");
    }
}

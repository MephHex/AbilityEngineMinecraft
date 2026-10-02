package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.PointTarget;

import java.util.Optional;

/**
 * Where the teleport effect sends someone: {@code to: <key>} (that spot), or {@code away_from: <key>, distance: N}
 * (N blocks straight out from that spot, level with where they are: e.g. everyone in an area flung out of it).
 * Platforms then find the nearest spot there where they fit. Nobody else can teleport someone unmovable
 * (block.displace).
 */
public final class TeleportAway {

    /** The destination for the effect's target, if it has one. */
    public static Optional<PointTarget> destination(EffectContext ctx, EntityTarget target) {
        if (ctx.execution() == null) return Optional.empty();
        if (!target.id().equals(ctx.caster())
                && ctx.engine().tags().has(target.id(), me.mephisto.ability_engine.engine.tag.Tags.BLOCK_DISPLACE)) {
            return Optional.empty(); // someone unmovable isn't teleported by others
        }
        String to = ctx.params().getString("to", null);
        if (to != null) return KeyQuery.read(ctx.execution(), to).flatMap(ctx.engine().world()::positionOf);
        var center = KeyQuery.read(ctx.execution(), ctx.params().requireString("away_from"))
                .flatMap(ctx.engine().world()::positionOf);
        var at = ctx.engine().world().positionOf(target);
        if (center.isEmpty() || at.isEmpty()) return Optional.empty();
        Vec3 out = new Vec3(at.get().position().x() - center.get().position().x(), 0,
                at.get().position().z() - center.get().position().z());
        if (out.lengthSquared() < 1e-6) { // right in the middle: out the way the caster looks
            out = ctx.engine().world().aimOf(ctx.caster()).map(a -> new Vec3(a.direction().x(), 0, a.direction().z()))
                    .filter(v -> v.lengthSquared() > 1e-6).orElse(new Vec3(1, 0, 0));
        }
        double distance = ctx.params().getDouble("distance", 6);
        return Optional.of(new PointTarget(at.get().world(), at.get().position().add(out.normalize().multiply(distance))));
    }

    /** {@code to}, or {@code away_from} with a {@code distance}. */
    public static void validate(me.mephisto.ability_engine.engine.data.Params params) {
        if (params.has("to") == params.has("away_from")) throw params.error("to", "give either to: <key> or away_from: <key>");
        if (params.has("away_from") && params.getDouble("distance", 6) <= 0) throw params.error("distance", "must be above 0 (blocks)");
    }

    private TeleportAway() {}
}

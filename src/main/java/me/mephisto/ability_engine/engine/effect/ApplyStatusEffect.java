package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.status.StatusRegistry;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * Effect id "status". Params: {@code status} (required), {@code duration} in ticks (optional,
 * defaults to the status definition), {@code from: world} (optional: it comes from nobody instead of the
 * caster, so it counts as a debuff even on the caster themselves - e.g. to test debuff immunity), {@code stacks: N}
 * (optional: put on that many times at once, e.g. a burst that then decays a stack at a time), {@code only_refresh: true}
 * (optional: only where it's already on them from the caster: its time starts over, it never lands fresh).
 * Replaces StunEffect: stun is now just data.
 */
public final class ApplyStatusEffect implements Effect {

    private final StatusRegistry statuses;

    public ApplyStatusEffect(StatusRegistry statuses) {
        this.statuses = statuses;
    }

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target)) return;
        String statusId = ctx.params().requireString("status");
        java.util.UUID source = "world".equals(ctx.params().getString("from", "caster")) ? null : ctx.caster();
        // A one-spell block (spell_block) also stops a spell's debuffs.
        boolean debuff = statuses.find(statusId).map(d -> !d.positive() && !d.links().neutral()).orElse(false);
        if (debuff && source != null && ctx.engine().spellShields().blocks(ctx)) return;
        // only_refresh: only starts its time over where it's already on them from this caster (it never lands fresh)
        if (ctx.params().getBool("only_refresh", false) && !ctx.engine().statuses().find(target.id(), statusId)
                .map(s -> java.util.Objects.equals(s.source(), source)).orElse(false)) return;
        int times = Math.max(1, ctx.params().getInt("stacks", 1));
        for (int i = 0; i < times; i++) {
            if (ctx.params().has("duration")) {
                ctx.engine().statuses().apply(target.id(), statusId, ctx.params().getInt("duration", 0), source);
            } else {
                ctx.engine().statuses().apply(target.id(), statusId, source);
            }
        }
    }

    @Override
    public void validate(Params params) {
        String id = params.requireString("status");
        if (statuses.find(id).isEmpty()) {
            throw params.error("status", "unknown status '" + id + "' (define it under 'statuses:')");
        }
        String from = params.getString("from", "caster");
        if (!from.equals("caster") && !from.equals("world")) throw params.error("from", "expected caster or world");
        if (params.has("stacks") && params.getInt("stacks", 1) < 1) throw params.error("stacks", "how many stacks to put on, at least 1");
    }
}

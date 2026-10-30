package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.Optional;

/**
 * Play a cosmetic cue at a blackboard target (default: the caster). Never affects gameplay.
 * <p>{@code numbered_by: <key>}: plays {@code <cue>_<n>}, n being the whole number stored there (e.g. a repeat's
 * repeat_index: each shot of a barrage one note higher), or the plain cue if there's no such one.
 * <p>{@code audience: caster}: only the caster sees and hears it (e.g. "that didn't work"); default everyone.
 * <p>{@code ahead: 5} (instead of {@code to}): a line cue from {@code at} to that many blocks ahead of it, level, the way
 * it faces: the caster's aim, a mirroring clone's mirrored aim, anyone else's own look (e.g. a cone slash drawn the way
 * each of them swings).
 */
public final class PlayCueNode implements GraphNode {

    private final String cueId;
    private final String atKey; // null = caster
    private final String toKey; // null = a one-point cue; set = drawn from "at" to "to" (a beam)
    private final String numberKey; // null = always cueId
    private boolean casterOnly;      // audience: caster
    private double ahead;            // > 0: a line from "at" this far the way it faces

    /** A line from {@code at} this many blocks ahead the way it faces (0 = off). */
    public PlayCueNode ahead(double blocks) {
        this.ahead = blocks;
        return this;
    }

    /** The level way {@code at} faces: see the class comment. */
    private java.util.Optional<me.mephisto.ability_engine.engine.math.Vec3> facing(ExecutionContext ctx, Optional<Target> at) {
        var world = ctx.engine().world();
        java.util.UUID who = at.filter(t -> t instanceof EntityTarget).map(t -> ((EntityTarget) t).id()).orElse(ctx.caster());
        boolean mirroring = !who.equals(ctx.caster()) && ctx.engine().mirrors().clonesOf(ctx.caster()).contains(who);
        var look = world.aimOf(mirroring ? ctx.caster() : who).map(a -> a.direction());
        if (mirroring) look = look.map(d -> ctx.engine().mirrors().reflect(who, d));
        return look.map(d -> new me.mephisto.ability_engine.engine.math.Vec3(d.x(), 0, d.z())).filter(d -> !d.isZero())
                .map(me.mephisto.ability_engine.engine.math.Vec3::normalize);
    }

    /** Only the caster sees and hears it. */
    public PlayCueNode casterOnly(boolean only) {
        this.casterOnly = only;
        return this;
    }

    public PlayCueNode(String cueId, String atKey) {
        this(cueId, atKey, null);
    }

    public PlayCueNode(String cueId, String atKey, String toKey) {
        this(cueId, atKey, toKey, null);
    }

    public PlayCueNode(String cueId, String atKey, String toKey, String numberKey) {
        this.cueId = cueId;
        this.atKey = atKey;
        this.toKey = toKey;
        this.numberKey = numberKey;
    }

    /** The cue to play: {@code <cue>_<n>} with numbered_by (when there is one), else the cue itself. */
    private String cueFor(ExecutionContext ctx) {
        if (numberKey == null) return cueId;
        Object raw = ctx.blackboard().raw(numberKey);
        if (!(raw instanceof Number n)) return cueId;
        String numbered = cueId + "_" + n.intValue();
        return ctx.engine().cues().has(numbered) ? numbered : cueId;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        String cueId = cueFor(ctx);
        Optional<Target> at = atKey == null ? Optional.of(new EntityTarget(ctx.caster())) : KeyQuery.read(ctx, atKey);
        var from = at.flatMap(ctx.engine().world()::positionOf);
        if (ahead > 0) { // a line the way it faces
            var dir = facing(ctx, at);
            if (from.isEmpty() || dir.isEmpty()) return NodeResult.NEXT;
            var end = from.get().position().add(dir.get().multiply(ahead));
            if (casterOnly) {
                ctx.engine().cues().playLine(ctx.engine().cueFor(ctx.caster(), cueId), from.get().world(), from.get().position(),
                        end, java.util.Set.of(ctx.caster()));
            } else {
                ctx.engine().cuesFor(ctx.caster()).playLine(cueId, from.get().world(), from.get().position(), end);
            }
            return NodeResult.NEXT;
        }
        if (casterOnly) { // (its variant too, if their character has one)
            var only = java.util.Set.of(ctx.caster());
            String id = ctx.engine().cueFor(ctx.caster(), cueId);
            var cues = ctx.engine().cues();
            if (toKey == null) {
                from.ifPresent(p -> cues.play(id, p.world(), p.position(), only));
            } else {
                var to = KeyQuery.read(ctx, toKey).flatMap(ctx.engine().world()::positionOf);
                if (from.isPresent() && to.isPresent()) {
                    cues.playLine(id, from.get().world(), from.get().position(), to.get().position(), only);
                }
            }
            return NodeResult.NEXT;
        }
        if (toKey == null) {
            from.ifPresent(p -> ctx.engine().cuesFor(ctx.caster()).play(cueId, p.world(), p.position()));
        } else {
            var to = KeyQuery.read(ctx, toKey).flatMap(ctx.engine().world()::positionOf);
            if (from.isPresent() && to.isPresent()) {
                ctx.engine().cuesFor(ctx.caster()).playLine(cueId, from.get().world(), from.get().position(), to.get().position());
            }
        }
        return NodeResult.NEXT;
    }
}

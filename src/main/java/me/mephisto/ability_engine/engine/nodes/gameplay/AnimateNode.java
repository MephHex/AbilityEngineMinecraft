package me.mephisto.ability_engine.engine.nodes.gameplay;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.graph.NodeResult;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.platform.Animator;
import me.mephisto.ability_engine.engine.platform.CueHandle;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.KeyQuery;

import java.util.UUID;

/**
 * Play (or stop) a model animation (Model Engine) on an entity ({@code at: <key>}, default: the caster). Cosmetic: no
 * model or no such animation and nothing happens, the cast goes on.
 * <p>
 * The name is given whole ({@code animation: warrior_ability2_swing}) or as a {@code part} of the cast's own name:
 * {@code <caster's character prefix>_<slot without underscores>_<part>} (a cast from ability_2 as the Warrior:
 * {@code part: swing} is warrior_ability2_swing). A cast without a slot (a hidden helper) uses its ability id instead
 * of the slot. {@code until_end}: it's stopped when the cast ends, however it ends.
 * <p>Speed: {@code scale_with: attack_speed} multiplies it by the caster's attack speed buffs and slows;
 * {@code fit: <ticks>} plays it in exactly that long ({@code fit: attack}: the time between the caster's basic attacks
 * right now, attack speed included). Time slows (time_scale) apply on top, by the animator.
 */
public final class AnimateNode implements GraphNode {

    private final String animation; // whole name, or null
    private final String part;      // or a part of the cast's name
    private final String atKey;     // null = caster
    private final Animator.Settings settings;
    private final boolean untilEnd;
    private final boolean stop;
    private boolean byAttackSpeed;
    private int fitTicks;      // 0 = none
    private boolean fitAttack; // fit: attack

    /** {@code scale_with: attack_speed}. */
    public AnimateNode byAttackSpeed() {
        byAttackSpeed = true;
        return this;
    }

    /** {@code fit: <ticks>}, or (attack) the time between basic attacks. */
    public AnimateNode fit(int ticks, boolean attack) {
        this.fitTicks = ticks;
        this.fitAttack = attack;
        return this;
    }

    public AnimateNode(String animation, String part, String atKey, Animator.Settings settings, boolean untilEnd,
                       boolean stop) {
        this.animation = animation;
        this.part = part;
        this.atKey = atKey;
        this.settings = settings;
        this.untilEnd = untilEnd;
        this.stop = stop;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        UUID entity = atKey == null ? ctx.caster()
                : KeyQuery.read(ctx, atKey).filter(t -> t instanceof EntityTarget)
                        .map(t -> ((EntityTarget) t).id()).orElse(null);
        String name = name(ctx);
        if (entity == null || name == null) return NodeResult.NEXT;
        Animator animator = ctx.engine().animator();
        if (stop) {
            animator.stop(entity, name);
        } else {
            CueHandle handle = animator.play(entity, name, settingsFor(ctx));
            if (untilEnd) ctx.instance().onEnd(handle::stop);
        }
        return NodeResult.NEXT;
    }

    private Animator.Settings settingsFor(ExecutionContext ctx) {
        double speed = settings.speed();
        if (byAttackSpeed) speed *= ctx.engine().stats().attackSpeedMultiplier(ctx.caster());
        int fit = fitAttack ? attackInterval(ctx) : fitTicks;
        if (speed == settings.speed() && fit == settings.fit()) return settings;
        return new Animator.Settings(speed, settings.lerpIn(), settings.lerpOut(), settings.force(), fit);
    }

    /** Ticks between the caster's basic attacks right now (their primary's cooldown, attack speed included); 0: none. */
    static int attackInterval(ExecutionContext ctx) {
        var engine = ctx.engine();
        java.util.UUID caster = ctx.caster();
        return engine.loadouts().abilityIn(caster, me.mephisto.ability_engine.engine.loadout.Slots.PRIMARY)
                .flatMap(id -> engine.abilities().find(id))
                .map(a -> Math.max(0, engine.stats().cooldownTicks(caster, a.id(), a.cooldownTicks())))
                .orElse(0);
    }

    /** The animation's whole name (null: a part, and the caster plays no character). */
    String name(ExecutionContext ctx) {
        if (animation != null) return animation;
        String prefix = ctx.engine().loadouts().characterOf(ctx.caster()).map(CharacterDef::animationPrefix).orElse(null);
        if (prefix == null) return null;
        String slot = ctx.blackboard().find(Keys.SLOT).orElse(ctx.instance().ability().id());
        return prefix + "_" + slot.replace("_", "") + "_" + part;
    }
}

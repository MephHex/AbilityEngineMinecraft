package me.mephisto.ability_engine.engine.combat;

import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.target.EntityTarget;

/**
 * Characters' passive reactions to being hit ({@code when_hit:} in a kit). Every damage effect (the
 * platform's, the tests') calls {@link #onHit} for a hit that lands.
 * <ul>
 *   <li>An enemy's BASIC attack (primary / secondary / melee, see SpellShields.isSpell) takes
 *       {@code reduce_cooldowns} ticks off the victim's cooldowns in {@code slots}.</li>
 * </ul>
 */
public final class HitReactions {

    public static void onHit(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target) || ctx.caster() == null) return;
        var engine = ctx.engine();
        if (ctx.caster().equals(target.id()) || engine.teams().allies(ctx.caster(), target.id())) return;
        if (SpellShields.isSpell(ctx)) return;
        CharacterDef.WhenHit whenHit = engine.loadouts().baseCharacterOf(target.id())
                .map(CharacterDef::whenHit).orElse(null);
        if (whenHit == null) return;
        for (String slot : whenHit.slots()) {
            engine.loadouts().abilityIn(target.id(), slot)
                    .ifPresent(ability -> engine.cooldowns().reduce(target.id(), ability, whenHit.reduceCooldownTicks()));
        }
    }

    private HitReactions() {}
}

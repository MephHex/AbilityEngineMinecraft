package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;

import java.util.Map;

/** "Apply effect X with these params" — one entry in an apply_effects node. */
public record EffectConfig(String effectId, Params params) {

    public static EffectConfig of(String effectId) { return new EffectConfig(effectId, Params.EMPTY); }

    public static EffectConfig of(String effectId, Map<String, ?> params) {
        return new EffectConfig(effectId, Params.of(params, effectId));
    }

    /**
     * As an on-hit (a status's on_hit / basic_on_hit / ability_on_hit, a bolt's infusion, a chain): it lands in the same
     * instant as the hit it rides on, so its damage ignores the target's invulnerability frames - otherwise the game
     * would swallow it (a smaller hit right after a bigger one does nothing). Other effects are unchanged.
     */
    public EffectConfig asOnHit() {
        if (!"damage".equals(effectId) || params.has("ignore_iframes")) return this;
        return new EffectConfig(effectId, params.with("ignore_iframes", true));
    }
}

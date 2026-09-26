package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.data.Params;

import java.util.Map;

/** "Apply effect X with these params" — one entry in an apply_effects node. */
public record EffectConfig(String effectId, Params params) {

    public static EffectConfig of(String effectId) { return new EffectConfig(effectId, Params.EMPTY); }

    public static EffectConfig of(String effectId, Map<String, ?> params) {
        return new EffectConfig(effectId, Params.of(params, effectId));
    }
}

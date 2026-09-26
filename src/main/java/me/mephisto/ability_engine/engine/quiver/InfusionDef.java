package me.mephisto.ability_engine.engine.quiver;

import me.mephisto.ability_engine.engine.effect.EffectConfig;

import java.util.List;

/**
 * Magic infused into a bolt, as data ({@code infusions:} in YAML). When the bolt hits, an
 * {@code apply_effects} node with {@code infusions: true} also applies {@code onHit} to whoever it hits.
 *
 * @param color       "#RRGGBB", how bolts carrying it are tinted on the HUD (mixed with other infusions)
 * @param description tooltip lines for bolts carrying it
 */
public record InfusionDef(String id, String name, String color, List<String> description, List<EffectConfig> onHit) {

    public InfusionDef {
        description = List.copyOf(description);
        onHit = List.copyOf(onHit);
    }
}

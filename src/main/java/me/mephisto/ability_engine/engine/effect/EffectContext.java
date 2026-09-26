package me.mephisto.ability_engine.engine.effect;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.target.Target;

import java.util.UUID;

/**
 * @param execution the cast this effect belongs to, or null when there is none (a status ticking,
 *                  e.g. burn damage). Effects that read blackboard keys must handle null.
 * @param caster    who is responsible (for status ticks: whoever applied the status)
 */
public record EffectContext(AbilityEngine engine, ExecutionContext execution, UUID caster, Target target, Params params) {}

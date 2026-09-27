package me.mephisto.ability_engine.engine.data;

import me.mephisto.ability_engine.engine.ability.activation.ActivationMode;
import me.mephisto.ability_engine.engine.ability.activation.ChannelActivation;
import me.mephisto.ability_engine.engine.ability.activation.HoldActivation;
import me.mephisto.ability_engine.engine.ability.activation.InstantActivation;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectConfig;
import me.mephisto.ability_engine.engine.effect.EffectRegistry;
import me.mephisto.ability_engine.engine.nodes.gameplay.ApplyEffectsNode;
import me.mephisto.ability_engine.engine.projectile.Accelerate;
import me.mephisto.ability_engine.engine.projectile.Drag;
import me.mephisto.ability_engine.engine.projectile.Gravity;
import me.mephisto.ability_engine.engine.projectile.Homing;
import me.mephisto.ability_engine.engine.projectile.MotionModifier;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;
import me.mephisto.ability_engine.engine.quiver.InfusionDef;
import me.mephisto.ability_engine.engine.quiver.QuiverDef;
import me.mephisto.ability_engine.engine.status.StackPolicy;
import me.mephisto.ability_engine.engine.status.StatusDef;
import me.mephisto.ability_engine.engine.target.ConeQuery;
import me.mephisto.ability_engine.engine.target.CursorQuery;
import me.mephisto.ability_engine.engine.target.HitscanQuery;
import me.mephisto.ability_engine.engine.target.KeyQuery;
import me.mephisto.ability_engine.engine.target.LineQuery;
import me.mephisto.ability_engine.engine.target.PathQuery;
import me.mephisto.ability_engine.engine.target.RadiusQuery;
import me.mephisto.ability_engine.engine.target.SelfQuery;
import me.mephisto.ability_engine.engine.target.TargetQuery;
import me.mephisto.ability_engine.engine.targeting.Targeting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** YAML section -> engine object. All reads go through Params so errors carry their path. */
public final class Parsers {

    public static TargetQuery query(Params p) {
        String type = p.requireString("type");
        return switch (type) {
            case "self" -> SelfQuery.INSTANCE;
            case "key" -> new KeyQuery(p.requireString("key"));
            case "hitscan" -> new HitscanQuery(p.requireDouble("range"), p.getDouble("ray_size", 0.2), p.getBool("blocks", false),
                    hitscanAllies(p));
            case "radius" -> new RadiusQuery(p.getString("center", null), p.requireDouble("radius"),
                    p.getInt("max", 0), p.getBool("include_caster", false));
            case "cone" -> new ConeQuery(p.requireDouble("range"), p.requireDouble("angle"), p.getInt("max", 0));
            case "cursor" -> new CursorQuery(p.requireDouble("range"));
            case "line" -> new LineQuery(p.requireDouble("range"), p.getDouble("width", 1.0));
            case "path" -> new PathQuery(p.requireString("from"), p.requireString("to"), p.getDouble("width", 1.0));
            default -> throw p.error("type", "unknown query type '" + type + "' (self, key, hitscan, radius, cone, cursor, line, path)");
        };
    }

    /** {@code targets: enemies} (default) or {@code allies} for a hitscan. */
    private static boolean hitscanAllies(Params p) {
        String t = p.getString("targets", "enemies");
        if (!t.equals("enemies") && !t.equals("allies")) throw p.error("targets", "expected enemies or allies");
        return t.equals("allies");
    }

    /** Each list entry is {@code {id: <effect>, ...params}}. Validated against the effect now, not mid-fight. */
    public static List<EffectConfig> effects(Params node, String key, EffectRegistry registry) {
        List<EffectConfig> out = new ArrayList<>();
        for (Params entry : node.getParamsList(key)) {
            String id = entry.requireString("id");
            if (!registry.has(id)) throw entry.error("id", "unknown effect '" + id + "', known: " + registry.ids());
            Params params = entry.without("id");
            Effect effect = registry.require(id);
            effect.validate(params);
            out.add(new EffectConfig(id, params));
        }
        if (out.isEmpty()) throw node.error(key, "needs at least one effect");
        return out;
    }

    /** Like effects(), but an absent/empty list is fine. */
    public static List<EffectConfig> optionalEffects(Params node, String key, EffectRegistry registry) {
        return node.has(key) ? effects(node, key, registry) : List.of();
    }

    public static ProjectileSpec projectile(Params p) {
        if (p.has("bounce_damping")) {
            throw p.error("bounce_damping", "was replaced by restitution (default 0.35) and friction (default 0.4)");
        }
        return ProjectileSpec.builder()
                .speed(p.getDouble("speed", 1.4))
                .size(p.getDouble("size", 0.5))
                .lifetimeTicks(p.getInt("lifetime", 200))
                .maxBounces(p.getInt("bounces", 0))
                .restitution(p.getDouble("restitution", 0.35))
                .friction(p.getDouble("friction", 0.4))
                .minBounceSpeed(p.getDouble("min_bounce_speed", 0.05))
                .motion(motion(p))
                .visual(p.getString("visual", "DIAMOND_BLOCK"))
                .count(p.getInt("count", 1))
                .spreadDegrees(p.getDouble("spread", 0))
                .range(p.getDouble("range", 0))
                .pierce(p.getInt("pierce", 0))
                .slide(slide(p))
                .build();
    }

    private static double slide(Params p) {
        double slide = p.getDouble("slide", 0);
        if (slide < 0 || slide >= 1) throw p.error("slide", "expected the share of speed kept per tick, 0 to 0.99");
        return slide;
    }

    /** The "motion:" list of a section. */
    public static List<MotionModifier> motion(Params p) {
        List<MotionModifier> out = new ArrayList<>();
        for (Params m : p.getParamsList("motion")) {
            String type = m.requireString("type");
            out.add(switch (type) {
                case "gravity" -> new Gravity(m.requireDouble("amount"));
                case "drag" -> new Drag(m.requireDouble("amount"));
                case "homing" -> new Homing(m.requireString("key"), m.getDouble("turn", 0.15));
                case "accelerate" -> new Accelerate(m.requireDouble("amount"), m.requireDouble("max"));
                default -> throw m.error("type", "unknown motion '" + type + "' (gravity, drag, homing, accelerate)");
            });
        }
        return out;
    }

    /** {@code mode: instant} or {@code mode: {type: channel, period: 4, duration: 60, ammo: ammo}}. */
    public static ActivationMode mode(Params ability) {
        Object raw = ability.raw("mode");
        if (raw == null || "instant".equals(raw)) return InstantActivation.INSTANCE;
        if (!(raw instanceof Map<?, ?>)) throw ability.error("mode", "expected 'instant' or a section");
        Params m = ability.getParams("mode");
        String type = m.requireString("type");
        return switch (type) {
            case "instant" -> InstantActivation.INSTANCE;
            case "channel" -> new ChannelActivation(m.requireInt("period"), m.requireInt("duration"),
                    m.getString("ammo", null), m.getInt("ammo_per_pulse", 1));
            case "hold" -> new HoldActivation(m.getInt("period", 1), m.getInt("release", 6),
                    m.getString("ammo", null), m.getDouble("ammo_per_pulse", 1), m.getString("while_projectile", null));
            default -> throw m.error("type", "unknown mode '" + type + "' (instant, channel, hold)");
        };
    }

    /** Dash {@code direction: aim} (default) or {@code movement}. True = along the caster's movement. */
    public static boolean dashDirection(Params p) {
        String d = p.getString("direction", "aim");
        if (!d.equals("aim") && !d.equals("movement")) throw p.error("direction", "expected aim or movement");
        return d.equals("movement");
    }

    /** One entry of {@code infusions:}. {@code on_hit} is required: an infusion that does nothing is a typo. */
    public static InfusionDef infusion(String id, Params p, EffectRegistry registry) {
        String color = p.getString("color", "#FFFFFF");
        if (!color.matches("#[0-9a-fA-F]{6}")) throw p.error("color", "expected #RRGGBB, got '" + color + "'");
        return new InfusionDef(id, p.getString("name", id), color, p.getStringList("description"),
                effects(p, "on_hit", registry));
    }

    /** Maximum reload speed level: Quick Charge 5 and up breaks the vanilla crossbow. */
    public static final int MAX_RELOAD_SPEED = 4;

    /**
     * A character's {@code quiver:} section. {@code hotbar} is where the queue starts (1-9); the queue
     * must fit in the hotbar. {@code statuses} are the known status ids (for reload_speed.stacks_of).
     */
    public static QuiverDef quiver(Params p, Set<String> statuses) {
        int size = p.getInt("size", 3);
        if (size < 1 || size > 9) throw p.error("size", "expected 1-9 bolts");
        int hotbar = p.getInt("hotbar", 0);
        if (hotbar < 0 || hotbar > 9) throw p.error("hotbar", "expected a hotbar slot 1-9 (or 0: not shown)");
        if (hotbar > 0 && hotbar + size - 1 > 9) {
            throw p.error("hotbar", size + " bolts from slot " + hotbar + " don't fit in the hotbar (slots 1-9)");
        }
        QuiverDef.ReloadSpeed speed = null;
        if (p.has("reload_speed")) {
            Params r = p.getParams("reload_speed");
            String stacksOf = r.getString("stacks_of", null);
            if (stacksOf != null && !statuses.contains(stacksOf)) {
                throw r.error("stacks_of", "unknown status '" + stacksOf + "' (define it under 'statuses:')");
            }
            int first = r.getInt("first", 1);
            if (first < 1 || first > MAX_RELOAD_SPEED) throw r.error("first", "expected 1-" + MAX_RELOAD_SPEED);
            int max = r.getInt("max", 3);
            if (max < 0 || max > MAX_RELOAD_SPEED) throw r.error("max", "expected 0-" + MAX_RELOAD_SPEED);
            Map<String, Integer> whileTags = new java.util.LinkedHashMap<>();
            Params w = r.getParams("while");
            for (String tag : w.keys()) {
                int level = w.requireInt(tag);
                if (level < 0 || level > MAX_RELOAD_SPEED) throw w.error(tag, "expected 0-" + MAX_RELOAD_SPEED);
                whileTags.put(tag, level);
            }
            speed = new QuiverDef.ReloadSpeed(stacksOf, first, max, whileTags);
        }
        return new QuiverDef(size, hotbar, speed, p.getStringSet("rapid_fire_while", java.util.Set.of()));
    }

    /** Default for every ground-targeted ability: at most this far below your feet, else the cliff edge. */
    public static final double DEFAULT_MAX_DROP = 6.0;

    /** {@code affects: enemies} (default), {@code allies} or {@code all}. The caster always passes. */
    public static ApplyEffectsNode.Affects affects(Params p) {
        String v = p.getString("affects", "enemies").toUpperCase(Locale.ROOT);
        try {
            return ApplyEffectsNode.Affects.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw p.error("affects", "expected enemies, allies or all");
        }
    }

    public static Targeting targeting(Params p) {
        Targeting.Shape shape;
        try {
            shape = Targeting.Shape.valueOf(p.getString("shape", "circle").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw p.error("shape", "expected circle, line, cone or point");
        }
        return new Targeting(shape, p.requireDouble("range"), p.getDouble("radius", 1.0),
                p.getDouble("width", 1.0), p.getDouble("angle", 60), p.getInt("timeout", 0),
                p.getBool("ground", false), p.getDouble("max_drop", DEFAULT_MAX_DROP));
    }

    /** A status with tags only; its effects (on_hit, tick) are read by statusEffects() in a second pass. */
    public static StatusDef status(String id, Params p) {
        String stacking = p.getString("stacking", "refresh").toUpperCase(Locale.ROOT);
        StackPolicy policy;
        try {
            policy = StackPolicy.valueOf(stacking);
        } catch (IllegalArgumentException e) {
            throw p.error("stacking", "expected refresh, extend or stack");
        }
        return new StatusDef(id, p.getInt("duration", 0), policy, p.getInt("max_stacks", 1),
                p.getStringSet("tags", Set.of()));
    }

    /**
     * Add a status's effects: {@code on_hit: [...]} (a buff) and {@code tick: {every, effects}} (over time).
     * Separate pass so statuses can refer to each other in any order (a buff that applies burn).
     */
    public static StatusDef statusEffects(StatusDef base, Params p, EffectRegistry registry) {
        List<EffectConfig> onHit = optionalEffects(p, "on_hit", registry);
        Params tick = p.getParams("tick");
        int every = tick.getInt("every", 0);
        List<EffectConfig> tickEffects = optionalEffects(tick, "effects", registry);
        if (every > 0 && tickEffects.isEmpty()) throw tick.error("effects", "tick needs effects");
        if (every <= 0 && !tickEffects.isEmpty()) throw tick.error("every", "tick needs every: <ticks>");
        double dealt = p.getDouble("damage_dealt", 1);
        double taken = p.getDouble("damage_taken", 1);
        if (dealt < 0) throw p.error("damage_dealt", "must be >= 0 (1.2 = 20% more damage)");
        if (taken < 0) throw p.error("damage_taken", "must be >= 0 (0.8 = 20% less damage taken)");
        return new StatusDef(base.id(), base.defaultDurationTicks(), base.stacking(), base.maxStacks(),
                base.grantedTags(), onHit, every, tickEffects,
                p.getBool("break_on_damage", false), p.getBool("once", false),
                p.getBool("positive", false), dealt, taken);
    }

    private Parsers() {}
}

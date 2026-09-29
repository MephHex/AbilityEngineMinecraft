package me.mephisto.ability_engine.engine.data;

import me.mephisto.ability_engine.engine.construct.ConstructSystem;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.graph.Ports;
import me.mephisto.ability_engine.engine.nodes.control.AwaitRecastNode;
import me.mephisto.ability_engine.engine.nodes.control.CounterNode;
import me.mephisto.ability_engine.engine.nodes.control.ReleaseTagsNode;
import me.mephisto.ability_engine.engine.nodes.control.DelayNode;
import me.mephisto.ability_engine.engine.nodes.control.HasTagNode;
import me.mephisto.ability_engine.engine.nodes.control.InRangeNode;
import me.mephisto.ability_engine.engine.nodes.control.SetNode;
import me.mephisto.ability_engine.engine.nodes.control.SwitchNode;
import me.mephisto.ability_engine.engine.nodes.debug.PrintNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.AcquireTargetNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.ApplyEffectsNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.BarrierNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.ConstructNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.DashNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.InfuseNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.PlayCueNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.ProjectileNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.RedirectProjectileNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.ReduceCooldownNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.ReloadNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.StartCueNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.SteerProjectileNode;
import me.mephisto.ability_engine.engine.nodes.gameplay.TakeBoltNode;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** The "type:" values allowed in ability YAML. Add your own with {@link #register}. */
public final class NodeTypes {

    private final Map<String, NodeFactory> factories = new HashMap<>();

    public static NodeTypes withBuiltins() {
        NodeTypes t = new NodeTypes();
        t.register("print", (p, e) -> new PrintNode(p.requireString("message")));
        t.register("delay", (p, e) -> new DelayNode(p.requireInt("ticks"), p.getBool("cast_bar", false),
                p.getBool("boss_bar", false)));
        t.register("switch", (p, e) -> {
            Set<String> cases = new LinkedHashSet<>(p.getParams("on").keys());
            cases.remove(Ports.DEFAULT);
            return new SwitchNode(p.requireString("key"), cases);
        });
        t.register("acquire_target", (p, e) -> new AcquireTargetNode(
                Parsers.query(p.requireParams("query")),
                Keys.target(p.getString("store", Keys.TARGET.name()))));
        t.register("apply_effects", (p, e) -> new ApplyEffectsNode(
                Parsers.query(p.requireParams("targets")),
                Parsers.effects(p, "effects", e.effects()),
                Parsers.affects(p),
                p.has("on_hit") ? p.getBool("on_hit", false) : null,
                p.getBool("infusions", false),
                p.getString("count", null),
                p.getString("times", null)));
        t.register("projectile", (p, e) -> new ProjectileNode(Parsers.projectile(p), p.getString("store", null)));
        t.register("barrier", (p, e) -> new BarrierNode(p.getDouble("distance", 1.0), p.getDouble("radius", 1.3),
                p.getBool("projectiles_only", false)));
        t.register("start_cue", (p, e) -> new StartCueNode(p.requireString("cue"), p.getString("at", null)));
        t.register("dash", (p, e) -> new DashNode(
                p.getDouble("speed", 1.2), p.requireDouble("range"), p.getDouble("radius", 0.6), p.getBool("flat", false),
                p.getBool("pierce", false), p.getString("store", null), Parsers.dashDirection(p),
                p.getString("mover", null), towardCursor(p), p.getString("to", null)));
        // ---- Vanguard: leaps, tethers, mid-cast aiming, manual cooldowns ----
        t.register("leap", (p, e) -> {
            me.mephisto.ability_engine.engine.nodes.gameplay.LeapNode.Direction dir;
            try {
                dir = me.mephisto.ability_engine.engine.nodes.gameplay.LeapNode.Direction.valueOf(
                        p.getString("direction", "aim").toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw p.error("direction", "expected aim, movement or up");
            }
            String until = p.getString("until", "land");
            if (!until.equals("land") && !until.equals("apex")) throw p.error("until", "expected land or apex");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.LeapNode(dir, p.getDouble("speed", 0.6),
                    p.requireDouble("up"), p.getDouble("gravity", 0.08), until.equals("apex"), p.getString("store", null));
        });
        t.register("choose_spot", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.ChooseSpotNode(
                new me.mephisto.ability_engine.engine.targeting.Targeting(
                        me.mephisto.ability_engine.engine.targeting.Targeting.Shape.CIRCLE, p.requireDouble("range"),
                        p.getDouble("radius", 1.0), 1.0, 60, p.getInt("timeout", 100), p.getBool("ground", true),
                        p.getDouble("max_drop", 40)),
                p.getString("store", "aim")));
        t.register("link", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.LinkNode(
                p.requireString("name"), p.requireString("target"), p.requireDouble("range"),
                p.getDouble("damage_taken", 1.0), p.getDouble("redirect", 0), p.getBool("copy_positive", false),
                p.getString("cue", null), p.getDouble("mirror", 0), p.getString("from", null)));
        t.register("find_link", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.FindLinkNode(
                p.requireString("name"), p.getString("store", "linked")));
        t.register("unlink", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.UnlinkNode(p.requireString("name")));
        t.register("start_cooldown", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.StartCooldownNode());
        t.register("end_cast", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.EndCastNode());
        t.register("fork", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.ForkNode());
        t.register("summon_clone", (p, e) -> {
            double health = p.getDouble("health", 0);
            if (health < 0) throw p.error("health", "must be >= 0 (0 = can't be hurt)");
            double share = p.getDouble("health_share", 0);
            if (share < 0) throw p.error("health_share", "must be >= 0");
            if (health > 0 && share > 0) throw p.error("health", "give health or health_share, not both");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.SummonCloneNode(
                    p.requireString("summon"), p.getString("store", "summon"), p.requireInt("lifetime"),
                    p.getString("at", null), p.getString("of", null), health, share, p.getBool("glowing", false));
        });
        t.register("move_to", (p, e) -> {
            var look = switch (p.getString("look", "none")) {
                case "none" -> me.mephisto.ability_engine.engine.nodes.gameplay.MoveToNode.Look.NONE;
                case "down" -> me.mephisto.ability_engine.engine.nodes.gameplay.MoveToNode.Look.DOWN;
                case "spot" -> me.mephisto.ability_engine.engine.nodes.gameplay.MoveToNode.Look.SPOT;
                default -> throw p.error("look", "expected down or spot (or leave it out)");
            };
            return new me.mephisto.ability_engine.engine.nodes.gameplay.MoveToNode(p.getString("to", "caster"),
                    p.getDouble("up", 0), p.getDouble("back", 0), look, p.getString("store", null),
                    p.getBool("return", false));
        });
        t.register("await_summon", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.AwaitSummonNode(
                p.requireString("summon")));
        t.register("dismiss_summon", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.DismissSummonNode(
                p.requireString("summon")));
        t.register("find_summon", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.FindSummonNode(
                p.requireString("summon"), p.getString("store", "summon")));
        t.register("swap", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.SwapNode(p.requireString("with")));
        t.register("counter", (p, e) -> new CounterNode(p.requireString("counter"), p.requireInt("every"),
                p.getBool("peek", false)));
        t.register("charge", (p, e) -> {
            double from = p.getDouble("from", 0);
            if (from < 0 || from > 1) throw p.error("from", "must be between 0 and 1");
            int min = p.getInt("min", 0);
            if (min < 0 || min > p.requireInt("ticks")) throw p.error("min", "must be between 0 and ticks");
            return new me.mephisto.ability_engine.engine.nodes.control.ChargeNode(p.requireInt("ticks"), from,
                    p.getString("store", "charge"), min, p.getBool("fire_when_full", true));
        });
        t.register("has_status", (p, e) -> {
            String status = p.requireString("status");
            if (e.statusDefs().find(status).isEmpty()) throw p.error("status", "unknown status '" + status + "'");
            return new me.mephisto.ability_engine.engine.nodes.control.HasStatusNode(status, p.getString("target", null),
                    p.getInt("min_stacks", 1), p.getBool("mine", false));
        });
        t.register("moving_toward", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.MovingTowardNode(
                p.requireString("target"), p.getDouble("angle", 45)));
        t.register("random", (p, e) -> {
            var ports = new java.util.ArrayList<>(p.getParams("on").keys());
            if (ports.size() < 2) throw p.error("on", "give at least two ports to pick from");
            return new me.mephisto.ability_engine.engine.nodes.control.RandomNode(ports);
        });
        t.register("ward_reset", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.WardResetNode());
        t.register("spell_shield", (p, e) -> {
            double max = p.requireDouble("max");
            if (max <= 0) throw p.error("max", "must be above 0");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.SpellShieldNode(max);
        });
        t.register("shield_charge", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.ShieldChargeNode(
                p.getString("store", "charge")));
        t.register("await_kill", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.AwaitKillNode(
                p.getBool("players_only", false), p.getString("store", "victim")));
        t.register("cancel_ability", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.CancelAbilityNode(
                p.requireString("ability")));
        t.register("release_tags", (p, e) -> new ReleaseTagsNode());
        t.register("steer_projectile", (p, e) -> new SteerProjectileNode(
                p.requireString("ability"), p.getDouble("turn", 0.25), p.getDouble("range", 60)));
        t.register("set", (p, e) -> new SetNode(p.requireString("key"), p.requireString("value")));
        t.register("construct", (p, e) -> new ConstructNode(
                p.getString("at", "aim"),
                p.getString("store", "construct"),
                p.getDouble("height", 1.0),
                p.getDouble("size", 0.8),
                p.getInt("fragile", 0),
                p.requireInt("fuse"),
                p.getString("visual", "AMETHYST_CLUSTER"),
                new ConstructSystem.Options(p.getBool("solid", true), p.getDouble("trigger", 0), p.getInt("arm", 0),
                        p.getInt("limit", 0))));
        t.register("await_recast", (p, e) -> new AwaitRecastNode(p.requireInt("window"), p.getString("while", null)));
        t.register("redirect_projectile", (p, e) -> new RedirectProjectileNode(
                p.requireString("projectile"),
                p.getString("toward", "cursor"),
                p.getDouble("range", 60),
                p.has("speed") ? p.getDouble("speed", 0) : null,
                p.has("motion") ? Parsers.motion(p) : null));
        // ---- quiver: a queue of bolts, loaded one at a time, that abilities infuse with magic ----
        t.register("reload", (p, e) -> new ReloadNode());
        t.register("take_bolt", (p, e) -> new TakeBoltNode());
        t.register("infuse", (p, e) -> {
            if (p.has("infusion") == p.has("random")) throw p.error("infusion", "give either infusion: <id> or random: [ids]");
            String key = p.has("random") ? "random" : "infusion";
            java.util.List<String> choices = p.has("random") ? p.getStringList("random") : java.util.List.of(p.requireString("infusion"));
            if (choices.isEmpty()) throw p.error("random", "list at least one infusion");
            for (String infusion : choices) {
                if (e.infusions().find(infusion).isEmpty()) {
                    throw p.error(key, "unknown infusion '" + infusion + "' (define it under 'infusions:')");
                }
            }
            int count = p.getInt("count", 1);
            if (count < 1) throw p.error("count", "must be at least 1");
            return new InfuseNode(choices, count, p.getBool("reroll", false));
        });
        t.register("has_tag", (p, e) -> new HasTagNode(p.requireString("tag"), p.getString("target", null)));
        t.register("in_range", (p, e) -> new InRangeNode(p.requireString("center"), p.getString("target", null),
                p.requireDouble("radius")));
        t.register("reduce_cooldown", (p, e) -> {
            if (p.has("ability") == p.has("slot")) throw p.error("ability", "give either ability: <id> or slot: <slot>");
            String slot = p.getString("slot", null);
            if (slot != null && !me.mephisto.ability_engine.engine.loadout.Slots.ALL.contains(slot)) {
                throw p.error("slot", "unknown slot, expected one of " + me.mephisto.ability_engine.engine.loadout.Slots.ALL);
            }
            return new ReduceCooldownNode(p.getString("ability", null), slot, p.requireInt("ticks"));
        });
        t.register("play_cue", (p, e) -> new PlayCueNode(p.requireString("cue"), p.getString("at", null), p.getString("to", null)));
        return t;
    }

    public void register(String type, NodeFactory factory) { factories.put(type, factory); }

    public NodeFactory require(String type, Params at) {
        NodeFactory f = factories.get(type);
        if (f == null) throw at.error("type", "unknown node type '" + type + "', known: " + factories.keySet());
        return f;
    }

    private static boolean towardCursor(Params p) {
        String t = p.getString("toward", "aim");
        if (!t.equals("aim") && !t.equals("cursor")) throw p.error("toward", "expected aim or cursor");
        return t.equals("cursor");
    }

}

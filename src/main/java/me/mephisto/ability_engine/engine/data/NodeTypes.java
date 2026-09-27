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
        t.register("delay", (p, e) -> new DelayNode(p.requireInt("ticks"), p.getBool("cast_bar", false)));
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
                p.getBool("infusions", false)));
        t.register("projectile", (p, e) -> new ProjectileNode(Parsers.projectile(p), p.getString("store", null)));
        t.register("barrier", (p, e) -> new BarrierNode(p.getDouble("distance", 1.0), p.getDouble("radius", 1.3)));
        t.register("start_cue", (p, e) -> new StartCueNode(p.requireString("cue"), p.getString("at", null)));
        t.register("dash", (p, e) -> new DashNode(
                p.getDouble("speed", 1.2), p.requireDouble("range"), p.getDouble("radius", 0.6), p.getBool("flat", false),
                p.getBool("pierce", false), p.getString("store", null), Parsers.dashDirection(p)));
        t.register("counter", (p, e) -> new CounterNode(p.requireString("counter"), p.requireInt("every")));
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
                new ConstructSystem.Options(p.getBool("solid", true), p.getDouble("trigger", 0), p.getInt("arm", 0))));
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
            String infusion = p.requireString("infusion");
            if (e.infusions().find(infusion).isEmpty()) {
                throw p.error("infusion", "unknown infusion '" + infusion + "' (define it under 'infusions:')");
            }
            int count = p.getInt("count", 1);
            if (count < 1) throw p.error("count", "must be at least 1");
            return new InfuseNode(infusion, count);
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
}

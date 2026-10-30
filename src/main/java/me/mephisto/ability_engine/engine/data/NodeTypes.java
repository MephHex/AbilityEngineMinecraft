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
        t.register("delay", (p, e) -> {
            String countOf = knownStatus(p, "boss_bar_count", e);
            if (countOf != null && !p.getBool("boss_bar", false)) throw p.error("boss_bar_count", "needs boss_bar: true");
            return new DelayNode(p.requireInt("ticks"), p.getBool("cast_bar", false), p.getBool("boss_bar", false))
                    .counting(countOf, p.getString("boss_bar_label", ""));
        });
        t.register("timer_color", (p, e) -> {
            if (!p.has("color") && !p.has("text")) throw p.error("color", "give color: (the bar) and/or text: (its title's letters)");
            String color = p.has("color") ? p.requireString("color").toLowerCase(java.util.Locale.ROOT) : null;
            if (color != null && !me.mephisto.ability_engine.engine.nodes.control.TimerColorNode.COLORS.contains(color)) {
                throw p.error("color", "one of " + me.mephisto.ability_engine.engine.nodes.control.TimerColorNode.COLORS);
            }
            java.util.List<String> text = p.has("text") ? p.getStringList("text") : null;
            if (text != null) {
                if (text.isEmpty()) throw p.error("text", "give at least one colour, \"#RRGGBB\"");
                for (String c : text) {
                    if (!c.matches("#[0-9a-fA-F]{6}")) throw p.error("text", "colours are \"#RRGGBB\", not '" + c + "'");
                }
            }
            return new me.mephisto.ability_engine.engine.nodes.control.TimerColorNode(color, text);
        });
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
                p.getString("times", null),
                p.getString("count_players", null)).countingKills(p.getString("count_kills", null)));
        t.register("projectile", (p, e) -> new ProjectileNode(seekMarksKnown(Parsers.projectile(p), p, e), p.getString("store", null),
                new ProjectileNode.Launch(p.getString("from", null), p.getDouble("up", 0), p.getDouble("back", 0),
                        p.getString("toward", null)), Parsers.pattern(p)));
        t.register("barrier", (p, e) -> new BarrierNode(p.getDouble("distance", 1.0), p.getDouble("radius", 1.3),
                p.getBool("projectiles_only", false), p.getBool("reflect", false), p.getBool("around", false)));
        t.register("start_cue", (p, e) -> new StartCueNode(p.requireString("cue"), p.getString("at", null)));
        t.register("dash", (p, e) -> {
            boolean follow = p.getBool("follow", false);
            if (follow && !p.has("to")) throw p.error("follow", "needs to: <key> (whoever to fly after)");
            return new DashNode(
                    p.getDouble("speed", 1.2), p.requireDouble("range"), p.getDouble("radius", 0.6), p.getBool("flat", false),
                    p.getBool("pierce", false), p.getString("store", null), Parsers.dashDirection(p),
                    p.getString("mover", null), towardCursor(p), p.getString("to", null), p.getDouble("stop_short", 0), follow)
                    .walls(p.has("on") && p.getParams("on").keys().contains(DashNode.WALL)); // wired: walls exit "wall"
        });
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
                    p.requireDouble("up"), p.getDouble("gravity", 0.08), until.equals("apex"), p.getString("store", null),
                    p.getBool("stop_at_enemies", false), p.getDouble("radius", 0.6));
        });
        t.register("choose_spot", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.ChooseSpotNode(
                new me.mephisto.ability_engine.engine.targeting.Targeting(
                        me.mephisto.ability_engine.engine.targeting.Targeting.Shape.CIRCLE, p.requireDouble("range"),
                        p.getDouble("radius", 1.0), 1.0, 60, p.getInt("timeout", 100), p.getBool("ground", true),
                        p.getDouble("max_drop", 40)),
                p.getString("store", "aim"), p.has("on") && p.getParams("on").keys().contains("cancelled")));
        t.register("link", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.LinkNode(
                p.requireString("name"), p.requireString("target"), p.requireDouble("range"),
                p.getDouble("damage_taken", 1.0), p.getDouble("redirect", 0), p.getBool("copy_positive", false),
                p.getString("cue", null), p.getDouble("mirror", 0), p.getString("from", null)));
        t.register("find_link", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.FindLinkNode(
                p.requireString("name"), p.getString("store", "linked")));
        t.register("unlink", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.UnlinkNode(p.requireString("name")));
        t.register("clone_mode", (p, e) -> {
            java.util.List<String> messages = p.has("messages") ? p.getStringList("messages") : java.util.List.of();
            if (!messages.isEmpty() && messages.size() != 3) throw p.error("messages", "three: copy, still, puppet");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.CloneModeNode(messages);
        });
        t.register("steal_ultimate", (p, e) -> {
            int window = p.getInt("window", 400);
            if (window < 1) throw p.error("window", "ticks the stolen ultimate can be cast in, at least 1 (400 = 20s)");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.StealUltimateNode(p.getString("target", "target"),
                    window, p.getString("message", "Their ultimate can't be stolen."));
        });
        t.register("start_cooldown", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.StartCooldownNode(
                p.getBool("restart", false)));
        t.register("on_cooldown", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.OnCooldownNode(
                p.getString("ability", null)));
        t.register("end_cast", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.EndCastNode());
        t.register("fork", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.ForkNode());
        t.register("summon_clone", (p, e) -> {
            if (p.has("beside") && p.has("at")) throw p.error("beside", "give at: or beside:, not both");
            double health = p.getDouble("health", 0);
            if (health < 0) throw p.error("health", "must be >= 0 (0 = can't be hurt)");
            double share = p.getDouble("health_share", 0);
            if (share < 0) throw p.error("health_share", "must be >= 0");
            if (health > 0 && share > 0) throw p.error("health", "give health or health_share, not both");
            double range = p.getDouble("range", 12);
            if (range <= 0) throw p.error("range", "blocks (with at: cursor), above 0");
            int hits = p.getInt("hits", 0);
            if (hits < 0) throw p.error("hits", "basic attacks that break it, at least 0 (0 = it doesn't count hits)");
            if (hits > 0 && health <= 0 && share <= 0) throw p.error("hits", "needs health (it has to be hittable)");
            String face = p.getString("face", "toward_owner");
            if (!face.equals("owner") && !face.equals("toward_owner")) throw p.error("face", "expected owner or toward_owner");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.SummonCloneNode(
                    p.requireString("summon"), p.getString("store", "summon"), p.requireInt("lifetime"),
                    p.getString("at", null), p.getString("of", null), health, share, p.getBool("glowing", false))
                    .extras(range, face.equals("owner"), p.getBool("disguise", false), p.getBool("mirror", false), hits,
                            p.getString("end_cue", null))
                    .beside(beside(p))
                    .onBreak(knownStatus(p, "on_break", e));
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
        t.register("swap", (p, e) -> {
            double speed = p.getDouble("speed", 0);
            if (speed < 0) throw p.error("speed", "blocks a tick for a flight (0.35), or leave it out for an instant swap");
            for (String k : java.util.List.of("lift", "side", "min_ticks", "max_ticks", "status")) {
                if (speed == 0 && p.has(k)) throw p.error(k, "only for a flight: give speed too");
            }
            int min = p.getInt("min_ticks", 10);
            int max = p.getInt("max_ticks", 60);
            if (min < 1 || max < min) throw p.error("max_ticks", "min_ticks at least 1, max_ticks at least min_ticks");
            String status = p.getString("status", null);
            if (status != null && e.statusDefs().find(status).isEmpty()) throw p.error("status", "unknown status '" + status + "' (define it under 'statuses:')");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.SwapNode(p.requireString("with"), speed,
                    p.getDouble("lift", 0), p.getDouble("side", 0), min, max, status);
        });
        t.register("refund", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.RefundNode(p.getString("message", null)));
        t.register("counter", (p, e) -> new CounterNode(p.requireString("counter"), p.requireInt("every"),
                p.getBool("peek", false)));
        t.register("charge", (p, e) -> {
            double from = p.getDouble("from", 0);
            if (from < 0 || from > 1) throw p.error("from", "must be between 0 and 1");
            int min = p.getInt("min", 0);
            if (min < 0 || min > p.requireInt("ticks")) throw p.error("min", "must be between 0 and ticks");
            var charge = new me.mephisto.ability_engine.engine.nodes.control.ChargeNode(p.requireInt("ticks"), from,
                    p.getString("store", "charge"), min, p.getBool("fire_when_full", true), p.getInt("release_gap", 0),
                    chargeLoad(p));
            if ("held".equals(p.raw("bar"))) {
                if (p.getInt("release_gap", 0) <= 0) throw p.error("bar", "held needs release_gap (the input's repeats)");
                return charge.withBarWhenHeld();
            }
            return p.getBool("bar", true) ? charge : charge.withoutBar();
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
        t.register("remember_spot", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.RememberSpotNode(
                p.requireString("of"), p.requireString("store"), p.getBool("ground", !p.has("into")), p.getDouble("into", 0)));
        t.register("ward_reset", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.WardResetNode());
        t.register("spell_shield", (p, e) -> {
            double max = p.requireDouble("max");
            if (max <= 0) throw p.error("max", "must be above 0");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.SpellShieldNode(max);
        });
        t.register("spell_block", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.SpellBlockNode(
                p.requireInt("duration")));
        t.register("shield_charge", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.ShieldChargeNode(
                p.getString("store", "charge")));
        t.register("await_kill", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.AwaitKillNode(
                p.getBool("players_only", false), p.getString("store", "victim")));
        t.register("cancel_ability", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.CancelAbilityNode(
                p.requireString("ability")));
        t.register("release_charge", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.ReleaseChargeNode(
                p.requireString("ability")));
        t.register("release_tags", (p, e) -> new ReleaseTagsNode());
        t.register("steer_projectile", (p, e) -> new SteerProjectileNode(
                p.requireString("ability"), p.getDouble("turn", 0.25), p.getDouble("range", 60)));
        t.register("set", (p, e) -> new SetNode(p.requireString("key"), p.requireString("value"), p.getString("of", null)));
        t.register("construct", (p, e) -> new ConstructNode(
                p.getString("at", "aim"),
                p.getString("store", "construct"),
                p.getDouble("height", 1.0),
                p.getDouble("size", 0.8),
                p.getInt("fragile", 0),
                p.requireInt("fuse"),
                p.getString("visual", "AMETHYST_CLUSTER"),
                new ConstructSystem.Options(p.getBool("solid", true), p.getDouble("trigger", 0), p.getInt("arm", 0),
                        p.getInt("limit", 0), triggeredBy(p), p.getBool("hidden", false), p.getString("cue", null),
                        p.getInt("cue_every", 10), p.getDouble("trigger_allies", 0))).withFuseSpread(p.getInt("fuse_spread", 0)));
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
        // ---- ammo ----
        t.register("spend", (p, e) -> {
            double amount = p.getDouble("amount", 1);
            if (amount <= 0) throw p.error("amount", "must be above 0");
            return new me.mephisto.ability_engine.engine.nodes.control.SpendNode(p.requireString("resource"), amount,
                    p.getBool("peek", false));
        });
        t.register("hold_reload", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.HoldReloadNode(
                p.requireString("resource")));
        t.register("refill", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.RefillNode(
                p.requireString("resource"), p.has("amount") ? p.getDouble("amount", 0) : null));
        t.register("landings", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.LandingsNode(
                p.requireInt("ticks"), p.getString("store", null)));
        t.register("repeat", (p, e) -> {
            Object times = p.raw("times");
            Integer fixed = times instanceof Number n ? n.intValue() : null;
            String key = times instanceof String str ? str : null;
            if (fixed == null && key == null) throw p.error("times", "give a number, or a blackboard key holding one");
            if (fixed != null && fixed < 1) throw p.error("times", "must be at least 1");
            return new me.mephisto.ability_engine.engine.nodes.control.RepeatNode(fixed, key, p.getDouble("scale", 1),
                    p.getInt("every", 0), p.getString("spend", null), p.getBool("attack_speed", false));
        });
        t.register("set_off_constructs", (p, e) -> {
            double radius = p.requireDouble("radius");
            if (radius <= 0) throw p.error("radius", "must be above 0");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.SetOffConstructsNode(p.requireString("ability"),
                    p.getString("center", "hit"), radius);
        });
        t.register("cast", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.CastNode(p.requireString("ability"),
                p.getBool("free", false)));
        t.register("end_projectiles", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.EndProjectilesNode(
                p.requireString("ability"), p.getString("node", null)));
        t.register("strike_constructs", (p, e) -> {
            double range = p.requireDouble("range");
            if (range <= 0) throw p.error("range", "must be above 0");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.StrikeConstructsNode(range,
                    p.getDouble("angle", 0), p.getDouble("width", 0.6));
        });
        t.register("tether", (p, e) -> {
            int duration = p.requireInt("duration");
            if (duration < 1) throw p.error("duration", "must be at least 1 tick");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.TetherNode(p.getString("target", "target"), duration,
                    p.requireDouble("range"), p.getInt("sight_grace", 10), p.getStringSet("break_on", Set.of()),
                    p.requireString("cue"), p.getInt("stages", 4), p.getInt("pulse_every", 0));
        });
        t.register("boss_bar", (p, e) -> {
            String status = p.requireString("stacks_of");
            if (e.statusDefs().find(status).isEmpty()) throw p.error("stacks_of", "unknown status '" + status + "'");
            int max = p.requireInt("max");
            if (max < 1) throw p.error("max", "how many stacks empty it, at least 1");
            return new me.mephisto.ability_engine.engine.nodes.control.BossBarNode(status, max);
        });
        t.register("grant_tags", (p, e) -> {
            Set<String> tags = p.getStringSet("tags", Set.of());
            if (tags.isEmpty()) throw p.error("tags", "give the tags to put on the caster until the cast ends");
            return new me.mephisto.ability_engine.engine.nodes.control.GrantTagsNode(tags);
        });
        t.register("for_each", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.ForEachNode(
                Parsers.query(p.requireParams("query")), p.getInt("max", 0), p.getString("store", "target")));
        t.register("health_below", (p, e) -> {
            double share = p.requireDouble("share");
            if (share <= 0 || share > 1) throw p.error("share", "a share of max HP, e.g. 0.1");
            return new me.mephisto.ability_engine.engine.nodes.control.HealthBelowNode(p.getString("target", "target"), share);
        });
        t.register("input_held", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.InputHeldNode(
                p.getInt("within", 4)));
        t.register("veil", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.VeilNode(
                p.getString("target", "target"), p.requireInt("duration")));
        t.register("reset_cooldowns", (p, e) -> {
            java.util.List<String> slots = p.getStringList("slots");
            for (String slot : slots) {
                if (!me.mephisto.ability_engine.engine.loadout.Slots.ALL.contains(slot)) {
                    throw p.error("slots", "unknown slot '" + slot + "', expected " + me.mephisto.ability_engine.engine.loadout.Slots.ALL);
                }
            }
            return new me.mephisto.ability_engine.engine.nodes.control.ResetCooldownsNode(slots);
        });
        t.register("start_line", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.StartLineNode(
                p.requireString("cue"), p.requireString("to"), p.getInt("every", 2)));
        // ---- Fae: rides, leashes, friend-or-foe ----
        t.register("is_ally", (p, e) -> new me.mephisto.ability_engine.engine.nodes.control.IsAllyNode(
                p.getString("target", "hit")));
        t.register("mount", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.MountNode(
                p.getString("target", "target"), knownStatus(p, "status", e), knownStatus(p, "self_status", e),
                p.getString("store", "mount"), p.getDouble("lift", 0)));
        t.register("dismount", (p, e) -> new me.mephisto.ability_engine.engine.nodes.gameplay.DismountNode());
        t.register("leash", (p, e) -> {
            double length = p.requireDouble("length");
            if (length <= 0) throw p.error("length", "must be above 0 (blocks of slack)");
            int duration = p.requireInt("duration");
            if (duration < 1) throw p.error("duration", "must be at least 1 tick");
            double pull = p.getDouble("pull", 0.35);
            if (pull <= 0) throw p.error("pull", "must be above 0");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.LeashNode(p.getString("target", "target"), length,
                    duration, pull, p.getDouble("max_speed", 1.5), p.getDouble("range", 32), p.getString("cue", null));
        });
        // ---- Graviton: a field slowing enemy shots ----
        t.register("slow_field", (p, e) -> {
            double slow = p.requireDouble("slow");
            if (slow <= 0 || slow >= 1) throw p.error("slow", "the share of their speed inside it, between 0 and 1 (0.15 = 15%)");
            double length = p.requireDouble("length"), width = p.requireDouble("width"), height = p.getDouble("height", 3);
            if (length <= 0) throw p.error("length", "must be above 0 (blocks)");
            if (width <= 0) throw p.error("width", "must be above 0 (blocks)");
            if (height <= 0) throw p.error("height", "must be above 0 (blocks)");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.SlowFieldNode(p.getString("at", "aim"), length, width,
                    height, slow);
        });
        // ---- Arcanist: a screen shots fly through (see Screens) ----
        t.register("screen", (p, e) -> {
            double width = p.requireDouble("width"), height = p.requireDouble("height");
            if (width <= 0) throw p.error("width", "must be above 0 (blocks)");
            if (height <= 0) throw p.error("height", "must be above 0 (blocks)");
            int hold = p.getInt("hold", 40);
            if (hold < 0) throw p.error("hold", "ticks an enemy projectile is held in it, at least 0");
            double accelerate = p.getDouble("accelerate", 1.0);
            if (accelerate <= 0) throw p.error("accelerate", "the speed multiplier for friendly projectiles, above 0 (1.5)");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.ScreenNode(p.getString("at", "aim"),
                    p.getString("store", "screen"), width, height, hold, accelerate,
                    p.has("effects") ? Parsers.effects(p, "effects", e.effects()) : java.util.List.of(),
                    p.getString("catch_cue", null), p.getString("boost_cue", null)).ahead(screenDistance(p));
        });
        // ---- Amethyst: a floor of hers (see Floors) ----
        t.register("floor", (p, e) -> {
            String over = p.getString("over", null);
            if (over != null) {
                for (String k : java.util.List.of("radius", "at", "shape", "up", "down", "drop")) {
                    if (p.has(k)) throw p.error(k, "over: lays it over that floor (its spot, size and shape): leave " + k + " out");
                }
            }
            double radius = over != null ? 1 : p.requireDouble("radius");
            if (radius <= 0) throw p.error("radius", "must be above 0 (blocks)");
            double drop = p.getDouble("drop", 4);
            if (drop < 0) throw p.error("drop", "how far down to look for the ground, at least 0 (blocks)");
            for (String key : java.util.List.of("enemy_status", "owner_status")) {
                String status = p.getString(key, null);
                if (status != null && e.statusDefs().find(status).isEmpty()) {
                    throw p.error(key, "unknown status '" + status + "' (define it under 'statuses:')");
                }
            }
            int duration = p.getInt("duration", 0);
            if (duration < 0) throw p.error("duration", "ticks it lasts, at least 0 (0 = until the cast ends)");
            String shape = p.getString("shape", "circle");
            if (!shape.equals("circle") && !shape.equals("square")) throw p.error("shape", "expected circle or square");
            return new me.mephisto.ability_engine.engine.nodes.gameplay.FloorNode(p.getString("at", "aim"), radius,
                    p.getString("name", null), p.getString("enemy_status", null), p.getString("owner_status", null), drop,
                    duration, p.getBool("drawn", true), p.getBool("sight", false)).square(shape.equals("square"))
                    .reach(reach(p, "up"), reach(p, "down")).over(over, p.getBool("lit", false))
                    .sticky(p.getBool("sticky", false)).enemyDelay(p.getInt("enemy_delay", 0));
        });
        t.register("play_cue", (p, e) -> {
            String audience = p.getString("audience", "everyone");
            if (!audience.equals("everyone") && !audience.equals("caster")) throw p.error("audience", "expected everyone or caster");
            if (p.has("ahead") && p.has("to")) throw p.error("ahead", "give to: or ahead:, not both");
            double ahead = p.getDouble("ahead", 0);
            if (ahead < 0) throw p.error("ahead", "blocks ahead of 'at' the line goes, above 0");
            return new PlayCueNode(p.requireString("cue"), p.getString("at", null), p.getString("to", null),
                    p.getString("numbered_by", null)).casterOnly(audience.equals("caster")).ahead(ahead);
        });
        return t;
    }

    public void register(String type, NodeFactory factory) { factories.put(type, factory); }

    public NodeFactory require(String type, Params at) {
        NodeFactory f = factories.get(type);
        if (f == null) throw at.error("type", "unknown node type '" + type + "', known: " + factories.keySet());
        return f;
    }

    /** charge's {@code load: { resource, every, max }} (null without one). */
    /** A floor's up / down: blocks, 0-10 (missing: NaN, the default). */
    private static double reach(Params p, String key) {
        if (!p.has(key)) return Double.NaN;
        double v = p.getDouble(key, 0);
        if (v < 0 || v > 10) throw p.error(key, "blocks above / below the floor that still count as on it, 0-10");
        return v;
    }

    /** summon_clone's beside: blocks to the caster's side, above 0 (0 = not beside them). */
    private static double beside(me.mephisto.ability_engine.engine.data.Params p) {
        double b = p.getDouble("beside", 0);
        if (b < 0 || b > 10) throw p.error("beside", "blocks to the caster's left or right, 0-10");
        return b;
    }

    private static me.mephisto.ability_engine.engine.nodes.control.ChargeNode.Load chargeLoad(Params p) {
        if (!p.has("load")) return null;
        Params l = p.getParams("load");
        int every = l.getInt("every", 5);
        if (every < 1) throw l.error("every", "must be at least 1 tick");
        int max = l.getInt("max", 99);
        if (max < 1) throw l.error("max", "must be at least 1");
        int start = l.getInt("start", 0);
        if (start < 0 || start > max) throw l.error("start", "must be between 0 and max");
        return new me.mephisto.ability_engine.engine.nodes.control.ChargeNode.Load(l.requireString("resource"), every, max, start);
    }

    /** A trap's {@code triggered_by: enemies} (default), {@code allies}, {@code all} or {@code everyone} (the caster too). */
    private static ConstructSystem.TriggeredBy triggeredBy(Params p) {
        String v = p.getString("triggered_by", "enemies");
        try {
            return ConstructSystem.TriggeredBy.valueOf(v.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw p.error("triggered_by", "expected enemies, allies, all, everyone (all and the caster) or owner_and_enemies");
        }
    }

    /** A screen's {@code distance} ahead of the caster (0 = at its {@code at} key instead). */
    private static double screenDistance(Params p) {
        double d = p.getDouble("distance", 0);
        if (d < 0) throw p.error("distance", "blocks ahead of the caster, above 0");
        if (d > 0 && p.has("at")) throw p.error("distance", "either distance (ahead of the caster) or at, not both");
        return d;
    }

    /** An optional status id that must exist (null if not given). */
    private static String knownStatus(Params p, String key, me.mephisto.ability_engine.engine.AbilityEngine e) {
        String status = p.getString(key, null);
        if (status != null && e.statusDefs().find(status).isEmpty()) {
            throw p.error(key, "unknown status '" + status + "' (define it under 'statuses:')");
        }
        return status;
    }

    /** A seeker's {@code mark} must be a status that exists. */
    private static me.mephisto.ability_engine.engine.projectile.ProjectileSpec seekMarksKnown(
            me.mephisto.ability_engine.engine.projectile.ProjectileSpec spec, Params p, me.mephisto.ability_engine.engine.AbilityEngine e) {
        for (var m : spec.motion()) {
            if (m instanceof me.mephisto.ability_engine.engine.projectile.Seek seek && seek.mark() != null
                    && e.statusDefs().find(seek.mark()).isEmpty()) {
                throw p.error("motion", "seek: unknown mark status '" + seek.mark() + "' (define it under 'statuses:')");
            }
            if (m instanceof me.mephisto.ability_engine.engine.projectile.MarkHoming home
                    && e.statusDefs().find(home.status()).isEmpty()) {
                throw p.error("motion", "homing: unknown status '" + home.status() + "' (define it under 'statuses:')");
            }
        }
        return spec;
    }

    private static boolean towardCursor(Params p) {
        String t = p.getString("toward", "aim");
        if (!t.equals("aim") && !t.equals("cursor")) throw p.error("toward", "expected aim or cursor");
        return t.equals("cursor");
    }

}

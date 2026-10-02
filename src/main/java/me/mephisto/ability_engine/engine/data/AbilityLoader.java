package me.mephisto.ability_engine.engine.data;

import me.mephisto.ability_engine.engine.status.StatusDef;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.Ability;
import me.mephisto.ability_engine.engine.ability.AbilityDisplay;
import me.mephisto.ability_engine.engine.graph.GraphBuilder;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.GraphValidationException;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.quiver.QuiverDef;
import me.mephisto.ability_engine.engine.state.ResourceDef;
import me.mephisto.ability_engine.engine.tag.Tags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads statuses and abilities from a plain Map (whatever YAML/JSON parser produced it).
 * A broken ability is reported and skipped; it never takes the others down with it.
 *
 * <pre>
 * statuses:
 *   stun: { duration: 40, tags: [state.stunned, block.move, block.ability] }
 * abilities:
 *   my_ability:
 *     cooldown: 60            # ticks
 *     cost: { mana: 20 }
 *     mode: instant           # or { type: channel, period: 4, duration: 60 }
 *     start: first_node       # optional, defaults to the first node listed
 *     display: { name: "Arc Shot", icon: PRISMARINE_SHARD, description: ["line 1", "line 2"] }
 *     nodes:
 *       first_node:
 *         type: acquire_target
 *         ...node params...
 *         on: { hit: other_node, miss: another }   # or  next: other_node  (= on: {out: ...})
 * infusions:
 *   poison: { name: "Poison", color: "#4E9331", on_hit: [ { id: status, status: poisoned } ] }
 * characters:
 *   gunner:
 *     name: "Gunner"
 *     weapon: IRON_SWORD
 *     slots: { ability_1: my_ability, ability_2: other, ultimate: big_one }
 * </pre>
 */
public final class AbilityLoader {

    private final AbilityEngine engine;

    public AbilityLoader(AbilityEngine engine) {
        this.engine = engine;
    }

    /** One parsed file: a name for error messages (e.g. "content/archmage.yml") and its YAML. */
    public record Source(String name, Map<String, Object> root) {}

    public LoadReport load(Map<String, Object> root, String source) {
        return load(List.of(new Source(source, root)));
    }

    /**
     * Load many files as one. Each phase runs across ALL files before the next starts, so files can
     * refer to each other in any order: statuses, then status effects, then infusions, then abilities,
     * then characters.
     * An id defined in two files is reported (naming both) and the second one is skipped.
     */
    public LoadReport load(List<Source> sources) {
        LoadReport report = new LoadReport();
        List<Params> files = new ArrayList<>();
        for (Source src : sources) {
            if (src.root() != null) files.add(Params.of(normalizeKeys(src.root()), src.name()));
        }

        Map<String, String> statusOrigin = new HashMap<>();
        Map<String, Params> statusParams = new LinkedHashMap<>();
        for (Params file : files) { // pass 1: every status exists, so pass 2 can refer to any of them
            Params statuses = file.getParams("statuses");
            for (String id : statuses.keys()) {
                if (!claim(report, "status", id, file.path(), statusOrigin)) continue;
                try {
                    engine.statusDefs().define(Parsers.status(id, statuses.requireParams(id)));
                    statusParams.put(id, statuses.requireParams(id));
                } catch (RuntimeException e) {
                    report.error(e.getMessage());
                }
            }
        }
        for (var entry : statusParams.entrySet()) { // pass 2: on_hit / tick effects
            var base = engine.statusDefs().find(entry.getKey());
            if (base.isEmpty()) continue;
            try {
                StatusDef def = Parsers.statusEffects(base.get(), entry.getValue(), engine.effects());
                knownStatus(entry.getValue(), "at_max", def.links().atMax());
                knownStatus(entry.getValue(), "requires", def.links().requires());
                knownStatus(entry.getValue(), "then", def.links().then());
                engine.statusDefs().define(def);
                report.status();
            } catch (RuntimeException e) {
                report.error(e.getMessage());
            }
        }

        Map<String, String> infusionOrigin = new HashMap<>(); // after statuses (they apply them), before abilities (infuse nodes)
        for (Params file : files) {
            Params infusions = file.getParams("infusions");
            for (String id : infusions.keys()) {
                if (!claim(report, "infusion", id, file.path(), infusionOrigin)) continue;
                try {
                    engine.infusions().define(Parsers.infusion(id, infusions.requireParams(id), engine.effects()));
                    report.infusion();
                } catch (RuntimeException e) {
                    report.error(e.getMessage());
                }
            }
        }

        Map<String, String> abilityOrigin = new HashMap<>();
        for (Params file : files) {
            Params abilities = file.getParams("abilities");
            for (String id : abilities.keys()) {
                if (!claim(report, "ability", id, file.path(), abilityOrigin)) continue;
                try {
                    engine.abilities().register(parseAbility(id, abilities.requireParams(id)));
                    report.ability();
                } catch (DataException | GraphValidationException e) {
                    report.error(e.getMessage());
                } catch (RuntimeException e) {
                    report.error(abilities.path() + "." + id + ": " + e);
                }
            }
        }

        // After abilities, so kits can be checked against what actually loaded (from any file).
        Map<String, String> characterOrigin = new HashMap<>();
        for (Params file : files) {
            Params characters = file.getParams("characters");
            for (String id : characters.keys()) {
                if (!claim(report, "character", id, file.path(), characterOrigin)) continue;
                try {
                    engine.characters().define(parseCharacter(id, characters.requireParams(id)));
                    report.character();
                } catch (RuntimeException e) {
                    report.error(e.getMessage());
                }
            }
        }
        return report;
    }

    /** A status another one refers to must exist (any file). */
    private void knownStatus(Params p, String key, String id) {
        if (id != null && engine.statusDefs().find(id).isEmpty()) {
            throw p.error(key, "unknown status '" + id + "' (define it under 'statuses:')");
        }
    }

    /** First file to define an id owns it; later ones are reported and skipped. */
    private static boolean claim(LoadReport report, String kind, String id, String file, Map<String, String> origin) {
        String first = origin.putIfAbsent(id, file);
        if (first == null) return true;
        report.error(file + ": " + kind + " '" + id + "' is already defined in " + first + " (skipped)");
        return false;
    }

    private static int charges(Params p) {
        int n = p.getInt("charges", 1);
        if (n < 1) throw p.error("charges", "must be at least 1");
        return n;
    }

    private static String refreshOnKill(Params p) {
        String v = p.getString("refresh_on_kill", "none");
        if (!v.equals("none") && !v.equals("players") && !v.equals("all")) {
            throw p.error("refresh_on_kill", "expected none, players or all");
        }
        return v;
    }

    private Map<String, String> slots(Params slotsSection) {
        return slots(slotsSection, false);
    }

    /** @param noneEmpties a form's slots: {@code none} empties that slot while the form lasts */
    private Map<String, String> slots(Params slotsSection, boolean noneEmpties) {
        Map<String, String> slots = new LinkedHashMap<>();
        for (String slot : slotsSection.keys()) {
            if (!Slots.ALL.contains(slot)) throw slotsSection.error(slot, "unknown slot, expected one of " + Slots.ALL);
            String abilityId = slotsSection.requireString(slot);
            if (noneEmpties && abilityId.equalsIgnoreCase("none")) {
                slots.put(slot, null);
                continue;
            }
            if (engine.abilities().find(abilityId).isEmpty()) {
                throw slotsSection.error(slot, "unknown ability '" + abilityId + "' (missing, or it failed to load)");
            }
            slots.put(slot, abilityId);
        }
        return slots;
    }

    /**
     * {@code ward: { name, out_of_combat, hotbar, icon, description }}: a passive debuff immunity; with
     * {@code absorb} a barrier, with {@code cast: <ability>} (and {@code cooldown}) a reflex.
     */
    private CharacterDef.Ward ward(Params p, Map<String, ResourceDef> resources, QuiverDef quiver) {
        if (!p.has("ward")) return null;
        Params w = p.getParams("ward");
        int ticks = w.requireInt("out_of_combat");
        if (ticks < 1) throw w.error("out_of_combat", "must be at least 1 tick");
        String cast = w.getString("cast", null);
        if (cast != null && engine.abilities().find(cast).isEmpty()) {
            throw w.error("cast", "unknown ability '" + cast + "' (missing, or it failed to load)");
        }
        int cooldown = w.getInt("cooldown", 0);
        if (cooldown < 0) throw w.error("cooldown", "must be >= 0 ticks");
        if (cooldown > 0 && cast == null) throw w.error("cooldown", "only a reflex (cast: <ability>) has a cooldown");
        int hotbar = w.getInt("hotbar", 0);
        if (hotbar < 0 || hotbar > 9) throw w.error("hotbar", "expected a hotbar slot 1-9 (or 0: not shown)");
        for (ResourceDef r : resources.values()) {
            if (hotbar > 0 && r.hotbarSlot() == hotbar) throw w.error("hotbar", "slot " + hotbar + " is used by resource '" + r.id() + "'");
        }
        if (quiver != null && hotbar > 0 && hotbar >= quiver.hotbarSlot() && hotbar < quiver.hotbarSlot() + quiver.size()) {
            throw w.error("hotbar", "slot " + hotbar + " is used by the quiver");
        }
        double absorb = w.getDouble("absorb", 0);
        if (absorb < 0 || absorb > 1) throw w.error("absorb", "must be between 0 and 1 (0.5 = half the next hit)");
        if (absorb > 0 && cast != null) throw w.error("cast", "a ward is a barrier (absorb) or a reflex (cast), not both");
        return new CharacterDef.Ward(w.getString("name", "Ward"), ticks, hotbar, w.getString("icon", null),
                w.has("description") ? w.getStringList("description") : List.of(), absorb, cast, cooldown);
    }

    /**
     * {@code hover: { height, speed, visual, visual_size, visual_lead, visual_turn, visual_up }}: a passive flight, at
     * most that high above the ground. Without a height (0) there's no hover, only the visual at their feet
     * ({@code visual_size} x its size, drawn {@code visual_lead} ticks of their movement ahead of them, turned
     * {@code visual_turn} degrees, raised {@code visual_up} blocks).
     */
    private static CharacterDef.Hover hover(Params p) {
        if (!p.has("hover")) return null;
        Params h = p.getParams("hover");
        double height = h.getDouble("height", 0);
        if (height < 0) throw h.error("height", "must be 0 (no hover, just the visual) or more (blocks above the ground)");
        boolean fly = h.getBool("fly", true);
        if (height > 0 && !fly && (height < 1 || height != Math.rint(height))) {
            throw h.error("height", "without flying it's whole blocks: 1, 2, ...");
        }
        double speed = h.getDouble("speed", 0.5);
        if (speed <= 0 || speed > 10) throw h.error("speed", "must be above 0 (x vanilla flying speed, 0.5 = half)");
        double visualSize = h.getDouble("visual_size", 1.0);
        if (visualSize <= 0 || visualSize > 10) throw h.error("visual_size", "must be above 0 (x the default size, 1.2 = 20% bigger)");
        double lead = h.getDouble("visual_lead", CharacterDef.Hover.DEFAULT_LEAD);
        if (lead < 0 || lead > 10) throw h.error("visual_lead", "must be 0-10 (ticks of movement it's drawn ahead; 0 = at your feet)");
        double turn = h.getDouble("visual_turn", 0);
        if (turn < -360 || turn > 360) throw h.error("visual_turn", "degrees, -360 to 360 (45 = an eighth of a turn)");
        double up = h.getDouble("visual_up", 0);
        if (up < -1 || up > 1) throw h.error("visual_up", "blocks, -1 to 1 (0.0625 = one pixel higher)");
        return new CharacterDef.Hover(height, speed, h.getString("visual", null), fly, visualSize, lead, turn, up);
    }

    /** {@code stats: { health, armor, base_damage, move_speed, attack_speed }}: missing ones get the defaults. */
    private static CharacterDef.Stats stats(Params p) {
        return stats(p, CharacterDef.Stats.DEFAULT);
    }

    /** The same, missing ones taken from {@code d} (a form's stats: the character's own for what it leaves out). */
    private static CharacterDef.Stats stats(Params p, CharacterDef.Stats d) {
        if (!p.has("stats")) return d;
        Params s = p.getParams("stats");
        for (String key : s.keys()) {
            if (!java.util.Set.of("health", "armor", "base_damage", "move_speed", "attack_speed", "scale").contains(key)) {
                throw s.error(key, "unknown stat, expected health, armor, base_damage, move_speed, attack_speed or scale");
            }
        }
        var stats = new CharacterDef.Stats(s.getDouble("health", d.health()), s.getDouble("armor", d.armor()),
                s.getDouble("base_damage", d.baseDamage()), s.getDouble("move_speed", d.moveSpeed()),
                s.getDouble("attack_speed", d.attackSpeed()), s.getDouble("scale", d.scale()));
        if (stats.scale() < 0.1 || stats.scale() > 3) throw s.error("scale", "must be between 0.1 and 3 (1.0 = normal size)");
        if (stats.health() <= 0) throw s.error("health", "must be above 0");
        if (stats.armor() < 0) throw s.error("armor", "must be >= 0");
        if (stats.baseDamage() < 0) throw s.error("base_damage", "must be >= 0");
        if (stats.moveSpeed() <= 0) throw s.error("move_speed", "must be above 0 (1.0 = vanilla)");
        if (stats.attackSpeed() < 0) throw s.error("attack_speed", "must be >= 0 (0 = the primary's own cooldown)");
        return stats;
    }

    /** {@code traits: [sneak_slow_fall]}: always-on behaviours the platform provides. */
    private static Set<String> traits(Params p) {
        Set<String> traits = p.getStringSet("traits", Set.of());
        for (String t : traits) {
            if (!CharacterDef.TRAITS.contains(t)) throw p.error("traits", "unknown trait '" + t + "', known: " + CharacterDef.TRAITS);
        }
        return traits;
    }

    /**
     * {@code forms: [ { while: <tag>, weapon, slots: {...}, status_bar, stats } ]}: kit changes while a tag is on. A slot
     * set to {@code none} is empty meanwhile; {@code stats} (only the ones that change) replace the character's own.
     */
    private java.util.List<CharacterDef.Form> forms(Params p, CharacterDef.Stats base) {
        java.util.List<CharacterDef.Form> forms = new java.util.ArrayList<>();
        for (Params f : p.getParamsList("forms")) {
            forms.add(new CharacterDef.Form(f.requireString("while"), f.getString("weapon", null),
                    slots(f.getParams("slots"), true), statusBar(f), f.has("stats") ? stats(f, base) : null));
        }
        return forms;
    }

    public CharacterDef parseCharacter(String id, Params p) {
        Map<String, String> slots = slots(p.requireParams("slots"));
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        Params res = p.getParams("resources");
        for (String name : res.keys()) {
            Params r = res.requireParams(name);
            int hotbar = r.getInt("hotbar", 0);
            if (hotbar < 0 || hotbar > 9) throw r.error("hotbar", "expected a hotbar slot 1-9");
            int reload = r.getInt("reload", 0);
            if (reload < 0) throw r.error("reload", "must be >= 0 (ticks to refill once it's empty)");
            resources.put(name, new ResourceDef(name, r.requireDouble("max"), r.getDouble("regen", 0),
                    r.getInt("delay", 0), hotbar, r.getString("icon", null), reload,
                    r.getString("shown_while", null), r.getString("hidden_while", null)));
        }
        QuiverDef quiver = p.has("quiver") ? Parsers.quiver(p.getParams("quiver"), engine.statusDefs().ids()) : null;
        if (quiver != null && quiver.hotbarSlot() > 0) {
            for (ResourceDef r : resources.values()) {
                int slot = r.hotbarSlot();
                if (slot >= quiver.hotbarSlot() && slot < quiver.hotbarSlot() + quiver.size()) {
                    throw p.error("quiver", "hotbar slot " + slot + " is used by both the quiver and resource '" + r.id() + "'");
                }
            }
        }
        CharacterDef.StatusBar statusBar = statusBar(p);
        CharacterDef.Stats stats = stats(p);
        return new CharacterDef(id, p.getString("name", id), p.getString("weapon", null), slots, resources, quiver,
                statusBar, forms(p, stats), traits(p), stats, ward(p, resources, quiver), statusItems(p), whenHit(p), hearing(p),
                hover(p), variants(p), lowHealth(p));
    }

    /** {@code low_health: { below: 0.4, status: <status> }}: that status is kept on them while their health is below it. */
    private CharacterDef.LowHealth lowHealth(Params p) {
        if (!p.has("low_health")) return null;
        Params l = p.getParams("low_health");
        double below = l.requireDouble("below");
        if (below <= 0 || below > 1) throw l.error("below", "a share of max HP, e.g. 0.4");
        String status = l.requireString("status");
        if (engine.statusDefs().find(status).isEmpty()) {
            throw l.error("status", "unknown status '" + status + "' (define it under 'statuses:')");
        }
        return new CharacterDef.LowHealth(below, status);
    }

    /** {@code variants: [ { while: <tag>, cue_suffix: _blue, visuals: { <visual>: <visual> } } ]}. */
    private static java.util.List<CharacterDef.Variant> variants(Params p) {
        java.util.List<CharacterDef.Variant> out = new java.util.ArrayList<>();
        for (Params v : p.getParamsList("variants")) {
            String suffix = v.getString("cue_suffix", null);
            Params visuals = v.getParams("visuals");
            Map<String, String> swaps = new LinkedHashMap<>();
            for (String from : visuals.keys()) swaps.put(from, visuals.requireString(from));
            if (suffix == null && swaps.isEmpty()) throw v.error("cue_suffix", "a variant needs cue_suffix and/or visuals");
            out.add(new CharacterDef.Variant(v.requireString("while"), suffix, swaps));
        }
        return out;
    }

    /** {@code hearing: { below, range, toward: { range, status }, hotbar, icon, name, description }}. */
    private CharacterDef.Hearing hearing(Params p) {
        if (!p.has("hearing")) return null;
        Params h = p.getParams("hearing");
        double below = h.requireDouble("below");
        if (below <= 0 || below > 1) throw h.error("below", "a share of max HP, e.g. 0.4");
        Params toward = h.getParams("toward");
        String status = toward.getString("status", null);
        if (status != null && engine.statusDefs().find(status).isEmpty()) {
            throw toward.error("status", "unknown status '" + status + "' (define it under 'statuses:')");
        }
        int hotbar = h.getInt("hotbar", 0);
        if (hotbar < 0 || hotbar > 9) throw h.error("hotbar", "expected a hotbar slot 1-9 (or 0: not shown)");
        return new CharacterDef.Hearing(below, h.getDouble("range", 30), toward.getDouble("range", 15), status,
                hotbar, h.getString("icon", null), h.getString("name", "Hearing"),
                h.has("description") ? h.getStringList("description") : List.of());
    }

    /** {@code when_hit: { reduce_cooldowns: <ticks>, slots: [...] }} (enemy basic attacks landing on them). */
    private static CharacterDef.WhenHit whenHit(Params p) {
        if (!p.has("when_hit")) return null;
        Params w = p.getParams("when_hit");
        int ticks = w.requireInt("reduce_cooldowns");
        if (ticks < 1) throw w.error("reduce_cooldowns", "must be at least 1 tick");
        List<String> slots = w.has("slots") ? w.getStringList("slots")
                : List.of(me.mephisto.ability_engine.engine.loadout.Slots.ABILITY_1,
                        me.mephisto.ability_engine.engine.loadout.Slots.ABILITY_2,
                        me.mephisto.ability_engine.engine.loadout.Slots.ABILITY_3);
        for (String slot : slots) {
            if (!me.mephisto.ability_engine.engine.loadout.Slots.ALL.contains(slot)) {
                throw w.error("slots", "unknown slot '" + slot + "', expected " + me.mephisto.ability_engine.engine.loadout.Slots.ALL);
            }
        }
        return new CharacterDef.WhenHit(ticks, slots);
    }

    /** {@code status_items: [ { status, hotbar, icon, name, description, glint_weapon } ]}. */
    private java.util.List<CharacterDef.StatusItem> statusItems(Params p) {
        java.util.List<CharacterDef.StatusItem> items = new java.util.ArrayList<>();
        for (Params i : p.getParamsList("status_items")) {
            String status = i.requireString("status");
            if (engine.statusDefs().find(status).isEmpty()) {
                throw i.error("status", "unknown status '" + status + "' (define it under 'statuses:')");
            }
            int hotbar = i.requireInt("hotbar");
            if (hotbar < 1 || hotbar > 9) throw i.error("hotbar", "expected a hotbar slot 1-9");
            items.add(new CharacterDef.StatusItem(status, hotbar, i.getString("icon", null), i.getString("name", status),
                    i.has("description") ? i.getStringList("description") : List.of(), i.getBool("glint_weapon", false)));
        }
        return items;
    }

    /**
     * {@code status_bar: <status>} (level = its stacks), or
     * {@code status_bar: { status: <status>, level: stacks | reload_speed | none, fill: time | stacks }}.
     */
    private CharacterDef.StatusBar statusBar(Params p) {
        if (!p.has("status_bar")) return null;
        Object raw = p.raw("status_bar");
        String status;
        CharacterDef.StatusBar.Level level = CharacterDef.StatusBar.Level.STACKS;
        CharacterDef.StatusBar.Fill fill = CharacterDef.StatusBar.Fill.TIME;
        if (raw instanceof Map<?, ?>) {
            Params bar = p.getParams("status_bar");
            status = bar.requireString("status");
            try {
                level = CharacterDef.StatusBar.Level.valueOf(bar.getString("level", "stacks").toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw bar.error("level", "expected stacks, reload_speed or none");
            }
            try {
                fill = CharacterDef.StatusBar.Fill.valueOf(bar.getString("fill", "time").toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw bar.error("fill", "expected time or stacks");
            }
        } else {
            status = p.getString("status_bar", null);
        }
        if (engine.statusDefs().find(status).isEmpty()) {
            throw p.error("status_bar", "unknown status '" + status + "' (define it under 'statuses:')");
        }
        return new CharacterDef.StatusBar(status, level, fill);
    }

    public Ability parseAbility(String id, Params p) {
        Params nodes = p.requireParams("nodes");
        GraphBuilder graph = GraphBuilder.create(id);

        for (String nodeId : nodes.keys()) {
            Params np = nodes.requireParams(nodeId);
            GraphNode node = engine.nodeTypes().require(np.requireString("type"), np).create(np, engine);
            graph.node(nodeId, node);

            if (np.has("next")) graph.next(nodeId, np.requireString("next"));
            Params on = np.getParams("on");
            for (String port : on.keys()) {
                if (on.raw(port) == null) continue; // "port: ~" = that case just ends (e.g. a switch case)
                graph.edge(nodeId, port, on.requireString(port));
            }
        }
        if (p.has("start")) graph.start(p.requireString("start"));

        var built = graph.build();
        boolean hasRecast = built.nodeIds().stream()
                .anyMatch(n -> built.node(n) instanceof me.mephisto.ability_engine.engine.nodes.control.AwaitRecastNode);
        String cdStart = p.getString("cooldown_starts", hasRecast ? "after_recast" : "cast");
        if (!cdStart.equals("cast") && !cdStart.equals("after_recast") && !cdStart.equals("manual")) {
            throw p.error("cooldown_starts", "expected cast, after_recast or manual");
        }
        // Abilities that dash are movement abilities (blocked while rooted) unless they say movement: false.
        boolean dashes = built.nodeIds().stream()
                .anyMatch(n -> built.node(n) instanceof me.mephisto.ability_engine.engine.nodes.gameplay.DashNode
                        || built.node(n) instanceof me.mephisto.ability_engine.engine.nodes.gameplay.LeapNode);
        Ability.Builder b = Ability.builder(id, built)
                .movement(p.getBool("movement", dashes))
                .cooldownAfterRecast(cdStart.equals("after_recast"))
                .manualCooldown(cdStart.equals("manual"))
                .aura(p.getString("aura", null))
                .cancelOnRepress(p.getBool("cancel_on_repress", false))
                .survivesDeath(p.getBool("survives_death", false))
                .refreshOnKill(refreshOnKill(p))
                .charges(charges(p))
                .recastMovement(p.getBool("recast_movement", false))
                .passiveWhile(p.getString("passive_while", null))
                .cooldown(p.getInt("cooldown", 0))
                .costs(costs(p.getParams("cost")))
                .mode(Parsers.mode(p))
                .blockedBy(p.getStringSet("blocked_by", Set.of(Tags.BLOCK_ABILITY)))
                .display(display(id, p.getParams("display")));
        if (p.has("interrupted_by")) b.interruptedBy(p.getStringSet("interrupted_by", Set.of()));
        if (p.has("targeting")) b.targeting(Parsers.targeting(p.getParams("targeting"), p.getParams("nodes")));
        if (p.has("active_tags")) b.activeTags(p.getStringSet("active_tags", Set.of()));
        return b.build();
    }

    private static AbilityDisplay display(String id, Params p) {
        return new AbilityDisplay(p.getString("name", id), p.getString("icon", null), p.getStringList("description"));
    }

    /**
     * SnakeYAML follows YAML 1.1, where unquoted on/off/yes/no are booleans EVEN AS KEYS:
     * {@code on: {hit: x}} arrives as {@code {true: {hit: x}}}. Turn every key back into a string
     * (true -> "on", false -> "off", 1 -> "1") so the loader sees what the author wrote.
     */
    @SuppressWarnings("unchecked")
    static <T> T normalizeKeys(T value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object k = e.getKey();
                String key = Boolean.TRUE.equals(k) ? "on" : Boolean.FALSE.equals(k) ? "off" : String.valueOf(k);
                out.put(key, normalizeKeys(e.getValue()));
            }
            return (T) out;
        }
        if (value instanceof java.util.List<?> list) {
            return (T) list.stream().map(AbilityLoader::normalizeKeys).toList();
        }
        return value;
    }

    private static Map<String, Integer> costs(Params p) {
        Map<String, Integer> out = new HashMap<>();
        for (String resource : p.keys()) out.put(resource, p.requireInt(resource));
        return out;
    }
}

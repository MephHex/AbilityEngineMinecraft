package me.mephisto.ability_engine.engine.data;

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
                engine.statusDefs().define(Parsers.statusEffects(base.get(), entry.getValue(), engine.effects()));
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

    /** First file to define an id owns it; later ones are reported and skipped. */
    private static boolean claim(LoadReport report, String kind, String id, String file, Map<String, String> origin) {
        String first = origin.putIfAbsent(id, file);
        if (first == null) return true;
        report.error(file + ": " + kind + " '" + id + "' is already defined in " + first + " (skipped)");
        return false;
    }

    public CharacterDef parseCharacter(String id, Params p) {
        Params slotsSection = p.requireParams("slots");
        Map<String, String> slots = new LinkedHashMap<>();
        for (String slot : slotsSection.keys()) {
            if (!Slots.ALL.contains(slot)) throw slotsSection.error(slot, "unknown slot, expected one of " + Slots.ALL);
            String abilityId = slotsSection.requireString(slot);
            if (engine.abilities().find(abilityId).isEmpty()) {
                throw slotsSection.error(slot, "unknown ability '" + abilityId + "' (missing, or it failed to load)");
            }
            slots.put(slot, abilityId);
        }
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        Params res = p.getParams("resources");
        for (String name : res.keys()) {
            Params r = res.requireParams(name);
            int hotbar = r.getInt("hotbar", 0);
            if (hotbar < 0 || hotbar > 9) throw r.error("hotbar", "expected a hotbar slot 1-9");
            resources.put(name, new ResourceDef(name, r.requireDouble("max"), r.getDouble("regen", 0),
                    r.getInt("delay", 0), hotbar, r.getString("icon", null)));
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
        String statusBar = p.getString("status_bar", null);
        if (statusBar != null && engine.statusDefs().find(statusBar).isEmpty()) {
            throw p.error("status_bar", "unknown status '" + statusBar + "' (define it under 'statuses:')");
        }
        return new CharacterDef(id, p.getString("name", id), p.getString("weapon", null), slots, resources, quiver,
                statusBar);
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
            for (String port : on.keys()) graph.edge(nodeId, port, on.requireString(port));
        }
        if (p.has("start")) graph.start(p.requireString("start"));

        var built = graph.build();
        boolean hasRecast = built.nodeIds().stream()
                .anyMatch(n -> built.node(n) instanceof me.mephisto.ability_engine.engine.nodes.control.AwaitRecastNode);
        String cdStart = p.getString("cooldown_starts", hasRecast ? "after_recast" : "cast");
        if (!cdStart.equals("cast") && !cdStart.equals("after_recast")) {
            throw p.error("cooldown_starts", "expected cast or after_recast");
        }
        // Abilities that dash are movement abilities (blocked while rooted) unless they say movement: false.
        boolean dashes = built.nodeIds().stream()
                .anyMatch(n -> built.node(n) instanceof me.mephisto.ability_engine.engine.nodes.gameplay.DashNode);
        Ability.Builder b = Ability.builder(id, built)
                .movement(p.getBool("movement", dashes))
                .cooldownAfterRecast(cdStart.equals("after_recast"))
                .aura(p.getString("aura", null))
                .cancelOnRepress(p.getBool("cancel_on_repress", false))
                .survivesDeath(p.getBool("survives_death", false))
                .cooldown(p.getInt("cooldown", 0))
                .costs(costs(p.getParams("cost")))
                .mode(Parsers.mode(p))
                .blockedBy(p.getStringSet("blocked_by", Set.of(Tags.BLOCK_ABILITY)))
                .display(display(id, p.getParams("display")));
        if (p.has("interrupted_by")) b.interruptedBy(p.getStringSet("interrupted_by", Set.of()));
        if (p.has("targeting")) b.targeting(Parsers.targeting(p.getParams("targeting")));
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

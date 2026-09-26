package me.mephisto.ability_engine.engine.quiver;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class InfusionRegistry {

    private final Map<String, InfusionDef> infusions = new TreeMap<>();

    public void define(InfusionDef infusion) { infusions.put(infusion.id(), infusion); }
    public Optional<InfusionDef> find(String id) { return Optional.ofNullable(infusions.get(id)); }
    public Set<String> ids() { return Set.copyOf(infusions.keySet()); }
    public void clear() { infusions.clear(); }

    /**
     * The color a bolt shows: its infusions' colors averaged ("#RRGGBB"), like mixed potions.
     * Null for a plain bolt, no bolt, or infusions that no longer exist.
     */
    public String tintOf(Bolt bolt) {
        if (bolt == null) return null;
        int r = 0, g = 0, b = 0, n = 0;
        for (String id : bolt.infusions()) {
            InfusionDef inf = infusions.get(id);
            if (inf == null) continue;
            int rgb = Integer.parseInt(inf.color().substring(1), 16);
            r += (rgb >> 16) & 0xFF;
            g += (rgb >> 8) & 0xFF;
            b += rgb & 0xFF;
            n++;
        }
        return n == 0 ? null : String.format("#%02X%02X%02X", r / n, g / n, b / n);
    }
}

package me.mephisto.ability_engine.engine.tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reference-counted tags per entity. If a stun and a freeze both grant "block.move",
 * the tag stays until BOTH are gone — the classic bug where the first status to expire
 * frees you early can't happen.
 */
public final class TagManager {

    private final Map<UUID, Map<String, Integer>> counts = new HashMap<>();
    private final List<TagListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(TagListener listener) { listeners.add(listener); }

    public void grant(UUID entity, String tag) {
        int n = counts.computeIfAbsent(entity, k -> new HashMap<>()).merge(tag, 1, Integer::sum);
        if (n == 1) listeners.forEach(l -> l.onTagAdded(entity, tag));
    }

    public void grantAll(UUID entity, Set<String> tags) { tags.forEach(t -> grant(entity, t)); }

    public void revoke(UUID entity, String tag) {
        Map<String, Integer> tags = counts.get(entity);
        if (tags == null) return;
        Integer n = tags.get(tag);
        if (n == null) return;
        if (n > 1) {
            tags.put(tag, n - 1);
            return;
        }
        tags.remove(tag);
        if (tags.isEmpty()) counts.remove(entity);
        listeners.forEach(l -> l.onTagRemoved(entity, tag));
    }

    public void revokeAll(UUID entity, Set<String> tags) { tags.forEach(t -> revoke(entity, t)); }

    public boolean has(UUID entity, String tag) {
        Map<String, Integer> tags = counts.get(entity);
        return tags != null && tags.containsKey(tag);
    }

    /** First tag from {@code any} the entity has, or null. */
    public String firstMatch(UUID entity, Set<String> any) {
        for (String t : any) if (has(entity, t)) return t;
        return null;
    }

    public Set<String> tagsOf(UUID entity) {
        return Set.copyOf(counts.getOrDefault(entity, Map.of()).keySet());
    }

    /** Force-remove every tag (death, logout). Listeners still fire. */
    public void clear(UUID entity) {
        Map<String, Integer> tags = counts.remove(entity);
        if (tags == null) return;
        for (String tag : new ArrayList<>(tags.keySet())) {
            listeners.forEach(l -> l.onTagRemoved(entity, tag));
        }
    }
}

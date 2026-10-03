package me.mephisto.ability_engine.engine.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only view over a config map with typed, forgiving number conversion.
 * YAML gives you Integer for "5" and Double for "5.0"; a raw {@code (int) map.get(..)} cast
 * blows up on the wrong one. Every error message carries the config path.
 */
public final class Params {

    public static final Params EMPTY = new Params(Map.of(), "");

    private final Map<String, Object> values;
    private final String path;

    private Params(Map<String, Object> values, String path) {
        this.values = values;
        this.path = path;
    }

    @SuppressWarnings("unchecked")
    public static Params of(Map<String, ?> values, String path) {
        return new Params(values == null ? Map.of() : (Map<String, Object>) values, path);
    }

    public static Params of(Map<String, ?> values) { return of(values, ""); }

    public String path() { return path; }
    public boolean has(String key) { return values.containsKey(key) && values.get(key) != null; }
    public Object raw(String key) { return values.get(key); }
    /** Keys in file order (SnakeYAML uses LinkedHashMap). */
    public Set<String> keys() { return values.keySet(); }

    private String at(String key) { return path.isEmpty() ? key : path + "." + key; }

    public DataException error(String key, String message) {
        return new DataException(at(key) + ": " + message);
    }

    // ---- strings ----
    public String getString(String key, String def) {
        Object v = values.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public String requireString(String key) {
        if (!has(key)) throw error(key, "is required");
        return getString(key, null);
    }

    // ---- numbers ----
    public double getDouble(String key, double def) {
        Object v = values.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            throw error(key, "expected a number, got '" + v + "'");
        }
    }

    public double requireDouble(String key) {
        if (!has(key)) throw error(key, "is required");
        return getDouble(key, 0);
    }

    public int getInt(String key, int def) {
        if (!has(key)) return def;
        double d = getDouble(key, def);
        if (d != Math.rint(d)) throw error(key, "expected a whole number, got " + d);
        return (int) d;
    }

    public int requireInt(String key) {
        if (!has(key)) throw error(key, "is required");
        return getInt(key, 0);
    }

    public boolean getBool(String key, boolean def) {
        Object v = values.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    // ---- nested ----
    @SuppressWarnings("unchecked")
    public Params getParams(String key) {
        Object v = values.get(key);
        if (v == null) return new Params(Map.of(), at(key));
        if (v instanceof Map<?, ?> m) return new Params((Map<String, Object>) m, at(key));
        throw error(key, "expected a section, got '" + v + "'");
    }

    public Params requireParams(String key) {
        if (!has(key)) throw error(key, "is required");
        return getParams(key);
    }

    /** List entries that are maps, each wrapped with an indexed path ("effects[0]"). */
    public List<Params> getParamsList(String key) {
        Object v = values.get(key);
        if (v == null) return List.of();
        if (!(v instanceof List<?> list)) throw error(key, "expected a list");
        java.util.ArrayList<Params> out = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> m)) throw error(key + "[" + i + "]", "expected a section");
            @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) m;
            out.add(new Params(typed, at(key) + "[" + i + "]"));
        }
        return out;
    }

    /** A list of strings in order. A single scalar becomes a one-element list. */
    public List<String> getStringList(String key) {
        Object v = values.get(key);
        if (v == null) return List.of();
        if (v instanceof Collection<?> c) return c.stream().map(String::valueOf).toList();
        return List.of(String.valueOf(v));
    }

    public Set<String> getStringSet(String key, Set<String> def) {
        Object v = values.get(key);
        if (v == null) return def;
        Set<String> out = new LinkedHashSet<>();
        if (v instanceof Collection<?> c) c.forEach(o -> out.add(String.valueOf(o)));
        else out.add(String.valueOf(v));
        return Set.copyOf(out);
    }

    /** Copy with one more (or a replaced) value. */
    public Params with(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.put(key, value);
        return new Params(copy, path);
    }

    /** Copy without the given keys — e.g. an effect entry minus its "id". */
    public Params without(String... keys) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        for (String k : keys) copy.remove(k);
        return new Params(copy, path);
    }

    @Override
    public String toString() { return values.toString(); }
}

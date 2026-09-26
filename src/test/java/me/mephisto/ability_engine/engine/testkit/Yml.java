package me.mephisto.ability_engine.engine.testkit;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny helpers to write YAML-shaped maps in Java tests, keeping key order. */
public final class Yml {

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    public static List<Object> list(Object... items) { return Arrays.asList(items); }

    public static Map<String, Object> abilities(Object... kv) { return map("abilities", map(kv)); }

    private Yml() {}
}

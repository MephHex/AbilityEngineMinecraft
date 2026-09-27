package me.mephisto.ability_engine.engine.graph;

/** Standard output port names. Nodes may also declare their own (e.g. switch cases). */
public final class Ports {
    public static final String OUT = "out";
    public static final String HIT = "hit";
    public static final String MISS = "miss";
    public static final String SPAWNED = "spawned";
    public static final String HIT_ENTITY = "hit_entity";
    public static final String HIT_BLOCK = "hit_block";
    public static final String EXPIRED = "expired";
    public static final String DEFAULT = "default";
    public static final String RECAST = "recast";
    public static final String TIMEOUT = "timeout";
    public static final String GONE = "gone";
    public static final String FUSE = "fuse";
    public static final String STRUCK = "struck";
    public static final String BROKEN = "broken";
    public static final String TRIGGERED = "triggered";
    public static final String TRIGGER = "trigger";
    // Not yes/no: YAML 1.1 reads unquoted yes/no keys as booleans.
    public static final String HAS = "has";
    public static final String LACKS = "lacks";
    public static final String INSIDE = "inside";
    public static final String OUTSIDE = "outside";
    public static final String EMPTY = "empty";
    public static final String FULL = "full";

    private Ports() {}
}

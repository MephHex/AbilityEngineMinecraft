package me.mephisto.ability_engine.engine.status;

import me.mephisto.ability_engine.engine.platform.TaskHandle;

import java.util.UUID;

/** A status currently on an entity. Mutable, owned by StatusManager. */
public final class ActiveStatus {
    final StatusDef def;
    final UUID source;
    long expiresAt; // Long.MAX_VALUE = infinite
    int stacks = 1;
    TaskHandle expiryTask;
    TaskHandle tickTask;
    me.mephisto.ability_engine.engine.platform.CueHandle cue; // its looping cue, if it has one

    ActiveStatus(StatusDef def, UUID source, long expiresAt) {
        this.def = def;
        this.source = source;
        this.expiresAt = expiresAt;
    }

    public StatusDef def() { return def; }
    public UUID source() { return source; }
    public long expiresAt() { return expiresAt; }
    public int stacks() { return stacks; }
    public boolean isInfinite() { return expiresAt == Long.MAX_VALUE; }
}

package me.mephisto.ability_engine.engine.tag;

import java.util.UUID;

/** Fired only on transitions: first grant (0 -> 1) and last revoke (1 -> 0). */
public interface TagListener {
    void onTagAdded(UUID entity, String tag);
    void onTagRemoved(UUID entity, String tag);
}

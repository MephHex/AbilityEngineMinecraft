package me.mephisto.ability_engine.engine.platform;

/** A running looping cue (an animation, a pose, an aura). Stopping is idempotent. */
public interface CueHandle {
    void stop();

    CueHandle NONE = () -> {};
}

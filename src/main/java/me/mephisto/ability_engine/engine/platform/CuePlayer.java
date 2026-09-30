package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.UUID;

/**
 * Port for cosmetic feedback (particles, sounds, animations). Gameplay logic never depends on cues,
 * so a missing or broken cue can't break an ability. Mirrors Unreal GAS "GameplayCues".
 */
public interface CuePlayer {

    /** One-shot: play once at a spot (a burst of particles, a sound). */
    void play(String cueId, String world, Vec3 position);

    /** Two points: something drawn between them (a beam, a tether, a lightning arc). Default: nothing. */
    default void playLine(String cueId, String world, Vec3 from, Vec3 to) {}

    /**
     * Looping: start something on an entity (a pose, an animation, an aura) and return a handle to
     * stop it. The engine stops it when the cast that started it ends. Default: not supported.
     */
    default CueHandle start(String cueId, UUID entity) { return CueHandle.NONE; }

    // ---- for an audience: only these entities (players) see and hear it (e.g. a duel in a veil) ----------
    // Defaults: everyone does (platforms that can't restrict it).

    default void play(String cueId, String world, Vec3 position, java.util.Set<UUID> audience) {
        play(cueId, world, position);
    }

    default void playLine(String cueId, String world, Vec3 from, Vec3 to, java.util.Set<UUID> audience) {
        playLine(cueId, world, from, to);
    }

    default CueHandle start(String cueId, UUID entity, java.util.Set<UUID> audience) {
        return start(cueId, entity);
    }
}

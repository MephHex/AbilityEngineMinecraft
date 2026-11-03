package me.mephisto.ability_engine.engine.platform;

import java.util.UUID;

/**
 * Port for model animations (Model Engine on Bukkit). Cosmetic like cues: a missing model or animation does nothing,
 * so an ability never depends on it. NONE when no model plugin is installed (and in tests).
 */
public interface Animator {

    /**
     * Play {@code animation} on the entity's model(s) that have it. Returns how to stop it again (NONE when nothing
     * played: no model, or none of its models has that animation).
     */
    CueHandle play(UUID entity, String animation, Settings settings);

    /** Stop {@code animation} on the entity (eased out by its lerp_out). Nothing if it isn't playing. */
    void stop(UUID entity, String animation);

    /**
     * The animator also plays it at the entity's time scale ({@code AbilityEngine.timeScale}), following it while it
     * changes (a time slow landing midway).
     *
     * @param speed   1 = as made (with fit: times the fitted speed)
     * @param lerpIn  ticks to blend into it
     * @param lerpOut ticks to blend out of it
     * @param force   restart it if it's already playing
     * @param fit     above 0: sped up or slowed down to last exactly these ticks, however long it was made
     */
    record Settings(double speed, int lerpIn, int lerpOut, boolean force, int fit) {
        public static final Settings DEFAULT = new Settings(1, 0, 0, true, 0);

        public Settings(double speed, int lerpIn, int lerpOut, boolean force) { this(speed, lerpIn, lerpOut, force, 0); }
    }

    Animator NONE = new Animator() {
        @Override public CueHandle play(UUID entity, String animation, Settings settings) { return CueHandle.NONE; }
        @Override public void stop(UUID entity, String animation) {}
    };
}

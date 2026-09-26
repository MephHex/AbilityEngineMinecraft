package me.mephisto.ability_engine.engine.construct;

/** How a construct looks. Cosmetic only; the engine owns position, timing and hits. */
public interface ConstructVisual {
    /** Every tick while it exists. */
    void update(double progress, boolean fragile);
    /** Idempotent. */
    void remove();
}

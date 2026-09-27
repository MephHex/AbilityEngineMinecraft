package me.mephisto.ability_engine.engine.target;

/** Something an ability can act on. Replaces the old Object location/entity fields. */
public sealed interface Target permits EntityTarget, PointTarget {
    /** "entity" or "point" — handy for switch nodes. */
    String kind();
}

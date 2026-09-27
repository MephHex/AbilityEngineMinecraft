package me.mephisto.ability_engine.engine.target;

import me.mephisto.ability_engine.engine.math.Vec3;

public record PointTarget(String world, Vec3 position) implements Target {
    @Override public String kind() { return "point"; }
}

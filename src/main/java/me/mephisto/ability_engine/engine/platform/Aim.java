package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

/** Where an entity is looking from and towards. {@code direction} is a unit vector. */
public record Aim(String world, Vec3 eye, Vec3 direction) {}

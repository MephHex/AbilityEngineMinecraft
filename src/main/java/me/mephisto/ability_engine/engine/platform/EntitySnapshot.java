package me.mephisto.ability_engine.engine.platform;

import me.mephisto.ability_engine.engine.math.Vec3;

import java.util.UUID;

/** A living entity found by a world query, with the center of its hitbox. */
public record EntitySnapshot(UUID id, Vec3 center) {}

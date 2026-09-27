package me.mephisto.ability_engine.engine.target;

import java.util.UUID;

public record EntityTarget(UUID id) implements Target {
    @Override public String kind() { return "entity"; }
}

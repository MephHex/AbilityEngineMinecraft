package me.mephisto.ability_engine.engine.graph;

/** A typed blackboard key. Reading a key checks the stored value's type and fails loudly. */
public record Key<T>(String name, Class<T> type) {

    public T cast(Object value) {
        if (value == null) return null;
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Blackboard key '" + name + "' holds "
                    + value.getClass().getSimpleName() + ", expected " + type.getSimpleName());
        }
        return type.cast(value);
    }
}

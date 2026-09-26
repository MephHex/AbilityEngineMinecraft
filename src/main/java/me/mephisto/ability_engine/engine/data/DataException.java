package me.mephisto.ability_engine.engine.data;

/** Bad ability data. The message always includes the path, e.g. "abilities.blast.nodes.aim.range". */
public class DataException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataException(String message) { super(message); }
}

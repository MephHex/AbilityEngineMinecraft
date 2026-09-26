package me.mephisto.ability_engine.engine.data;

import java.util.ArrayList;
import java.util.List;

public final class LoadReport {
    private int abilities;
    private int statuses;
    private int characters;
    private final List<String> errors = new ArrayList<>();
    private String source;

    /** Where the content was read from (shown by /ae reload). */
    public void setSource(String source) { this.source = source; }
    public String source() { return source; }

    void ability() { abilities++; }
    void status() { statuses++; }
    void character() { characters++; }
    public void error(String message) { errors.add(message); }

    public int abilities() { return abilities; }
    public int statuses() { return statuses; }
    public int characters() { return characters; }
    public List<String> errors() { return List.copyOf(errors); }
    public boolean isClean() { return errors.isEmpty(); }

    @Override
    public String toString() {
        return abilities + " abilities, " + statuses + " statuses, " + characters + " characters, " + errors.size() + " error(s)";
    }
}

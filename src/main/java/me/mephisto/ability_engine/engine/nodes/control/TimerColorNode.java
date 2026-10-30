package me.mephisto.ability_engine.engine.nodes.control;

import me.mephisto.ability_engine.engine.graph.ExecutionContext;
import me.mephisto.ability_engine.engine.graph.GraphNode;
import me.mephisto.ability_engine.engine.graph.NodeResult;

import java.util.List;

/**
 * The cast's timer (its boss bar, from a {@code delay} with {@code boss_bar: true}) turns {@code color}: pink, blue, red,
 * green, yellow, purple or white. {@code text: ["#RRGGBB", ...]}: its title's letters in those colours in turn (e.g. a
 * room's checkered tiles). Before the timer starts or after, it's what it starts with. E.g. Trick Room's bar is purple
 * (magenta and pink letters) while the room is the Fae's, blue (light blue and white) once it's turned against her enemies.
 */
public final class TimerColorNode implements GraphNode {

    public static final java.util.Set<String> COLORS = java.util.Set.of("pink", "blue", "red", "green", "yellow", "purple", "white");

    private final String color;      // null = leave it
    private final List<String> text; // null = leave it

    public TimerColorNode(String color, List<String> text) {
        this.color = color;
        this.text = text;
    }

    @Override
    public NodeResult execute(ExecutionContext ctx) {
        if (color != null) ctx.instance().setTimerColor(color);
        if (text != null) ctx.instance().setTimerTextColors(text);
        return NodeResult.NEXT;
    }
}

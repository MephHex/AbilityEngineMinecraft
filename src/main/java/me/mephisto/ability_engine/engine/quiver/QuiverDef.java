package me.mephisto.ability_engine.engine.quiver;

import java.util.Map;

/**
 * A character's bolt queue, declared in YAML:
 * <pre>
 * quiver:
 *   size: 3            # queued bolts (the loaded one is extra)
 *   hotbar: 7          # shown in slots 7, 8, 9: the leftmost is the next to load
 *   reload_speed: { stacks_of: hunters_rhythm, max: 3, while: { state.overdrive: 4 } }
 * </pre>
 *
 * @param hotbarSlot  1-9: first hotbar slot the queue is drawn in (0 = not shown)
 * @param reloadSpeed how much faster than normal the weapon reloads; null = never faster
 */
public record QuiverDef(int size, int hotbarSlot, ReloadSpeed reloadSpeed) {

    /**
     * Reload speed level, 0 = normal. On Bukkit each level is one level of Quick Charge on the crossbow
     * (vanilla: 1.25s to draw, 0.25s faster per level).
     *
     * @param stacksOf  +1 level per stack of this status on the owner (null = none)
     * @param first     the level the first stack gives (default 1): with 2, stacks 1/2/3 give 2/3/4
     * @param max       cap for the level from stacks
     * @param whileTags while the owner has one of these tags, at least this level (the highest applies)
     */
    public record ReloadSpeed(String stacksOf, int first, int max, Map<String, Integer> whileTags) {
        public ReloadSpeed {
            whileTags = Map.copyOf(whileTags);
        }
    }
}

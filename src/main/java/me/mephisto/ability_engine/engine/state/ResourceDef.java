package me.mephisto.ability_engine.engine.state;

/**
 * A resource pool's rules, declared per character in YAML:
 * {@code resources: { focus: { max: 100, regen: 12, delay: 20, hotbar: 9, icon: LAPIS_LAZULI } }}
 *
 * @param regenPerSecond how fast it refills
 * @param delayTicks     no regen until this long after the last spend
 * @param hotbarSlot     1-9: show it as an item whose stack size is the amount (0 = not shown)
 * @param icon           platform item id for that item
 * @param reloadTicks    ammo: once it's empty (below 1), it refills to max this long after the last spend
 *                       (0 = never)
 * @param shownWhile     only shown on the hotbar while the owner has this tag (null = always), e.g. the
 *                       ammo of the weapon that's out
 * @param hiddenWhile    not shown while the owner has this tag (null = never hidden)
 */
public record ResourceDef(String id, double max, double regenPerSecond, int delayTicks, int hotbarSlot, String icon,
                          int reloadTicks, String shownWhile, String hiddenWhile) {

    public ResourceDef(String id, double max, double regenPerSecond, int delayTicks, int hotbarSlot, String icon) {
        this(id, max, regenPerSecond, delayTicks, hotbarSlot, icon, 0, null, null);
    }
}

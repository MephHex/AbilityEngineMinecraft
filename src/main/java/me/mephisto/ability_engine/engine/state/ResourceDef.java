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
 * @param refillSweep    its hotbar item shows the next unit coming back (a cooldown sweep)
 * @param onWeapon       its amount is the weapon's stack size (e.g. ammo on the gun), instead of an item of its own
 * @param start          how much a freshly picked character has (below 0: full)
 * @param expireTicks    above 0: all of it is lost this long after the last time any was gained (ammo that can't be
 *                       stockpiled before a fight); every gain starts the time over
 * @param expireStatus   with expireTicks: a status kept on the owner for that time from every gain (e.g. to show the
 *                       time left on the XP bar), taken off when it runs out or they spend the last of it (null = none)
 */
public record ResourceDef(String id, double max, double regenPerSecond, int delayTicks, int hotbarSlot, String icon,
                          int reloadTicks, String shownWhile, String hiddenWhile, boolean refillSweep, boolean onWeapon,
                          double start, int expireTicks, String expireStatus) {

    /** Never expiring. */
    public ResourceDef(String id, double max, double regenPerSecond, int delayTicks, int hotbarSlot, String icon,
                       int reloadTicks, String shownWhile, String hiddenWhile, boolean refillSweep, boolean onWeapon,
                       double start) {
        this(id, max, regenPerSecond, delayTicks, hotbarSlot, icon, reloadTicks, shownWhile, hiddenWhile, refillSweep, onWeapon,
                start, 0, null);
    }

    /** Starting full. */
    public ResourceDef(String id, double max, double regenPerSecond, int delayTicks, int hotbarSlot, String icon,
                       int reloadTicks, String shownWhile, String hiddenWhile, boolean refillSweep, boolean onWeapon) {
        this(id, max, regenPerSecond, delayTicks, hotbarSlot, icon, reloadTicks, shownWhile, hiddenWhile, refillSweep, onWeapon, -1);
    }

    public ResourceDef(String id, double max, double regenPerSecond, int delayTicks, int hotbarSlot, String icon,
                       int reloadTicks, String shownWhile, String hiddenWhile, boolean refillSweep) {
        this(id, max, regenPerSecond, delayTicks, hotbarSlot, icon, reloadTicks, shownWhile, hiddenWhile, refillSweep, false);
    }

    /** Without a refill sweep (its hotbar item shows no cooldown sweep for the next unit coming back). */
    public ResourceDef(String id, double max, double regenPerSecond, int delayTicks, int hotbarSlot, String icon,
                       int reloadTicks, String shownWhile, String hiddenWhile) {
        this(id, max, regenPerSecond, delayTicks, hotbarSlot, icon, reloadTicks, shownWhile, hiddenWhile, false);
    }

    public ResourceDef(String id, double max, double regenPerSecond, int delayTicks, int hotbarSlot, String icon) {
        this(id, max, regenPerSecond, delayTicks, hotbarSlot, icon, 0, null, null);
    }
}

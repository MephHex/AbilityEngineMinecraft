package me.mephisto.ability_engine.engine.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Critical strikes, rolled like Dota 2 / League of Legends do it: not a flat chance (a pseudo-random distribution).
 * The first attack after a crit has a small chance, {@code C}; every attack that doesn't crit raises it by {@code C}
 * again ({@code C x N} on the N-th attack since the last crit), and a crit starts it over. {@code C} is worked out from
 * the stated chance so that, over many attacks, the share that crit is still that chance (25%: C = 8.5%, so 8.5%,
 * 17%, 25.5%, 34%... and a crit is never more than 12 attacks away). Streaks of crits and long droughts are rare.
 * <p>A cast rolls once: everyone the same swing (or shot, or cast) hits gets the same result, and its on-hits and
 * extra damage go with it.
 */
public final class Crits {

    /** How much a critical strike deals (config.yml crit-damage): 1.75 = 175%. */
    public static final double DEFAULT_DAMAGE = 1.75;

    private static final class Streak {
        int attempts;          // attacks since the last crit (the next is attempts + 1)
        long lastCast = -1;    // the cast last rolled for, and what it got
        boolean lastCrit;
        boolean selfDone;      // its on_crit effects on the attacker have run
    }

    private final Random random;
    private final Map<UUID, Streak> streaks = new HashMap<>();
    private final Map<Integer, Double> constants = new HashMap<>(); // chance in 0.1% steps -> C
    private double damage = DEFAULT_DAMAGE;

    public Crits() { this(new Random()); }

    public Crits(Random random) { this.random = random; }

    public void setDamage(double damage) { this.damage = damage > 1 ? damage : DEFAULT_DAMAGE; }

    /** What a critical strike deals, before the attacker's crit_damage: 1.75 = 175%. */
    public double damage() { return damage; }

    /**
     * Does this cast crit? {@code chance} is the attacker's crit chance now (0..1). The first damage of a cast rolls
     * (and moves the streak along); the rest of the same cast gets that result.
     */
    public boolean roll(UUID attacker, long cast, double chance) {
        Streak s = streaks.computeIfAbsent(attacker, k -> new Streak());
        if (s.lastCast == cast) return s.lastCrit;
        s.lastCast = cast;
        s.selfDone = false;
        if (chance <= 0) {
            s.lastCrit = false;
            return false;
        }
        s.attempts++;
        boolean crit = chance >= 1 || random.nextDouble() < Math.min(1, constant(chance) * s.attempts);
        if (crit) s.attempts = 0;
        s.lastCrit = crit;
        return crit;
    }

    /** Did this cast of theirs crit (it's been rolled)? */
    public boolean critted(UUID attacker, long cast) {
        Streak s = streaks.get(attacker);
        return s != null && s.lastCast == cast && s.lastCrit;
    }

    /**
     * The first time this is asked for a cast that critted: true (its on_crit effects on the attacker run once per
     * cast, however many it hit); after that, false.
     */
    public boolean firstForSelf(UUID attacker, long cast) {
        Streak s = streaks.get(attacker);
        if (s == null || s.lastCast != cast || !s.lastCrit || s.selfDone) return false;
        s.selfDone = true;
        return true;
    }

    /** They're gone (left, a new character): their streak starts over. */
    public void forget(UUID attacker) { streaks.remove(attacker); }

    /** C for this chance (cached in 0.1% steps). */
    public double constant(double chance) {
        int key = (int) Math.round(Math.max(0, Math.min(1, chance)) * 1000);
        return constants.computeIfAbsent(key, k -> constantFor(k / 1000.0));
    }

    /**
     * The C whose streaks crit {@code chance} of the time: the average share of crits with C is 1 / (expected attacks
     * to a crit). Found by bisection; the share only grows with C.
     */
    public static double constantFor(double chance) {
        if (chance <= 0) return 0;
        if (chance >= 1) return 1;
        double lo = 0, hi = chance;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            if (averageChance(mid) < chance) lo = mid;
            else hi = mid;
        }
        return (lo + hi) / 2;
    }

    /** The long-run share of crits when the N-th attack since the last crit has chance min(1, c x N). */
    static double averageChance(double c) {
        double expected = 0;   // expected attacks until a crit
        double noCritYet = 1;  // chance the first N-1 attacks all missed
        int max = (int) Math.ceil(1 / c);
        for (int n = 1; n <= max; n++) {
            double p = Math.min(1, c * n);
            expected += n * noCritYet * p;
            noCritYet *= 1 - p;
        }
        return 1 / expected;
    }
}

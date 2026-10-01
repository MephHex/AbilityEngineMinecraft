package me.mephisto.ability_engine.engine.combat;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.link.LinkManager;
import me.mephisto.ability_engine.engine.status.ActiveStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a hit really deals, after statuses, tethers and armor. Every damage effect (the platform's, the tests')
 * runs its amount through here:
 * <ol>
 *   <li>x the attacker's {@code damage_dealt} statuses (Strength)</li>
 *   <li>x the victim's {@code damage_taken} statuses</li>
 *   <li>each tether on the victim takes off its share ({@code damage_taken}), and {@code redirect} of what
 *       it took off goes to the tether's owner instead; {@code mirror} of what's left ALSO hits the owner
 *       (a soul: hurting it hurts its owner)</li>
 *   <li>armor on what's left, except the share that pierces it (the max_hp part of a hit)</li>
 *   <li>a ready barrier (a ward with {@code absorb}) takes its share off, and is used up</li>
 * </ol>
 * An enemy's hit that still deals something sets off the victim's ready reflex (a ward with {@code cast}).
 * Redirected damage is final: it isn't modified again (no armor either).
 */
public final class DamageModifiers {

    /** Damage sent to someone else instead (a tether's owner). */
    public record Redirect(UUID to, double amount) {}

    public record Result(double amount, List<Redirect> redirects) {}

    public static Result apply(AbilityEngine engine, UUID attacker, UUID victim, double amount) {
        return apply(engine, attacker, victim, amount, 0);
    }

    public static Result apply(AbilityEngine engine, UUID attacker, UUID victim, double amount, boolean armored) {
        return apply(engine, attacker, victim, amount, armored ? 0 : 1);
    }

    /** @param pierceShare share of the hit (0..1) that armor doesn't reduce (a max_hp part) */
    public static Result apply(AbilityEngine engine, UUID attacker, UUID victim, double amount, double pierceShare) {
        engine.combat().hit(attacker, victim); // both are in combat now (e.g. a ward's out-of-combat timer)
        if (attacker != null && !attacker.equals(victim)) {
            for (ActiveStatus s : engine.statuses().on(attacker)) amount *= s.def().damageDealt();
        }
        for (ActiveStatus s : engine.statuses().on(victim)) amount *= s.def().damageTaken();
        amount *= farMultiplier(engine, attacker, victim);
        List<Redirect> redirects = new ArrayList<>();
        for (LinkManager.Link link : engine.links().onTarget(victim)) {
            if (link.owner().equals(attacker)) continue; // your own hits on your bonded ally aren't shielded
            double prevented = amount * (1 - link.damageTaken());
            amount -= prevented;
            if (link.redirect() > 0 && prevented > 0) redirects.add(new Redirect(link.owner(), prevented * link.redirect()));
            if (link.mirror() > 0 && amount > 0) redirects.add(new Redirect(link.owner(), amount * link.mirror()));
        }
        double share = Math.max(0, Math.min(1, pierceShare));
        amount = engine.stats().afterArmor(victim, amount * (1 - share)) + amount * share;
        if (attacker == null || !attacker.equals(victim)) amount = engine.wards().absorbHit(victim, amount);
        if (amount > 0 && attacker != null && engine.teams().enemies(attacker, victim)) engine.wards().hitTaken(victim, attacker);
        return new Result(Math.max(0, amount), redirects);
    }

    /**
     * The victim's statuses with {@code far_damage_taken} (a domain): when the attacker is farther than {@code beyond}
     * from them, the hit is multiplied. The world's hits and the victim's own are never "far".
     */
    private static double farMultiplier(AbilityEngine engine, UUID attacker, UUID victim) {
        if (attacker == null || attacker.equals(victim)) return 1;
        double m = 1;
        for (ActiveStatus s : engine.statuses().on(victim)) {
            var far = s.def().farDamage();
            if (far == null) continue;
            var from = engine.world().positionOf(new me.mephisto.ability_engine.engine.target.EntityTarget(attacker));
            var at = engine.world().positionOf(new me.mephisto.ability_engine.engine.target.EntityTarget(victim));
            if (from.isEmpty() || at.isEmpty()) continue;
            boolean outside = !from.get().world().equals(at.get().world())
                    || from.get().position().distance(at.get().position()) > far.beyond();
            if (outside) m *= far.multiplier();
        }
        return m;
    }

    private DamageModifiers() {}
}

package me.mephisto.ability_engine.engine.combat;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.link.LinkManager;
import me.mephisto.ability_engine.engine.status.ActiveStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a hit really deals, after statuses and tethers. Every damage effect (the platform's, the tests')
 * runs its amount through here:
 * <ol>
 *   <li>x the attacker's {@code damage_dealt} statuses (Strength)</li>
 *   <li>x the victim's {@code damage_taken} statuses</li>
 *   <li>each tether on the victim takes off its share ({@code damage_taken}), and {@code redirect} of what
 *       it took off goes to the tether's owner instead; {@code mirror} of what's left ALSO hits the owner
 *       (a soul: hurting it hurts its owner)</li>
 * </ol>
 * Redirected damage is final: it isn't modified again.
 */
public final class DamageModifiers {

    /** Damage sent to someone else instead (a tether's owner). */
    public record Redirect(UUID to, double amount) {}

    public record Result(double amount, List<Redirect> redirects) {}

    public static Result apply(AbilityEngine engine, UUID attacker, UUID victim, double amount) {
        if (attacker != null && !attacker.equals(victim)) {
            for (ActiveStatus s : engine.statuses().on(attacker)) amount *= s.def().damageDealt();
        }
        for (ActiveStatus s : engine.statuses().on(victim)) amount *= s.def().damageTaken();
        List<Redirect> redirects = new ArrayList<>();
        for (LinkManager.Link link : engine.links().onTarget(victim)) {
            if (link.owner().equals(attacker)) continue; // your own hits on your bonded ally aren't shielded
            double prevented = amount * (1 - link.damageTaken());
            amount -= prevented;
            if (link.redirect() > 0 && prevented > 0) redirects.add(new Redirect(link.owner(), prevented * link.redirect()));
            if (link.mirror() > 0 && amount > 0) redirects.add(new Redirect(link.owner(), amount * link.mirror()));
        }
        return new Result(Math.max(0, amount), redirects);
    }

    private DamageModifiers() {}
}

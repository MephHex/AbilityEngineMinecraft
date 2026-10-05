package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.combat.DamageModifiers;
import org.bukkit.Bukkit;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;

/**
 * Runs EVERY hit (ability damage and vanilla alike) through the engine's DamageModifiers: Strength
 * ({@code damage_dealt}), {@code damage_taken} statuses, tethers (Radiant Bond: the bonded ally takes
 * less, part of it hits the Vanguard instead) and armor (not on damage over time or % max HP hits).
 * Redirected damage is magic damage credited to the original attacker and isn't modified again.
 */
public final class DamageModifierListener implements Listener {

    private final AbilityEngine engine;
    private boolean redirecting;

    public DamageModifierListener(AbilityEngine engine) {
        this.engine = engine;
    }

    /** Vanilla healing (regeneration, a full hunger bar, potions) is reduced like ability heals (healing_taken). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(org.bukkit.event.entity.EntityRegainHealthEvent event) {
        double m = engine.stats().healingMultiplier(event.getEntity().getUniqueId());
        if (m != 1) event.setAmount(event.getAmount() * m);
    }

    /**
     * A blow that would kill them: if something saves them (on_lethal: a status's, their character's), the blow is
     * cancelled and they're left at that share of their max HP instead. Not for /kill or the void.
     */
    private void savedFromDeath(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        var cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.KILL || cause == EntityDamageEvent.DamageCause.VOID) return;
        if (event.getFinalDamage() < living.getHealth()) return;
        var saved = engine.preventDeath(living.getUniqueId());
        if (saved.isEmpty()) return;
        event.setCancelled(true);
        engine.movement().setHealthShare(living.getUniqueId(), saved.getAsDouble());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (redirecting || !(event.getEntity() instanceof LivingEntity)) return;
        Entity attacker = event.getDamageSource().getCausingEntity();
        UUID victim = event.getEntity().getUniqueId();
        // Armor applies to everything (vanilla hits and falls too) except a hit's % max HP part (damage over time).
        double pierce = me.mephisto.ability_engine.bukkit.effect.DamageEffect.pierceShare();
        var result = DamageModifiers.apply(engine, attacker != null ? attacker.getUniqueId() : null, victim,
                event.getDamage(), pierce, me.mephisto.ability_engine.bukkit.effect.DamageEffect.isAbilityDamage());
        if (result.amount() != event.getDamage()) event.setDamage(result.amount());
        savedFromDeath(event);
        if (!event.isCancelled() && event.getFinalDamage() > 0) { // hurt: on_damaged (a hit taken while channeling...)
            engine.notifyDamaged(victim, attacker != null ? attacker.getUniqueId() : null);
        }

        for (var redirect : result.redirects()) {
            if (!(Bukkit.getEntity(redirect.to()) instanceof LivingEntity owner) || owner.isDead()) continue;
            DamageSource.Builder source = DamageSource.builder(DamageType.MAGIC);
            if (attacker != null && !attacker.equals(owner)) source.withCausingEntity(attacker).withDirectEntity(attacker);
            redirecting = true;
            try {
                owner.setNoDamageTicks(0); // a hit he just took mustn't swallow this one
                owner.damage(redirect.amount(), source.build());
            } finally {
                redirecting = false;
            }
        }
    }
}

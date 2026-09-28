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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (redirecting || !(event.getEntity() instanceof LivingEntity)) return;
        Entity attacker = event.getDamageSource().getCausingEntity();
        UUID victim = event.getEntity().getUniqueId();
        // Armor applies to everything (vanilla hits and falls too) except damage over time / % max HP hits.
        boolean armored = !me.mephisto.ability_engine.bukkit.effect.DamageEffect.isIgnoringArmor();
        var result = DamageModifiers.apply(engine, attacker != null ? attacker.getUniqueId() : null, victim,
                event.getDamage(), armored);
        if (result.amount() != event.getDamage()) event.setDamage(result.amount());

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

package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.tag.Tags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * A kill made by someone confused (Confuse Ray) is the kill of whoever confused them: their kill credit, their on-kill
 * passives, and whatever another plugin gives for a kill (gold for a camp or a boss), since those all read the game's
 * killer. Done first (lowest priority), so everything after sees the new killer. The hits themselves stay the confused
 * one's (their stats, their on-hits).
 */
public final class ConfusionCredit implements Listener {

    private final AbilityEngine engine;

    public ConfusionCredit(AbilityEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(EntityDeathEvent event) {
        Entity killer = event.getEntity().getKiller();
        if (killer == null) { // a confused mob: no player killer, but the hit came from it
            var cause = event.getEntity().getLastDamageCause();
            killer = cause == null ? null : cause.getDamageSource().getCausingEntity();
        }
        if (killer == null || !engine.tags().has(killer.getUniqueId(), Tags.CONFUSED)) return;
        var source = engine.confusion().sourceOf(killer.getUniqueId());
        if (source.isEmpty() || source.get().equals(event.getEntity().getUniqueId())) return; // (their own death: no kill)
        Player credited = Bukkit.getPlayer(source.get());
        if (credited != null) event.getEntity().setKiller(credited);
    }
}

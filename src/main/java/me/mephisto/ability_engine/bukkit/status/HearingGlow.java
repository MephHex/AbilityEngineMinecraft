package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.platform.VisualEntities;
import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The hearing passive's glow (e.g. the Whisperer's "Heard in Dreams"): every enemy a player hears glows
 * for THAT player only, through walls. Minecraft has no per-viewer glow, so each heard enemy gets an
 * invisible, glowing Mannequin standing in them, shown only to the listener: an invisible glowing entity
 * draws just its outline, so the listener sees the enemy's silhouette lit up.
 */
public final class HearingGlow {

    private final AbilityEngine engine;
    private final Plugin plugin;
    /** listener -> (heard enemy -> its glowing stand-in) */
    private final Map<UUID, Map<UUID, Mannequin>> glows = new HashMap<>();

    public HearingGlow(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        for (UUID listener : List.copyOf(glows.keySet())) {
            Player p = Bukkit.getPlayer(listener);
            if (p == null || !engine.loadouts().has(listener)) clear(listener);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean hears = engine.loadouts().baseCharacterOf(id).map(c -> c.hearing() != null).orElse(false);
            if (!hears) {
                clear(id);
                continue;
            }
            List<UUID> heard = engine.hearing().heard(id);
            Map<UUID, Mannequin> mine = glows.computeIfAbsent(id, k -> new HashMap<>());
            // gone quiet (healed, left, died): their glow goes
            mine.entrySet().removeIf(e -> {
                boolean keep = heard.contains(e.getKey()) && e.getValue().isValid()
                        && Bukkit.getEntity(e.getKey()) instanceof LivingEntity l && l.isValid() && !l.isDead();
                if (!keep && e.getValue().isValid()) e.getValue().remove();
                return !keep;
            });
            for (UUID enemy : heard) {
                if (!(Bukkit.getEntity(enemy) instanceof LivingEntity target) || !target.isValid()) continue;
                Mannequin glow = mine.get(enemy);
                if (glow == null) {
                    glow = spawn(p, target);
                    mine.put(enemy, glow);
                } else {
                    Location at = target.getLocation();
                    if (!at.getWorld().equals(glow.getWorld())) {
                        glow.remove();
                        mine.put(enemy, spawn(p, target));
                    } else {
                        glow.teleport(at);
                    }
                }
            }
        }
    }

    private Mannequin spawn(Player listener, LivingEntity target) {
        return target.getWorld().spawn(target.getLocation(), Mannequin.class, m -> {
            m.setVisibleByDefault(false);        // nobody sees it...
            m.setInvisible(true);                // ...and even the listener sees only the glowing outline
            m.setGlowing(true);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setGravity(false);
            m.setCollidable(false);
            m.setPersistent(false);
            VisualEntities.mark(m);              // abilities ignore it
            listener.showEntity(plugin, m);      // ...except the listener
        });
    }

    /** Remove a listener's glows (character changed, left). */
    public void clear(UUID listener) {
        Map<UUID, Mannequin> mine = glows.remove(listener);
        if (mine == null) return;
        for (Entity e : mine.values()) {
            if (e.isValid()) e.remove();
        }
    }
}

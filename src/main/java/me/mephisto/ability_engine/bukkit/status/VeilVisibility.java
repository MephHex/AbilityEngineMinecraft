package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.tag.Tags;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Duels in a veil (Into the Veil) on Bukkit: the two duelists only see each other. Every other living thing
 * near them (players, mannequins, mobs, others' summons) is hidden from them, and they (with their own
 * summons) are hidden from every other player, for as long as the veil lasts. Only the two
 * of them see and hear its ambience (dark motes at the edge of their sight, souls, whispers), and their
 * abilities' visuals (see AudienceWorld). Everyone else sees only two drifting motes where they are: red for
 * the one who cast it, green for the one pulled in. Vanilla hits across the veil (a mob, a bow) are
 * cancelled; the engine already keeps abilities apart.
 */
public final class VeilVisibility implements Listener {

    private final AbilityEngine engine;
    private final Plugin plugin;
    /** viewer -> players this veil hid from them (so only those are shown again) */
    private final Map<UUID, Set<UUID>> hidden = new HashMap<>();
    private long ticks;

    public VeilVisibility(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 2);
    }

    /** How far around a duel other entities are hidden from the duelists (and the duel from others). */
    private static final double REACH = 64;

    private void tick() {
        ticks += 2;
        var veils = engine.veils();
        var online = Bukkit.getOnlinePlayers();
        // Everything that could need hiding: whoever is in a veil, and every living thing near one
        // (players, mannequins, mobs, summons). Ability visuals keep their own visibility.
        Map<UUID, Entity> candidates = new HashMap<>();
        for (UUID id : veils.veiled()) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) continue;
            candidates.put(id, e);
            for (Entity near : e.getNearbyEntities(REACH, REACH, REACH)) {
                if (near instanceof org.bukkit.entity.LivingEntity && !me.mephisto.ability_engine.bukkit.platform.VisualEntities.isVisual(near)) {
                    candidates.put(near.getUniqueId(), near);
                }
            }
        }
        for (Player viewer : online) {
            UUID v = viewer.getUniqueId();
            Set<UUID> mine = hidden.computeIfAbsent(v, k -> new HashSet<>());
            for (Entity other : candidates.values()) {
                if (other.getUniqueId().equals(v)) continue;
                // across a veil (summons count as their owner's): a duelist and anyone else, either way round
                boolean hide = veils.blocks(v, other.getUniqueId());
                if (hide && (mine.add(other.getUniqueId()) || viewer.canSee(other))) {
                    viewer.hideEntity(plugin, other); // (again, if something else showed them meanwhile)
                }
            }
            // show again what's no longer across a veil (or is gone)
            for (UUID id : List.copyOf(mine)) {
                Entity other = Bukkit.getEntity(id);
                if (other != null && other.isValid() && veils.blocks(v, id)) continue;
                mine.remove(id);
                // (someone hidden by an ability, e.g. state.hidden, stays hidden)
                if (other != null && other.isValid() && !engine.tags().has(id, Tags.HIDDEN)) viewer.showEntity(plugin, other);
            }
            if (veils.isVeiled(v) && ticks % 10 == 0) ambience(viewer);
        }
        hidden.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        markers(online);
    }

    private static final Particle.DustOptions CASTER = new Particle.DustOptions(Color.fromRGB(220, 30, 40), 1.6f);
    private static final Particle.DustOptions PULLED = new Particle.DustOptions(Color.fromRGB(60, 220, 110), 1.6f);

    /** What outsiders see of a duel: a red mote (the caster) and a green one (the one pulled in). */
    private void markers(java.util.Collection<? extends Player> online) {
        var veils = engine.veils();
        for (UUID id : veils.veiled()) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) continue;
            Location at = e.getLocation().add(0, e.getHeight() * 0.6, 0);
            Particle.DustOptions color = veils.isInitiator(id) ? CASTER : PULLED;
            UUID partner = veils.partnerOf(id).orElse(null);
            for (Player viewer : online) {
                UUID v = viewer.getUniqueId();
                if (v.equals(id) || v.equals(partner) || !viewer.getWorld().equals(at.getWorld())) continue;
                viewer.spawnParticle(Particle.DUST, at, 3, 0.1, 0.15, 0.1, 0, color);
            }
        }
    }

    /** Only the duelist sees and hears it. */
    private void ambience(Player p) {
        Location at = p.getLocation();
        var fog = new Particle.DustOptions(Color.fromRGB(15, 5, 25), 2.2f);
        for (int i = 0; i < 24; i++) { // a dark ring at the edge of sight
            double a = Math.PI * 2 * i / 24 + ticks * 0.02;
            p.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * 9, at.getY() + 1 + Math.sin(ticks * 0.1 + i) * 0.6,
                    at.getZ() + Math.sin(a) * 9, 2, 0.3, 0.4, 0.3, 0, fog);
        }
        p.spawnParticle(Particle.SOUL, at.clone().add(0, 1, 0), 3, 4, 1.5, 4, 0.01);
        p.spawnParticle(Particle.SCULK_SOUL, at.clone().add(0, 1, 0), 2, 5, 1.5, 5, 0.01);
        if (ticks % 40 == 0) p.playSound(at, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.6f, 0.7f);
        if (ticks % 30 == 0) p.playSound(at, Sound.ENTITY_WARDEN_AMBIENT, 0.25f, 1.6f);
    }

    /** Vanilla hits (not abilities: the engine handles those) across a veil don't land. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) damager = shooter;
        if (engine.veils().blocks(damager.getUniqueId(), e.getEntity().getUniqueId())) e.setCancelled(true);
    }
}

package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.platform.VisualEntities;
import me.mephisto.ability_engine.engine.AbilityEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A character's {@code marks:} passive (e.g. Sucker Punch): a small glyph floating over each marked enemy, seen only by
 * the player who has the passive (see MarkManager for who's marked). Each is a text display shown to that player alone,
 * moved along with the enemy every tick.
 */
public final class MarkDisplays {

    /** How far above the top of their head it floats (blocks). */
    private static final double ABOVE_HEAD = 0.45;
    /** A faint lilac: easy to see, not loud. */
    private static final TextColor COLOR = TextColor.color(0xD9B8FF);

    private final AbilityEngine engine;
    private final Plugin plugin;
    /** viewer -> (marked enemy -> its glyph) */
    private final Map<UUID, Map<UUID, TextDisplay>> shown = new HashMap<>();

    public MarkDisplays(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        for (UUID viewer : List.copyOf(shown.keySet())) {
            if (Bukkit.getPlayer(viewer) == null || engine.marks().defOf(viewer).isEmpty()) clear(viewer);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            var def = engine.marks().defOf(id);
            if (def.isEmpty()) continue;
            List<UUID> marked = engine.marks().marked(id);
            Map<UUID, TextDisplay> mine = shown.computeIfAbsent(id, k -> new HashMap<>());
            mine.entrySet().removeIf(e -> {
                boolean keep = marked.contains(e.getKey()) && e.getValue().isValid();
                if (!keep && e.getValue().isValid()) e.getValue().remove();
                return !keep;
            });
            for (UUID enemy : marked) {
                if (!(Bukkit.getEntity(enemy) instanceof LivingEntity target) || !target.isValid()) continue;
                Location at = over(target);
                TextDisplay glyph = mine.get(enemy);
                if (glyph != null && !glyph.getWorld().equals(at.getWorld())) {
                    glyph.remove();
                    glyph = null;
                }
                if (glyph == null) mine.put(enemy, spawn(p, at, def.get().glyph()));
                else glyph.teleport(at);
            }
        }
    }

    private static Location over(LivingEntity target) {
        return target.getLocation().add(0, target.getBoundingBox().getHeight() + ABOVE_HEAD, 0);
    }

    private TextDisplay spawn(Player viewer, Location at, String text) {
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setVisibleByDefault(false); // only the viewer
            t.text(Component.text(text, COLOR));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setTeleportDuration(2); // glides after them instead of jumping
            t.setPersistent(false);
            VisualEntities.mark(t);
        });
        viewer.showEntity(plugin, d);
        return d;
    }

    private void clear(UUID viewer) {
        Map<UUID, TextDisplay> mine = shown.remove(viewer);
        if (mine != null) mine.values().forEach(d -> { if (d.isValid()) d.remove(); });
    }

    /** Plugin disable. */
    public void shutdown() {
        for (UUID viewer : List.copyOf(shown.keySet())) clear(viewer);
    }
}

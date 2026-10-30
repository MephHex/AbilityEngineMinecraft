package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.platform.VisualEntities;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.tag.TagListener;
import me.mephisto.ability_engine.engine.tag.Tags;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Crowd control spelled out over the head of whoever has it ("STUNNED 1.2s"; several: one per line), for everyone but
 * them (they have their hotbar for it). Each is a text display that follows them every tick; the time left is the
 * longest of the statuses giving that tag (none shown when it doesn't come from a timed status). Nothing over someone
 * hidden or invisible: it would give them away.
 */
public final class CcLabels implements TagListener {

    /** What's shown, in this order, and in what colour. */
    private record Kind(String tag, String word, TextColor color) {}

    private static final List<Kind> KINDS = List.of(
            new Kind(Tags.STUNNED, "STUNNED", TextColor.color(0xFFD66B)),
            new Kind(Tags.FROZEN, "FROZEN", TextColor.color(0x9FE3FF)),
            new Kind(Tags.PARALYZED, "PARALYZED", TextColor.color(0xF4E04D)),
            new Kind(Tags.ROOTED, "ROOTED", TextColor.color(0x8BC34A)),
            new Kind(Tags.SILENCED, "SILENCED", TextColor.color(0x4FD6C8)),
            new Kind(Tags.DISARMED, "DISARMED", TextColor.color(0xFF7A6B)),
            new Kind(Tags.INVERTED, "TOPSY-TURVY", TextColor.color(0xFF96E6)),
            new Kind(Tags.CONFUSED, "CONFUSED", TextColor.color(0xC48BFF)));
    private static final Set<String> TAGS = Set.of(KINDS.stream().map(Kind::tag).toArray(String[]::new));

    /** A status this short (ticks) is one a floor keeps on whoever stands on it: no countdown for it. */
    private static final int HELD = 10;

    /** Above the top of their head: clear of a name tag when they have one, else just over the head. */
    private static final double OVER_NAME = 0.8, OVER_HEAD = 0.45;

    private final AbilityEngine engine;
    private final Plugin plugin;
    /** Who might have crowd control right now (from the tag events; checked again every tick). */
    private final Set<UUID> watched = new HashSet<>();
    private final Map<UUID, TextDisplay> shown = new HashMap<>();

    public CcLabels(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
    }

    public void start() {
        engine.tags().addListener(this);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    @Override
    public void onTagAdded(UUID entity, String tag) {
        if (TAGS.contains(tag)) watched.add(entity);
    }

    @Override
    public void onTagRemoved(UUID entity, String tag) {} // the tick notices

    private void tick() {
        for (UUID id : List.copyOf(watched)) {
            Component text = Bukkit.getEntity(id) instanceof LivingEntity e && e.isValid() && !e.isDead()
                    && !engine.tags().has(id, Tags.HIDDEN) && !engine.tags().has(id, Tags.INVISIBLE) ? label(id) : null;
            if (text == null) {
                drop(id);
                if (TAGS.stream().noneMatch(t -> engine.tags().has(id, t))) watched.remove(id);
                continue;
            }
            LivingEntity e = (LivingEntity) Bukkit.getEntity(id);
            Location at = over(e);
            TextDisplay d = shown.get(id);
            if (d != null && (!d.isValid() || !d.getWorld().equals(at.getWorld()))) {
                drop(id);
                d = null;
            }
            if (d == null) {
                shown.put(id, spawn(e, at, text));
            } else {
                d.teleport(at);
                if (!text.equals(d.text())) d.text(text);
            }
        }
    }

    /** Every crowd control they have, with its time left; null if none. */
    private Component label(UUID id) {
        Component out = null;
        for (Kind k : KINDS) {
            if (!engine.tags().has(id, k.tag())) continue;
            Component part = Component.text(k.word(), k.color(), TextDecoration.BOLD);
            long left = ticksLeft(id, k.tag());
            if (left > 0) part = part.append(Component.text(" " + String.format(java.util.Locale.ROOT, "%.1fs", left / 20.0),
                    NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
            out = out == null ? part : out.append(Component.newline()).append(part); // one above the other
        }
        return out;
    }

    /** The longest time left among the timed statuses giving them this tag (0: none, or not from a status). */
    private long ticksLeft(UUID id, String tag) {
        long now = engine.clock().now(), best = 0;
        for (var s : engine.statuses().on(id)) {
            if (s.isInfinite() || !s.def().grantedTags().contains(tag)) continue;
            // (kept on them while they stand somewhere, a few ticks at a time: "while inside", no countdown)
            if (s.def().defaultDurationTicks() <= HELD) continue;
            best = Math.max(best, s.expiresAt() - now);
        }
        return best;
    }

    private static Location over(LivingEntity e) {
        boolean named = e instanceof Player || e.isCustomNameVisible();
        return e.getLocation().add(0, e.getBoundingBox().getHeight() + (named ? OVER_NAME : OVER_HEAD), 0);
    }

    private TextDisplay spawn(LivingEntity holder, Location at, Component text) {
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.text(text);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setTeleportDuration(2); // glides after them instead of jumping
            t.setPersistent(false);
            VisualEntities.mark(t);
        });
        if (holder instanceof Player p) p.hideEntity(plugin, d); // not over their own head
        return d;
    }

    private void drop(UUID id) {
        TextDisplay d = shown.remove(id);
        if (d != null && d.isValid()) d.remove();
    }

    /** Plugin disable. */
    public void shutdown() {
        for (UUID id : List.copyOf(shown.keySet())) drop(id);
        watched.clear();
    }
}

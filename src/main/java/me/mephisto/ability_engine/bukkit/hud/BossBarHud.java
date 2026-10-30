package me.mephisto.ability_engine.bukkit.hud;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.AbilityInstanceRegistry;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Timers on the boss bar: a {@code delay} with {@code boss_bar: true} (e.g. an ultimate's duration)
 * shows the ability's name and a bar running out, only to the caster.
 */
public final class BossBarHud implements Listener {

    private final AbilityEngine engine;
    private final Map<UUID, BossBar> showing = new HashMap<>();
    private TaskHandle task;

    public BossBarHud(AbilityEngine engine) {
        this.engine = engine;
    }

    public void start() {
        task = engine.scheduler().every(1, 1, this::tick);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) hide(p);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Optional<AbilityInstanceRegistry.Timer> timer = engine.instances().timer(p.getUniqueId());
            if (timer.isEmpty()) {
                hide(p);
                continue;
            }
            Component name = title(timer.get().ability().display().name(), timer.get().textColors());
            if (timer.get().ticksLeft().isPresent()) { // a countdown: the seconds left too
                name = name.append(Component.text(String.format(java.util.Locale.ROOT, "  %.1fs",
                        timer.get().ticksLeft().get() / 20.0), NamedTextColor.WHITE));
            }
            if (timer.get().note() != null) { // e.g. what the ultimate has left
                name = name.append(Component.text("  ◆ " + timer.get().note(), NamedTextColor.LIGHT_PURPLE));
            }
            float left = (float) Math.max(0, Math.min(1, timer.get().left()));
            BossBar.Color color = colorOf(timer.get().color());
            BossBar bar = showing.get(p.getUniqueId());
            if (bar == null) {
                bar = BossBar.bossBar(name, left, color, BossBar.Overlay.NOTCHED_10);
                showing.put(p.getUniqueId(), bar);
                p.showBossBar(bar);
            } else {
                bar.name(name);
                bar.progress(left);
                if (bar.color() != color) bar.color(color);
            }
        }
    }

    /** The bar's title: gold, or its letters in the timer's colours in turn (spaces don't take a turn), like a room's tiles. */
    private static Component title(String text, java.util.List<String> colors) {
        if (colors.isEmpty()) return Component.text(text, NamedTextColor.GOLD);
        var out = Component.text();
        int turn = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String letter = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                out.append(Component.text(letter));
                continue;
            }
            var color = net.kyori.adventure.text.format.TextColor.fromHexString(colors.get(turn++ % colors.size()));
            out.append(Component.text(letter, color == null ? NamedTextColor.GOLD : color));
        }
        return out.build();
    }

    /** The timer's colour (timer_color), yellow by default. */
    private static BossBar.Color colorOf(String color) {
        if (color == null) return BossBar.Color.YELLOW;
        try {
            return BossBar.Color.valueOf(color.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.YELLOW;
        }
    }

    private void hide(Player p) {
        BossBar bar = showing.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        showing.remove(event.getPlayer().getUniqueId());
    }
}

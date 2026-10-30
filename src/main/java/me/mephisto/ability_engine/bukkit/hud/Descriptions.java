package me.mephisto.ability_engine.bukkit.hud;

import me.mephisto.ability_engine.engine.AbilityEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ability descriptions with their scalings filled in. A line may hold tokens, each shown in the colour of the stat it
 * scales with (the same as the inventory's stat items), followed by the exact number for this player right now:
 * <ul>
 *   <li>{@code {base:1.3}}: "130% base damage (52)", gold. The exact number counts their base damage and their damage
 *       dealt (Strength...).</li>
 *   <li>{@code {sp:0.9}}: "90% spell power (18)", blue (spell power's colour): their spell power points (0 without any),
 *       not boosted by damage dealt (an ability's {@code sp:} damage part).</li>
 *   <li>{@code {max_hp:0.1}}: "10% of their max HP", red (whoever it hits: no exact number).</li>
 *   <li>{@code {own_hp:0.2}}: "20% of your max HP (42)", red (e.g. a heal or a shield of your own).</li>
 * </ul>
 * The rest of the line is plain grey text, as before.
 */
final class Descriptions {

    private static final Pattern TOKEN = Pattern.compile("\\{(base|sp|max_hp|own_hp):([0-9]*\\.?[0-9]+)}");
    private static final TextColor TEXT = NamedTextColor.GRAY;
    private static final TextColor EXACT = NamedTextColor.WHITE;

    /** One description line for {@code player}; {@code ability}: from an ability slot (spell power counts). */
    static Component line(AbilityEngine engine, UUID player, boolean ability, String text) {
        Component out = Component.empty().decoration(TextDecoration.ITALIC, false);
        Matcher m = TOKEN.matcher(text);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) out = out.append(plain(text.substring(at, m.start()), TEXT));
            double share = Double.parseDouble(m.group(2));
            out = out.append(switch (m.group(1)) {
                case "base" -> scaling(pct(share) + " base damage", NamedTextColor.GOLD,
                        share * damageOf(engine, player, ability));
                case "sp" -> scaling(pct(share) + " spell power", NamedTextColor.BLUE,
                        share * engine.stats().spellPower(player));
                case "own_hp" -> scaling(pct(share) + " of your max HP", NamedTextColor.RED,
                        share * engine.stats().maxHealth(player));
                default -> plain(pct(share) + " of their max HP", NamedTextColor.RED);
            });
            at = m.end();
        }
        if (at < text.length()) out = out.append(plain(text.substring(at), TEXT));
        return out;
    }

    /** What 100% base damage is for them right now. */
    private static double damageOf(AbilityEngine engine, UUID player, boolean ability) {
        var stats = engine.stats();
        return stats.baseDamage(player) * stats.damageDealtMultiplier(player); // (ability or not: the same)
    }

    /**
     * The numbers their descriptions show right now, as one string: when it changes (an item, a buff) the icons are
     * drawn again.
     */
    static String numbers(AbilityEngine engine, UUID player) {
        var stats = engine.stats();
        return String.format(Locale.ROOT, "%.2f|%.4f|%.4f|%.1f", stats.baseDamage(player), stats.damageDealtMultiplier(player),
                stats.spellPower(player), stats.maxHealth(player));
    }

    private static Component scaling(String what, TextColor color, double exact) {
        return plain(what, color).append(plain(" (" + Math.round(exact) + ")", EXACT));
    }

    private static String pct(double share) {
        double p = share * 100;
        return (Math.abs(p - Math.rint(p)) < 1e-6 ? String.valueOf((long) Math.rint(p)) : String.format(Locale.ROOT, "%.1f", p)) + "%";
    }

    private static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private Descriptions() {}
}

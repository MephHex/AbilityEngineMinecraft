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
 * Ability descriptions with their scalings filled in. A line may hold tokens: the scaling in the colour of the stat it
 * scales with (the same as the inventory's stat items), then the exact number for this player right now in brackets.
 * <ul>
 *   <li>{@code {base:1.3}}: "130% base damage (52)", gold. The exact number counts their base damage and their damage
 *       dealt (Strength...).</li>
 *   <li>{@code {sp:0.9}}: "90% spell power (18)", blue: their spell power points (0 without any), not boosted by damage
 *       dealt (an ability's {@code sp:} damage part).</li>
 *   <li>{@code {bonus_hp:0.1}}: "10% bonus HP (12)", green: their max HP above their character's own.</li>
 *   <li>{@code {own_hp:0.2}}: "20% of your max HP (42)", red (e.g. a heal or a shield of your own).</li>
 *   <li>{@code {max_hp:0.1}}: "10% of their max HP", red (whoever it hits: no exact number).</li>
 *   <li>{@code {flat:90}}: just "90" (a flat amount).</li>
 *   <li>{@code {sp_per:10/50}}: "+10 per 50 spell power (+N)", blue (e.g. a heal growing with spell power).</li>
 *   <li>{@code {max_hp_sp:0.01/50}}: "+1% max HP per 50 spell power (+N%)", blue (a % max HP hit that grows).</li>
 *   <li>{@code {max_hp_bonus_dmg:0.001}}: "+0.1% max HP per bonus damage (+N%)", gold (per point of damage above their
 *       character's own: Strength, +damage items).</li>
 *   <li>{@code {crit_per:10/0.2}}: "+10 per 20% crit chance (+N)", red (e.g. a backstab's bonus).</li>
 *   <li>{@code {max_hp_dmg:0.01/10}}: "+1% max HP per 10 damage (+N%)", gold (their base damage x damage dealt).</li>
 * </ul>
 * A damage token can end in its hit's damage type: {@code {sp:1.2|magic}}. The number in brackets (a flat token's number,
 * a max_hp token's whole text) is then in that type's colour: orange physical, blue magic, dark purple true damage (the
 * only kind armor doesn't reduce). That's what the hit deals, which isn't the same as what it scales with: a physical hit
 * can scale with spell power. Without a type the number is white. The rest of the line is plain grey text.
 */
final class Descriptions {

    private static final Pattern TOKEN = Pattern.compile(
            "\\{(base|sp|max_hp|own_hp|bonus_hp|flat|sp_per|max_hp_sp|max_hp_bonus_dmg|crit_per|max_hp_dmg):([0-9]*\\.?[0-9]+)(?:/([0-9]*\\.?[0-9]+))?"
                    + "(?:\\|(physical|magic|true))?}");
    private static final TextColor TEXT = NamedTextColor.GRAY;
    private static final TextColor EXACT = NamedTextColor.WHITE;
    /** The damage types' colours. */
    static final TextColor PHYSICAL = TextColor.color(0xFF7A1F);
    static final TextColor MAGIC = NamedTextColor.BLUE;
    static final TextColor TRUE = NamedTextColor.DARK_PURPLE;
    /** Bonus HP's colour. */
    static final TextColor BONUS_HP = NamedTextColor.GREEN;

    /** One description line for {@code player}; {@code ability}: from an ability slot (spell power counts). */
    static Component line(AbilityEngine engine, UUID player, boolean ability, String text) {
        Component out = Component.empty().decoration(TextDecoration.ITALIC, false);
        Matcher m = TOKEN.matcher(text);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) out = out.append(plain(text.substring(at, m.start()), TEXT));
            double share = Double.parseDouble(m.group(2));
            double per = m.group(3) == null ? 1 : Double.parseDouble(m.group(3));
            TextColor type = typeColor(m.group(4));
            TextColor number = type != null ? type : EXACT;
            var stats = engine.stats();
            out = out.append(switch (m.group(1)) {
                case "base" -> scaling(pct(share) + " base damage", NamedTextColor.GOLD,
                        "" + Math.round(share * damageOf(engine, player, ability)), number);
                case "sp" -> scaling(pct(share) + " spell power", NamedTextColor.BLUE,
                        "" + Math.round(share * stats.spellPower(player)), number);
                case "own_hp" -> scaling(pct(share) + " of your max HP", NamedTextColor.RED,
                        "" + Math.round(share * stats.maxHealth(player)), number);
                case "bonus_hp" -> scaling(pct(share) + " bonus HP", BONUS_HP,
                        "" + Math.round(share * stats.bonusHealth(player)), number);
                case "flat" -> plain(num(share), number);
                case "sp_per" -> scaling("+" + num(share) + " per " + num(per) + " spell power", NamedTextColor.BLUE,
                        "+" + Math.round(share * stats.spellPower(player) / per), number);
                case "max_hp_sp" -> scaling("+" + pct(share) + " max HP per " + num(per) + " spell power", NamedTextColor.BLUE,
                        "+" + pct(share * stats.spellPower(player) / per), number);
                case "max_hp_bonus_dmg" -> scaling("+" + pct(share) + " max HP per bonus damage", NamedTextColor.GOLD,
                        "+" + pct(share * stats.bonusDamage(player)), number);
                case "max_hp_dmg" -> scaling("+" + pct(share) + " max HP per " + num(per) + " damage", NamedTextColor.GOLD,
                        "+" + pct(share * damageOf(engine, player, ability) / per), number);
                case "crit_per" -> scaling("+" + num(share) + " per " + pct(per) + " crit chance", NamedTextColor.RED,
                        "+" + Math.round(share * stats.critChance(player) / per), number);
                default -> plain(pct(share) + " of their max HP", type != null ? type : NamedTextColor.RED);
            });
            at = m.end();
        }
        if (at < text.length()) out = out.append(plain(text.substring(at), TEXT));
        return out;
    }

    /** physical / magic / true: its colour; null for none. */
    static TextColor typeColor(String type) {
        if (type == null) return null;
        return switch (type) {
            case "physical" -> PHYSICAL;
            case "magic" -> MAGIC;
            default -> TRUE;
        };
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

    private static Component scaling(String what, TextColor color, String exact, TextColor numberColor) {
        return plain(what, color).append(plain(" (" + exact + ")", numberColor));
    }

    /** 90 -> "90", 2.5 -> "2.5". */
    private static String num(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-6 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v);
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

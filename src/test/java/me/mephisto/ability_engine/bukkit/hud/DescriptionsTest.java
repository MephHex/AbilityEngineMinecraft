package me.mephisto.ability_engine.bukkit.hud;

import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Scalings in descriptions: the stat's colour, the exact number for the player. */
class DescriptionsTest {

    private static String text(Component c) {
        StringBuilder b = new StringBuilder();
        if (c instanceof TextComponent t) b.append(t.content());
        for (Component child : c.children()) b.append(text(child));
        return b.toString();
    }

    @Test
    void tokensBecomeTheStatInItsColourWithTheExactNumber() throws Exception {
        TestEngine t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        UUID p = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(p, "changeling_kit"); // base damage 40, 210 HP (the test-only kit: its numbers stay put)
        Component line = Descriptions.line(t.engine, p, false, "Slash: {base:1.3}, then {max_hp:0.1} and {own_hp:0.2}.");
        assertEquals("Slash: 130% base damage (52), then 10% of their max HP and 20% of your max HP (42).", text(line));
        Component scaling = line.children().get(1);
        assertEquals(NamedTextColor.GOLD, scaling.color(), "base damage: gold, like its stat item");
        assertEquals(NamedTextColor.GRAY, line.children().get(0).color(), "the rest: plain grey");
        assertEquals("A plain line.", text(Descriptions.line(t.engine, p, false, "A plain line.")));
        assertEquals("85% base damage (34)", text(Descriptions.line(t.engine, p, false, "{base:0.85}")));
        Component sp = Descriptions.line(t.engine, p, true, "{sp:0.9}");
        assertEquals("90% spell power (0)", text(sp), "no spell power: 0");
        assertEquals(NamedTextColor.BLUE, sp.children().get(0).color(), "spell power: blue, like its stat item");
        assertEquals("130% base damage (52)", text(Descriptions.line(t.engine, p, true, "{base:1.3}")),
                "an ability's base damage: the same as a basic attack's");
    }

    @Test
    void theNumberInBracketsIsInTheHitsDamageTypeColour() throws Exception {
        TestEngine t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        UUID p = t.spawn(0, 1, 0);
        t.engine.loadouts().assign(p, "changeling_kit"); // base damage 40, 210 HP, no spell power
        Component sp = Descriptions.line(t.engine, p, true, "{sp:1.2|magic}").children().get(0);
        assertEquals("120% spell power (0)", text(sp));
        assertEquals(NamedTextColor.BLUE, sp.color(), "the scaling: its stat's colour");
        assertEquals(Descriptions.MAGIC, sp.children().get(0).color(), "the number: magic damage's");
        Component phys = Descriptions.line(t.engine, p, true, "{sp:0.9|physical}").children().get(0);
        assertEquals(NamedTextColor.BLUE, phys.color(), "scales with spell power...");
        assertEquals(Descriptions.PHYSICAL, phys.children().get(0).color(), "...but deals physical damage");
        Component flat = Descriptions.line(t.engine, p, true, "{flat:90|physical}").children().get(0);
        assertEquals("90", text(flat));
        assertEquals(Descriptions.PHYSICAL, flat.color());
        Component dot = Descriptions.line(t.engine, p, true, "{max_hp:0.08|true}").children().get(0);
        assertEquals("8% of their max HP", text(dot));
        assertEquals(NamedTextColor.DARK_PURPLE, dot.color(), "true damage: dark purple");
        assertEquals(NamedTextColor.RED, Descriptions.line(t.engine, p, true, "{max_hp:0.1}").children().get(0).color(),
                "no type: as before");
        assertEquals("+1% max HP per 50 spell power (+0%)",
                text(Descriptions.line(t.engine, p, true, "{max_hp_sp:0.01/50|magic}")));
        assertEquals("+10 per 50 spell power (+0)", text(Descriptions.line(t.engine, p, true, "{sp_per:10/50}")));
        Component bonus = Descriptions.line(t.engine, p, true, "{bonus_hp:0.005|physical}").children().get(0);
        assertEquals("0.5% bonus HP (0)", text(bonus));
        assertEquals(Descriptions.BONUS_HP, bonus.color());
    }
}

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
}

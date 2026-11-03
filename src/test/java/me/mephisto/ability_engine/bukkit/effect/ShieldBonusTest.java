package me.mephisto.ability_engine.bukkit.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** shield_bonus: a shield takes (1 + bonus) times the damage; what's left once it breaks is dealt as is. */
class ShieldBonusTest {

    @Test
    void noShieldNoBonus() {
        assertEquals(100, DamageEffect.againstShield(100, 0, 0.5), 1e-9);
        assertEquals(100, DamageEffect.againstShield(100, 80, 0), 1e-9);
    }

    @Test
    void aBigShieldTakesItAllWithTheBonus() {
        assertEquals(150, DamageEffect.againstShield(100, 200, 0.5), 1e-9);
    }

    @Test
    void aSmallShieldBreaksAndTheRestIsDealtAsIs() {
        // 60 shield breaks after 40 raw damage (x1.5); the other 60 raw goes on: 60 + 60
        assertEquals(120, DamageEffect.againstShield(100, 60, 0.5), 1e-9);
    }
}

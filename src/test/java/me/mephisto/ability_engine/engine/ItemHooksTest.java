package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.combat.DamageModifiers;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.testkit.ShippedContent;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What items are made of (ItemSystem gives its items as statuses): max_health, ability_damage_taken, ability_lifesteal,
 * basic_on_hit and the chain effect. A Dragon Hunter (fixtures: 230 HP, base damage 42; his basic attack hits the
 * first enemy within 2.8 blocks) at the origin looking +x, team blue; enemies red (200 HP, no armor).
 */
class ItemHooksTest {

    private static final double BASE = 42;

    private TestEngine t;
    private UUID p;

    private void setup(Map<String, Object> statuses) throws IOException {
        t = new TestEngine();
        ShippedContent.loadClean(t.engine);
        t.load(Map.of("statuses", statuses));
        t.world.floor(0);
        p = t.spawn(0, 1, 0);
        t.world.team(p, "blue");
        t.engine.loadouts().assign(p, "dragon_hunter");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    private void give(String status) { t.engine.statuses().apply(p, status, 0, p); }

    private boolean has(UUID id, String status) { return t.engine.statuses().has(id, status); }

    @Test
    void onHitDamageGoesThroughInvulnerabilityFrames() {
        var plain = me.mephisto.ability_engine.engine.effect.EffectConfig.of("damage", Map.of("base", 0.5));
        assertTrue(plain.asOnHit().params().getBool("ignore_iframes", false),
                "it lands with the hit it rides on: the game would swallow it otherwise");
        var chosen = me.mephisto.ability_engine.engine.effect.EffectConfig.of("damage", Map.of("base", 0.5, "ignore_iframes", false));
        assertFalse(chosen.asOnHit().params().getBool("ignore_iframes", true), "unless the content says otherwise");
        var status = me.mephisto.ability_engine.engine.effect.EffectConfig.of("status", Map.of("status", "x"));
        assertEquals(status, status.asOnHit(), "only damage");
    }

    @Test
    void percentMaxHpDamageOnMobsIsCappedAtAPlayersMaxHp() throws IOException {
        setup(Map.of());
        t.load(Map.of("abilities", Map.of("venom", Map.of("nodes", Map.of("bite", Map.of("type", "apply_effects",
                "targets", Map.of("type", "radius", "radius", 20),
                "effects", List.of(Map.of("id", "damage", "max_hp", 0.1, "knockback", false))))))));
        UUID player = foe(5, 0);
        t.world.players.add(player);               // a player: never capped
        UUID boss = foe(-5, 0);                    // a mob with 1200 HP
        t.world.maxHealth.put(boss, 1200.0);
        t.world.maxHealth.put(player, 1200.0);
        assertTrue(t.engine.activator().activate(p, "venom").success());
        assertEquals(120, t.damage(player), 1e-6, "a player: 10% of their 1200");
        assertEquals(20, t.damage(boss), 1e-6, "a mob: 10% of at most 200");
    }

    @Test
    void maxHealthMultipliesTheirMaxHp() throws IOException {
        setup(Map.of("elixir", Map.of("max_health", 1.2)));
        assertEquals(230, t.engine.stats().maxHealth(p), 1e-9);
        give("elixir");
        assertEquals(230 * 1.2, t.engine.stats().maxHealth(p), 1e-9, "+20%");
    }

    @Test
    void abilityDamageTakenOnlyCutsAbilityDamage() throws IOException {
        setup(Map.of("nullmail", Map.of("ability_damage_taken", 0.8)));
        UUID enemy = foe(5, 0);
        give("nullmail");
        assertEquals(80, DamageModifiers.apply(t.engine, enemy, p, 100, 1, true).amount(), 1e-9, "an ability: 20% less");
        assertEquals(100, DamageModifiers.apply(t.engine, enemy, p, 100, 1, false).amount(), 1e-9, "a vanilla hit: all of it");
    }

    @Test
    void abilityLifestealHealsAShareOfAbilityDamageNotFire() throws IOException {
        setup(Map.of("vamp", Map.of("ability_lifesteal", 0.3)));
        UUID enemy = foe(2, 0);
        give("vamp");
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        assertEquals(BASE, t.damage(enemy), 1e-6);
        assertEquals(0, t.lifesteal.getOrDefault(p, 0.0), 1e-9, "primary fire: nothing back");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success()); // Venom Dagger: an ability
        assertEquals(BASE * 0.6 * 0.3, t.lifesteal.getOrDefault(p, 0.0), 1e-6, "an ability: 30% of it back");
    }

    @Test
    void abilityDamageTakenCutsAbilitiesNotFire() throws IOException {
        setup(Map.of("nullmail", Map.of("ability_damage_taken", 0.8)));
        UUID enemy = foe(2, 0);
        t.engine.statuses().apply(enemy, "nullmail", 0, enemy); // the enemy wears it
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        assertEquals(BASE, t.damage(enemy), 1e-6, "primary fire: all of it");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        assertEquals(BASE + BASE * 0.6 * 0.8, t.damage(enemy), 1e-6, "an ability: 20% less");
    }

    @Test
    void abilityOnHitOnlyLandsWithAbilities() throws IOException {
        setup(Map.of(
                "wounded", Map.of("duration", 60, "healing_taken", 0.6),
                "vamp", Map.of("ability_on_hit", List.of(Map.of("id", "status", "status", "wounded")))));
        UUID enemy = foe(2, 0);
        give("vamp");
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        assertFalse(has(enemy, "wounded"), "primary fire: no");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success());
        assertTrue(has(enemy, "wounded"), "an ability: yes");
    }

    @Test
    void basicOnHitOnlyLandsWithBasicAttacksAndOnceIsUsedUp() throws IOException {
        setup(Map.of(
                "dazed", Map.of("duration", 10, "move_speed", 0.3),
                "talon", Map.of("once", true, "basic_on_hit", List.of(
                        Map.of("id", "damage", "base", 0.5, "knockback", false),
                        Map.of("id", "status", "status", "dazed")))));
        UUID enemy = foe(2, 0);
        give("talon");
        assertTrue(t.engine.loadouts().activate(p, Slots.ABILITY_3).success()); // Venom Dagger: not a basic attack
        assertTrue(has(p, "talon"), "an ability doesn't use it up");
        assertFalse(has(enemy, "dazed"));
        double before = t.damage(enemy);

        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success()); // a basic attack
        assertEquals(before + BASE + BASE * 0.5, t.damage(enemy), 1e-6, "the swing + 50% base damage");
        assertTrue(has(enemy, "dazed"), "and slowed");
        assertFalse(has(p, "talon"), "once: used up");
    }

    @Test
    void aChainJumpsToNearbyEnemiesWithTheOnHits() throws IOException {
        setup(Map.of(
                "wounded", Map.of("duration", 60, "healing_taken", 0.6),
                "vamp", Map.of("on_hit", List.of(Map.of("id", "status", "status", "wounded"))),
                "shiv", Map.of("basic_on_hit", List.of(Map.of("id", "chain", "radius", 5, "max", 2, "base", 0.3)))));
        UUID main = foe(2, 0);
        UUID near1 = foe(4, 1);
        UUID near2 = foe(4, -1);
        UUID near3 = foe(5.5, 0);   // a third one in range: only 2 jumps
        UUID far = foe(12, 0);
        UUID friend = t.spawn(3, 1, 0);
        t.world.team(friend, "blue");
        give("vamp");
        give("shiv");
        assertTrue(t.engine.loadouts().activate(p, Slots.PRIMARY).success());
        assertEquals(BASE, t.damage(main), 1e-6);
        assertEquals(BASE * 0.3, t.damage(near1), 1e-6, "the chain: 30% base damage");
        assertEquals(BASE * 0.3, t.damage(near2), 1e-6, "the nearest two");
        assertEquals(0, t.damage(near3), 1e-9, "only 2 jumps");
        assertEquals(0, t.damage(far), 1e-9, "out of range");
        assertEquals(0, t.damage(friend), 1e-9, "never an ally");
        assertTrue(has(near1, "wounded") && has(near2, "wounded"), "with the on-hits");
    }
}

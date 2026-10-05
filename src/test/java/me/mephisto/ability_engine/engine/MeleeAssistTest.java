package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.target.ClickAssist;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A swing the player's game saw land counts on the server too (within the swing's range + 1 block), and primary-fire
 * projectiles are wider against entities. The fighter at the origin looking +x, team blue.
 */
class MeleeAssistTest {

    private TestEngine t;
    private UUID me;

    private static Map<String, Object> swing(Map<String, Object> query) {
        return map("nodes", map(
                "aim", map("type", "acquire_target", "query", query, "on", map("hit", "hit")),
                "hit", map("type", "apply_effects", "targets", map("type", "key", "key", "target"),
                        "effects", list(map("id", "damage", "amount", 10)))));
    }

    private static Map<String, Object> area(Map<String, Object> query) {
        return map("nodes", map("hit", map("type", "apply_effects", "targets", query,
                "effects", list(map("id", "damage", "amount", 10)))));
    }

    private void setup(String primary) {
        t = new TestEngine();
        t.load(map(
                "abilities", map(
                        "rake", swing(map("type", "hitscan", "range", 3)),
                        "slash", area(map("type", "cone", "range", 3, "angle", 60)),
                        "poke", area(map("type", "line", "range", 3, "width", 1)),
                        "bolt", map("nodes", map(
                                "fire", map("type", "projectile", "speed", 1.0, "size", 0.4, "range", 20,
                                        "on", map("hit_entity", "hit")),
                                "hit", map("type", "apply_effects", "targets", map("type", "key", "key", "hit"),
                                        "effects", list(map("id", "damage", "amount", 10)))))),
                "characters", map("fighter", map("slots", map("primary", primary)))));
        me = t.spawn(0, 1, 0);
        t.world.team(me, "blue");
        t.engine.loadouts().assign(me, "fighter");
    }

    private UUID foe(double x, double z) {
        UUID e = t.spawn(x, 1, z);
        t.world.team(e, "red");
        return e;
    }

    /** LMB: the primary, as the player's game reported a hit on {@code clicked} (null: a swing at the air). */
    private void click(UUID clicked) {
        Map<String, Object> extra = clicked == null ? Map.of() : Map.of(ClickAssist.KEY, new EntityTarget(clicked));
        assertTrue(t.engine.loadouts().activate(me, Slots.PRIMARY, true, extra).success());
        t.engine.cooldowns().clearAll(me);
    }

    @Test
    void aHitscanSwingCountsWhatThePlayerHitWithinReach() {
        setup("rake");
        UUID side = foe(2.5, 1.2);              // off the ray (the server's aim): missed
        click(null);
        assertEquals(0, t.damage(side), 1e-9);
        click(side);                            // their game saw it land
        assertEquals(10, t.damage(side), 1e-9);

        UUID far = foe(4.5, 0.6);               // past range + 1
        click(far);
        assertEquals(0, t.damage(far), 1e-9, "too far even with the assist");
    }

    @Test
    void aHitscanSwingNeverHitsAClickedAlly() {
        setup("rake");
        UUID friend = t.spawn(2.5, 1, 1.2);
        t.world.team(friend, "blue");
        click(friend);
        assertEquals(0, t.damage(friend), 1e-9);
    }

    @Test
    void coneAndLineSwingsCountItTooJustPastTheirRange() {
        setup("slash");
        UUID edge = foe(3.6, 0.2);              // half a block past the cone's 3
        click(null);
        assertEquals(0, t.damage(edge), 1e-9);
        click(edge);
        assertEquals(10, t.damage(edge), 1e-9);

        setup("poke");
        UUID beside = foe(3.5, 0.3);
        click(beside);
        assertEquals(10, t.damage(beside), 1e-9);
    }

    @Test
    void primaryFireProjectilesAreWiderAgainstEntities() {
        setup("bolt");
        UUID near = foe(8, 0.75);               // the bolt's own reach (0.2 + a body's 0.4) is 0.6: it flies past
        click(null);
        t.time.advance(30);
        assertEquals(0, t.damage(near), 1e-9);

        t.engine.setPrimaryHitbox(0.25);        // config.yml primary-projectile-hitbox
        click(null);
        t.time.advance(30);
        assertEquals(10, t.damage(near), 1e-9, "0.85 now");
    }
}

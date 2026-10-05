package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.construct.Strike;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.testkit.TestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static me.mephisto.ability_engine.engine.testkit.Yml.list;
import static me.mephisto.ability_engine.engine.testkit.Yml.map;
import static org.junit.jupiter.api.Assertions.*;

/** Allies (same vanilla /team) are never affected by, hit by, or able to trigger your abilities. */
class TeamTest {

    private static Map<String, Object> hurt(Object targets, String affects) {
        Map<String, Object> node = map("type", "apply_effects", "targets", targets,
                "effects", list(map("id", "damage", "amount", 10)));
        if (affects != null) node.put("affects", affects);
        return map("nodes", map("hit", node));
    }

    private static Map<String, Object> file() {
        return map(
                "abilities", map(
                        "nova", hurt(map("type", "radius", "radius", 7), null),
                        "bless", hurt(map("type", "radius", "radius", 7), "allies"),
                        "self", hurt(map("type", "self"), null),
                        "beam", map("nodes", map(
                                "aim", map("type", "acquire_target", "query", map("type", "hitscan", "range", 20),
                                        "on", map("hit", "hit")),
                                "hit", map("type", "apply_effects", "targets", map("type", "key", "key", "target"),
                                        "effects", list(map("id", "damage", "amount", 10))))),
                        "shot", map("nodes", map(
                                "fire", map("type", "projectile", "speed", 1.0, "size", 0.5, "on", map("hit_entity", "hit")),
                                "hit", map("type", "apply_effects", "targets", map("type", "key", "key", "hit"),
                                        "effects", list(map("id", "damage", "amount", 10))))),
                        "punch", hurt(map("type", "key", "key", "target"), null)),
                "characters", map("brawler", map("slots", map("primary", "shot", "melee", "punch"))));
    }

    private TestEngine t;
    private UUID caster, ally, enemy, mob;

    private void setup() {
        t = new TestEngine();
        t.load(file());
        caster = t.spawn(0, 1, 0);
        ally = t.spawn(3, 1, 0);
        enemy = t.spawn(6, 1, 0);
        mob = t.spawn(0, 1, 3);
        t.world.team(caster, "red");
        t.world.team(ally, "red");
        t.world.team(enemy, "blue");
        // mob: no team -> enemy of everyone
    }

    @Test
    void areaEffectsSkipAllies() {
        setup();
        t.engine.activator().activate(caster, "nova");
        assertEquals(0.0, t.damage(ally), 1e-9);
        assertEquals(10.0, t.damage(enemy), 1e-9);
        assertEquals(10.0, t.damage(mob), 1e-9, "no team = enemy");
    }

    @Test
    void affectsAlliesIsTheOptInForBuffs() {
        setup();
        t.engine.activator().activate(caster, "bless");
        assertEquals(10.0, t.damage(ally), 1e-9);
        assertEquals(0.0, t.damage(enemy), 1e-9);
        assertEquals(0.0, t.damage(mob), 1e-9);
    }

    @Test
    void selfTargetingStillWorks() {
        setup();
        t.engine.activator().activate(caster, "self");
        assertEquals(10.0, t.damage(caster), 1e-9);
    }

    @Test
    void projectilesFlyThroughAllies() {
        setup();
        t.engine.activator().activate(caster, "shot");
        t.time.advance(15);
        assertEquals(0.0, t.damage(ally), 1e-9);
        assertEquals(10.0, t.damage(enemy), 1e-9, "hit the enemy standing behind the ally");
    }

    @Test
    void hitscansPassThroughAllies() {
        setup();
        t.engine.activator().activate(caster, "beam");
        assertEquals(0.0, t.damage(ally), 1e-9);
        assertEquals(10.0, t.damage(enemy), 1e-9);
    }

    @Test
    void withoutTeamsEveryoneElseIsAnEnemy() {
        setup();
        t.world.team(caster, null);
        t.world.team(ally, null);
        t.engine.activator().activate(caster, "nova");
        assertEquals(10.0, t.damage(ally), 1e-9);
    }

    @Test
    void meleeHitsExactlyTheClickedEntityButNeverAllies() {
        setup();
        t.engine.loadouts().assign(caster, "brawler");
        assertTrue(t.engine.loadouts().activateOn(caster, Slots.MELEE, new EntityTarget(enemy)).success());
        assertEquals(10.0, t.damage(enemy), 1e-9);
        assertEquals(0.0, t.damage(mob), 1e-9, "only the clicked one");

        t.engine.loadouts().activateOn(caster, Slots.MELEE, new EntityTarget(ally));
        assertEquals(0.0, t.damage(ally), 1e-9, "clicking an ally does nothing");
    }

    // ---- constructs -----------------------------------------------------------------------------

    private void shippedWithBinding() throws IOException {
        t = new TestEngine();
        me.mephisto.ability_engine.engine.testkit.ShippedContent.loadClean(t.engine);
        t.world.floor(0);
        caster = t.spawn(0, 1, 0);
        t.world.team(caster, "red");
        t.world.look(caster, new Vec3(1, -0.2, 0));
        t.engine.activator().activate(caster, "unstable_binding");
        assertTrue(t.engine.targeting().confirm(caster).success());
        assertEquals(1, t.engine.constructs().activeCount());
    }

    @Test
    void alliesCantBreakOrTriggerYourBinding() throws IOException {
        shippedWithBinding();
        ally = t.spawn(10, 1, 0);
        t.world.team(ally, "red");
        var binding = t.engine.constructs().all().get(0);

        assertFalse(t.engine.constructs().strike(binding, Strike.melee(ally)), "ally punch: nothing");
        t.world.look(ally, new Vec3(-1, 0, 0));
        t.engine.activator().activate(ally, "arcane_bolt"); // flies through it
        t.time.advance(5);
        assertEquals(1, t.engine.constructs().activeCount(), "still standing");
    }

    @Test
    void enemiesCanBreakItWhileFragile() throws IOException {
        shippedWithBinding();
        enemy = t.spawn(4.7, 1, 1.5);
        t.world.team(enemy, "blue");
        assertTrue(t.engine.constructs().strike(t.engine.constructs().all().get(0), Strike.melee(enemy)));
        assertEquals(0, t.engine.constructs().activeCount());
    }
}

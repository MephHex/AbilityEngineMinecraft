package me.mephisto.ability_engine.engine.combat;

import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.platform.CuePlayer;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.target.EntityTarget;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spell shields (the spell_shield node): while one is up, SPELL damage to its holder is absorbed (not
 * dealt) and stored as charge, up to {@code max}. A spell is ability damage from anything but a basic
 * attack (primary / secondary / melee slots), and damage over time. Basic attacks and vanilla hits land
 * as usual. Surrounds the holder: every direction.
 */
public final class SpellShields {

    public static final String ABSORB_CUE = "spellshield_absorb";
    private static final Set<String> BASIC_ATTACK_SLOTS = Set.of(Slots.PRIMARY, Slots.SECONDARY, Slots.MELEE);

    private static final class Shield {
        final double max;
        double charge;

        Shield(double max) { this.max = max; }
    }

    private final WorldQuery world;
    private final CuePlayer cues;
    private final Map<UUID, Shield> shields = new HashMap<>();

    public SpellShields(WorldQuery world, CuePlayer cues) {
        this.world = world;
        this.cues = cues;
    }

    public void raise(UUID holder, double max) { shields.put(holder, new Shield(max)); }

    public void lower(UUID holder) { shields.remove(holder); }

    public boolean has(UUID holder) { return shields.containsKey(holder); }

    /** Charge stored so far (0 without a shield). */
    public double charge(UUID holder) {
        Shield s = shields.get(holder);
        return s == null ? 0 : s.charge;
    }

    public double max(UUID holder) {
        Shield s = shields.get(holder);
        return s == null ? 0 : s.max;
    }

    /**
     * A damage effect is about to hit: if its target holds a shield and this is a spell, absorb it (the
     * effect must then deal nothing). Returns true when absorbed.
     */
    public boolean absorb(EffectContext ctx, double amount) {
        if (!(ctx.target() instanceof EntityTarget target) || target.id().equals(ctx.caster())) return false;
        Shield s = shields.get(target.id());
        if (s == null || !isSpell(ctx)) return false;
        s.charge = Math.min(s.max, s.charge + Math.max(0, amount));
        ctx.engine().combat().hit(ctx.caster(), target.id()); // still a fight
        world.positionOf(target).ifPresent(p -> cues.play(ABSORB_CUE, p.world(), p.position()));
        return true;
    }

    /** Damage over time (no cast behind it) or ability damage not from a basic attack slot. */
    public static boolean isSpell(EffectContext ctx) {
        if (ctx.execution() == null) return true;
        Object slot = ctx.execution().blackboard().raw(Keys.SLOT.name());
        return !(slot instanceof String s && BASIC_ATTACK_SLOTS.contains(s));
    }
}

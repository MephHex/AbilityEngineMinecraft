package me.mephisto.ability_engine.engine.stats;

import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.team.Teams;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ultimates charge up instead of cooling down: from 0 to 100%.
 * <ul>
 *   <li>Landing an ability (1, 2, 3) on an enemy: +{@code abilityHit} (5%); a basic attack (primary / secondary fire,
 *       melee): +{@code basicHit} (2%). Once per cast, however many it hits (the first hit of a cast counts);
 *       damage over time doesn't, nor does the ultimate itself.</li>
 *   <li>By itself: +{@code perSecond} (0.5%) every second.</li>
 *   <li>All of it x the holder's {@code ult_charge_rate} statuses (an item: 1.25 = 25% faster).</li>
 * </ul>
 * At 100% the ultimate can be cast, which spends it all (back to 0). A new character starts at 0; dying keeps it.
 * Off (the engine's default, or {@link #configure} with enabled false): ultimates have their plain cooldowns.
 */
public final class UltimateCharge {

    public static final double FULL = 100;

    private final LoadoutManager loadouts;
    private final StatSheets stats;
    private final Teams teams;
    private final me.mephisto.ability_engine.engine.platform.TaskScheduler scheduler;
    private me.mephisto.ability_engine.engine.platform.TaskHandle ticker; // the charge over time, while on
    private boolean enabled; // off until configured (the plugin's config.yml turns it on)
    private double abilityHit = 5;
    private double basicHit = 2;
    private double perSecond = 0.5;
    private final Map<UUID, Double> charge = new HashMap<>();
    private final Map<UUID, String> characterOf = new HashMap<>(); // whose charge it is: a new character starts over

    public UltimateCharge(LoadoutManager loadouts, StatSheets stats, Teams teams,
                          me.mephisto.ability_engine.engine.platform.TaskScheduler scheduler) {
        this.loadouts = loadouts;
        this.stats = stats;
        this.teams = teams;
        this.scheduler = scheduler;
        stats.setUltimatesCharge(false);
    }

    /** From config.yml (ultimate-charge:). Off: ultimates use their cooldowns again. */
    public void configure(boolean enabled, double abilityHit, double basicHit, double perSecond) {
        this.enabled = enabled;
        this.abilityHit = Math.max(0, abilityHit);
        this.basicHit = Math.max(0, basicHit);
        this.perSecond = Math.max(0, perSecond);
        stats.setUltimatesCharge(enabled);
        if (enabled && ticker == null) ticker = scheduler.every(20, 20, this::tickSecond); // a little by itself
        if (!enabled && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    public boolean enabled() { return enabled; }

    /** Their charge, 0-100. */
    public double of(UUID player) {
        sync(player);
        return charge.getOrDefault(player, 0.0);
    }

    public boolean ready(UUID player) { return of(player) >= FULL - 1e-9; }

    /** Does casting this ability wait on the caster's charge (it's their ultimate, and ultimates charge)? */
    public boolean gates(UUID caster, String abilityId) { return enabled && stats.isUltimate(caster, abilityId); }

    /** More charge (percent points), x their ult_charge_rate; capped at 100. Only with a character. */
    public void add(UUID player, double amount) {
        sync(player);
        if (!characterOf.containsKey(player) || amount <= 0) return;
        double now = charge.getOrDefault(player, 0.0) + amount * stats.ultChargeMultiplier(player);
        charge.put(player, Math.min(FULL, now));
    }

    /** Set it outright (e.g. /ae ult, a game mode giving a head start), 0-100. */
    public void set(UUID player, double value) {
        sync(player);
        if (characterOf.containsKey(player)) charge.put(player, Math.max(0, Math.min(FULL, value)));
    }

    /** The ultimate was cast: all of it spent. */
    public void spend(UUID player) { charge.put(player, 0.0); }

    /**
     * A hit landed (the damage effect reports it). An enemy hit by a cast's first landing hit charges its caster: an
     * ability more than a basic attack. Damage over time (no cast) and the ultimate's own hits don't.
     */
    public void landed(EffectContext ctx, UUID victim) {
        if (!enabled || ctx.execution() == null) return;
        UUID caster = ctx.caster();
        if (caster == null || caster.equals(victim) || !teams.enemies(caster, victim)) return;
        var instance = ctx.execution().instance();
        String ability = instance.ability().id();
        if (stats.isUltimate(caster, ability) || !instance.markLanded()) return;
        add(caster, stats.isFire(caster, ability) ? basicHit : abilityHit);
    }

    /** Every second: everyone with a character charges a little by themselves. */
    public void tickSecond() {
        if (!enabled) return;
        for (UUID player : loadouts.assignedPlayers()) add(player, perSecond);
    }

    /** A different character (or none) than the charge was for: it starts over. */
    private void sync(UUID player) {
        String now = loadouts.baseCharacterOf(player).map(CharacterDef::id).orElse(null);
        if (Objects.equals(now, characterOf.get(player))) return;
        charge.remove(player);
        if (now == null) characterOf.remove(player);
        else characterOf.put(player, now);
    }
}

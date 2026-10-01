package me.mephisto.ability_engine.engine;

import me.mephisto.ability_engine.engine.ability.AbilityInstanceRegistry;
import me.mephisto.ability_engine.engine.ability.AbilityRegistry;
import me.mephisto.ability_engine.engine.ability.activation.AbilityActivator;
import me.mephisto.ability_engine.engine.data.NodeTypes;
import me.mephisto.ability_engine.engine.effect.ApplyStatusEffect;
import me.mephisto.ability_engine.engine.effect.EffectRegistry;
import me.mephisto.ability_engine.engine.construct.ConstructSystem;
import me.mephisto.ability_engine.engine.graph.GraphRunner;
import me.mephisto.ability_engine.engine.loadout.CharacterRegistry;
import me.mephisto.ability_engine.engine.loadout.LoadoutManager;
import me.mephisto.ability_engine.engine.platform.CuePlayer;
import me.mephisto.ability_engine.engine.platform.GameClock;
import me.mephisto.ability_engine.engine.platform.Platform;
import me.mephisto.ability_engine.engine.platform.TaskScheduler;
import me.mephisto.ability_engine.engine.platform.WorldQuery;
import me.mephisto.ability_engine.engine.projectile.ProjectileSystem;
import me.mephisto.ability_engine.engine.quiver.InfusionRegistry;
import me.mephisto.ability_engine.engine.quiver.QuiverManager;
import me.mephisto.ability_engine.engine.state.CooldownManager;
import me.mephisto.ability_engine.engine.state.ResourceManager;
import me.mephisto.ability_engine.engine.status.StatusManager;
import me.mephisto.ability_engine.engine.status.StatusRegistry;
import me.mephisto.ability_engine.engine.tag.TagManager;
import me.mephisto.ability_engine.engine.team.Teams;
import me.mephisto.ability_engine.engine.targeting.TargetingManager;
import me.mephisto.ability_engine.engine.platform.IndicatorRenderer;

import java.util.UUID;

/**
 * Composition root of the engine (replaces AbilityServices). Built from a {@link Platform};
 * zero Bukkit imports anywhere under {@code engine}. Nodes reach services via {@code ctx.engine()}.
 */
public final class AbilityEngine {

    private final Platform platform;
    private final EngineLog log;
    private final GraphRunner runner;
    private final AbilityRegistry abilities = new AbilityRegistry();
    private final StatusRegistry statusDefs = new StatusRegistry();
    private final EffectRegistry effects = new EffectRegistry();
    private final NodeTypes nodeTypes = NodeTypes.withBuiltins();
    private final CooldownManager cooldowns;
    private final ResourceManager resources;
    private final TagManager tags = new TagManager();
    private final me.mephisto.ability_engine.engine.summon.SummonManager summons;
    private final StatusManager statuses;
    private final AbilityInstanceRegistry instances = new AbilityInstanceRegistry();
    private final AbilityActivator activator;
    private final ProjectileSystem projectiles;
    private final ConstructSystem constructs;
    private final CharacterRegistry characters = new CharacterRegistry();
    private final LoadoutManager loadouts;
    private final TargetingManager targeting;
    private final Teams teams;
    private final me.mephisto.ability_engine.engine.barrier.BarrierSystem barriers;
    private final InfusionRegistry infusions = new InfusionRegistry();
    private final QuiverManager quivers;
    private final me.mephisto.ability_engine.engine.link.LinkManager links;
    private java.util.random.RandomGenerator random = new java.util.Random();
    private final me.mephisto.ability_engine.engine.stats.StatSheets stats;
    private final me.mephisto.ability_engine.engine.combat.CombatTracker combat;
    private final me.mephisto.ability_engine.engine.ward.WardManager wards;
    private final me.mephisto.ability_engine.engine.ward.HearingManager hearing;
    private final me.mephisto.ability_engine.engine.combat.SpellShields spellShields;
    private final me.mephisto.ability_engine.engine.ride.RideManager rides;

    public AbilityEngine(Platform platform) {
        this.platform = platform;
        this.log = new EngineLog(platform.logger());
        this.runner = new GraphRunner(log);
        this.teams = new Teams(platform.world());
        teams.setTags(tags);
        this.summons = new me.mephisto.ability_engine.engine.summon.SummonManager(platform.clones(), platform.scheduler());
        this.barriers = new me.mephisto.ability_engine.engine.barrier.BarrierSystem(platform.world(), teams, platform.cues());
        this.cooldowns = new CooldownManager(platform.clock(), id -> abilities.find(id)
                .map(a -> new CooldownManager.Charges(a.charges(), a.cooldownTicks()))
                .orElse(CooldownManager.Charges.SINGLE));
        this.resources = new ResourceManager(platform.clock());
        this.statuses = new StatusManager(platform.clock(), platform.scheduler(), tags, statusDefs, log);
        this.links = new me.mephisto.ability_engine.engine.link.LinkManager(platform.world(), platform.cues(),
                platform.scheduler(), log);
        links.attach(statuses);
        this.rides = new me.mephisto.ability_engine.engine.ride.RideManager(platform.movement(), platform.world(),
                platform.scheduler(), log);
        rides.attach(statuses, statusDefs);
        this.activator = new AbilityActivator(this);
        this.constructs = new ConstructSystem(platform.constructRenderer(), platform.scheduler(), teams, platform.world(), log);
        constructs.setCues(this::cuesFor);
        this.projectiles = new ProjectileSystem(platform.world(), constructs, teams, barriers, platform.projectileRenderer(), platform.scheduler(), log);
        this.quivers = new QuiverManager(tags, statuses,
                id -> loadouts().characterOf(id).map(me.mephisto.ability_engine.engine.loadout.CharacterDef::quiver));
        this.loadouts = new LoadoutManager(characters, activator, resources, quivers, tags, abilities, instances);
        this.targeting = new TargetingManager(this);
        this.stats = new me.mephisto.ability_engine.engine.stats.StatSheets(loadouts, platform.world(), statuses);
        this.combat = new me.mephisto.ability_engine.engine.combat.CombatTracker(platform.clock());
        this.spellShields = new me.mephisto.ability_engine.engine.combat.SpellShields(platform.world(), platform.cues());
        this.wards = new me.mephisto.ability_engine.engine.ward.WardManager(loadouts, tags, combat, platform.clock(),
                platform.cues(), platform.world(), platform.scheduler(), statuses);
        this.hearing = new me.mephisto.ability_engine.engine.ward.HearingManager(loadouts, platform.world(), teams, statuses,
                summons, platform.scheduler());
        // A reflex (a ward with cast:) casts its ability at whoever hit its holder, wherever they're aiming.
        wards.setReflexes((holder, attacker, ability) -> activator.activateOnId(holder, ability,
                new me.mephisto.ability_engine.engine.target.EntityTarget(attacker), java.util.Map.of()));
        // A duel in a veil: a summon or a projectile's body is on its owner's side of it.
        teams.veils().setOwnerResolver(id -> summons.ownerOf(id).or(() -> projectiles.bodyOwner(id)));
        // Debuff immunity from anything else (a status granting state.debuff_immune): blocks without using it up.
        statuses.addGuard((target, def, source) -> tags.has(target, me.mephisto.ability_engine.engine.tag.Tags.DEBUFF_IMMUNE)
                && me.mephisto.ability_engine.engine.status.StatusManager.isDebuff(target, def, source));

        tags.addListener(instances); // interrupts
        statuses.setEffectApplier((source, target, list) -> { // status ticks (burn damage etc.)
            if (!platform.world().isAlive(target)) return;
            for (var config : list) {
                effects.require(config.effectId()).apply(new me.mephisto.ability_engine.engine.effect.EffectContext(
                        this, null, source != null ? source : target,
                        new me.mephisto.ability_engine.engine.target.EntityTarget(target), config.params()));
            }
        });
        effects.register("status", new ApplyStatusEffect(statusDefs));
        effects.register("remove_status", new me.mephisto.ability_engine.engine.effect.RemoveStatusEffect(statusDefs));
        effects.register("purge_buffs", new me.mephisto.ability_engine.engine.effect.PurgeBuffsEffect());
    }

    // ---- platform ----
    public GameClock clock() { return platform.clock(); }
    public TaskScheduler scheduler() { return platform.scheduler(); }
    public WorldQuery world() { return platform.world(); }
    public me.mephisto.ability_engine.engine.platform.MovementControl movement() { return platform.movement(); }
    public CuePlayer cues() { return platform.cues(); }

    /**
     * Cues caused by {@code actor} (their abilities): if they're in a veil, only the two in it see and hear
     * them; otherwise everyone does. While one of their character's variants applies, each cue plays as its
     * variant where there is one (blue flames instead of orange ones).
     */
    public CuePlayer cuesFor(java.util.UUID actor) {
        CuePlayer base = platform.cues();
        var veils = veils();
        return new CuePlayer() {
            @Override
            public void play(String cueId, String world, me.mephisto.ability_engine.engine.math.Vec3 position) {
                var audience = veils.audienceOf(actor);
                String id = cueFor(actor, cueId);
                if (audience.isEmpty()) base.play(id, world, position);
                else base.play(id, world, position, audience);
            }

            @Override
            public void playLine(String cueId, String world, me.mephisto.ability_engine.engine.math.Vec3 from,
                                 me.mephisto.ability_engine.engine.math.Vec3 to) {
                var audience = veils.audienceOf(actor);
                String id = cueFor(actor, cueId);
                if (audience.isEmpty()) base.playLine(id, world, from, to);
                else base.playLine(id, world, from, to, audience);
            }

            @Override
            public me.mephisto.ability_engine.engine.platform.CueHandle start(String cueId, java.util.UUID entity) {
                var audience = veils.audienceOf(actor);
                String id = cueFor(actor, cueId);
                return audience.isEmpty() ? base.start(id, entity) : base.start(id, entity, audience);
            }

            @Override
            public boolean has(String cueId) { return base.has(cueId); }
        };
    }

    /** The variant of {@code actor}'s character that applies right now (they have its tag), if any. */
    private java.util.Optional<me.mephisto.ability_engine.engine.loadout.CharacterDef.Variant> variantOf(java.util.UUID actor) {
        if (actor == null) return java.util.Optional.empty();
        return loadouts.characterOf(actor).flatMap(c -> c.variants().stream()
                .filter(v -> tags.has(actor, v.whileTag())).findFirst());
    }

    /** The cue {@code actor}'s abilities play for {@code cueId} right now: its variant if there is one. */
    public String cueFor(java.util.UUID actor, String cueId) {
        if (cueId == null) return null;
        return variantOf(actor).map(v -> v.cueSuffix())
                .map(suffix -> cueId + suffix).filter(platform.cues()::has).orElse(cueId);
    }

    /** How a projectile {@code actor} shoots with this visual looks right now: its variant's visual, if one swaps it. */
    public String visualFor(java.util.UUID actor, String visual) {
        if (visual == null) return null;
        return variantOf(actor).map(v -> v.visuals().get(visual)).orElse(visual);
    }

    /** Who may see what {@code actor} does right now: the two in its veil, or empty = everyone. */
    public java.util.Set<java.util.UUID> audienceOf(java.util.UUID actor) { return veils().audienceOf(actor); }
    public IndicatorRenderer indicators() { return platform.indicators(); }
    public EngineLog log() { return log; }

    // ---- engine ----
    public GraphRunner runner() { return runner; }
    public AbilityRegistry abilities() { return abilities; }
    public StatusRegistry statusDefs() { return statusDefs; }
    public EffectRegistry effects() { return effects; }
    public NodeTypes nodeTypes() { return nodeTypes; }
    public CooldownManager cooldowns() { return cooldowns; }
    public ResourceManager resources() { return resources; }
    public TagManager tags() { return tags; }
    public StatusManager statuses() { return statuses; }
    public AbilityInstanceRegistry instances() { return instances; }
    public AbilityActivator activator() { return activator; }
    public ProjectileSystem projectiles() { return projectiles; }
    public ConstructSystem constructs() { return constructs; }
    public CharacterRegistry characters() { return characters; }
    public LoadoutManager loadouts() { return loadouts; }
    public TargetingManager targeting() { return targeting; }
    public Teams teams() { return teams; }
    public me.mephisto.ability_engine.engine.team.Veils veils() { return teams.veils(); }
    public me.mephisto.ability_engine.engine.barrier.BarrierSystem barriers() { return barriers; }
    public InfusionRegistry infusions() { return infusions; }
    public QuiverManager quivers() { return quivers; }
    public me.mephisto.ability_engine.engine.link.LinkManager links() { return links; }
    /** Max HP, armor, base damage, speeds: character stat sheets. */
    public me.mephisto.ability_engine.engine.stats.StatSheets stats() { return stats; }
    /** Who was in combat when (dealt or took damage). */
    public me.mephisto.ability_engine.engine.combat.CombatTracker combat() { return combat; }
    /** Characters' wards: debuff immunity that recharges out of combat. */
    public me.mephisto.ability_engine.engine.ward.WardManager wards() { return wards; }
    public me.mephisto.ability_engine.engine.ward.HearingManager hearing() { return hearing; }
    /** Who rides whom (a fae perched on an ally). */
    public me.mephisto.ability_engine.engine.ride.RideManager rides() { return rides; }
    /** Spell shields: spell damage absorbed as charge. */
    public me.mephisto.ability_engine.engine.combat.SpellShields spellShields() { return spellShields; }
    /** Randomness for gameplay rolls (random infusions...). Tests swap in a seeded one. */
    public java.util.random.RandomGenerator random() { return random; }
    public void setRandom(java.util.random.RandomGenerator random) { this.random = random; }

    /** Cancel casts, clear statuses and tags. For death, logout, or mobs despawning. Keeps cooldowns, resources and character. */
    public void resetEntity(UUID entity, String reason) {
        summons.dismissAll(entity);
        links.breakAll(entity);
        targeting.cancel(entity, reason);
        instances.cancelAll(entity, reason);
        statuses.clear(entity);
        tags.clear(entity);
    }

    /** Like {@link #resetEntity}, for a death: casts of abilities that survive death keep running (traps). */
    public void resetOnDeath(UUID entity) {
        summons.dismissAll(entity); // echoes go with you
        links.breakAll(entity);     // and tethers break
        targeting.cancel(entity, "death");
        for (var instance : instances.of(entity)) {
            if (!instance.ability().survivesDeath()) instance.cancel("death");
        }
        statuses.clear(entity);
        tags.clear(entity);
    }

    /** Stop everything. Statuses are cleared so their tag listeners undo any Bukkit side effects. */
    public void shutdown() {
        targeting.cancelAll("shutdown");
        instances.cancelEverything("shutdown");
        projectiles.shutdown();
        constructs.shutdown();
        summons.shutdown();
        links.shutdown();
        rides.shutdown();
        statuses.clearAll();
    }

    public me.mephisto.ability_engine.engine.summon.SummonManager summons() { return summons; }

    /**
     * Someone dealt damage (the platform's damage effect reports it). Ends the attacker's statuses
     * with break_on_damage (stealth).
     */
    public void notifyDamageDealt(UUID attacker, UUID victim) {
        if (attacker == null || attacker.equals(victim)) return;
        for (var s : java.util.List.copyOf(statuses.on(attacker))) {
            if (s.def().breakOnDamage()) statuses.remove(attacker, s.def().id());
        }
    }

    /**
     * Someone got a kill: wakes the killer's await_kill nodes and resets their abilities that refresh on
     * kills (refresh_on_kill).
     */
    public void notifyKill(UUID killer, UUID victim, boolean victimIsPlayer) {
        for (var instance : instances.of(killer)) instance.kill(victim, victimIsPlayer); // await_kill nodes
        loadouts.characterOf(killer).ifPresent(c -> c.slots().values().forEach(id -> abilities.find(id).ifPresent(a -> {
            if (a.refreshOnKill().equals("all") || (victimIsPlayer && a.refreshOnKill().equals("players"))) {
                cooldowns.clear(killer, id);
            }
        })));
    }
}

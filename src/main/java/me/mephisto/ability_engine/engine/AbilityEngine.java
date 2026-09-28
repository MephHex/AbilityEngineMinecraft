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
        this.activator = new AbilityActivator(this);
        this.constructs = new ConstructSystem(platform.constructRenderer(), platform.scheduler(), teams, platform.world(), log);
        this.projectiles = new ProjectileSystem(platform.world(), constructs, teams, barriers, platform.projectileRenderer(), platform.scheduler(), log);
        this.quivers = new QuiverManager(tags, statuses,
                id -> loadouts().characterOf(id).map(me.mephisto.ability_engine.engine.loadout.CharacterDef::quiver));
        this.loadouts = new LoadoutManager(characters, activator, resources, quivers, tags);
        this.targeting = new TargetingManager(this);
        this.stats = new me.mephisto.ability_engine.engine.stats.StatSheets(loadouts, platform.world());

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
    }

    // ---- platform ----
    public GameClock clock() { return platform.clock(); }
    public TaskScheduler scheduler() { return platform.scheduler(); }
    public WorldQuery world() { return platform.world(); }
    public me.mephisto.ability_engine.engine.platform.MovementControl movement() { return platform.movement(); }
    public CuePlayer cues() { return platform.cues(); }
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
    public me.mephisto.ability_engine.engine.barrier.BarrierSystem barriers() { return barriers; }
    public InfusionRegistry infusions() { return infusions; }
    public QuiverManager quivers() { return quivers; }
    public me.mephisto.ability_engine.engine.link.LinkManager links() { return links; }
    /** Max HP, armor, base damage, speeds: character stat sheets. */
    public me.mephisto.ability_engine.engine.stats.StatSheets stats() { return stats; }
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

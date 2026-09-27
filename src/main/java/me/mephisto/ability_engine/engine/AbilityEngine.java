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

    public AbilityEngine(Platform platform) {
        this.platform = platform;
        this.log = new EngineLog(platform.logger());
        this.runner = new GraphRunner(log);
        this.teams = new Teams(platform.world());
        this.barriers = new me.mephisto.ability_engine.engine.barrier.BarrierSystem(platform.world(), teams, platform.cues());
        this.cooldowns = new CooldownManager(platform.clock());
        this.resources = new ResourceManager(platform.clock());
        this.statuses = new StatusManager(platform.clock(), platform.scheduler(), tags, statusDefs, log);
        this.activator = new AbilityActivator(this);
        this.constructs = new ConstructSystem(platform.constructRenderer(), platform.scheduler(), teams, platform.world(), log);
        this.projectiles = new ProjectileSystem(platform.world(), constructs, teams, barriers, platform.projectileRenderer(), platform.scheduler(), log);
        this.quivers = new QuiverManager(tags, statuses,
                id -> loadouts().characterOf(id).map(me.mephisto.ability_engine.engine.loadout.CharacterDef::quiver));
        this.loadouts = new LoadoutManager(characters, activator, resources, quivers);
        this.targeting = new TargetingManager(this);

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

    /** Cancel casts, clear statuses and tags. For death, logout, or mobs despawning. Keeps cooldowns, resources and character. */
    public void resetEntity(UUID entity, String reason) {
        targeting.cancel(entity, reason);
        instances.cancelAll(entity, reason);
        statuses.clear(entity);
        tags.clear(entity);
    }

    /** Stop everything. Statuses are cleared so their tag listeners undo any Bukkit side effects. */
    public void shutdown() {
        targeting.cancelAll("shutdown");
        instances.cancelEverything("shutdown");
        projectiles.shutdown();
        constructs.shutdown();
        statuses.clearAll();
    }
}

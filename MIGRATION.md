# AbilityEngine refactor — migration guide

**How to apply:** delete your whole `src/` folder and replace it with the one in this zip.
Every file was touched, and many old files no longer exist, so a merge would leave stale classes behind.
Your `pom.xml` stays; see "Build" below for the one addition.

## Package layout

`NEW` = package didn't exist before. Everything under `engine/` has zero Bukkit imports.

```
me.mephisto.ability_engine
├── AbilityEnginePlugin                  rewritten: wiring only
├── engine/
│   ├── AbilityEngine                    NEW  composition root (replaces AbilityServices)
│   ├── EngineLog                        NEW  replaces System.out; /ae debug toggles it
│   ├── math/            Vec3            was engine/physics
│   ├── platform/                        NEW  ports the engine talks to (Bukkit implements them)
│   │     GameClock, TaskScheduler, TaskHandle, WorldQuery, Aim, EntitySnapshot, SweepHit,
│   │     ProjectileRenderer, ProjectileVisual, CuePlayer, Platform
│   ├── graph/                           AbilityGraph, GraphBuilder, GraphNode, NodeResult, Ports,
│   │                                    GraphRunner, ExecutionContext, Resumer, Blackboard, Key, Keys,
│   │                                    GraphValidationException
│   ├── nodes/control/   DelayNode, SwitchNode
│   ├── nodes/debug/     PrintNode
│   ├── nodes/gameplay/  AcquireTargetNode, ApplyEffectsNode, ProjectileNode, PlayCueNode
│   ├── target/                          NEW  (replaces engine/targeting + engine/casting)
│   │     Target, EntityTarget, PointTarget, TargetQuery, SelfQuery, KeyQuery,
│   │     HitscanQuery, RadiusQuery, ConeQuery, AreaFilter
│   ├── effect/                          was engine/effects
│   │     Effect, EffectConfig, EffectContext, EffectRegistry, ApplyStatusEffect
│   ├── tag/                             NEW  Tags, TagManager, TagListener
│   ├── status/                          NEW  StatusDef, StackPolicy, StatusRegistry, StatusManager, ActiveStatus
│   ├── state/                           was engine/playerState: CooldownManager, ResourceManager
│   ├── ability/                         was engine/abilities
│   │     Ability, AbilityRegistry, AbilityInstance, AbilityInstanceRegistry
│   ├── ability/activation/              was engine/abilities/cast
│   │     ActivationMode, InstantActivation, ChannelActivation, ActiveChannel,
│   │     AbilityActivator, ActivationResult
│   ├── projectile/                      NEW  ProjectileSpec, ProjectileSystem, Projectile,
│   │                                         MotionModifier, Gravity, Drag, Homing
│   └── data/                            NEW  AbilityLoader, NodeTypes, NodeFactory, Parsers, Params,
│                                             LoadReport, DataException
└── bukkit/
    ├── platform/        NEW  Convert, PaperClock, PaperTaskScheduler, BukkitWorldQuery,
    │                         BukkitProjectileRenderer, BukkitCuePlayer
    ├── effect/          DamageEffect, BukkitEffects        (was bukkit/effects)
    ├── status/          NEW  TagBindings, MovementLock
    ├── input/           NEW  AbilityInputListener, PlayerLifecycleListener, ItemBindings
    ├── command/         NEW  AbilityCommand
    └── data/            NEW  AbilityFiles

src/main/resources/  plugin.yml, abilities.yml (NEW)
src/test/java/       NEW  engine tests + fakes (engine/testkit)
```

## Old file → new home

| Old | New |
|---|---|
| engine/abilities/AbilityServices | engine/AbilityEngine |
| engine/abilities/Ability | engine/ability/Ability (record + builder) |
| engine/abilities/AbilityRegistry | engine/ability/AbilityRegistry |
| engine/abilities/cast/CasterPolicy | engine/ability/activation/AbilityActivator |
| engine/abilities/cast/PolicyResult | engine/ability/activation/ActivationResult |
| engine/abilities/cast/CastExecutionStrategy | engine/ability/activation/ActivationMode |
| engine/abilities/cast/InstantCastStrategy | engine/ability/activation/InstantActivation |
| engine/abilities/cast/ChannelCastStrategy + ChannelConfig | engine/ability/activation/ChannelActivation |
| engine/abilities/cast/ChannelPhase | engine/ability/activation/ActiveChannel |
| engine/abilities/cast/ActiveCastRegistry | engine/ability/AbilityInstanceRegistry (tracks every cast) |
| engine/casting/CastMethod, CastResult | engine/target/TargetQuery (returns List&lt;Target&gt;) |
| engine/casting/ProjectileSpawner | engine/platform/ProjectileRenderer + engine/projectile/ProjectileSystem |
| engine/targeting/TargetSelector | engine/target/TargetQuery |
| engine/targeting/SingleTargetSelector | engine/target/KeyQuery |
| engine/targeting/RadialTargetSelector | engine/target/RadiusQuery |
| engine/targeting/ConicTargetSelector | engine/target/ConeQuery |
| engine/effects/* | engine/effect/* (EffectRegistry is an instance now) |
| engine/graph/AbilityGraphContext | engine/graph/ExecutionContext |
| engine/graph/AbilityNode | engine/graph/GraphNode |
| engine/nodes/gameplay/CastTargetNode | engine/nodes/gameplay/AcquireTargetNode |
| engine/nodes/gameplay/EffectApplicationNode | engine/nodes/gameplay/ApplyEffectsNode |
| engine/physics/Vec3 | engine/math/Vec3 |
| engine/playerState/CooldownManager, ResourceManager | engine/state/* |
| engine/playerState/CrowdControlManager | engine/status/StatusManager + engine/tag/TagManager |
| bukkit/casting/HitscanCast | engine/target/HitscanQuery (world access via WorldQuery) |
| bukkit/casting/ProjectileCast | engine/projectile/ProjectileSystem + bukkit/platform/BukkitProjectileRenderer |
| bukkit/effects/EffectLoader | bukkit/effect/BukkitEffects |
| bukkit/effects/types/DamageEffect | bukkit/effect/DamageEffect |
| bukkit/effects/types/StunEffect | `stun` status in abilities.yml + bukkit/status/TagBindings |
| bukkit/listener/AbilityInputListener | bukkit/input/AbilityInputListener |
| bukkit/listener/Commands | bukkit/command/AbilityCommand |
| bukkit/abilities/TestAbilities | src/main/resources/abilities.yml |
| resources/settings.yml, java/.../resources/* | deleted (template leftovers) |

## Bugs fixed

1. **Projectile task leak** — every shot started a repeating task that was never cancelled. Now one ticker for all projectiles, stopped when none are in flight.
2. **Off-thread Bukkit calls** — DelayNode used a raw Thread, ChannelPhase a java.util.Timer. Both now use the main-thread scheduler.
3. **Channels cancelled after any stun** — ChannelPhase compared milliseconds to tick-based stun expiry. Interrupts are now tag events (`interrupted_by`).
4. **Damage was 0** — DamageEffect registered with 0 and ignored params. Now reads `amount`, validated at load.
5. **Radial/cone selectors were stubs** returning fake strings. Real geometry now.
6. **Projectiles tunneled/hit through walls** — collision was checked only at the endpoint, entities before blocks. Now a swept ray that returns whichever is hit first.
7. **ActiveCastRegistry never unregistered** finished channels → "already_active" forever. Instances unregister themselves.
8. **Cooldowns tied to world time** (the advanceTime gamerule bug). Now `Bukkit.getCurrentTick()`.
9. `(int)` casts on params crashed on YAML doubles → `Params` with typed getters and path-aware errors.
10. Homing accelerated forever → constant-speed steering.
11. `location.getChunk().isLoaded()` loaded chunks as a side effect → `world.isChunkLoaded`.
12. Stun attribute modifiers could stick after logout/crash → cleared on quit, death, disable, and scrubbed on join.
13. `ResourceManager.has()` returned false for zero-cost abilities on players without pools.
14. SwitchNode read a hardcoded "hitType" key nothing wrote → key is a parameter.

## Build (pom.xml)

Nothing new for the plugin: SnakeYAML, Adventure and JOML come with `paper-api`. For the tests, add:

```xml
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.10.2</version>
  <scope>test</scope>
</dependency>
```
and make sure `maven-surefire-plugin` is 3.x (older ones don't find JUnit 5 tests). Run with `mvn test`.

Java 21. Built against Paper 1.21.10 (1.21.9+ is needed for Mannequins), so attributes use the 1.21.3+
names: `MOVEMENT_SPEED`, `JUMP_STRENGTH`, `KNOCKBACK_RESISTANCE`, `MAX_ABSORPTION`, `MAX_HEALTH`
(`bukkit/status/MovementLock`, `bukkit/effect/OverflowShields`).

## Using it

- `plugins/AbilityEngine/abilities.yml` is created on first start. Edit it, then `/ae reload`. **Don't use `/reload`.**
- `/ae bind <ability>` while holding an item → right-click it to cast. `/ae unbind` to clear.
- `/ae cast <id>`, `/ae cdclear [id]`, `/ae list`, `/ae status` (your tags, statuses, running casts),
  `/ae setres <resource> <amount>` (e.g. `mana 100`, `fuel 50`), `/ae debug` (graph trace in console).
- Permission `abilityengine.admin` (op by default).

## Notes

- Projectile visuals are now centered on the physics position (the old `(0, 0.5, -0.5)` translation assumed
  a rotated display). If a model looks offset, adjust the translation in `BukkitProjectileRenderer`.
- Mobs that despawn (rather than die) keep their engine status entries until restart. Harmless at small
  scale; hook Paper's `EntityRemoveFromWorldEvent` → `engine.resetEntity(...)` if it ever matters.
- New node type: implement `GraphNode`, register it with `engine.nodeTypes().register("my_type", (params, engine) -> ...)`
  before `files.reload()` in the plugin.
- New effect: implement `Effect`, register in `BukkitEffects`. New tag side effect: `tagBindings.bind(...)`.

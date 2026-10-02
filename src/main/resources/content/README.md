# AbilityEngine content

Every `.yml` file in this folder (and its subfolders) is loaded. Edit, then run `/ae reload`
(never `/reload`). All times are in server ticks (20 ticks = 1 second).

## Files

Each file may have any of these sections: `statuses:`, `infusions:`, `abilities:`, `characters:`.
Split them however you like: one file per character, per role, per anything.

- Files can use each other's content in any order: a character in `umbrella.yml` can use an
  ability from `archmage.yml`, and any ability can apply a status from `shared.yml`.
- The same id in two files is an error naming both files.
- A YAML syntax error in any file cancels the whole reload and keeps what was loaded before.
- An old single `abilities.yml` next to this folder is still read too.

## Abilities

Wiring: each node lists where its output ports go.

    next: some_node                      -> shorthand for  on: { out: some_node }
    on: { hit: node_a, miss: node_b }    -> named ports

An unwired port just ends that branch. Typos are reported by `/ae reload`.

- **Node types:** print, delay, switch, acquire_target, apply_effects, projectile, play_cue, set,
  await_recast, redirect_projectile, construct, steer_projectile,
  dash `{ speed, range, radius, flat, direction }` -> hit / miss,
  start_cue `{ cue, at }` (a looping cue on an entity, default the caster, until the cast ends),
  barrier `{ distance, radius }` (a frontal barrier on the caster until the cast ends),
  counter `{ counter, every }` -> trigger / out ("every Nth time", remembered between casts),
  release_tags (drop the ability's active_tags early, e.g. right after a dash),
  has_tag `{ tag, target }` -> has / lacks (does the caster, or `target: <key>`, have a tag right now),
  in_range `{ center, radius, target }` -> inside / outside (is the caster, or `target`, within
  `radius` of the key `center`; e.g. "was I caught in my own explosion"),
  reload -> out / full, take_bolt -> out / empty, infuse `{ infusion, count }` (see Quivers),
  reduce_cooldown `{ ability | slot, ticks }` (take time off one of the caster's cooldowns, e.g.
  `slot: ability_1` on every hit)
- **Query types:** self, key, hitscan, radius, cone, cursor,
  line `{ range, width }` (a beam: everyone within width/2 of the line, stops at the first block
  and at enemy barriers; writes beam_start / beam_end for the visual),
  path `{ from, to, width }` (everyone along the segment between two keys, e.g. a dash's path)
- **Effects:** damage `{amount, ignore_iframes, lifesteal, overflow, knockback}` (`knockback: false`:
  magic damage like vanilla poison, no knockback; for damage over time), heal `{amount, overflow}`,
  status `{status, duration}`, remove_status `{status}` (e.g. use up a mark),
  teleport `{to, ground}`, knockback `{from, radius, center, edge, lift}`
- **Damage** is in "design HP": 10 design HP = 1 Minecraft health point (half a heart), so a
  normal 20-health player is 200. The ratio is `damage-scale` in config.yml.
- **Blackboard keys:** caster (always), aim (where you aimed), target (acquire_target default),
  hit (projectile / dash result), slot (which character slot the cast came from)
- **Cast bar:** channels show on the XP bar automatically; a delay shows it with `cast_bar: true`.
- **Recasts:** abilities with an `await_recast` node start their cooldown when the recast window
  closes (recast used, window timed out, or the cast ended), not on the first cast. Their hotbar
  icon glints while a recast is available. `cooldown_starts: cast` restores the old timing.
- **Cues:** `play_cue` plays a one-shot cue at a spot (hit, impact, cast, spark, shatter, fizzle,
  teleport...), or between two spots with `to:` (geyser_beam). `start_cue` starts a looping one on
  an entity until the cast ends (riptide, parasol, guard_pose, geyser_charge).
- **Air Anchor:** the `state.anchored` tag makes the holder hover in place (no falling).
- **Overflow:** with `overflow: true`, healing that doesn't fit becomes a decaying shield
  (absorption hearts). `overflow_max` (default 150) and `overflow_decay` per second (default 25).
- **Traps:** a `construct` with `trigger: <radius>` is a trap: once armed (`arm: <ticks>`), the first
  enemy that comes that close sets it off (-> `triggered`, the enemy stored as "hit"). Add
  `solid: false` so projectiles and punches pass through it. Allies never set it off. `limit: N`: at most
  N from the same caster and ability at once; one more ends the oldest.
- **Sliding:** a `projectile` with `slide: 0.6` doesn't stop when it lands (out of bounces): it skids
  along the ground keeping that share of its speed each tick, and exits `hit_block` where it stops.
- **Piercing:** a `projectile` with `pierce: N` passes through N enemies, running `hit_entity` for each
  (like vanilla Piercing), and stops at the next one.
- **Dashes:** `pierce: true` passes through enemies; `store: name` records name_start / name_end.
  `direction: movement` dashes the way the caster is WALKING (strafe left = dash left, always flat)
  instead of where they aim; standing still, it goes straight ahead.
- **Movement abilities** (anything with a `dash` node, or `movement: true`, e.g. a blink) can't be used
  while the caster has `block.move` (rooted, stunned), and a dash in progress stops when they get it.
  `movement: false` opts a dash out (e.g. one that breaks roots).
- **Ability options:** `aura: <looping cue>` runs for the whole cast; `cancel_on_repress: true` lets
  the ability's key end it early; `survives_death: true` keeps its casts running when the caster dies
  (thrown traps stay armed; logging out or changing character still ends them).
- **Death and character changes:** dying clears every status and tag on you, and nothing lands on you while you're
  dead (a hit that kills and also poisons doesn't poison the corpse); respawning starts clean too. Changing character
  (`/ae char`) clears them as well: nothing of the old kit stays on you (e.g. a planted Sylvan walks again).
- **Tags with effects in game:** `state.resistant` = 40% less damage taken, `state.slowed` = -40% speed,
  `state.hasted` = +30% speed, `state.sturdy` = half knockback, `state.invisible` = invisible (held items
  still show, like vanilla).
- **Projectile visual:** an item Material (`DIAMOND_BLOCK`), or `"entity:<EntityType>"`
  (`"entity:END_CRYSTAL"`). `"upside_down:<visual>"` turns an item or `block:` visual over (e.g.
  `"upside_down:SPORE_BLOSSOM"`: a hanging spore blossom facing up). Arrow types (`"entity:ARROW"`, `SPECTRAL_ARROW`, `TRIDENT`) are real arrows
  flown by the game itself (vanilla drop and drag: `motion` and `bounces` don't apply, `speed` is the
  launch speed). The engine still checks their path every tick, so enemies, allies, barriers and
  constructs work as usual; they can't hurt anything by themselves or be picked up. An infused bolt's
  `ARROW` is tinted in its infusions' colors.

### Targeting (aim previews)

Add `targeting: { shape: circle|line|cone|point, range, radius/width/angle, ground: true, max_drop }`
to show a preview first: pressing the ability's key again confirms, RMB cancels (players without a
character, casting /ae bind items, confirm with LMB). No time limit unless you add `timeout: <ticks>`.
`/ae quickcast` skips the preview.

- The preview ray ignores entities (you can place things under someone).
- `ground: true` projects the aim straight down onto the ground, at most `max_drop` (default 6)
  below your feet; deeper ground (off a cliff) falls back to the cliff edge.
- Out of line of sight, a glowing marker only you can see shows the spot.

### Barriers

A `barrier` node raises a flat disc in front of the caster, facing where they look. From its front it
absorbs enemy projectiles, stops enemy hitscans and dashes like a wall, and blocks enemy direct hits
(melee, what a projectile/dash hit) and vanilla mob hits. Area effects, allies, and anything from
behind or the sides are unaffected. Tag `block.knockback` makes the holder knockback-immune.

### Teams

Vanilla `/team`. `apply_effects` only hits enemies (plus yourself when it targets you); add
`affects: allies` or `affects: all` for heals and buffs. Projectiles, hitscans and dashes pass
through allies; allies can't trigger or break your constructs.

## Statuses and buffs

`tags`, `duration`, `stacking` (refresh | extend | stack), `max_stacks`, plus:

- `tick: { every, effects: [...] }` - damage/heal over time, credited to whoever applied it.
- `on_hit: [...]` - makes it a **buff**: while you have it, your on-hit hits also apply these.
  `apply_effects` nodes apply on-hits with `on_hit: true`; by default only casts from the
  primary slot do. `on_hit: false` turns it off anywhere.

## Quivers and infusions

A character with a `quiver:` has a queue of bolts plus one bolt loaded in their weapon.

- **Loading** takes the front (leftmost) bolt into the weapon; the rest shift forward and a plain bolt
  joins at the back. Only one bolt is loaded at a time.
- **Infusions** are magic on a bolt, defined like statuses under `infusions:` with a `name`, a
  `color` ("#RRGGBB", tints the bolt on the hotbar) and the `on_hit` effects it adds. They only ever
  go onto QUEUED bolts, never the loaded one. One bolt can carry several (colors mix).
- **Nodes:** `reload` loads the next bolt (-> out, or `full` if already loaded: nothing moves);
  `infuse { infusion, count }` infuses the next `count` queued bolts; `take_bolt` fires the loaded
  bolt (-> out, or `empty`: a dry fire) and stores it as "bolt"; then an `apply_effects` with
  `infusions: true` also applies that bolt's infusions to whoever it hits.
- **A CROSSBOW weapon** with a quiver is a real crossbow: hold RMB to draw it (vanilla), and when it's
  drawn the next bolt loads (not while stunned). RMB again fires the primary slot, which should
  `take_bolt`; LMB doesn't shoot.
  The secondary slot is unused (RMB is the draw).
- `quiver: { size, hotbar, reload_speed }`: `size` bolts (default 3) shown from hotbar slot `hotbar`
  (the leftmost loads next). `reload_speed: { stacks_of: <status>, max, while: { <tag>: <level> } }`
  makes the crossbow draw faster: each level is Quick Charge (1.25s, 0.25s faster per level, 4 at most).
  The level is the stacks of `stacks_of` (up to `max`), or more while you have a `while` tag.
  `first: 2` makes the first stack worth level 2 (stacks 1/2/3 give 2/3/4).

See `alchemist.yml`.

## Characters

Which ability sits in which slot:
`primary` (LMB), `secondary` (RMB), `melee` (LMB on an entity, optional), `ability_1` (1),
`ability_2` (2), `ability_3` (3), `ultimate` (F; its icon sits in the offhand slot). Keys are set in
config.yml. Any slot may be left out.

- `resources:` pools with `max`, `regen` (per second), `delay` (regen delay in ticks);
  `hotbar: 1-9` shows the pool as an item whose stack size is the amount.
- `status_bar: <status>` shows a status on the XP bar: the level number is its stacks, the bar drains
  with its time left (e.g. a passive's stacks). A cast bar takes over while one is running.
  `status_bar: { status: <status>, level: reload_speed }` shows the quiver's reload speed as the number;
  `level: none` shows no number at all (a plain countdown bar); `level: seconds` shows the seconds it has left. The
  bar drains against the time the status was given (an ultimate's longer one too).
- `weapon:` the item locked in the main hand; it also shows the primary fire's tooltip and fire
  rate. Use something with no right-click behaviour of its own (NOT bows, shields, food,
  tridents), except a CROSSBOW together with a `quiver:` (see Quivers). Icons with a cooldown overlay must use different materials from each other and the
  weapon.

Try it: `/ae char archmage`, back to normal: `/ae char none`.

## Added for the Dreamer

- **Nodes:** fork (-> out / also: run two branches at once, e.g. you and your echo dash together),
  summon_clone `{ summon, store, lifetime }` (a mannequin of you that outlives the cast; a new one
  replaces the old), find_summon `{ summon, store }` -> found / none, swap `{ with }` (trade places).
- **Dash:** `mover: <key>` makes someone else dash (an echo); `toward: cursor` sends it toward the
  point your crosshair is on (stopping there if it's closer than `range`) instead of along your aim.
  With `pierce: true`, the `passed` port runs once for EACH enemy as the dash reaches them, with
  `hit` = that enemy: put the damage there. Floors don't stop a dash (a slightly downward dash glides
  along the ground); walls do.
- **summon_clone** `at: aim` places it on the spot you confirmed in an aim preview.
- **Damage:** `backstab: 1.5` multiplies the damage when the hit comes from behind the target
  (outside the front 220 degrees of where they face), with a crit effect.
- **Statuses:** `break_on_damage: true` ends it when the holder deals damage (stealth);
  `once: true` uses up a buff with the first hit that applies its on_hit effects.
- **Abilities:** `refresh_on_kill: players` (or `all`) resets the cooldown on kills.
- **Tags:** `state.hidden` (other players can't see you at all), `state.untargetable` (abilities
  ignore you: shots, rays and dashes pass through, areas skip you), `state.blinded`.

## Added for the Vanguard

- **Nodes:**
  leap `{ direction: aim|movement|up, speed, up, gravity, until: land|apex, store }` (a jump arc the
  engine flies: forward `speed` per tick, `up` launch speed, pulled down by `gravity`; ends on landing,
  or at the top of the arc with `until: apex`),
  choose_spot `{ range, radius, timeout, ground, max_drop, store }` -> out / none (mid-cast aim
  preview: the ability's key again picks the spot; on timeout or RMB it uses where you look; `none` if that's nothing),
  link `{ name, target, range, damage_taken, redirect, copy_positive, cue }` (a tether from the caster
  to someone; see below), find_link `{ name, store }` -> found / none, unlink `{ name }`,
  start_cooldown (start the cooldown now; for `cooldown_starts: manual`), end_cast (end the whole
  cast right away, e.g. from one branch of a fork).
- **Tethers (link):** one per caster and `name` (linking someone else moves it). While it holds, the
  target takes `damage_taken` x damage (0.7 = 30% less) and `redirect` of what that prevented hits the
  caster instead; `copy_positive: true` copies statuses marked `positive: true` that the caster gets
  onto the target. `cue` is a line drawn between them. It breaks beyond `range`, or when either dies.
- **Abilities:** `cooldown_starts: manual` only starts the cooldown at a `start_cooldown` node (a cast
  that never reaches one is free).
- **apply_effects:** `count: <key>` stores how many it hit; `times: <key>` applies the effects that
  many times per target (0 if the key is missing), e.g. a shield per enemy hit.
- **Hitscan:** `targets: allies` aims at allies instead of enemies (with `ray_size` for a generous hitbox).
- **Dash:** `to: <key>` dashes straight to a stored spot and stops there (or where it touches the ground).
- **Barrier:** `projectiles_only: true` only stops projectiles (rays, dashes and melee pass).
- **Statuses:** `damage_dealt: 1.25` (+25% damage dealt), `damage_taken: 0.8` (20% less damage taken).
  They apply to every hit, vanilla ones too. `attack_speed: 0.6` makes the holder's basic attacks
  (primary / melee) 40% slower: their cooldown is divided by it, and a crossbow must be drawn 1/0.6 as
  long before it loads (Alchemist's Paralysis). Above 1 is faster.
- **Status particles:** anyone with `state.silenced` gives off teal wisps fading to near-black, `state.poisoned`
  green poison swirls, `state.paralyzed` yellow sparks (teal / cyan = anti-magic: silence, Counterspell,
  Null Ward).
- **Buffs** (what tethers with `copy_positive` copy, like Radiant Bond): any status with a **`buff.*` tag**
  (e.g. `tags: [state.hasted, buff.tonic]`), on-hit effects, `damage_dealt` above 1, `damage_taken` below 1 or `attack_speed` above 1.
  `positive: true` / `positive: false` overrides it: e.g. Soul Rend's charge and Hunter's Rhythm have a
  `buff.*` tag, but they're `positive: false` so they aren't shared. Beneficial vanilla potion effects
  (drunk potions, beacons, /effect) are copied too. Buffs that only exist while an ability runs (its
  `active_tags`, e.g. Overdrive's speed) aren't statuses, so they aren't copied.
- **Effects:** shield `{ amount, max, decay }` gives absorption (design HP; `decay` per second,
  default 0 = until it's broken).
- **Tags:** `state.sturdy` halves knockback.
- **Cues:** leap_off, leap_slam, radiant_bond, radiant_tether (line), tether_break, heroic_launch,
  heroic_impact, and the looping `bulwark` (a shield held in front of you).

## Added for Arcane Barrage, Overdrive and Volatile Flask

- **Nodes:**
  charge `{ ticks, from, store, min, fire_when_full }` -> out / early (charge while the input is held:
  continues when it's let go; full after `ticks`, where it goes by itself unless `fire_when_full: false`,
  which waits for the let-go; stores the power, `from` (let go at once, default 0) up to 1.0; let go
  before `min` ticks: exits `early` instead. Fills the XP bar while charging. Tip: with a spyglass, give
  the ability `cooldown_starts: manual` and a start_cooldown after it fires: a cooldown on the held
  spyglass ends the zoom),
  await_kill `{ players_only, store }` -> kill (waits for the caster's next elimination, the victim in
  `store`; chain two for "up to 2"), cancel_ability `{ ability }` (end the caster's running casts of
  another ability, e.g. the last shot ends the ultimate).
- **delay:** `boss_bar: true` shows the wait as a boss bar with the ability's name, running out (an
  ultimate's duration).
- **Damage:** `scale_by: <key>` multiplies it by the number stored there (a charge's power);
  `damage_type: freeze | fire | magic` deals it as that vanilla damage, without knockback.
- **Effects:** `self: true` on any effect puts it on the caster instead of whoever was hit (an infusion
  or on-hit that heals the shooter).
- **remove_status:** `stacks: N` only takes N stacks off.
- **counter:** `peek: true` only looks (trigger if counting now would trigger); count separately, e.g.
  only swings that hit something.
- **switch:** `on: { "0": ~ }` - `~` wires a case to nothing (that case just ends).
- **infuse:** `random: [a, b, c]` instead of `infusion:`: each of the `count` bolts gets one, at random.
- **Quiver:** `rapid_fire_while: [tags]` - with one of these tags, no drawing: RMB (or holding it)
  shoots straight from the quiver, as fast as the primary's cooldown allows (the primary needs a
  `reload` node for the empty case, see alchemist.yml).
- **Characters:** `forms:` change the kit while the player has a tag (first match wins):
  `- { while: <tag>, weapon, slots: { ... }, status_bar }`. A SPYGLASS weapon: hold RMB to zoom in and
  charge the primary (a charge node), let go to fire; LMB does nothing.
- **Statuses:** `duration: 0` lasts until removed.
- **Tags:** `state.frozen` (blue hearts, frost overlay and vanilla's powder-snow slow; no vanilla freeze
  damage), `state.flying` (creative-style flight; no fall damage on the landing after it ends).
- **Visuals:** `"block:<Material>"` shows a real block (e.g. `"block:EXPOSED_COPPER_TRAPDOOR"`, flat).
- **Cues:** barrage_rise, barrage_shot, barrage_hit, barrage_blast, barrage_reload.

## Added for Soul Rend, Umbrella and the flask reroll

- **Abilities:** `charges: N` - N uses stored up; each comes back `cooldown` after the last one used.
  It only counts as on cooldown with none left. On the hotbar the stack shows the charges left, and the cooldown sweep
  shows the next one coming back, even while others are left to use (with none left, the stack counts the seconds).
- **Characters:** `traits: [sneak_slow_fall]` - always-on behaviours: hold SHIFT while falling to float
  down (Slow Falling).
- **summon_clone:** `of: <key>` makes it a look-alike of someone else, where THEY stand (it's still your
  summon); `health: N` (design HP) makes it a real target that can be hit and killed, on `of`'s team;
  `glowing: true`. It also stores where it was placed as `<store>_at`.
- **Nodes:** await_summon `{ summon }` -> destroyed / gone (killed, or expired / dismissed),
  dismiss_summon `{ summon }`. find_summon only finds summons that are still alive.
- **link:** `from: <key>` - the tether's owner is someone else (e.g. an enemy tied to their soul);
  `mirror: 0.5` - half the damage the target takes ALSO hits the owner.
- **infuse:** `reroll: true` (with `random:`) - a bolt that already has one of the listed infusions gets
  it replaced by the new roll instead of piling up another.
- **Cues:** thrust_windup, umbrella_thrust (line), soul_rend_ready, soul_rend, soul_tether (line),
  soul_return (line).

## Added for Dream Tempest

- **move_to** `{ to, up, back, look, store, return }`: put the caster at `to` (a key, default where they
  are) raised by `up` blocks and `back` blocks behind them (negative = ahead); `look: down` turns their
  view to the ground below, `look: spot` at the spot they left (a view from the side); `store` keeps the
  spot they were at; `return: true` puts them back there when the cast ends, however it ends.
- **Cues:** dream_tempest (slashes somewhere in a 5-block circle), dream_tempest_ring (that circle's edge
  on the ground, 5 blocks: keep it in step with the ability's radius).

## Character stats

Full sheets and reasoning: `docs/character-stats.md`.

- **Characters:** `stats: { health, armor, base_damage, move_speed, attack_speed }`. Missing stats get
  the defaults: 200 / 0 / 40 / 1.0 / none.
  - `health`: max HP. Everyone is shown as 10 hearts.
  - `armor`: damage taken x 100 / (100 + armor). The 100 is `armor-constant` in config.yml.
  - `base_damage`: what `base:` on a damage effect is a share of.
  - `move_speed`: x vanilla walking; slows and haste multiply on top of it.
  - `attack_speed`: basic attacks per second; the primary's cooldown becomes 20 / it ticks. Leave it
    out to keep the primary's own cooldown (a crossbow).
- **Damage** is any of these, added together:
  - `base: 1.2`: 120% of the caster's base damage (armor reduces it)
  - `max_hp: 0.1`: 10% of the target's max HP (**ignores armor**; also for damage over time)
  - `amount: 50`: flat (armor reduces it)

  For example `{ id: damage, base: 1.2, max_hp: 0.1 }` from an Arcanist with 35 base damage, on a 200 HP
  target: 42 (minus armor) + 20. Backstab and `scale_by` multiply the whole hit.
- **Heal and shield:** `amount:` (flat) and/or `max_hp: 0.2` (20% of the target's max HP), added together.
- **summon_clone:** `health_share: 0.6` instead of `health:`, i.e. 60% of `of`'s max HP.
- **In game:** the stat items are in the top row of the inventory. Hover one to see the value right now.

## Added for the AntiMage (now the Gunner)

- **Characters:** `ward: { name, out_of_combat, hotbar, icon, description }` is a passive debuff
  immunity. After `out_of_combat` ticks without dealing or taking damage, the next **debuff** doesn't
  land. Blocking one uses it up, and it recharges from the later of the last hit and the block. Its item
  sits in hotbar slot `hotbar`: glinting when ready, otherwise the count is the seconds left.
- **Debuff:** a status that isn't a buff (see Buffs), put on you by someone else. Your own statuses and
  buffs never count. The tag `state.debuff_immune` blocks debuffs too (without using anything up).
  To test: `{ id: status, status: stun, from: world }` puts a status on as if from nobody, so it's a
  debuff even on the caster (`self_stun_test` does this). Or run `/ae apply <status> [ticks]` in game.
- **Nodes:**
  - has_status `{ status, target, min_stacks, mine }` -> has / lacks. `mine: true` = only if the caster
    put it there, e.g. your own mark.
  - moving_toward `{ target, angle }` -> toward / away: is the caster walking toward it.
  - random `on: { a: x, b: y, ... }`: one of its ports, at random.
  - ward_reset: the caster's ward is ready right now.
  - spell_shield `{ max }`: until the cast ends, SPELL damage to the caster is absorbed and stored as
    charge, up to `max`. Spells are ability damage from anything but the primary / secondary / melee
    slots, plus damage over time. Basic attacks and vanilla hits still land.
  - shield_charge `{ store }` -> out / full: the stored charge (e.g. for `scale_by`).
- **Effects:** purge_buffs removes every buff from the target.
- **Cues:** null_burst, null_pool (a 3-block ring), spellshield (looping), spellshield_absorb,
  spellshield_blast, spellshield_purge, ward_block, ward_ready.

## Crowd control

What each one stops. While it lasts, the blocked icons (and the weapon, for primary fire) show a barrier;
the passive never does.

| | Primary fire (and melee) | Abilities (secondary, 1-3, ultimate) |
|---|---|---|
| **Stun** (`state.stunned`, with `block.ability` + `block.move`) | blocked | blocked |
| **Silence** (`state.silenced`) | works | blocked |
| **Disarm** (`state.disarmed`) | blocked | works |
| **Root** (`block.move`) | works | only movement abilities (dashes, blinks) and movement recasts are blocked |

`recast_movement: true` on an ability: its RECAST moves you (Dream Echo's swap, Shield Rush), so it waits out
a root. The window stays open and the first cast isn't affected.

The statuses are `stun`, `silence`, `disarm` and `root` (shared.yml). Silence and disarm are checked per
slot, so an ability is only silenced when it's in an ability slot.

- **remember_spot** `{ of, store, ground }` stores where `of` (an entity or a point) is right now, as a
  fixed spot: it doesn't follow the entity afterwards. With `ground: true` (default) it's dropped onto the
  ground below. For example, Volatile Nullifier's pool stays where the flask burst.

## Added for the Gunner

- **Resources:** `refill_sweep: true` - its hotbar item shows a cooldown sweep for the next unit coming back by `regen`
  (e.g. the next shard of ammo); the stack still counts what's there.
- **Resources (ammo):** `reload: <ticks>` - once it's empty, it refills to max that long after the last
  spend. `shown_while: <tag>` / `hidden_while: <tag>` - only shown on the hotbar while you have (or don't
  have) the tag; two resources can share a slot this way (the ammo of the gun in your hand). While it
  reloads, its item shows a cooldown sweep.
- **Characters:** `status_items: [ { status, hotbar, icon, name, description, glint_weapon } ]` - a status
  shown as a hotbar item while you have it, stack = its stacks (e.g. magic rounds left). Several can share a
  slot (the first one you have shows). `glint_weapon: true` makes the weapon glint meanwhile.
- **Nodes:**
  - spend `{ resource, amount, peek }` -> out / empty: spend some of a resource (a bullet); not enough: empty.
  - refill `{ resource, amount }`: back to max (or + amount).
  - repeat `{ times, scale, every, spend }` -> each / out: runs `each` `times` times (a number, or a key
    holding one x `scale`, rounded up), `every` ticks apart, then `out`. `spend: <resource>` costs 1 each
    time and stops early when it runs out. Each `each` branch gets `repeat_index` (1, 2, ...), e.g. for a
    switch that makes the first bullet different.
  - set_off_constructs `{ ability, center, radius }`: your own constructs from `ability` within `radius` of
    `center` (a key, default hit) go off: they exit `triggered`, armed or not, solid or not (e.g. a seed's burst
    setting off traps near the enemy).
  - strike_constructs `{ range, angle | width }`: your own constructs inside this cone (`angle`) or line
    (`width`) are struck, as if your projectile hit them (e.g. shooting your Powder Keg sets it off).
  - spell_block `{ duration }` -> blocked / expired: the first enemy spell that reaches you in that time is
    blocked, its damage and its debuffs (basic attacks and damage over time still land).
- **charge:** `release_gap: N` - for inputs without a let-go signal (RMB on a normal item): the input repeats
  while it's held, and N ticks without a repeat means it was let go. A quick click counts as 0 ticks held;
  a new click while it charges lets the old charge go and casts again.
  `load: { resource, start, every, max }` - instead of power, it loads that resource while held: `start`
  on the press itself, then 1 every `every` ticks (up to `max`, or until it runs out), spent as it loads so the player sees the count drop; `store`
  gets how many (none loaded when let go: `early`). While loaded, the resource doesn't reload.
- **hold_reload** `{ resource }`: restart its reload timer without spending (e.g. each bullet of a volley
  that was already paid for, so the gun reloads after the last shot).
- **Cues:** shotgun_blast, buckshot_blast, revolver_shot (lines: `at: caster, to: aim`), rounds_loaded,
  spell_blocked, keg_throw, keg_blast.

## Added for the Copper Golem

- **Stats:** `scale: 1.2` - model size (and hitbox), 1.0 = normal.
- **Ward:** `absorb: 0.5` turns it into a BARRIER: instead of blocking a debuff, it takes that share off the
  next hit's damage (after armor), then recharges out of combat like a ward. Debuffs land as usual.
- **Characters:** `when_hit: { reduce_cooldowns: <ticks>, slots: [...] }` - an enemy's basic attack
  (primary / secondary / melee) landing on them takes that much off their cooldowns in `slots` (default
  ability_1-3).
- **Statuses:** `move_speed: 0.9` multiplies the holder's speed PER STACK (5 stacks = x0.59): a slow that
  builds up.
- **Radius query:** `inner: 4` - a ring: only what's farther than that from the centre (on the ground), e.g.
  one band of a shockwave rolling outward.
- **start_line** `{ cue, to, every }`: keep drawing a line cue from the caster to `to` every `every` ticks
  (default 2) while the cast lasts: a target, or a projectile stored with `store:` (it follows it in flight
  and stops when it lands), e.g. the Golem's chain.
- **dash:** `stop_short: 1.5` - with `to:`, stop that many blocks before it. With `mover: hit, to: caster`
  it drags whoever was hit to the caster (Groundbreaker).
- **Projectile:** `visual_size: 0.8` - the visual's size when it should differ from the hitbox (`size`);
  `face_flight: true` - an item visual turns to point along its flight (a tool head first).
- **shield:** `lasts: <ticks>` - whatever is left of the shield disappears that long after the last one given.
- **Cues:** barrier_break, golem_swing, anchor_throw, anchor_chain (line), anchor_land, rust_step,
  rust_burst, conduction_slam, shockwave_2 / _4 / _6 / _8 (rings of that radius), rod_charge,
  lightning_strike, electric_field (5 blocks).

## Added for the Whisperer

- **Characters:** `hearing: { below, range, toward: { range, status }, hotbar, icon, name, description }` - a
  passive that hears wounded enemies: enemies below `below` of their max HP within `range` blocks. They glow
  for this player only (through walls), the item in hotbar slot `hotbar` counts them (empty with none), and
  moving toward one within `toward.range` keeps `toward.status` on you (e.g. faster).
- **Nodes:**
  - tether `{ target, duration, range, sight_grace, break_on, cue, stages }` -> complete / broken: hold a
    tether for `duration` ticks; it breaks past `range` blocks, after a wall has been in the way for more than
    `sight_grace` ticks, or when you get a `break_on` tag. Drawn as `<cue>_1` .. `<cue>_<stages>` as it
    charges (fills the cast bar).
  - health_below `{ target, share }` -> below / above: e.g. an execute below 10% HP.
  - input_held `{ within }` -> held / free: is the ability's key still held (it repeated within the last
    `within` ticks)? Needs a `charge` with `release_gap` listening in the same cast (`bar: false` hides its
    cast bar). A tap never counts as held.
- **Motion:** `{ type: seek, range, speed, base, turn, max_distance, hover }` - a seeker: it homes at `speed`
  on the nearest enemy the caster hears (hearing passive), however far, else on any enemy within `range` of
  it; while it homes, distance doesn't count (only `lifetime`). With no target it flies straight at `base`
  for `max_distance` blocks, then hovers there for `hover` ticks (still looking), then expires.
- **Projectile:** `health: 60` gives it a body enemies can hit and kill (an `"entity:..."` visual, on your
  team); killed, the projectile exits `destroyed`. `through_blocks: true` - terrain doesn't stop it.
  `hits_caster: true` - it can hit you too (exits `hit_entity` with you as `hit`) once it has flown clear
  of you, e.g. a flask you throw up and catch.
- **veil** `{ target, duration }` -> won / out: a duel. The caster and the target are pulled into a veil
  for `duration` ticks (a boss bar): they can only affect each other (shots, rays, dashes and effects skip
  anyone across it), and on Bukkit they only see each other (everyone else is hidden from them and them
  from everyone). `won` as soon as the target dies; the veil also lifts if the caster dies. Their abilities'
  cues and projectile/construct visuals are shown only to the two of them (everyone else sees a red mote
  for the caster and a green one for the target); their summons and projectile bodies count as theirs, so
  they work inside the veil.
- **reset_cooldowns** `{ slots: [...] }`: the caster's abilities in those slots are ready at once.
- **Keys:** a stored projectile (`store:`) can be used like a spot: where it is, or where it ended (e.g. `center: shade`).
- **Tags:** `state.darkness` (vanilla Darkness), `state.withered` (Wither's black hearts; the status does the
  damage, not vanilla).
- **Cues:** sickle_rake, dream_step, dream_arrive, dream_rift, rift_close, whisper_bind, whisper_tether_1..4
  (lines), whisper_curse, whisper_snap, shade_aura, shade_execute, shade_dissolve, veil_warning, veil_enter,
  veil_exit.

## Added for the Fae

- **Characters:** `hover: { height, fly, speed, visual, visual_size }` - a passive hover, no fall damage either
  way (without a `height`, or 0: no hover at all, only the `visual` at your feet, and falls hurt as usual):
  - `fly: false`: always floating `height` blocks (whole blocks) above the ground. You walk, jump and drop off
    ledges that much higher, over water too, on a floor of invisible blocks only you see. No flying.
  - `fly: true` (default): double-tap jump to fly, at most `height` blocks above the ground below (higher, you
    sink back down). `speed` is x vanilla flying speed (0.5 = half), scaled by the move speed stat and slows like
    walking. Can't fly while stunned or rooted (you drop). The limit holds off during dashes.
  - Free flight (`state.flying`) overrides either while it lasts. `visual` is what you ride, under your feet: a
    block shows its real model upside down (a spore blossom opens upward), an item lies flat. `visual_size` scales
    it (default 1.0: as wide as you are; /ae reload applies it). `visual_lead: 2` (default) draws it that many ticks
    of your movement ahead of you, so it keeps up instead of trailing behind (0 = right at your feet). `visual_turn: 45`
    turns it that many degrees about its upright axis (it faces where you face; e.g. so a petal isn't straight ahead).
    `visual_up: 0.125` raises it that many blocks above your feet (0.0625 = one pixel).
- **charge:** `bar: held` (with `release_gap`) - the cast bar only shows once the key is really held (its first
  repeat), so a tap shows nothing (e.g. "hold to cancel").
- **Ward:** `cast: <ability>` (with `cooldown: <ticks>`) turns it into a REFLEX: the next enemy hit that gets
  through casts that ability (the next tick, with `target` = whoever hit you). Ready again `cooldown` later, or
  sooner after `out_of_combat` ticks without fighting. Give the ability `blocked_by: []` so it works while stunned.
- **Nodes:**
  - mount `{ target, status, self_status, store, lift }` -> out / none / off: sit on someone's head (you go where
    they go; with `lift: <blocks>` that much higher, on an invisible seat; they don't see you, everyone else
    does), and your hunger bar shows their hearts, until the cast ends, a dismount node, or a new mount; "out" right away. `status` is on them while
    you're up there, `self_status` on you. If the ride ends by itself (they die, the game takes you off, another
    ability's dismount, you press SHIFT to hop off), a branch runs from `off`. The mount is stored as "mount".
  - dismount: off whatever you ride. In the ride's own ability it's quiet (`off` doesn't run); from another
    ability (e.g. an ultimate that leaps off) the ride's `off` branch runs, so it lands and starts its cooldown.
  - leash `{ target, length, duration, pull, max_speed, range, cue }` -> out / broken: drag someone along for
    `duration` ticks: beyond `length` blocks they're pulled toward you (`pull` x the excess per tick, at most
    `max_speed`). Broken: they're gone, or more than `range` (32) away.
  - is_ally `{ target }` -> ally / enemy / none (default target: hit).
- **Dash:** `follow: true` with `to: <entity key>` flies after that entity, re-aimed every tick, and exits `hit`
  (hit = them) on reaching them (above their head). Only walls stop it; it gives up after `range` blocks' worth.
  Anyone dashing has the tag `state.dashing`.
- **Projectile:** `hits_allies: true` - allies don't let it through (it exits `hit_entity` for friend and foe).
  `bounce_walls: true` - it bounces off walls and ceilings instead of sticking to them (too slow to bounce: it
  slides off), and lands only on the ground (with `restitution` / `friction` as for `bounces`).
  `bounce_off_own: true` - it bounces off your own constructs from the same ability (traps too, solid or not),
  mirrored off them and a little slower, e.g. a thrown trap glancing off one already planted.
- **Traps (construct):** `triggered_by: enemies | allies | all` (never the owner) `| everyone` (all and the owner
  too); `hidden: true` - only the owner
  and their allies see it (and hear it arm). Any construct: `cue: <id>` plays every `cue_every` ticks (default 10)
  while it stands.
- **Recasts:** while a cast whose cooldown is still waiting runs (`after_recast` or `manual`), a held key's
  repeats can't start a second one. An await_recast doesn't start a `manual` cooldown.
- **Cooldowns:** `start_cooldown { restart: true }` starts the full cooldown over from now, even if it already
  started (e.g. every time a fae perches). `on_cooldown { ability }` -> cooling / ready (default: this ability),
  e.g. a recast (free by itself) that should wait for the cooldown: send `cooling` back to the await_recast.
- **Tags:** `state.nauseous` (vanilla Nausea), `state.poison_hearts` (Poison's green hearts; the status does the
  damage, vanilla Poison's own is cancelled).
- **Cues:** fae_blossom_shot, fae_blossom_burst, fae_spore_burst, fae_seed_throw, fae_seed_idle, fae_seed_wilt,
  fae_seed_latched / fae_seed_latched_ally (looping), fae_seed_burst, fae_seed_bloom, fae_flit, fae_perch,
  fae_unperch, fae_gust, fae_trap_throw, fae_trap_wilt, fae_trap_spring, fae_wings, fae_vine_shot, fae_latch,
  fae_vine (line), fae_release.

## Added for the Pyromancer

- **Statuses:** `decay: <ticks>` (with a `duration`): when its time is up it doesn't end at once, it loses a stack
  every `decay` ticks (a gauge cooling down). Applying it again stops that and starts the duration over.
  `at_max: <status>`: reaching its `max_stacks` puts that status on the holder too (a full gauge sets off a state).
  `requires: <status>`: it only lasts while the holder has that one (it ends with it, and doesn't land without it).
  `cue: <looping cue>`: runs on the holder while it lasts (their character's variants apply).
- **landings** `{ ticks, store }` -> landed / out: for `ticks`, every time the caster lands (after at least 3 ticks
  in the air: a jump, a fall) "landed" runs as its own branch, with the spot on the ground stored as `store`.
- **Statuses:** `healing_taken: 0.6` - the holder heals that much of any healing (0.6 = 40% less): heal effects,
  lifesteal, vanilla regeneration. Several multiply. The poisons (the Fae's toxin, the Alchemist's Poison) have it.
- **Statuses:** `jump_boost: 3` - Jump Boost of that level while it lasts (3 = Jump Boost III); with several, the
  highest counts. (The old tag `state.jump_boost` still works: Jump Boost II.)
- **Characters:** `status_bar: { status, fill: stacks }` fills the XP bar with the status's stacks out of its
  `max_stacks` (a gauge) instead of its time left.
  `variants: [ { while: <tag>, cue_suffix: _blue, visuals: { "block:FIRE": "block:SOUL_FIRE" } } ]`: while they have
  the tag their abilities look different: every cue `x` plays as `x_blue` where that cue exists (otherwise as `x`),
  and projectiles shown as a `visuals` key are shown as its value (e.g. Hellfire Inferno's blue flames).
- **Projectile:** `trail: <cue>` plays a cue where it is every `trail_every` ticks (default 1) while it flies or
  hovers (it's the caster's cue: variants apply). `from: <key>` starts it there instead of the caster's eyes,
  `up` blocks above it and `back` blocks back toward the caster; `toward: <key>` flies at that key instead of along
  the aim (a meteor: `from: aim, up: 28, back: 10, toward: aim`).
- **Motion seek:** `to: <key>` (and `up`) - with no target it flies to that spot (e.g. `aim`) and hovers there for
  `hover` ticks, instead of flying `max_distance`. `lock: true` - it keeps after the first enemy it found until
  they're gone. `mark: <status>` - whoever it's after keeps that status meanwhile (e.g. `glowing`).
- **Cues:** each in orange and `_blue`: pyro_bolt_cast, pyro_bolt_trail, pyro_bolt_hit, pyro_bolt_fizzle,
  pyro_fireball_cast, pyro_fireball_trail, pyro_fireball_explode, pyro_wisp_cast, pyro_wisp_trail, pyro_wisp_explode,
  pyro_wisp_fade, pyro_coals (looping: coals tossed hand to hand), pyro_coal_scorch (also leaves fire on the ground,
  only a look, for 4s and 1.8 blocks: keep it in step with the patch), pyro_coal_patch (1.8 blocks),
  pyro_judgment_cast, pyro_judgment_ring (a 6-block ring on the ground: keep it in step with the ability's radius),
  pyro_meteor_fall, pyro_meteor_trail, pyro_meteor_impact, pyro_scorched (6 blocks). Blue only: pyro_overheat
  (looping, with its own start and end).

## Added for the Sylvan

- **Forms:** `stats: { ... }` - a form with stats of its own (only the ones that change; the rest are the
  character's): max HP, armor, base damage, speed, attack speed and size (`scale`) all follow the form while it
  lasts (the share of health she has is kept when max HP changes). A form slot set to `none` is empty meanwhile
  (e.g. a stage with no primary fire). `slots: {}` changes nothing.
- **Slots:** `sneak` - SHIFT: pressing it casts the slot, letting go lets its charge go (a `charge` node: hold SHIFT
  to ...). No hotbar icon. Riding someone, SHIFT still hops off instead.
- **Statuses:** `then: <status>` - when its time runs out (not when it's removed early) that status goes on the holder:
  one stage growing into the next. `far_damage_taken: { beyond: 8, multiplier: 0.5 }` - hits from attackers more than
  `beyond` blocks from the holder are multiplied (a domain: half damage from outside it, 0 = immune).
- **Effects:** teleport `{ away_from: <key>, distance: 8 }` - instead of `to`: that many blocks straight out from the
  key, level with where they are (everyone in an area flung out of it).
- **Tags:** `block.displace` - nobody else can move them: no pulls (leash), drags (another's dash with `mover`),
  teleports or swaps; with `block.knockback`, completely unmovable. `block.walk` - held where they stand (no walking or
  jumping) WITHOUT a root's slowness, so the view doesn't zoom in; abilities still work (e.g. planted). A root
  (`block.move`) still zooms the view in: that's the game's own slowness effect.
- **Traps (construct):** `trigger_allies: 1.2` - allies (when they can set it off) have to come that close instead of
  `trigger` (e.g. a fruit enemies set off from 3 blocks that allies walk up to).
- **Cues:** sylvan_buried, sylvan_sapling, sylvan_tree, sylvan_large_tree (looping, one per form: her look, for now a
  block on her head, and the trees' domain edge, 8 / 14 blocks: keep them in step with far_damage_taken. A model can
  replace them later under the same ids), sylvan_seed_shot, sylvan_seed_hit, sylvan_blink, sylvan_burrow,
  sylvan_uproot, sylvan_thorn_shot, sylvan_thorn_hit, sylvan_root_shot, sylvan_rooted, sylvan_scatter (3.5 blocks),
  sylvan_sap_shot, sylvan_sap_hit, sylvan_fruit_fall, sylvan_fruit_bomb, sylvan_lash_throw, sylvan_root_line (line),
  sylvan_drag, sylvan_strength_sap (8 blocks), sylvan_fruit_drop, sylvan_fruit_idle (also its 3-block reach on the ground), sylvan_fruit_burst,
  sylvan_fruit_eaten, sylvan_fruit_compost, sylvan_fruit_smashed, sylvan_deep_roots (14), sylvan_screech (14),
  sylvan_walk, sylvan_drain (14), sylvan_take_root.

## Added for the Fae's toadstool (Deathcap Snare)

- **Aim previews:** `arc: <node>` - for a thrown projectile: names the ability's projectile node, and the preview shows
  where that throw would come down if thrown right now (its speed, gravity and drag, bouncing off walls with
  `bounce_walls`), instead of where the crosshair is. Confirming throws it along the aim as usual, so it lands where
  the preview was (unless someone's in the way). `range` isn't needed with it.
- **Radius query:** `sight: true` - only those the centre can see: nobody behind a wall or around a corner (e.g. a burst
  that doesn't go through walls).

## Added for the Berserker

- **Characters:** `low_health: { below: 0.4, status: <status> }` - a passive: while their health is below that share of
  their max HP, the status is kept on them (it drops off within half a second once they're healed back above it). Show
  it with a `status_items` entry (and `glint_weapon: true`).
- **Abilities:** `passive_while: <tag>` - while the caster has that tag the ability is passive: it's in effect anyway
  (e.g. an ultimate keeps its buff up), so its icon glints and pressing its key does nothing (no cast, no cooldown,
  no message).
- **apply_effects:** `count_players: <key>` - like `count`, but only the players it affected (mobs, summons and
  projectile bodies don't count), e.g. "a basic attack that lands on a player": a `switch` on the key after it.
- **Tags:** `state.unstoppable` - crowd control doesn't hold them. The crowd-control parts of a debuff are a slower
  move or attack speed and the tags `state.stunned`, `state.silenced`, `state.disarmed`, `state.rooted`,
  `state.slowed`, `state.frozen`, `state.paralyzed`, `block.ability`, `block.move`, `block.walk`; while they're
  unstoppable those parts are switched off, and everything else about the debuff still works: a slowing poison (the
  Fae's toxin) still poisons them, it just doesn't slow them. If it's still on them when they stop being unstoppable,
  it slows them again for the time it has left. A debuff that's nothing but crowd control (a stun, a root, a plain
  slow) doesn't land at all, and ends as they become unstoppable. Give `block.knockback` and `block.displace` too for
  "nothing moves them".
- **Cues:** berserker_chop, berserker_leap, berserker_spin (3.2 blocks), berserker_war_cry_charge, berserker_war_cry,
  berserker_war_cry_aura (looping), berserker_cleave_windup, berserker_cleave_ring (rings of 2.5 and 5 blocks on the
  ground: keep them in step with Reaping Cleave's radii), berserker_cleave, berserker_bloodlust (looping),
  berserker_rampage_start, berserker_rampage (looping).

## Added for the Valkyrie

- **Hover:** `fuel: <resource>` and `fuel_drain: 20` (per second) - with `fly: true`, flying drains that resource (a
  gauge); empty, they drop and can't take off again until it's back to a quarter. The resource's own `regen` and
  `delay` refill it.
- **Traits:** `slow_fall` - always Slow Falling while falling (not flying or gliding); `elytra` - an elytra in the
  chest slot (jump while falling to glide; a chestplate of their own stays, and then there's no elytra).
- **Tags:** `state.gliding` - on whoever glides on an elytra right now (e.g. `has_tag` for an ability that's different
  while gliding).
- **Effects:** `cleanse` - removes every debuff (a non-buff status someone else put on them).
- **Forms:** a form may change `secondary` (RMB) too, e.g. LMB / RMB picking someone while an ultimate chooses. A BOW
  weapon (in a form) works like any weapon: LMB fires the primary.
- **Cues:** valkyrie_slash, valkyrie_thrust, valkyrie_dive, valkyrie_stab, valkyrie_smite, valkyrie_flit, valkyrie_tether_1 (line),
  valkyrie_mend, valkyrie_valor, valkyrie_tether_snap, valkyrie_bow, valkyrie_arrow_shot, valkyrie_light_burst (3 blocks),
  valkyrie_blessing_open, valkyrie_blessing, valkyrie_blessed (looping).

## Added for the Amethyst

- **Projectile hits:** every `hit_entity` gets `hit_index`: which enemy along its path this is, 0 for the first, 1 for
  the one behind it (with `pierce`)... E.g. `switch { key: hit_index, on: { "0": full, default: less } }`.
- **Barrier:** every enemy projectile it absorbs runs its `absorbed` port as a branch of its own, with `absorbed_at`
  (where it was caught) and `absorbed_from` (whose it was). `reflect: true` sends what it catches back at the shooter:
  a copy of their own shot (the same projectile and its hit logic, the shot's values carried over), now cast by the
  barrier's owner, so its damage and effects land on the shooter and their side. `around: true` makes it a shell all
  the way around its owner (a sphere of `radius`): projectiles, rays, dashes and melee are stopped from every side.
- **Constructs as lingering shots:** a projectile's `expired` into a `construct` `at: <its store key>, height: 0` leaves
  it hanging where it ran out; `set_off_constructs` (e.g. on RMB) makes them all exit `triggered`, where a projectile
  `from: construct, toward: caster, hits_caster: true` flies it back.
- **Abilities:** `needs_constructs: <ability>` - it can only be used while the caster has constructs from that ability
  standing (e.g. recalling shards left hanging). Its icon's stack counts them; with none its cooldown sweep stays full
  (greyed out) and its key does nothing. `also_flying: <node>` counts that ability's projectiles from that node still
  flying too.
- **cast** `{ ability }`: the caster casts another ability of theirs right now, as if they'd pressed its key (with its
  own checks), e.g. a channel that, full, fires the same volley its LMB can fire early.
- **end_projectiles** `{ ability, node }`: the caster's projectiles from that ability (only those its `node` launched;
  default any) end right where they are, mid-flight: each exits `expired` there (e.g. a shard that then hangs, to be
  recalled with `set_off_constructs` right after).
- **Constructs:** `fuse_spread: 30` - each one's fuse is its `fuse` give or take up to that many ticks, at random (so
  they don't all end together). A non-solid one's visual `"hover:<item>"` stands upright, facing whoever looks at it, and
  bobs gently in the air (each at its own pace), e.g. a shard hanging where it stopped. `"hover:glow:<item>"` also makes
  it glow for its owner only (nobody else sees the glow).
- **Cues:** amethyst_shard_shot, amethyst_shard_hit, amethyst_shard_linger, amethyst_shard_fade, amethyst_shard_caught,
  amethyst_recall, amethyst_recall_hit, amethyst_gather, amethyst_volley_shot, amethyst_burst, amethyst_ward (looping),
  amethyst_reflect, amethyst_orbit (looping: one more shard circling her each time), amethyst_orbit_fire (the oldest
  circling shard goes, as the volley's shot), amethyst_choose, amethyst_encase, amethyst_crystal (looping), amethyst_shatter.

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
- **Tags with effects in game:** `state.resistant` = 40% less damage taken, `state.slowed` = -40% speed,
  `state.hasted` = +30% speed, `state.invisible` = invisible (held items still show, like vanilla).
- **Projectile visual:** an item Material (`DIAMOND_BLOCK`), or `"entity:<EntityType>"`
  (`"entity:END_CRYSTAL"`). Arrow types (`"entity:ARROW"`, `SPECTRAL_ARROW`, `TRIDENT`) are real arrows
  flown by the game itself (vanilla drop and drag: `motion` and `bounces` don't apply, `speed` is the
  launch speed). The engine still checks their path every tick, so enemies, allies, barriers and
  constructs work as usual; they can't hurt anything by themselves or be picked up. An infused bolt's
  `ARROW` is tinted in its infusions' colors.

### Targeting (aim previews)

Add `targeting: { shape: circle|line|cone|point, range, radius/width/angle, ground: true, max_drop }`
to show a preview first: LMB confirms, RMB cancels. No time limit unless you add `timeout: <ticks>`.
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

See `hunter.yml`.

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
  `level: none` shows no number at all (a plain countdown bar).
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

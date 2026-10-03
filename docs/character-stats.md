# Character stat sheets (proposal)

Status: **implemented** (the sheets below are in the kits' `stats:`). Numbers are first-pass. They were
derived from what the kits dealt before, so switching to stats didn't change how anything feels; tune
from here.

### Decisions

- **Players see their stats:** the top row of the inventory holds one item per stat. Hovering one shows
  its value right now: current health, Strength on base damage, a slow on move speed, the Alchemist's draw.
- **Armor applies to everything** (abilities, basic attacks, vanilla hits, falls) **except damage over
  time and % max HP hits.** Damage over time is % max HP now: poison, frost and flame are 5% / 5% / 3%
  of the target's max HP a tick, the shared burn 2.5%. Tether redirect and soul mirror damage stay
  final: not reduced again.
- **Heals and shields are flat**, unless an effect says `max_hp:`.
- **Alchemist has no attack speed stat:** his crossbow draw (and Hunter's Rhythm) is his fire rate, and the
  stat item shows the draw time instead.
- **Placeholders fixed:** Arcane Bolt deals 100% base damage on a direct hit (35% splash on the ground).
  The Umbrella has a real primary, Spear Poke (a 3.5-block line, 100%).
- **Soul Rend's soul** looks like its owner and has **60% of their max HP** (`health_share: 0.6`).
- **Move speed** is added to the base speed, so slows (-40%) and haste (+30%) multiply on top of it.
- **Hearts:** everyone shows 10 hearts; the real HP is behind them (and on the Max HP item).

All health and damage are in **design HP**, the unit the YAML already uses: 10 design HP = half a heart,
and a vanilla player (20 health) = 200.

---

## 1. The stats

| Stat | What it does | Unit | Default (a kit without a sheet) |
|---|---|---|---|
| **Max HP** (`health`) | Health pool | design HP | 200 |
| **Armor** (`armor`) | Damage taken x `100 / (100 + armor)` | points | 0 |
| **Base damage** (`base_damage`) | Abilities and basic attacks deal **% of this** | design HP | 40 |
| **Move speed** (`move_speed`) | x vanilla walking speed (1.0 = 4.3 blocks/s walk, 5.6 sprint) | multiplier | 1.0 |
| **Attack speed** (`attack_speed`) | Basic attacks per second: the primary's cooldown = `20 / attack_speed` ticks | attacks/s | from the primary's cooldown |

### Armor: `reduction = armor / (armor + 100)`

| Armor | 0 | 10 | 25 | 40 | 50 | 100 | 200 |
|---|---|---|---|---|---|---|---|
| Damage reduced | 0% | 9% | 20% | 29% | 33% | 50% | 67% |
| Effective HP of 200 HP | 200 | 220 | 250 | 280 | 300 | 400 | 600 |

- Every armor point is worth the same **+1% effective HP**, so armor never runs away or caps out.
- The constant (100) would be one setting in `config.yml` (`armor-constant`).

### Base damage: `damage = base_damage x %`

Abilities say *"110% base damage"* instead of *"50"*.

- **Tuning a character's overall damage is then one number.**
- Buffs that raise base damage (or Strength's x1.25) scale every hit at once.
- A flat `amount:` would still work for things that shouldn't scale (environment, tests).

### Writing an ability's damage

Every damage effect is a sum of parts:

    damage = base% x caster's base damage   +   max_hp% x target's max HP   +   flat amount
             (armor reduces it)                 (armor does NOT)                (armor reduces it)

```yaml
- { id: damage, base: 1.2, max_hp: 0.1 }   # 120% base damage + 10% of their max HP
```

For example, an Arcanist with 35 base damage hitting a 250 HP Essence Reaver (25 armor) with that deals
`42 x 100/125 + 25 = 33.6 + 25 = 58.6`. The tables below give each ability's base% today; a max HP part
can be added to any of them.

### How the stats combine with what already exists

- **Strength / `damage_dealt`** multiplies *after* base damage.
- **`damage_taken` statuses and the Radiant Bond tether** apply *before* armor, so every reduction stacks multiplicatively.
- **Backstab (x1.5) and `scale_by`** (Arcane Barrage's charge) multiply the % as today.
- **Slow (-40%) and haste (+30%)** multiply the move-speed stat. For example, the Dreamer at 1.10 slowed → 0.66.
- **Hearts on screen:** proposal: show every character as 10 hearts (`setHealthScale(20)`), with the real HP behind it. Otherwise a 300 HP Vanguard has 15 hearts over two rows.

---

## 2. Sheets

EHP = effective HP against damage that armor reduces = `HP x (1 + armor/100)`.
Basic DPS = base damage x attack speed (a single target, no crits or passives).

### Vanguard: Tank / Support

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **300** | **40** (29%) | **45** | **0.95** | **1.4** (14 ticks) | 420 | 63 |

The frontliner: the most health and armor, a little slower. Bulwark (half knockback) and Radiant Bond
already protect his team, so his own damage stays middling.

| Ability | Now | As % of base (45) |
|---|---|---|
| Valiant Strike (primary) | 45 | **100%** |
| Heroic Leap: slam | 50 | **110%** |
| Heroic Leap: shield per enemy hit | 20 | flat, or **7% max HP** (see Q3) |
| Hero's Descent: impact | 60 | **135%** |
| Hero's Descent: shield | 120 | flat, or **40% max HP** (see Q3) |

### Essence Reaver: Bruiser / Sustain

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **250** | **25** (20%) | **35** | **1.00** | **1.67** (12 ticks) | 312 | 58 |

Durable, but his real toughness is lifesteal and Overflow. Fast swings for small hits keep the heals coming.

| Ability | Now | As % of base (35) |
|---|---|---|
| Reaver Strike (primary) | 35 | **100%** |
| Vital Overflow cleave (3rd hit) | 45, heals 100% of it | **130%** |
| Essence Absorption: attach | 30 | **85%** |
| Essence Absorption: recall | 40, heals 100% of it | **115%** |
| Meditation heal (per tick) | 12 | flat (see Q3) |
| Soul Rend: strike | 40 | **115%** |
| Soul Rend: soul health | 150 | flat, or % of the *victim's* max HP (see Q6) |
| Soul Rend: backlash | 90 | **260%** |
| Soul Rend: your heal | 150 | flat, or **60% max HP** (see Q3) |

### Umbrella: Diver

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **225** | **20** (17%) | **40** | **1.05** | **1.25** (16 ticks) | 270 | 50 |

Gets in with two Royal Lunges and Parasol Drift, and holds with Parasol Guard: medium health, a little faster.
**Her primary is still the `test_blast` placeholder** (a 20-block ray, 3s cooldown). The attack speed here
assumes a real spear poke (see Q5).

| Ability | Now | As % of base (40) |
|---|---|---|
| Primary (placeholder test_blast) | 40 | **100%** |
| Royal Lunge (x2 charges) | 50 | **125%** |
| Piercing Thrust | 55 | **140%** |
| Sovereign Geyser | 180 | **450%** |

### Dreamer: Assassin

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **180** | **10** (9%) | **40** | **1.10** | **2.0** (10 ticks) | 198 | 80 (120 from behind) |

Fastest and most fragile melee. He wins by getting behind people (backstab x1.5), not by trading hits.

| Ability | Now | As % of base (40) |
|---|---|---|
| Dream Blade (primary) | 40, x1.5 from behind | **100%** |
| Crescent Rush (and its echo) | 55 each, x1.5 from behind | **140%** |
| Dream Tempest (per 0.2s pulse) | 10 (150 over 3s) | **25%** (375% total) |

### Alchemist: Marksman

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **175** | **10** (9%) | **45** | **1.00** | draw-based: ~0.8/s, up to ~2/s | 193 | 36 → ~90 |

Squishy ranged damage. His fire rate already comes from the crossbow draw (1.25s) and Hunter's Rhythm
(Quick Charge 2/3/4). Proposal: **attack speed doesn't apply to crossbows** (see Q4). The number above is
only for comparison.

| Ability | Now | As % of base (45) |
|---|---|---|
| Infused Bolt (primary) | 45 | **100%** |
| Snare mark bonus | +40 | **90%** |
| Volatile Flask | 50 | **110%** |
| Poison (per second, 4 ticks) | 10 (40 total) | **22%** per tick (90% total) |
| Frost (per second, 3 ticks) | 10 (30 total) | **22%** per tick (67% total) |
| Flame (per 0.5s, 6 ticks) | 6 (36 total) | **13%** per tick (80% total) |
| Vitality (heal on hit) | 30 | flat, or % max HP (see Q3) |
| Flask tonic (self heal) | 6 x 6 | flat (see Q3) |

### Arcanist: Mage

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **175** | **5** (5%) | **30** | **1.00** | **1.25** (16 ticks) | 184 | 37 (at 100%) |

The least armor: control (Missile stun, Binding) and burst (Barrage) from range. **Arcane Bolt deals 4
damage today** (its burst only), which looks like a leftover. The sheet assumes it becomes 100% base
damage (30) (see Q5).

| Ability | Now | As % of base (30) |
|---|---|---|
| Arcane Bolt (primary) | **4** (!) | **100%** proposed (13% today) |
| Arcane Missile: plain hit | 25 | **85%** |
| Arcane Missile: empowered explosion | 50 | **165%** |
| Unstable Binding: shatter | 30 | **100%** |
| Arcane Barrage: direct hit | 220 x charge (40-100%) | **730%** x charge |
| Arcane Barrage: terrain blast | 90 x charge | **300%** x charge |

### Gunner: Gunslinger Anti-Caster

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **210** | **15** (13%) | **38** | **1.10** | shotgun 0.7s, revolver 0.4s (their own cooldowns) | 242 | ~40 (revolver, sustained) |

Two guns: LMB is a shotgun (2 shells), RMB a revolver (6 bullets; it fires when you let go, holding loads more). The gun you used
last is in your hand and its ammo shows in slot 8; an empty gun reloads by itself. Null Ward (passive) shrugs
off one debuff after 6s out of combat; Counterspell blocks a spell and loads magic rounds.

| Ability | Damage |
|---|---|
| Scattergun (LMB) | **130%** to everyone in a 7-block, 45 degree cone; 2 shells, reload 1.5s |
| Six-Shooter (RMB) | fires on let-go: press loads 1, holding loads up to 6; **55%** for the first, **45%** each after; 6 bullets, reload 2s |
| Buckshot | **160%** in a 9-block, 70 degree cone, knockback, 1.5s slow; recoil throws you back; refills the shotgun |
| Volatile Nullifier | **90%** + 2s silence, then a 4s silencing pool; caught in it yourself: speed, Null Ward ready, 3 magic rounds |
| Counterspell | blocks the first enemy spell for 2s (damage and debuffs); blocked: speed and 3 magic rounds |
| Powder Keg (ult) | **200%** in 5 blocks, burn **12% of their max HP** over 3s, knocked away; shoot it to set it off early |

Magic rounds (the next 3 shots of either gun, or Buckshot): Blind 1.5s, Weakness 3s (-25% damage, 30% slower
attacks) or Silence 1.5s.

### Copper Golem: Bruiser

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **320** | **45** (31%) | **34** | **0.92** | **1.0** (20 ticks) | 464 | 34 |

20% bigger than a player. Cuprous Might: after 5s out of combat a copper barrier takes half of the next hit;
enemy basic attacks landing on him take 0.5s off his abilities' cooldowns.

| Ability | Damage |
|---|---|
| Anchor Swing (primary) | **100%** in a wide 3.5-block arc |
| Groundbreaker | **90%** + 1s stun, and the chain drags them to him |
| Rustbreaker | shield up to 120 over 2s (slower and slower), 1s stun on himself, then **130%** (4.5 blocks) + 2s disarm |
| Conduction Field | **80%** + knock-up, a ring rolling out to 8 blocks |
| Lightning Rod (ult) | **220%** (5 blocks) + 2s paralysis, then 6s: faster, a field that deals **30%** every 0.5s and keeps them paralyzed |

### Whisperer: Diver

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **200** | **10** (9%) | **50** | **1.05** | **1.67** (12 ticks) | 220 | 83 |

Hears enemies below 40% HP within 30 blocks (they glow for them only; +15% speed moving toward one within 15).

| Ability | Damage |
|---|---|
| Sickle Rake (primary) | **100%** (5 HP), 3 blocks |
| Dream Step | blink 8 blocks, blinds 1.5s around the exit; press again within 3s to return to the rift |
| Binding Whisper | 2s tether (7 blocks, breaks past 9 / out of sight 0.5s / stunned or silenced: 40% cd back); held: Darkness + Blindness + 4 HP wither over 4s |
| Chorus Shade | 6 HP flat; executes below 10% HP; flies through terrain, 12 blocks then hovers 3s; homes on heard enemies (any distance) or anyone within 6; enemies can kill it (6 HP) |
| Into the Veil (ult) | 7s 1v1 in place, both in Darkness: only the two see and can affect each other (their abilities' visuals too; outsiders see a red and a green mote); +20% damage, cooldowns reset; win: heal 8 HP |

### Fae: Support / Trapper

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **160** | **5** (5%) | **35** | **1.00** | **1.25** (16 ticks) | 168 | 33 (more with splash) |

Half size. Sits in her blossom (just the look; her first passive is still to come). The next enemy hit
on her knocks back enemies within 4 blocks (10s, or 4s out of combat).

| Ability | Damage |
|---|---|
| Blossom Shot (primary) | **75%** within 2.2 blocks of where it bursts |
| Seed Bomb | 6s; latches onto friend, foe or her (or waits 15s on the ground, 3 at most): 2s later **110%** to enemies within 3.5 blocks, or heals allies within 3.5 blocks 4 HP |
| Perch | sit in her blossom on an ally's head (they don't see her, others do): a 60 HP shield for them (2s); untouchable; they're +20% faster, +2 HP per basic attack; 10s cd from sitting down; again (off cd, restarts it): +35% speed 2s or fly to another ally; shift: off |
| Deathcap Snare | 3 charges, 12s each; an aim preview shows where the throw lands; bursts on the ground or whoever it hits: **100%** within 3.5 blocks (not through walls) + 8% max HP poison (green hearts), 35% slow and nausea for 4s |
| Wild Hunt (ult) | 8s free flight; F again shoots a vine (20 blocks, small hitbox): an enemy it catches is stunned 3.5s and dragged along on a 4-block vine |

### Pyromancer: Mage / Burst

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **180** | **0** (0%) | **30** | **1.00** | **2.0** (10 ticks) | 180 | 60 (120 overheated) |

Overheat (passive, the XP bar and hotbar slot 9): every enemy her abilities hit is a stack (8 at most; burning and
her scorched ground don't count); 3s without a hit and it's back to 0 at once. Full: she overheats while she keeps
hitting: blue flames, Fire Bolts every 5 ticks (x2), +15% speed.

| Ability | Damage |
|---|---|
| Fire Bolt (primary) | **100%** magic damage |
| Fireball | **160%** within 3.5 blocks, small knockback, burn (12% max HP over 3s) |
| Hunting Wisp | sent to a spot (25 blocks); waits up to 30s for an enemy within 7, hunts the first one (they glow): **140%** within 3 blocks + burn |
| Hot Coals | 6s of Jump Boost III; every landing scorches the ground for 4s (1.8 blocks): enemies in it are slowed 35% and burn |
| Scorching Judgment (ult) | marked 6-block area (everyone sees its edge): ~2s later a meteor, **300%** + knockback + burn; then 8s of scorched ground, **15%** every 0.5s + burn |

### Sylvan: Grows from Skirmisher to Siege Tree

Starts as a seed; Take Root plants her, and each stage grows into the next (3s buried, 20s a sapling, 60s a young
tree, then a large tree for good; a death makes her a seed again). From the sapling on she can't walk (held in
place, not slowed: her view isn't zoomed in). The young and large tree can't be knocked back or moved at all.

| Stage | Max HP | Armor | Base dmg | Move speed | Attack speed | Size |
|---|---|---|---|---|---|---|
| Seed | **120** | **0** | **24** | **1.15** | **1.6** (13 ticks) | 0.45 |
| Sapling | **160** | **10** (9%) | **28** | planted | **2.0** (10 ticks) | 0.7 |
| Young tree | **250** | **25** (20%) | **32** | planted | **1.4** (14 ticks) | 1.15 |
| Large tree | **380** | **40** (29%) | **36** | planted (0.8 walking) | no primary | 1.7 |

| Stage | Ability | Damage |
|---|---|---|
| Seed | Seed Shot (primary) | **80%** + a little knockback |
| | Blink | up to 7 blocks where she looks |
| | Take Root | 0.5s untouchable, 3s buried, then a sapling |
| Sapling | Thorn (primary) | **100%** + a little knockback |
| | Root Snare | through everyone (18 blocks): **60%**, rooted 2s |
| | Scatter | 3.5-block area: **50%**, teleported 8 blocks out of it |
| | Uproot (hold SHIFT 1.5s) | a seed again |
| Young tree | domain (8 blocks) | half damage from enemies outside it |
| | Sticky Sap (primary) | **90%**, slowed 30% 2s |
| | Fruit Bomb | falls on a spot: **130%** within 3 blocks, knockback |
| | Root Lash | **70%**, held 3s; again: dragged 8 blocks toward her crosshair |
| | Strength Sap | allies in the domain: +20% damage, +20% speed, 5s |
| Large tree | domain (14 blocks, shown) | immune to enemies outside it |
| | Windfall | up to 5 fruits, 10s: an enemy within 3 sets it off, **100%** within 3 + stun 1.5s; an ally walking up to it heals 12% max HP; expired heals her 4% |
| | Deep Roots | whole domain: **40%**, rooted 2s |
| | Screech | whole domain: knocked away, disarmed 3s |
| | Walking Tree (ult) | walks 8s, no abilities: **30%** every 0.5s to enemies in the domain, all of it heals her |

### Berserker: Bruiser / Juggernaut

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **260** | **20** (17%) | **42** | **1.00** | **1.15** (17 ticks) | 312 | 48 (~84 bloodlusted) |

Bloodlust (passive): below 40% health, +25% damage and 40% faster swings. He sustains through War Cry's lifesteal
and Reaping Cleave's blade, and Rampage keeps him from being peeled.

| Ability | Damage |
|---|---|
| Axe Chop (primary) | **100%** in a wide 3.3-block arc; 30% lifesteal during War Cry; landing on a player: Whirling Leap -0.25s |
| Whirling Leap | a ~2.5-block hop, then a spin: **90%** within 3.2 blocks, slowed 40% 1.5s |
| War Cry | 0.5s charge, then 5s: +25% speed, Axe Chops heal 30% of their damage |
| Reaping Cleave | 0.75s windup, then **90%** within 5 blocks; the outer band (2.5-5) also takes **8% max HP** true damage, all of it healed |
| Rampage (ult) | 8s: unstoppable (no crowd control, knockback or displacement), +20% speed, War Cry the whole time |

### Valkyrie: Diver / Skirmisher

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **220** | **15** (13%) | **38** | **1.05** | **1.4** (14 ticks; 2.06 at 5 Fervor) | 253 | 53 (78 at 5 Fervor) |

Wings (passive): gliding on an elytra, Slow Falling (no fall damage). Fervor (passive): each enemy hit, a stack
(5 max, 3s): +8% attack speed a stack.

| Ability | Effect |
|---|---|
| Gilded Slash (primary) | **100%** in a 3.2-block arc |
| Valkyrie's Leap (1, 8s) | leap + a 4-block half circle: **100%**, attack reset; on a hit again (x2); then soar up and dive onto a spot: **140%** in 4 blocks, knocked up. Not while gliding |
| Valkyrie's Charge (2, 10s) | on foot: a 6-block dash, **80%**, flung behind her, stunned 1s; gliding: home onto an enemy (25 blocks), **60%**, carry them 8 blocks: into terrain **15% max HP**. A hit: a shield of 15% her max HP (4s) |
| War Cry (3, 16s) | she and allies within 8 blocks: +25% speed, +20% damage, 4s |
| Divine Ward (ult) | LMB an ally / RMB herself: no damage for 4s, can't die |

### Amethyst: Mage / Zone Control

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **180** | **15** (13%) | **45** | **1.00** | **5.0** (4 ticks; 1.5 out of ammo) | 207 | 90 (27 out of ammo) |

Lingering Shards (passive): 6 shards of ammo; they pierce (half damage to those behind the first) and hang at full
range for 5s (6 at most); Recall brings them back into the ammo, which also refills a shard every 5s. Out of ammo: plain, slower shards.

| Ability | Effect |
|---|---|
| Amethyst Shard (primary) | **40%** to the first, **20%** to up to 3 behind; at full range it hangs 5s. Out of ammo: **40%**, no pierce |
| Recall (3, 10s) | every hanging shard flies back (and into the ammo): **45%** per shard that hits, slowed x0.88 per shard 2s, bleeding 4s (6% max HP, -40% healing, -15% damage) |
| Shard Volley | a channel: gathers up to 6 (0.45s each, circling her; x0.9 speed per shard, nothing else usable), LMB looses them early, full by itself: each hit slows 30% 1.5s and bursts behind them, **60%** within 2.5 blocks |
| Crystal Ward | 2.5s shell all around her (4 circling shards), +30% speed; the first shot it catches (any side) goes back at the shooter as their own shot, now hers, and it ends |
| Crystallize (ult) | LMB an enemy / RMB herself: 2.5s as an amethyst geode (untouchable, can't act), then **180%** within 4 blocks, knockback, slowed; on herself she heals 25% |

### Dragon Hunter: Fighter / Debuffer

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **230** | **20** (17%) | **42** | **1.00** | **0.8** (25 ticks) | 276 | 34 |

Blind Obsession (passive): each basic attack that lands shreds 6% of the target's armor (5 stacks: 27%, 5s); a new
target resets it.

| Ability | Effect |
|---|---|
| Zweihander Swing (primary) | **100%** to the first enemy within 2.8 blocks, a little knockback; after Hunter's Lunge it stuns 1s |
| Hunter's Lunge (1, 7s) | a 4-block dash; the next basic attack is ready at once and stuns |
| Dragonbone Slam (2, 10s) | wind up to 2s (down to x0.55 speed, nothing else usable), slam a 4.5-block cone: **60%** + up to **120%** by wind-up (LMB early); full: **200%** ignoring armor |
| Venom Dagger (3, 12s) | **60%** all around (3.5 blocks), poison 12% max HP over 4s (-40% healing); Hunter's Lunge ready |
| Dragon Harpoon (ult) | a harpoon (**80%**): 5s disarmed, slowed 67% wearing off; below 20% max HP while it's stuck, F again: blink and execute |

### Iceman: Tank / Control

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **320** | **40** (29%) | **34** | **0.95** | **0.7** (28 ticks) | 448 | 24 |

Cold-Blooded (passive): nothing can freeze him. Ice Prison (passive): every 3rd mace hit on the same enemy freezes them
1.25s (stunned, 3% max HP freeze damage every 0.5s).

| Ability | Effect |
|---|---|
| Ice Mace (primary) | **100%** to the first enemy within 3 blocks; builds Ice Prison |
| Glacial Shockwave (1, 10s) | a 7-block, 70-degree cone: **80%**, enemies pulled toward him; a 40 HP shield per enemy hit (5s) |
| Frostbreath (2, 12s) | 3s channel, a 6-block cone: **12%** every 0.25s and a stacking slow (x0.93 each); 1.5s in it: frozen |
| Ice Wall (3, 13s) | a real ice wall, 7 long and 3 tall, across the aim (16 blocks), 5s |
| Glacial Tomb (ult) | thrown mace: the enemy hit is entombed 3s (untargetable, no damage, can't act) in a 5-block storm (-30% speed); then it explodes: **100%** and frozen; a miss: the storm where it lands |

### Lifeweaver: Support / Healer

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **200** | **5** (5%) | **32** | **1.05** | (no LMB; RMB drain 5 a second) | 210 | 48 (drain, half back as healing) |

Lingering Soul (passive): a killing blow makes her a Spectral Remnant for 5s (untargetable, no abilities but the ult),
then she dies; Apotheosis meanwhile revives her at 50% HP.

| Ability | Effect |
|---|---|
| Life Drain (RMB, hold) | **30%** every 0.2s to the enemy in her sights (18 blocks), half back as healing |
| Harmonic Strike (1, 8s) | an orb: **60%** to each enemy it passes (half heals her), then back: allies heal 30 + 15 per enemy hit |
| Beyond Life and Death (2, 16s) | LMB an ally / RMB herself: 6s +25% speed, +30% attack speed; killed meanwhile: back at 50%, decaying 5s (5% max HP every 0.5s), a kill in time revives at 25% |
| Lifeline (3, 14s) | a 3s tether: heal 4% max HP every 0.5s (24%); held all the way: a 60 HP shield for both |
| Apotheosis (ult) | 6s: untargetable, through units, +30% speed, no abilities; allies within 7 blocks take 60% less damage, regenerate 6% max HP/s |

---

## 3. Sanity check: time to kill with basic attacks only

Seconds of basic attacks (first hit at 0s) for the row character to kill the column character, from full
health, with armor, and without abilities, crits or passives. Alchemist is at his base draw speed, so he's
roughly 2x faster with full Rhythm.

| Attacker ↓ / target → | Vanguard | Reaver | Umbrella | Dreamer | Alchemist | Arcanist |
|---|---|---|---|---|---|---|
| **Vanguard** | 6.4 | 4.3 | 3.6 | 2.9 | 2.9 | 2.9 |
| **Essence Reaver** | 6.6 | 4.8 | 4.2 | 3.0 | 3.0 | 3.0 |
| **Umbrella** | 8.0 | 5.6 | 4.8 | 3.2 | 3.2 | 3.2 |
| **Dreamer** | 5.0 | 3.5 | 3.0 | 2.0 | 2.0 | 2.0 |
| **Alchemist** (base draw) | 11.2 | 7.5 | 6.2 | 5.0 | 5.0 | 5.0 |
| **Arcanist** | 10.4 | 8.0 | 6.4 | 4.8 | 4.8 | 4.8 |

What it shows:

- The squishies (Dreamer, Alchemist, Arcanist) all die in about 2-3s to melee.
- The Vanguard takes about 2x as long to kill as anyone else.
- The Dreamer kills fastest; from behind, divide his row by 1.5.
- The ranged kits are slow with basic attacks alone, as expected, since their abilities carry them.

---

## 4. How it would look in YAML

```yaml
characters:
  vanguard:
    name: "Vanguard"
    weapon: IRON_SWORD
    stats:
      health: 300
      armor: 40
      base_damage: 45
      move_speed: 0.95
      attack_speed: 1.4            # optional: otherwise the primary's own cooldown
    slots: { ... }

abilities:
  vanguard_ab1:
    nodes:
      slam:
        type: apply_effects
        targets: { type: radius, radius: 4 }
        effects:
          - { id: damage, base: 1.1 }      # 110% of the caster's base damage
          # - { id: damage, amount: 50 }   # still allowed: flat, doesn't scale
```

A status could also raise stats while it lasts, for example `stats: { armor: 20, move_speed: 0.1 }`. That
would be a later step, not part of the first version.

---

## 5. The original open questions (answered above, kept for reference)

1. **Armor constant 100:** is that the curve you want, and does armor apply to *everything*? My
   proposal: yes to abilities, DoTs and vanilla hits; **no** to tether redirect and Soul Rend mirror
   damage (true damage), and **no** to fall and void damage.
2. **DoTs:** scale with the applier's base damage **at the moment it's applied** (proposed; a buff
   later doesn't change a running poison) or live on each tick?
3. **Heals and shields:** keep them **flat** (proposed for now), or as **% of the caster's max HP**, so
   tanky characters heal and shield more?
4. **Alchemist's attack speed:** leave the crossbow draw and Rhythm as the fire rate (proposed), or should
   attack speed also shorten the draw?
5. **Placeholders:** should the Arcanist's bolt go from 4 → 100% (30)? And should the Umbrella get a real
   melee primary (a spear poke, 16 ticks) instead of `test_blast`?
6. **Soul Rend's soul:** a flat 150 health, or a share of its owner's max HP (for example 60%, so a
   Vanguard's soul is sturdier)?
7. **Hearts:** show everyone as 10 hearts with the real HP behind them (proposed), or let max HP change
   the number of hearts?
8. **Move speed and sprinting:** the multiplier applies to walking and sprinting alike (proposed), right?

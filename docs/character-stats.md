# Character stat sheets (proposal)

Status: **implemented** (the sheets below are in the kits' `stats:`). Numbers are first-pass. They were
derived from what the kits dealt before, so switching to stats didn't change how anything feels; tune
from here.

### Decisions

- **Players see their stats:** the top row of the inventory holds one item per stat. Hovering one shows
  its value right now: current health, Strength on base damage, a slow on move speed, the Alchemist's draw.
- **Armor applies to everything** (abilities, basic attacks, vanilla hits, falls) **except damage over
  time and % max HP hits.** Damage over time is % max HP now: poison, frost and flame are 15% of
  the target's max HP each in all (3.75% a tick); poison and flame are reduced by armor like flat damage, frost is true
  damage (ignores armor). The shared burn is 2.5% a tick. Tether redirect and soul mirror damage stay
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
| Volatile Flask | 9 + spell power x 1.1 | **20%** + **110% spell power** |
| Poison (per second, 4 ticks) | 3.75% max HP (15% total, -40% healing), armor reduces it | % max HP |
| Frost (every 0.75s, 4 ticks) | 3.75% max HP (15% total), true damage, slowed | % max HP |
| Flame (every 0.75s, 4 ticks) | 3.75% max HP (15% total), armor reduces it | % max HP |
| Flask tonic (self heal) | 10 x 6 | flat (see Q3) |

The flask's random infusions are Frost, Flame and Paralysis (no Vitality any more).

### Arcanist: Mage

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **175** | **5** (5%) | **30** | **1.00** | **1.25** (16 ticks) | 184 | 37 (at 100%) |

The least armor: control (Missile stun, Screen) and burst (Ascension) from range. (The kit file has base damage 28
and 10 armor now; the numbers below are shares of base damage, so they hold either way.)

| Ability | Damage |
|---|---|
| Arcane Bolt (primary) | **50% + 50% spell power** to everyone within 1 block of where it bursts (an enemy or terrain); curves slightly toward marked enemies |
| Passive: Arcane Barrage | bolt hit +1 charge, missile hit +2; at 6: 5s of bigger bolts bursting in **2.5 blocks** (same damage) |
| Arcane Missile: plain hit | **100%**, marks 4s (glowing; bolts curve to them) |
| Arcane Missile: empowered (recast in flight, or out of a wall) | stun 0.6s + mark, explosion **165% + 10% of their max HP** in 3 blocks |
| Arcane Screen | enemy shots through it held 2s; allied shots +60% speed and **60% spell power** on their hit (no base part) |
| Arcane Ascension (ult) | 12s of slow, level flight, 3 geometries (a kill: +1): each channeled 1s (no abilities or basic attacks meanwhile), then **220%** in 4 blocks, knocked back |

### Gunner: Gunslinger Anti-Caster

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **210** | **15** (13%) | **38** | **1.10** | shotgun 0.7s, revolver 0.4s (their own cooldowns) | 242 | ~40 (revolver, sustained) |

Two guns: LMB is a shotgun (2 shells), RMB a revolver (6 bullets; it fires when you let go, holding loads more). The gun you used
last is in your hand and its ammo shows in slot 8; an empty gun reloads by itself. Null Ward (passive) shrugs
off one debuff after 6s out of combat; Counterspell blocks a spell.

| Ability | Damage |
|---|---|
| Scattergun (LMB) | **130%** to everyone in a 7-block, 45 degree cone; 2 shells, reload 1.5s |
| Six-Shooter (RMB) | fires on let-go: press loads 1, holding loads up to 6; **55%** for the first, **45%** each after; 6 bullets, reload 2s |
| Buckshot | **160%** in a 9-block, 70 degree cone, knockback, 1.5s slow; recoil throws you away from where you aimed (shoot at your feet: up, a blast jump); refills the shotgun |
| Smoke Grenade | bounces up to 3 times, then **90%** + 2s blind (3.5 blocks), and 4s of smoke that blinds anyone in it; caught in it yourself: speed, Null Ward ready |
| Counterspell | blocks the first enemy spell for 2s (damage and debuffs); blocked: speed |
| Powder Keg (ult) | **200%** in 5 blocks, burn **12% of their max HP** over 3s, knocked away; shoot it to set it off early |

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
| **200** | **10** (9%) | **40** | **1.05** | **1.40** (14 ticks) | 220 | 56 |

Hears enemies below 40% HP within 30 blocks (they glow for them only; +15% speed moving toward one within 15).

| Ability | Damage |
|---|---|
| Sickle Rake (primary) | **100%** (4 HP), 3 blocks |
| Dream Step | blink 8 blocks, blinds 0.75s around the exit; press again within 3s to return to the rift |
| Binding Whisper | 2s tether (8 blocks, breaks past 9 / out of sight 0.5s / stunned or silenced: 40% cd back); held: Darkness + Blindness 4s, 1.5 HP wither over 3s |
| Chorus Shade | 6 HP flat; executes below 10% HP; flies through terrain, 12 blocks then hovers 3s; homes on heard enemies (any distance) or anyone within 6; enemies can kill it (6 HP) |
| Into the Veil (ult) | pick an enemy within 5 blocks: a 7s 1v1 in place, both in Darkness: only the two see and can affect each other (their abilities' visuals too; outsiders see a red and a green mote); +20% damage for the first 6s, cooldowns reset; win: heal 8 HP |

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
| **180** | **0** (0%) | **30** | **1.00** | **2.0** (10 ticks) | 180 | 60 (75 overheated) |

Overheat (passive, the XP bar and hotbar slot 9): every enemy her abilities hit is a stack (8 at most; burning and
her scorched ground don't count); 2s without a hit and it's back to 0 at once. Full: she overheats while she keeps
hitting: blue flames, Fire Bolts every 8 ticks (x1.25), +15% speed.

| Ability | Damage |
|---|---|
| Fire Bolt (primary) | **100%** magic damage, 15 blocks |
| Fireball | **120%** within 3.5 blocks, small knockback, burn (4% max HP over 2s) |
| Hunting Wisp | sent to a spot (25 blocks); waits up to 30s for an enemy within 7, hunts the first one (they glow): **100%** within 3 blocks (no burn) |
| Hot Coals | 6s of Jump Boost II; every landing scorches the ground for 4s (1.8 blocks): enemies in it burn |
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
| Gilded Slash (primary) | **100%** in a 5-block arc |
| Valkyrie's Leap (1, 8s) | leap + a 4-block half circle: **100%**, attack reset; on a hit again (x2); then soar up and dive onto a spot: **140%** in 4 blocks, knocked up. Not while gliding |
| Valkyrie's Charge (2, 10s) | on foot: a 6-block dash, **80%**, flung behind her, stunned 1s; gliding: home onto an enemy (25 blocks), **60%**, carry them 8 blocks: into terrain **15% max HP**. A hit: a shield of 15% her max HP (4s) |
| War Cry (3, 16s) | she and allies within 8 blocks: +25% speed, +20% damage, 4s |
| Divine Ward (ult) | LMB an ally / RMB herself: no damage for 4s, can't die |

### Amethyst: Mage / Zone Control

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **180** | **15** (13%) | **35** | **1.00** | **4.5** (rounds to 4 ticks: 5 a second; ~1.5 out of ammo) | 207 | 140 (~40 out of ammo) |

Piercing Shards (passive): her ammo (the count on her weapon, 6 at most, none to start with) is her piercing shards;
it doesn't refill by itself: Allegro gives 4, Arpeggio 2, Geode Bloom 2, Geode Heart 6. It expires: all of it is lost
8s after the last gain (every gain starts it over; the XP bar shows the time). With ammo, each shot is a piercing shard
(80% to the first, 50% behind), hanging where it ends for 7s (6 at most), for Recall (recalled shards come back to her,
not into the ammo); their on-hit effects land going out and coming back. Without ammo: regular shards (80% to the first
enemy), much slower.

Crystal floor (Geode Bloom, Geode Heart): enemies on it are 20% slower, she's 20% faster, and SHIFT + a movement key
glides her 3 blocks that way (every 0.5s, never off the floor). Her only movement. Recall's slow and the floor's together
are capped at 40%.

| Ability | Effect |
|---|---|
| Amethyst Shard (primary) | with ammo, a piercing shard (20 blocks): **80%** to the first, **50%** to each behind, then it hangs 7s where it ends. Without: a regular shard, **80%** to the first enemy, ~1.5 a second |
| Allegro (1, 9s) | +4 ammo; +40% attack speed at the cast, fading evenly to nothing over 4s (Arpeggio too). No movement speed |
| Geode Bloom (2, 12s from when its floor ends) | a geode thrown in an arc, through everyone, banking off walls (mirrored, 70% speed kept, 3 at most, then it drops): it bursts where it lands, **60%** and rooted 1s within 2.5 blocks, and leaves a crystal floor 4 blocks round for 6s; +2 ammo. One at a time |
| Recall (RMB, 10s; 5s if a shard hits a player) | its icon in slot 9 counts the shards out; every hanging piercing shard flies back (not into the ammo): **100%** per shard that hits, **10% less** for each shard of the Recall that hit the same enemy before (down to 10%), with on-hits; slowed x0.75 per shard 2s (40% at most with the floor's) |
| Arpeggio (3, 10s) | +2 ammo, a 0.5s charge, then 6 real basic attacks in a run (one every 0.2s, faster with attack speed), piercing while she has ammo. Can't be stopped; nothing else meanwhile; off her floor 40% slower. After Allegro: 6 piercing shards |
| Geode Heart (ult) | 2s encased: untargetable, no damage, heals **25%** of max HP (F from 0.5s breaks out early). Shatters: **100%** and rooted 1s within 3 blocks, a crystal floor 7 blocks round for 6s, a ring of 6 shards hanging at its edge, +6 ammo |

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
| Dragonbone Slam (2, 10s) | wind up to 2s (down to x0.55 speed, nothing else usable), slam a 10-block cone: **60%** + up to **120%** by wind-up (LMB early); full: **200%** ignoring armor |
| Venom Dagger (3, 12s) | **60%** all around (3.5 blocks), poison 12% max HP over 4s (-40% healing); Hunter's Lunge ready |
| Dragon Harpoon (ult) | a harpoon (**80%**): 5s disarmed, slowed 67% wearing off; below 20% max HP while it's stuck, F again: blink and execute |

### Iceman: Tank / Control

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **320** | **40** (29%) | **34** | **0.95** | **0.7** (28 ticks) | 448 | 24 |

Cold-Blooded (passive): nothing can freeze him. Ice Prison (passive): every 3rd mace hit on the same enemy freezes them
1.25s (stunned, 5% max HP freeze damage every 0.5s: 10%).

| Ability | Effect |
|---|---|
| Ice Mace (primary) | **100%** to the first enemy within 4 blocks; builds Ice Prison |
| Glacial Shockwave (1, 10s) | a 7-block, 70-degree cone: **80%**, enemies pulled toward him; a 40 HP shield per enemy hit (5s) |
| Frostbreath (2, 12s) | 3s channel (nothing else usable, the XP bar fills), an 8-block, 70-degree cone: **20%** every 0.25s and a stacking slow (x0.93 each); 1.5s in it: frozen |
| Ice Wall (3, 13s) | a real ice wall, 7 long and 3 tall, across the aim (16 blocks), 5s |
| Glacial Tomb (ult) | thrown mace: the enemy hit takes **100%** and is entombed 3s (untargetable, no damage, can't act) in a 5-block storm (-30% speed); then it explodes: **100%** and frozen; a miss: the storm where it lands |

### Lifeweaver: Support / Healer

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **200** | **5** (5%) | **32** | **1.05** | (no LMB; RMB drain 5 a second) | 210 | 48 (drain, 40% back as healing) |

Lingering Soul (passive): a killing blow makes her a Spectral Remnant for 5s (untargetable, no abilities but the ult),
then she dies; Apotheosis meanwhile revives her at 50% HP.

| Ability | Effect |
|---|---|
| Life Drain (RMB, hold) | **30%** every 0.2s to the enemy in her sights (8 blocks), 40% back as healing |
| Harmonic Strike (1, 8s) | an orb: **60%** to each enemy it passes (half heals her), then back toward where she was (no homing): allies it passes heal 30 + 15 per enemy hit |
| Beyond Life and Death (2, 16s) | LMB an ally / RMB herself: 6s +25% speed, +30% attack speed; killed meanwhile: back at 50%, decaying 5s (5% max HP every 0.5s), a kill in time revives at 25% |
| Lifeline (3, 14s) | a 3s tether: heal 4% max HP every 0.5s (24%); held all the way: a 60 HP shield for both |
| Apotheosis (ult) | 6s: untargetable, through units, +30% speed, no abilities; allies within 7 blocks take 60% less damage, regenerate 6% max HP/s |

### Graviton: Tank / Control

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **250** | **25** (20%) | **30** | **0.95** | **0.87** (23 ticks: an orb every 1.15s) | 313 | 16 (to everyone in the burst) |

Event Horizon (passive): after 4s out of combat a shield builds up, +10 every 0.5s up to 75; it stays until broken.

| Ability | Effect |
|---|---|
| Graviton Orb (primary) | a short-range orb (~0.6s, no gravity) that bounces once off the ground or a wall, then bursts on an enemy, terrain or when it fades: **60%** to everyone within 2.5 blocks |
| Gravity Grasp (1, 12s from when it's over) | a knot (18 blocks): **50%** and -70% speed 2.5s; press again to drag them 10 blocks toward the crosshair; slammed into another enemy: **120%** and a 1.5s stun to both |
| Kinetic Sink (2, 10s) | channel 2.5s (no basic attacks): a well in front swallows enemy projectiles, +25 shield each (up to 200) |
| Stasis Field (3, 15s) | a 10x6 field (4 tall) on a spot 20 blocks away for 5s: enemy projectiles in it move at 10% speed |
| Gravitic Collapse (ult) | a circle (14 blocks away, radius 6): he rises and hangs; enemies over it float up for 2.5s (they can drift out, slowly: -65% speed); then those still over it are smashed down: **300%** and a 2s stun |

### Vampire: Mage / Sustain

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **210** | **10** (9%) | **36** | **1.05** | **1.5** (13 ticks) | 231 | 54 |

Blood Feast (passive): every kill heals her 15% of her max HP (31) and makes her 30% faster for 3s.

| Ability | Effect |
|---|---|
| Blood Missile (primary) | a missile (1.5 blocks/tick, 20 blocks): **100%** |
| Sanguine Tether (1, 14s) | channel 2.5s (no basic attacks; 1 again stops): tethers up to 3 enemies within 8 blocks; every 0.5s each takes **15%** and a drop of blood heals her 10 (up to 120 from 3); whoever stays within 10 blocks (and in sight) bleeds 2% max HP every 0.5s for 4s (16%). Nobody near: free |
| Bat Form (2, 8s) | 1s of free flight as a bat (she's hidden, the bat is seen); no fall damage after |
| Charm (3, 14s) | a charm (16 blocks): **40%**, then 1.75s charmed: no spells, no basic attacks, walked to her at ~1.6 blocks/s |
| Crimson Rain (ult) | rises ~4 blocks and hangs 4s (F again stops): a missile where she looks every 0.25s, **35%** within 1.8 blocks each (16 missiles: up to 560%) |

### Slime: Brawler / Tank

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **300** | **30** (23%) | **38** | **0.95** (up to 1.25 when low) | **1.2** (17 ticks) | 390 | 46 |

Gelatinous (passive): every 20% of max HP lost drops a slime a couple of blocks away (20s, 6 at most); she eats it by
stepping on it (+15% max HP = 45), enemies squash it. Size and speed by health: 80-100% x1.2 / 0.95, 60-80% x1.1 / 1.0,
40-60% x1.0 / 1.05, 20-40% x0.9 / 1.12, below 20% x0.75 / 1.25.

| Ability | Effect |
|---|---|
| Slime Slap (primary) | a slap in front (3.2 blocks, 80 degrees): **100%** |
| Ooze Burst (1, 10s) | costs 10% of her max HP (not below 15%: free, nothing happens): drops a slime; enemies within 4.5 blocks take 8% of their max HP, -40% speed 2s |
| Slime Ball (2, 12s) | 0.6s wind-up, a big slime ball (20 blocks): **90%**, flung 8 blocks along the throw; into a wall (mid-air too): stuck, rooted 1.5s |
| Belly Flop (3, 12s) | a circle (16 blocks, radius 3.5): up ~6 blocks, then down onto it: **100%**, knocked up, stunned 0.6s |
| Bounce Storm (ult) | 5 bounces (up ~5 blocks the way she looks, then straight down): **80%** within 4 blocks of each landing, knocked up and back; no abilities or basic attacks meanwhile, no fall damage |

### Firecracker: Marksman

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **190** | **5** (5%) | **40** | **1.05** | **1.6** (12 ticks; two bullets each) | 200 | 64 |

Double Tap (passive): every basic attack is two bullets 0.15s apart, 50% each, both applying on-hit effects; a kill gives
+50% attack speed for 3s.

| Ability | Effect |
|---|---|
| Firecracker (primary) | two small bullets (1.8 blocks/tick, 25 blocks): **50%** each, crackling into sparks |
| Rocket (1, 9s) | a rocket (30 blocks): **90%** within 3 blocks and burn; any enemy within 14 blocks looking at the blast (70 degrees, in sight) is blinded 2s |
| Scatter Fire (2, 10s) | 3 fast rockets close together (10 degrees), ricocheting off terrain for 1.5s, through enemies: **45%** each per rocket |
| Firework Bomb (3, 14s) | a spot (18 blocks): 0.4s wind-up, a bomb that goes off 1.5s later (or when 3 is pressed again): **60%** within 4 blocks, knocked back and up, -40% speed 2s; on herself only the push (and no fall damage) |
| Grand Finale (ult) | 6s (a boss bar): basic attacks become big fireworks, still two a shot: **100%** each and knockback |

---

### Fae Trickster: Diver

| Max HP | Armor | Base dmg | Move speed | Attack speed | EHP | Basic DPS |
|---|---|---|---|---|---|---|
| **210** | **15** (13%) | **34** | **1.05** | **1.3** (15 ticks) | 242 | 44 |

A melee diver between the Umbrella and the Valkyrie: in with Trick Room's dash, around with its teleports (and the room
moved with 2 again), a cone with Play Rough, and Topsy-Turvy to turn enemies' controls and team against them. Its first
swipe on each enemy silences them 1s (Sucker Punch), and swipes on players take 1s off Play Rough. The role is assumed
(diver). Its swipe used to be 85% of 40; now the stat is 34 and the swipe 100%, the same 34 a hit.

| Ability | Now | As % of base (34) |
|---|---|---|
| Swipe (primary) | 34 | **100%** |
| Play Rough (two slashes 0.2s apart, 3-block 90-degree cone, 5s cooldown) | 25.5 + 25.5 | **75% + 75%** |
| Topsy-Turvy (the orb; cast inside Trick Room: no damage) | 6.8 + spell power x 0.9 | **20%** + **90% spell power** |
| Trick Room, Switcheroo | no damage | |

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

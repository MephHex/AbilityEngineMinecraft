# Character stat sheets (proposal)

Status: **implemented** (the sheets below are in the kits' `stats:`). Numbers are first-pass. They were
derived from what the kits dealt before, so switching to stats didn't change how anything feels; tune
from here.

### Decisions

- **Players see their stats:** the top row of the inventory holds one item per stat. Hovering one shows
  its value right now: current health, Strength on base damage, a slow on move speed, the Hunter's draw.
- **Armor applies to everything** (abilities, basic attacks, vanilla hits, falls) **except damage over
  time and % max HP hits.** Damage over time is % max HP now: poison, frost and flame are 5% / 5% / 3%
  of the target's max HP a tick, the shared burn 2.5%. Tether redirect and soul mirror damage stay
  final: not reduced again.
- **Heals and shields are flat**, unless an effect says `max_hp:`.
- **Hunter has no attack speed stat:** his crossbow draw (and Hunter's Rhythm) is his fire rate, and the
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

### Hunter: Marksman

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

### Test kits (Gunner, Pyro, Tidecaller)

No sheet: they get the defaults (200 HP, 0 armor, base damage 40, speed 1.0).

---

## 3. Sanity check: time to kill with basic attacks only

Seconds of basic attacks (first hit at 0s) for the row character to kill the column character, from full
health, with armor, and without abilities, crits or passives. Hunter is at his base draw speed, so he's
roughly 2x faster with full Rhythm.

| Attacker ↓ / target → | Vanguard | Reaver | Umbrella | Dreamer | Hunter | Arcanist |
|---|---|---|---|---|---|---|
| **Vanguard** | 6.4 | 4.3 | 3.6 | 2.9 | 2.9 | 2.9 |
| **Essence Reaver** | 6.6 | 4.8 | 4.2 | 3.0 | 3.0 | 3.0 |
| **Umbrella** | 8.0 | 5.6 | 4.8 | 3.2 | 3.2 | 3.2 |
| **Dreamer** | 5.0 | 3.5 | 3.0 | 2.0 | 2.0 | 2.0 |
| **Hunter** (base draw) | 11.2 | 7.5 | 6.2 | 5.0 | 5.0 | 5.0 |
| **Arcanist** | 10.4 | 8.0 | 6.4 | 4.8 | 4.8 | 4.8 |

What it shows:

- The squishies (Dreamer, Hunter, Arcanist) all die in about 2-3s to melee.
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
4. **Hunter's attack speed:** leave the crossbow draw and Rhythm as the fire rate (proposed), or should
   attack speed also shorten the draw?
5. **Placeholders:** should the Arcanist's bolt go from 4 → 100% (30)? And should the Umbrella get a real
   melee primary (a spear poke, 16 ticks) instead of `test_blast`?
6. **Soul Rend's soul:** a flat 150 health, or a share of its owner's max HP (for example 60%, so a
   Vanguard's soul is sturdier)?
7. **Hearts:** show everyone as 10 hearts with the real HP behind them (proposed), or let max HP change
   the number of hearts?
8. **Move speed and sprinting:** the multiplier applies to walking and sprinting alike (proposed), right?

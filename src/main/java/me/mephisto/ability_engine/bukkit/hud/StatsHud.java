package me.mephisto.ability_engine.bukkit.hud;

import me.mephisto.ability_engine.bukkit.effect.DamageEffect;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.status.ActiveStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A character's stat sheet in game.
 * <ul>
 *   <li>Applied to the player: max HP (always shown as 10 hearts, the real HP behind them), move speed
 *       (added to the base, so slows and haste multiply on top of it), and vanilla attack speed set to the
 *       primary fire's rate, so the attack indicator under the crosshair and the arm swing follow it (it
 *       keeps up with forms and slowed attacks, e.g. Paralysis).</li>
 *   <li>Shown in the inventory's top row: one item per stat. Hovering one shows its value right now
 *       (current health, Strength on base damage, a slow on move speed...). Refreshed twice a second.</li>
 * </ul>
 */
public final class StatsHud {

    /** Top row of the main inventory (slots 9-13), clear of the hotbar and its quiver/resource slots. */
    private static final int FIRST_SLOT = 9;
    /** Vanilla walking speed (the player's base movement attribute). */
    private static final double VANILLA_SPEED = 0.1;
    /** Every character shows 10 hearts, whatever their max HP. */
    private static final double SHOWN_HEALTH = 20;

    private final AbilityEngine engine;
    private final DamageEffect scaleSource;
    private final NamespacedKey hudKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey speedKey;
    private final NamespacedKey attackSpeedKey;
    private final NamespacedKey scaleKey;
    private final NamespacedKey statusSpeedKey;
    /** Per player: the lore last drawn, so items are only replaced when something changed. */
    private final Map<UUID, List<String>> shown = new HashMap<>();

    public StatsHud(Plugin plugin, AbilityEngine engine, DamageEffect scaleSource) {
        this.engine = engine;
        this.scaleSource = scaleSource;
        this.hudKey = new NamespacedKey(plugin, "hud"); // same key as HotbarHud: cleared and locked with it
        this.healthKey = new NamespacedKey("ability_engine", "character_health");
        this.speedKey = new NamespacedKey("ability_engine", "character_speed");
        this.attackSpeedKey = new NamespacedKey("ability_engine", "character_attack_speed");
        this.scaleKey = new NamespacedKey("ability_engine", "character_scale");
        this.statusSpeedKey = new NamespacedKey("ability_engine", "status_speed");
    }

    public void start() {
        engine.scheduler().every(10, 10, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!engine.loadouts().has(p.getUniqueId())) continue;
                draw(p);
                syncAttackSpeed(p);
            }
        });
        // Statuses that change move speed by stacks (Rustbreaker's rust) build up fast: keep up every 2 ticks.
        engine.scheduler().every(2, 2, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (engine.loadouts().has(p.getUniqueId())) syncStatusSpeed(p);
                syncJumpBoost(p); // anyone: a status can be on a player without a character too
            }
        });
    }

    // ---- attributes -----------------------------------------------------------------------------

    /** Max HP and move speed from the character's sheet (replacing any from a previous character). */
    public void apply(Player p) {
        CharacterDef.Stats stats = engine.stats().of(p.getUniqueId());
        AttributeInstance health = p.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double maxBefore = health.getValue();
            double share = maxBefore > 0 ? Math.min(1, p.getHealth() / maxBefore) : 1;
            strip(health, healthKey);
            double wanted = stats.health() / scaleSource.scale();
            health.addModifier(new AttributeModifier(healthKey, wanted - health.getBaseValue(), AttributeModifier.Operation.ADD_NUMBER));
            double max = health.getValue();
            // A new max (another character, or a form with its own stats: growing up): the same share of it as before
            if (!p.isDead() && Math.abs(max - maxBefore) > 1e-6) p.setHealth(Math.max(Math.min(max, 0.5), share * max));
            else if (p.getHealth() > max) p.setHealth(max);
        }
        p.setHealthScale(SHOWN_HEALTH);
        p.setHealthScaled(true);
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            strip(speed, speedKey);
            // ADD_NUMBER changes the base; slows and haste (MULTIPLY_SCALAR_1) then multiply the result.
            speed.addModifier(new AttributeModifier(speedKey, speed.getBaseValue() * (stats.moveSpeed() - 1),
                    AttributeModifier.Operation.ADD_NUMBER));
        }
        AttributeInstance size = p.getAttribute(Attribute.SCALE);
        if (size != null) {
            strip(size, scaleKey);
            if (stats.scale() != 1) {
                size.addModifier(new AttributeModifier(scaleKey, stats.scale() - 1, AttributeModifier.Operation.ADD_SCALAR));
            }
        }
        shown.remove(p.getUniqueId());
        draw(p);
        syncAttackSpeed(p);
        syncStatusSpeed(p);
    }

    /**
     * Statuses' {@code move_speed} (per stack, e.g. a slow that builds up), multiplied onto the speed after
     * everything else. Only touched when it changes.
     */
    private void syncStatusSpeed(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        double wanted = engine.stats().moveSpeedMultiplier(p.getUniqueId()) - 1;
        AttributeModifier ours = speed.getModifier(statusSpeedKey);
        if (ours == null ? Math.abs(wanted) < 1e-6 : Math.abs(ours.getAmount() - wanted) < 1e-6) return;
        strip(speed, statusSpeedKey);
        if (Math.abs(wanted) >= 1e-6) {
            speed.addModifier(new AttributeModifier(statusSpeedKey, wanted, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    /** The old tag for Jump Boost II (Hot Coals before jump_boost: on statuses). */
    private static final String JUMP_BOOST_TAG = "state.jump_boost";
    /** Players wearing our Jump Boost, so only ours is ever taken away (not a potion they drank). */
    private final java.util.Set<UUID> jumpBoosted = new java.util.HashSet<>();

    /**
     * Statuses' {@code jump_boost} (the highest one; the old {@code state.jump_boost} tag counts as 2): vanilla Jump
     * Boost of that level while it lasts. Only touched when it changes.
     */
    private void syncJumpBoost(Player p) {
        UUID id = p.getUniqueId();
        int level = Math.max(engine.stats().jumpBoost(id), engine.tags().has(id, JUMP_BOOST_TAG) ? 2 : 0);
        PotionEffect has = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (level <= 0) {
            if (jumpBoosted.remove(id) && has != null && has.isInfinite()) p.removePotionEffect(PotionEffectType.JUMP_BOOST);
            return;
        }
        jumpBoosted.add(id);
        if (has != null && has.isInfinite() && has.getAmplifier() == level - 1) return;
        if (has != null) p.removePotionEffect(PotionEffectType.JUMP_BOOST); // a lower level doesn't replace a higher one
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PotionEffect.INFINITE_DURATION, level - 1, false, false));
    }

    /**
     * Vanilla attack speed (attacks per second) = the primary fire's rate right now: 20 / its cooldown in
     * ticks, after the sheet's attack speed and any slowed/faster attacks. Whatever the held weapon item adds
     * or takes away is made up for, so the crosshair's attack indicator fills exactly when the next shot
     * is ready. Only touched when it changes.
     */
    private void syncAttackSpeed(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;
        UUID id = p.getUniqueId();
        String primary = engine.loadouts().abilityIn(id, Slots.PRIMARY).orElse(null);
        var ability = primary == null ? java.util.Optional.<me.mephisto.ability_engine.engine.ability.Ability>empty()
                : engine.abilities().find(primary);
        int ticks = ability.map(a -> engine.stats().cooldownTicks(id, a.id(), a.cooldownTicks())).orElse(0);
        if (ticks <= 0) {
            strip(attr, attackSpeedKey);
            return;
        }
        double wanted = 20.0 / ticks;
        AttributeModifier ours = attr.getModifier(attackSpeedKey);
        double without = attr.getValue() - (ours == null ? 0 : ours.getAmount());
        double amount = wanted - without;
        if (ours != null && Math.abs(ours.getAmount() - amount) < 0.01) return;
        strip(attr, attackSpeedKey);
        attr.addModifier(new AttributeModifier(attackSpeedKey, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    /** A fresh character: full health. */
    public void fill(Player p) {
        AttributeInstance health = p.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) p.setHealth(health.getValue());
    }

    /** Back to a plain player (no character, or leaving). */
    public void remove(Player p) {
        strip(p.getAttribute(Attribute.MAX_HEALTH), healthKey);
        strip(p.getAttribute(Attribute.MOVEMENT_SPEED), speedKey);
        strip(p.getAttribute(Attribute.ATTACK_SPEED), attackSpeedKey);
        strip(p.getAttribute(Attribute.SCALE), scaleKey);
        strip(p.getAttribute(Attribute.MOVEMENT_SPEED), statusSpeedKey);
        if (jumpBoosted.remove(p.getUniqueId())) p.removePotionEffect(PotionEffectType.JUMP_BOOST); // not saved with them
        p.setHealthScaled(false);
        AttributeInstance health = p.getAttribute(Attribute.MAX_HEALTH);
        if (health != null && p.getHealth() > health.getValue()) p.setHealth(health.getValue());
        shown.remove(p.getUniqueId());
    }

    private static void strip(AttributeInstance attribute, NamespacedKey key) {
        if (attribute == null) return;
        AttributeModifier old = attribute.getModifier(key);
        if (old != null) attribute.removeModifier(old);
    }

    // ---- the stat items -------------------------------------------------------------------------

    private record Stat(Material icon, String name, TextColor color, List<String> lines) {}

    /** (Re)draw the stat items if any value changed. */
    public void draw(Player p) {
        UUID id = p.getUniqueId();
        List<Stat> stats = stats(p);
        List<String> key = stats.stream().map(s -> s.name() + s.lines()).toList();
        if (key.equals(shown.get(id))) return;
        shown.put(id, key);
        for (int i = 0; i < stats.size(); i++) p.getInventory().setItem(FIRST_SLOT + i, item(stats.get(i)));
    }

    private List<Stat> stats(Player p) {
        UUID id = p.getUniqueId();
        CharacterDef.Stats sheet = engine.stats().of(id);
        double scale = scaleSource.scale();
        List<Stat> out = new ArrayList<>();

        double max = sheet.health();
        double now = p.getHealth() * scale;
        double shield = p.getAbsorptionAmount() * scale;
        List<String> health = new ArrayList<>(List.of(
                String.format("Health: %.0f / %.0f", now, max),
                String.format("One heart = %.0f HP (always 10 hearts)", max / 10)));
        if (shield > 0) health.add(String.format("Shield: %.0f", shield));
        out.add(new Stat(Material.GLISTERING_MELON_SLICE, "Max HP: " + fmt(max), NamedTextColor.RED, health));

        out.add(new Stat(Material.IRON_CHESTPLATE, "Armor: " + fmt(sheet.armor()), NamedTextColor.GRAY, List.of(
                String.format("Takes %.0f%% less damage", engine.stats().armorReduction(id) * 100),
                String.format("(armor / (armor + %.0f))", engine.stats().armorConstant()),
                "Damage over time and % max HP hits",
                "ignore armor.")));

        double dealt = 1;
        List<String> sources = new ArrayList<>();
        for (ActiveStatus s : engine.statuses().on(id)) {
            if (s.def().damageDealt() != 1) {
                dealt *= s.def().damageDealt();
                sources.add(String.format("x%.2f %s", s.def().damageDealt(), s.def().id()));
            }
        }
        List<String> damage = new ArrayList<>(List.of("Your abilities and attacks deal", "a % of this."));
        if (!sources.isEmpty()) {
            damage.add(String.format("Right now: %.0f (%s)", sheet.baseDamage() * dealt, String.join(", ", sources)));
        }
        out.add(new Stat(Material.BLAZE_POWDER, "Base damage: " + fmt(sheet.baseDamage()), NamedTextColor.GOLD, damage));

        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        double current = speed == null ? sheet.moveSpeed() : speed.getValue() / VANILLA_SPEED;
        out.add(new Stat(Material.SUGAR, String.format("Move speed: %.0f%%", sheet.moveSpeed() * 100), NamedTextColor.AQUA,
                List.of(String.format("Right now: %.0f%%", current * 100), "(100% = vanilla walking; slows and",
                        "haste multiply it, sprinting too)")));

        out.add(attackSpeed(p, sheet));
        return out;
    }

    /** Attacks per second; a crossbow shows its draw time instead (Quick Charge from Hunter's Rhythm). */
    private Stat attackSpeed(Player p, CharacterDef.Stats sheet) {
        UUID id = p.getUniqueId();
        var character = engine.loadouts().baseCharacterOf(id).orElse(null);
        boolean crossbow = character != null && character.quiver() != null && "CROSSBOW".equalsIgnoreCase(character.weapon());
        double slowed = engine.stats().attackSpeedMultiplier(id); // Paralysis: 0.6
        String why = slowed == 1 ? null : String.format("Right now x%.2f (slowed attacks)", slowed);
        if (crossbow) {
            if (engine.quivers().rapidFire(id)) {
                double perSecond = primaryCooldown(character).map(t -> 20.0 / t).orElse(0.0) * slowed;
                return new Stat(Material.FEATHER, "Draw speed: rapid fire", NamedTextColor.GREEN,
                        lines(why, String.format("No drawing: %.1f shots a second", perSecond)));
            }
            int quickCharge = engine.quivers().reloadSpeed(id);
            double draw = Math.max(0, 1.25 - 0.25 * quickCharge) / slowed;
            return new Stat(Material.FEATHER, String.format("Draw time: %.2fs", draw), NamedTextColor.GREEN, lines(why,
                    "Quick Charge " + quickCharge + " (Hunter's Rhythm makes", "it faster)"));
        }
        double perSecond = (sheet.attackSpeed() > 0 ? sheet.attackSpeed()
                : character == null ? 0 : primaryCooldown(character).map(t -> 20.0 / t).orElse(0.0)) * slowed;
        return new Stat(Material.FEATHER, String.format("Attack speed: %.2f/s", perSecond), NamedTextColor.GREEN,
                lines(why, "Basic attacks per second"));
    }

    /** The lines, with an extra first one when there is a reason to show. */
    private static List<String> lines(String first, String... rest) {
        List<String> out = new ArrayList<>();
        if (first != null) out.add(first);
        out.addAll(List.of(rest));
        return out;
    }

    private java.util.Optional<Integer> primaryCooldown(CharacterDef character) {
        return java.util.Optional.ofNullable(character.abilityIn(Slots.PRIMARY)).flatMap(engine.abilities()::find)
                .map(a -> a.cooldownTicks()).filter(t -> t > 0);
    }

    private ItemStack item(Stat stat) {
        ItemStack item = new ItemStack(stat.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(stat.name(), stat.color()));
        meta.lore(stat.lines().stream().map(l -> plain(l, NamedTextColor.GRAY)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(hudKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    private static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}

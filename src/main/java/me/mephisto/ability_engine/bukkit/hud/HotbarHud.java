package me.mephisto.ability_engine.bukkit.hud;

import me.mephisto.ability_engine.bukkit.input.InputAction;
import me.mephisto.ability_engine.bukkit.input.Keybinds;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.Ability;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.loadout.Slots;
import me.mephisto.ability_engine.engine.quiver.Bolt;
import me.mephisto.ability_engine.engine.quiver.InfusionDef;
import me.mephisto.ability_engine.engine.quiver.QuiverDef;
import me.mephisto.ability_engine.engine.state.ResourceDef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Draws a character's kit on the hotbar and equips/unequips characters.
 *
 * <pre>
 * [ult]  [ab1][ab2][ab3][ - ][WEAPON][ - ][ind][ind][ind]
 * offhand  0    1    2    3     4      5    6    7    8   (3 and 5 stay empty for the scroll guard;
 *                                                         6-8 are for indicators: resources, a quiver)
 * </pre>
 * The weapon IS the primary fire: its tooltip describes it and its cooldown sweep shows the fire rate.
 *
 * <p>Quiver characters: the queued bolts are arrows in their quiver's hotbar slots (tipped and tinted when
 * infused; the leftmost loads next), and a CROSSBOW weapon mirrors the engine: it shows loaded while a
 * bolt is loaded, and its Quick Charge level is the quiver's reload speed (so the client draws it at
 * that speed). The arrows also give the vanilla crossbow something to draw.
 *
 * Cooldowns show twice: the vanilla item-cooldown sweep (player.setCooldown, animated by the client)
 * and the icon's stack size = seconds left (updated every 5 ticks). Every item we place is tagged,
 * so we can remove exactly our items (on unequip, quit, death, or stale ones after a crash).
 */
public final class HotbarHud {

    public static final int WEAPON_SLOT = 4;
    /** PlayerInventory index of the offhand, where the ultimate's icon sits. */
    public static final int OFFHAND_SLOT = 40;
    /** Items stack to 64 by default; icons are raised to 99 so longer cooldowns still count down. */
    private static final int MAX_COUNT = 99;
    private static final Map<String, Integer> HOTBAR_POSITIONS = Map.of(
            Slots.ABILITY_1, 0, Slots.ABILITY_2, 1, Slots.ABILITY_3, 2, Slots.ULTIMATE, OFFHAND_SLOT);
    private static final Map<String, Material> FALLBACK_ICONS = Map.of(
            Slots.ABILITY_1, Material.LIME_DYE,
            Slots.ABILITY_2, Material.LIGHT_BLUE_DYE,
            Slots.ABILITY_3, Material.ORANGE_DYE,
            Slots.ULTIMATE, Material.NETHER_STAR);

    private final AbilityEngine engine;
    private final Keybinds keybinds;
    private final NamespacedKey hudKey;
    /** Per player: when each material's cooldown sweep we last sent ends (server ticks). */
    private final Map<UUID, Map<Material, Long>> sweepEnds = new HashMap<>();
    /** Per player: what the quiver HUD last showed, so it's only redrawn when something changed. */
    private final Map<UUID, QuiverShown> quiverShown = new HashMap<>();

    /**
     * @param revision the quiver's revision (queue + loaded bolt) @param reloadSpeed the weapon's Quick Charge
     * @param rapid    rapid fire (no drawing: the crossbow always shows the next bolt ready)
     */
    private record QuiverShown(long revision, int reloadSpeed, boolean rapid) {}
    /** Per player: which crowd control each slot showed last ("" = none), so icons swap only on a change. */
    private final Map<UUID, Map<String, String>> ccShown = new HashMap<>();
    /** Per player: the ward item last drawn ("ready" or seconds left), so it's only redrawn when it changes. */
    private final Map<UUID, String> wardShown = new HashMap<>();
    /** Per player: what each status item slot last showed ("status:stacks", "" = nothing). */
    private final Map<UUID, Map<Integer, String>> statusItemsShown = new HashMap<>();
    /** Per player: whether the weapon was last drawn glinting (a glint_weapon status item). */
    private final Map<UUID, Boolean> weaponGlint = new HashMap<>();
    /** Per player: the kit last drawn (with any form applied), so a form starting or ending redraws it. */
    private final Map<UUID, CharacterDef> renderedAs = new HashMap<>();

    public HotbarHud(Plugin plugin, AbilityEngine engine, Keybinds keybinds) {
        this.engine = engine;
        this.keybinds = keybinds;
        this.hudKey = new NamespacedKey(plugin, "hud");
    }

    // ---- character sessions -------------------------------------------------------------

    /** Overwrites the kit's hotbar slots and the offhand. Returns false for an unknown character. */
    public boolean equip(Player p, String characterId) {
        if (engine.characters().find(characterId).isEmpty()) return false;
        engine.instances().cancelAll(p.getUniqueId(), "character_change");
        engine.loadouts().assign(p.getUniqueId(), characterId);
        render(p);
        if (stats != null) stats.fill(p); // a fresh character starts at full health
        return true;
    }

    /** Max HP, move speed and the stat items (set by the plugin). */
    private StatsHud stats;

    public void setStats(StatsHud stats) { this.stats = stats; }

    /** Undo a character's max HP and speed (leftovers from a crash, on join). */
    public void scrubStats(Player p) {
        if (stats != null) stats.remove(p);
    }

    public void unequip(Player p) {
        if (stats != null) stats.remove(p);
        engine.instances().cancelAll(p.getUniqueId(), "character_change");
        engine.loadouts().clear(p.getUniqueId());
        clear(p);
    }

    // ---- drawing ----------------------------------------------------------------------------

    /** Full redraw. Clears the HUD if the player has no (or a no-longer-existing) character. */
    public void render(Player p) {
        clear(p);
        Optional<CharacterDef> character = engine.loadouts().characterOf(p.getUniqueId());
        if (character.isEmpty()) {
            if (stats != null) stats.remove(p);
            return;
        }
        renderedAs.put(p.getUniqueId(), character.get());

        PlayerInventory inv = p.getInventory();
        inv.setItem(WEAPON_SLOT, weapon(p, character.get()));
        inv.setHeldItemSlot(WEAPON_SLOT);

        for (String slot : Slots.ALL) {
            if (onWeapon(slot)) continue; // primary/secondary are described on the weapon itself
            ability(character.get(), slot).ifPresent(a -> inv.setItem(position(slot), icon(slot, a)));
        }
        for (ResourceDef def : visibleGauges(p.getUniqueId(), character.get()).values()) {
            inv.setItem(def.hotbarSlot() - 1, gauge(p, def));
        }
        drawBolts(p, character.get());
        statusItemsShown.remove(p.getUniqueId());
        weaponGlint.put(p.getUniqueId(), weaponGlints(p.getUniqueId(), character.get()));
        updateStatusItems(p);
        wardShown.remove(p.getUniqueId());
        updateWard(p);
        ccShown.remove(p.getUniqueId()); // the next update puts barriers back if still crowd-controlled
        quiverShown.put(p.getUniqueId(), quiverState(p));
        if (stats != null) stats.apply(p); // max HP, speed, and the stat items in the inventory
        refresh(p);
    }

    /** Re-sync cooldown sweeps and counters with the engine. Cheap; call after casts and cooldown resets. */
    public void refresh(Player p) {
        UUID id = p.getUniqueId();
        long now = engine.clock().now();
        Map<Material, Long> sent = sweepEnds.computeIfAbsent(id, k -> new HashMap<>());
        engine.loadouts().characterOf(id).ifPresent(c -> {
            for (String slot : Slots.ALL) {
                ability(c, slot).ifPresent(a -> {
                    long remaining = engine.cooldowns().remainingTicks(id, a.id());
                    Material m = Slots.PRIMARY.equals(slot) ? primarySweepMaterial(id, c, a) : iconMaterial(slot, a);
                    syncSweep(p, sent, m, now, remaining);
                });
            }
        });
        updateCounters(p);
        syncQuiver(p);
    }

    /**
     * Re-sending a cooldown restarts the client's sweep from full, so only send when the end time
     * actually changed (a new cast, /ae cdclear). Everything else keeps animating untouched.
     */
    private static void syncSweep(Player p, Map<Material, Long> sent, Material m, long now, long remaining) {
        long end = now + remaining;
        Long previous = sent.get(m);
        boolean running = previous != null && previous > now;
        if (remaining <= 0 && !running) {           // idle, and already shown as idle
            sent.put(m, now);
            return;
        }
        if (previous != null && Math.abs(previous - end) <= 1) return; // same sweep, already showing
        sent.put(m, end);
        p.setCooldown(m, (int) Math.min(Integer.MAX_VALUE, remaining));
    }

    /** Start the updaters: cooldown counters every 5 ticks, resource gauges every 2 (they drain fast). */
    public void start() {
        engine.scheduler().every(5, 5, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                // Full refresh: sweeps too, since a cooldown can start without a key press
                // (a recast window timing out). Sweeps are only re-sent when they changed.
                if (engine.loadouts().has(p.getUniqueId())) refresh(p);
            }
        });
        engine.scheduler().every(2, 2, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Optional<CharacterDef> now = engine.loadouts().characterOf(p.getUniqueId());
                if (now.isEmpty()) continue;
                // A form started or ended (e.g. an ultimate that swaps the weapon and primary): redraw the kit.
                if (!now.get().equals(renderedAs.get(p.getUniqueId()))) render(p);
                updateGauges(p);
                updateStatusItems(p);
                updateWard(p);
                updateCrowdControl(p);
                updateGlints(p);
                syncQuiver(p); // reload speed follows statuses that expire on their own
            }
        });
    }

    private static boolean onWeapon(String slot) {
        return Slots.PRIMARY.equals(slot) || Slots.SECONDARY.equals(slot) || Slots.MELEE.equals(slot);
    }

    // ---- recast glint ----------------------------------------------------------------------

    /** Icons glow (enchantment glint) while their ability is waiting for a recast. */
    private void updateGlints(Player p) {
        UUID id = p.getUniqueId();
        engine.loadouts().characterOf(id).ifPresent(c -> {
            PlayerInventory inv = p.getInventory();
            for (String slot : Slots.ALL) {
                if (onWeapon(slot)) continue;
                ability(c, slot).ifPresent(a -> {
                    int pos = position(slot);
                    ItemStack item = inv.getItem(pos);
                    if (!isHudItem(item)) return;
                    boolean want = engine.instances().awaitingRecast(id, a.id());
                    ItemMeta meta = item.getItemMeta();
                    boolean has = meta.hasEnchantmentGlintOverride() && meta.getEnchantmentGlintOverride();
                    if (want == has) return;
                    meta.setEnchantmentGlintOverride(want ? Boolean.TRUE : null);
                    item.setItemMeta(meta);
                    inv.setItem(pos, item);
                });
            }
        });
    }

    // ---- quiver -----------------------------------------------------------------------------

    /** A crossbow weapon + a quiver: the vanilla crossbow draws and shows loaded (see CrossbowListener). */
    public boolean usesCrossbow(Player p) {
        return engine.loadouts().characterOf(p.getUniqueId())
                .filter(c -> c.quiver() != null && weaponMaterial(c) == Material.CROSSBOW).isPresent();
    }

    /** A SPYGLASS weapon (e.g. during Arcane Barrage): hold RMB to zoom in and charge the primary, let go to fire. */
    public boolean usesScope(Player p) {
        return engine.loadouts().characterOf(p.getUniqueId()).filter(c -> weaponMaterial(c) == Material.SPYGLASS).isPresent();
    }

    private QuiverShown quiverState(Player p) {
        UUID id = p.getUniqueId();
        return new QuiverShown(engine.quivers().revision(id), engine.quivers().reloadSpeed(id), engine.quivers().rapidFire(id));
    }

    /**
     * Redraw the bolts and the weapon if the quiver or the reload speed changed. Cheap when nothing did.
     * A change of reload speed alone waits while the player is drawing (swapping the item mid-draw would
     * restart it); a bolt loaded by an ability shows at once and ends any draw in progress.
     */
    private void syncQuiver(Player p) {
        UUID id = p.getUniqueId();
        Optional<CharacterDef> c = engine.loadouts().characterOf(id);
        if (c.isEmpty() || c.get().quiver() == null) return;
        QuiverShown now = quiverState(p);
        QuiverShown before = quiverShown.get(id);
        if (now.equals(before)) return;

        boolean boltsChanged = before == null || before.revision() != now.revision();
        if (!boltsChanged && p.isHandRaised()) return; // only the speed changed: after the draw
        if (boltsChanged) drawBolts(p, c.get());
        ItemStack held = p.getInventory().getItem(WEAPON_SLOT);
        // (A crowd-control barrier on the weapon stays until it ends; updateCrowdControl puts the weapon back.)
        if (isHudItem(held) && engine.loadouts().crowdControl(id, Slots.PRIMARY).isEmpty()) {
            boolean loadedNow = engine.quivers().isLoaded(id);
            boolean shownLoaded = held.getItemMeta() instanceof CrossbowMeta cm && cm.hasChargedProjectiles();
            p.getInventory().setItem(WEAPON_SLOT, weapon(p, c.get()));
            if (loadedNow && !shownLoaded && p.isHandRaised()) p.clearActiveItem(); // loaded for you: stop drawing
        }
        quiverShown.put(id, now);
    }

    /** The queued bolts as arrows in the quiver's hotbar slots, next-to-load leftmost. */
    private void drawBolts(Player p, CharacterDef c) {
        QuiverDef q = c.quiver();
        if (q == null || q.hotbarSlot() <= 0) return;
        List<Bolt> queue = engine.quivers().queue(p.getUniqueId());
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < q.size(); i++) {
            inv.setItem(q.hotbarSlot() - 1 + i, i < queue.size() ? boltItem(queue.get(i), i == 0) : null);
        }
    }

    /** A plain bolt is an arrow; an infused one a tipped arrow in the infusions' mixed color. */
    private ItemStack boltItem(Bolt bolt, boolean next) {
        List<InfusionDef> infusions = infusionsOf(bolt);
        ItemStack item = new ItemStack(infusions.isEmpty() ? Material.ARROW : Material.TIPPED_ARROW);
        ItemMeta meta = item.getItemMeta();
        String tint = engine.infusions().tintOf(bolt);
        if (meta instanceof PotionMeta potion && tint != null) potion.setColor(Color.fromRGB(Integer.parseInt(tint.substring(1), 16)));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP); // no "No Effects" potion line
        String name = boltName(infusions);
        meta.displayName(next
                ? plain("[Next] ", NamedTextColor.YELLOW).append(plain(name, NamedTextColor.WHITE))
                : plain(name, NamedTextColor.WHITE));
        List<Component> lore = new ArrayList<>();
        for (InfusionDef inf : infusions) {
            lore.add(plain(inf.name(), TextColor.fromHexString(inf.color())));
            for (String line : inf.description()) lore.add(plain("  " + line, NamedTextColor.GRAY));
        }
        if (next) lore.add(plain("Loads next (hold RMB to draw)", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        return tag(item, meta);
    }

    private List<InfusionDef> infusionsOf(Bolt bolt) {
        List<InfusionDef> out = new ArrayList<>();
        for (String id : bolt.infusions()) engine.infusions().find(id).ifPresent(out::add);
        return out;
    }

    private static String boltName(List<InfusionDef> infusions) {
        if (infusions.isEmpty()) return "Bolt";
        List<String> names = new ArrayList<>();
        for (InfusionDef inf : infusions) names.add(inf.name());
        return String.join(" + ", names) + " Bolt";
    }

    // ---- resource gauges -------------------------------------------------------------------

    // ---- crowd control: blocked slots show a barrier -------------------------------------------

    /**
     * An icon whose slot can't be used right now (stunned: all; silenced: abilities; disarmed: primary
     * fire, on the weapon; rooted: movement abilities) turns into a barrier until it can. The passive
     * (ward) never does.
     */
    private void updateCrowdControl(Player p) {
        UUID id = p.getUniqueId();
        Optional<CharacterDef> character = engine.loadouts().characterOf(id);
        if (character.isEmpty()) return;
        CharacterDef c = character.get();
        Map<String, String> now = new HashMap<>();
        for (String slot : Slots.ALL) {
            if (ability(c, slot).isEmpty()) continue;
            now.put(slot, engine.loadouts().crowdControl(id, slot).orElse(""));
        }
        Map<String, String> before = ccShown.getOrDefault(id, Map.of());
        if (now.equals(before)) return;
        ccShown.put(id, now);
        PlayerInventory inv = p.getInventory();
        for (var entry : now.entrySet()) {
            String slot = entry.getKey();
            String cc = entry.getValue();
            if (cc.equals(before.get(slot))) continue;
            if (Slots.PRIMARY.equals(slot)) {                        // primary fire lives on the weapon
                inv.setItem(WEAPON_SLOT, cc.isEmpty() ? weapon(p, c) : barrier(c.name(), cc));
            } else if (!onWeapon(slot)) {
                Ability a = ability(c, slot).get();
                inv.setItem(position(slot), cc.isEmpty() ? icon(slot, a) : barrier(a.display().name(), cc));
            }
        }
        refresh(p); // counters and sweeps back on the restored icons
    }

    private ItemStack barrier(String name, String ccTag) {
        String why = switch (ccTag) {
            case me.mephisto.ability_engine.engine.tag.Tags.STUNNED -> "Stunned";
            case me.mephisto.ability_engine.engine.tag.Tags.SILENCED -> "Silenced";
            case me.mephisto.ability_engine.engine.tag.Tags.DISARMED -> "Disarmed";
            case me.mephisto.ability_engine.engine.tag.Tags.ROOTED -> "Rooted";
            default -> "Blocked";
        };
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(MAX_COUNT);
        meta.displayName(plain(name + " - " + why, NamedTextColor.RED));
        meta.lore(List.of(plain("Can't be used while " + why.toLowerCase(java.util.Locale.ROOT) + ".", NamedTextColor.GRAY)));
        return tag(item, meta);
    }

    /**
     * The character's ward (passive debuff immunity): glinting while ready, otherwise the stack counts the
     * seconds until it's ready (they only run out of combat).
     */
    private void updateWard(Player p) {
        UUID id = p.getUniqueId();
        engine.loadouts().characterOf(id).map(CharacterDef::ward).filter(w -> w.hotbarSlot() > 0).ifPresent(ward ->
                engine.wards().state(id).ifPresent(state -> {
                    int seconds = (int) Math.min(MAX_COUNT, Math.max(1, Math.ceil(state.rechargeTicks() / 20.0)));
                    String shown = state.ready() ? "ready" : String.valueOf(seconds);
                    if (shown.equals(wardShown.get(id))) return;
                    wardShown.put(id, shown);
                    p.getInventory().setItem(ward.hotbarSlot() - 1, wardItem(ward, state.ready(), seconds));
                }));
    }

    private ItemStack wardItem(CharacterDef.Ward ward, boolean ready, int seconds) {
        Material m = ward.icon() == null ? null : Material.matchMaterial(ward.icon());
        ItemStack item = new ItemStack(m != null && m.isItem() ? m : Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(MAX_COUNT);
        meta.displayName(plain("[Passive] ", NamedTextColor.YELLOW).append(plain(ward.name(), NamedTextColor.WHITE)));
        List<Component> lore = new ArrayList<>();
        for (String line : ward.description()) lore.add(plain(line, NamedTextColor.GRAY));
        String readyText = ward.isBarrier()
                ? String.format("Ready: takes %.0f%% of the next hit", ward.absorb() * 100)
                : "Ready: the next debuff won't land";
        lore.add(ready ? plain(readyText, NamedTextColor.GREEN)
                : plain("Recharging: " + seconds + "s out of combat", NamedTextColor.RED));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(ready ? Boolean.TRUE : null);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setAmount(ready ? 1 : seconds);
        return tag(item, meta);
    }

    /**
     * The resource shown in each hotbar slot right now: the first one (in the order they're declared) whose
     * shown_while / hidden_while tags allow it, e.g. the ammo of the gun in your hand.
     */
    private Map<Integer, ResourceDef> visibleGauges(UUID id, CharacterDef c) {
        Map<Integer, ResourceDef> out = new java.util.LinkedHashMap<>();
        for (ResourceDef def : c.resources().values()) {
            if (def.hotbarSlot() <= 0 || out.containsKey(def.hotbarSlot())) continue;
            if (def.shownWhile() != null && !engine.tags().has(id, def.shownWhile())) continue;
            if (def.hiddenWhile() != null && engine.tags().has(id, def.hiddenWhile())) continue;
            out.put(def.hotbarSlot(), def);
        }
        return out;
    }

    /**
     * Stack size follows the amount (1..99; Minecraft can't show 0 or more than 99), name shows the exact
     * value. Ammo that's reloading shows a cooldown sweep for the time left.
     */
    private void updateGauges(Player p) {
        UUID id = p.getUniqueId();
        engine.loadouts().characterOf(id).ifPresent(c -> {
            PlayerInventory inv = p.getInventory();
            Map<Integer, ResourceDef> visible = visibleGauges(id, c);
            Map<Material, Long> sent = sweepEnds.computeIfAbsent(id, k -> new HashMap<>());
            long now = engine.clock().now();
            for (ResourceDef def : c.resources().values()) {
                int slot = def.hotbarSlot();
                if (slot <= 0) continue;
                int pos = slot - 1;
                ItemStack item = inv.getItem(pos);
                if (!visible.containsKey(slot)) {           // nothing to show here right now
                    if (isHudItem(item)) inv.setItem(pos, null);
                    continue;
                }
                if (visible.get(slot) != def) continue;
                if (def.reloadTicks() > 0) {
                    syncSweep(p, sent, gaugeMaterial(def), now, engine.resources().reloadRemaining(id, def.id()));
                }
                if (item == null || item.getType() != gaugeMaterial(def) // another gauge (or nothing) was there
                        || item.getAmount() != gaugeAmount(p, def)
                        || !gaugeName(p, def).equals(item.getItemMeta().displayName())) {
                    if (item == null || isHudItem(item)) inv.setItem(pos, gauge(p, def));
                }
            }
        });
    }

    private int shownValue(Player p, ResourceDef def) {
        return engine.resources().get(p.getUniqueId(), def.id());
    }

    private int gaugeAmount(Player p, ResourceDef def) {
        return Math.max(1, Math.min(MAX_COUNT, shownValue(p, def)));
    }

    private Component gaugeName(Player p, ResourceDef def) {
        int shown = shownValue(p, def);
        String title = Character.toUpperCase(def.id().charAt(0)) + def.id().substring(1);
        if (engine.resources().reloadRemaining(p.getUniqueId(), def.id()) > 0) {
            return plain(title + ": reloading...", NamedTextColor.RED);
        }
        return plain(title + " " + shown + "/" + (int) def.max(), shown <= 0 ? NamedTextColor.RED : NamedTextColor.AQUA);
    }

    private static Material gaugeMaterial(ResourceDef def) {
        Material m = def.icon() == null ? null : Material.matchMaterial(def.icon());
        return m != null && m.isItem() ? m : Material.LAPIS_LAZULI;
    }

    private ItemStack gauge(Player p, ResourceDef def) {
        ItemStack item = new ItemStack(gaugeMaterial(def));
        item.setAmount(gaugeAmount(p, def));
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(MAX_COUNT);
        meta.displayName(gaugeName(p, def));
        if (def.reloadTicks() > 0) {
            meta.lore(List.of(plain(String.format("Reloads %.1fs after the last shot when empty", def.reloadTicks() / 20.0),
                    NamedTextColor.GRAY)));
        }
        return tag(item, meta);
    }

    // ---- status items (e.g. magic rounds loaded) ------------------------------------------------

    /** The first status item per slot the player has right now. */
    private Map<Integer, CharacterDef.StatusItem> activeStatusItems(UUID id, CharacterDef c) {
        Map<Integer, CharacterDef.StatusItem> out = new HashMap<>();
        for (CharacterDef.StatusItem item : c.statusItems()) {
            if (!out.containsKey(item.hotbarSlot()) && engine.statuses().has(id, item.status())) out.put(item.hotbarSlot(), item);
        }
        return out;
    }

    private boolean weaponGlints(UUID id, CharacterDef c) {
        return activeStatusItems(id, c).values().stream().anyMatch(CharacterDef.StatusItem::glintWeapon);
    }

    /** Status items: shown while the status lasts, stack = its stacks; the weapon glints with glint_weapon ones. */
    private void updateStatusItems(Player p) {
        UUID id = p.getUniqueId();
        Optional<CharacterDef> character = engine.loadouts().characterOf(id);
        if (character.isEmpty() || character.get().statusItems().isEmpty()) return;
        CharacterDef c = character.get();
        PlayerInventory inv = p.getInventory();
        Map<Integer, CharacterDef.StatusItem> active = activeStatusItems(id, c);
        Map<Integer, String> shown = statusItemsShown.computeIfAbsent(id, k -> new HashMap<>());
        for (CharacterDef.StatusItem entry : c.statusItems()) {
            int slot = entry.hotbarSlot();
            CharacterDef.StatusItem want = active.get(slot);
            int stacks = want == null ? 0 : engine.statuses().find(id, want.status()).map(s -> s.stacks()).orElse(0);
            String key = want == null ? "" : want.status() + ":" + stacks;
            if (key.equals(shown.get(slot))) continue;
            shown.put(slot, key);
            ItemStack there = inv.getItem(slot - 1);
            if (there != null && !isHudItem(there)) continue; // the player's own item: leave it
            inv.setItem(slot - 1, want == null ? null : statusItem(want, stacks));
        }
        boolean glint = weaponGlints(id, c);
        if (glint != weaponGlint.getOrDefault(id, false)) {
            weaponGlint.put(id, glint);
            ItemStack held = inv.getItem(WEAPON_SLOT);
            // (a crowd-control barrier on the weapon stays; updateCrowdControl puts the weapon back, glint included)
            if (isHudItem(held) && engine.loadouts().crowdControl(id, Slots.PRIMARY).isEmpty()) {
                inv.setItem(WEAPON_SLOT, weapon(p, c));
            }
        }
    }

    private ItemStack statusItem(CharacterDef.StatusItem entry, int stacks) {
        Material m = entry.icon() == null ? null : Material.matchMaterial(entry.icon());
        ItemStack item = new ItemStack(m != null && m.isItem() ? m : Material.GLOWSTONE_DUST);
        item.setAmount(Math.max(1, Math.min(MAX_COUNT, stacks)));
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(MAX_COUNT);
        meta.displayName(plain(entry.name() + (stacks > 0 ? " x" + stacks : ""), NamedTextColor.LIGHT_PURPLE));
        List<Component> lore = new ArrayList<>();
        for (String line : entry.description()) lore.add(plain(line, NamedTextColor.GRAY));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        return tag(item, meta);
    }

    /** Icon stack size = whole seconds of cooldown left (rounded up), 1 when ready (no number shown). */
    private void updateCounters(Player p) {
        UUID id = p.getUniqueId();
        engine.loadouts().characterOf(id).ifPresent(c -> {
            PlayerInventory inv = p.getInventory();
            for (String slot : Slots.ALL) {
                if (onWeapon(slot)) continue;
                ability(c, slot).ifPresent(a -> {
                    int pos = position(slot);
                    ItemStack item = inv.getItem(pos);
                    if (!isHudItem(item)) return;
                    long remaining = engine.cooldowns().remainingTicks(id, a.id());
                    int seconds = remaining <= 0 ? 1 : (int) Math.min(MAX_COUNT, Math.ceil(remaining / 20.0));
                    // Charges: while any are left, the stack shows how many (the sweep only shows with none).
                    if (a.charges() > 1 && remaining <= 0) seconds = engine.cooldowns().charges(id, a.id());
                    if (item.getAmount() != seconds) {
                        item.setAmount(seconds);
                        inv.setItem(pos, item);
                    }
                });
            }
        });
    }

    /** Remove every item this HUD placed. Leaves the player's own items alone. */
    public void clear(Player p) {
        sweepEnds.remove(p.getUniqueId()); // next refresh re-sends every sweep
        quiverShown.remove(p.getUniqueId());
        wardShown.remove(p.getUniqueId());
        ccShown.remove(p.getUniqueId());
        renderedAs.remove(p.getUniqueId());
        statusItemsShown.remove(p.getUniqueId());
        weaponGlint.remove(p.getUniqueId());
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isHudItem(contents[i])) inv.setItem(i, null);
        }
    }

    public boolean isHudItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(hudKey, PersistentDataType.BYTE);
    }

    // ---- items ------------------------------------------------------------------------------

    private Optional<Ability> ability(CharacterDef c, String slot) {
        String abilityId = c.abilityIn(slot);
        return abilityId == null ? Optional.empty() : engine.abilities().find(abilityId);
    }

    private static int position(String slot) {
        return Slots.PRIMARY.equals(slot) ? WEAPON_SLOT : HOTBAR_POSITIONS.getOrDefault(slot, 8);
    }

    private static Material iconMaterial(String slot, Ability a) {
        Material m = a.display().icon() == null ? null : Material.matchMaterial(a.display().icon());
        return m != null && m.isItem() ? m : FALLBACK_ICONS.getOrDefault(slot, Material.PAPER);
    }

    private static Material weaponMaterial(CharacterDef c) {
        Material m = c.weapon() == null ? null : Material.matchMaterial(c.weapon());
        return m != null && m.isItem() ? m : Material.IRON_SWORD;
    }

    /**
     * Where the primary's cooldown sweep shows: on the item in hand, unless the primary's own icon is one of
     * the character's weapons (two guns: the shotgun's sweep stays on the shotgun while the revolver is out).
     */
    private Material primarySweepMaterial(UUID id, CharacterDef c, Ability a) {
        Material icon = a.display().icon() == null ? null : Material.matchMaterial(a.display().icon());
        if (icon != null) {
            java.util.Set<Material> weapons = new java.util.HashSet<>();
            weapons.add(weaponMaterial(c));
            engine.loadouts().baseCharacterOf(id).ifPresent(base -> weapons.add(weaponMaterial(base)));
            for (CharacterDef.Form f : c.forms()) {
                Material m = f.weapon() == null ? null : Material.matchMaterial(f.weapon());
                if (m != null) weapons.add(m);
            }
            if (weapons.contains(icon)) return icon;
        }
        return weaponMaterial(c);
    }

    private ItemStack weapon(Player p, CharacterDef c) {
        ItemStack item = new ItemStack(weaponMaterial(c));
        ItemMeta meta = item.getItemMeta();
        if (weaponGlints(p.getUniqueId(), c)) meta.setEnchantmentGlintOverride(true); // magic rounds loaded
        meta.displayName(plain(c.name(), NamedTextColor.GOLD));
        meta.setUnbreakable(true);
        List<Component> lore = new ArrayList<>();
        if (c.quiver() != null) crossbowState(p, meta, lore);
        for (String slot : List.of(Slots.PRIMARY, Slots.SECONDARY, Slots.MELEE)) {
            ability(c, slot).ifPresent(a -> {
                String key = Slots.MELEE.equals(slot) ? "LMB on target"
                        : keybinds.actionFor(slot).map(InputAction::defaultKey).orElse("-");
                lore.add(plain("[" + key + "] ", NamedTextColor.YELLOW).append(plain(a.display().name(), NamedTextColor.WHITE)));
                for (String line : a.display().description()) lore.add(plain(line, NamedTextColor.GRAY));
            });
        }
        if (!lore.isEmpty()) meta.lore(lore);
        return tag(item, meta);
    }

    /**
     * The quiver's state on the weapon: loaded or not (a crossbow shows the bolt), reload speed as Quick
     * Charge (the client draws faster; the glint shows it's active), and a tooltip line for both.
     */
    private void crossbowState(Player p, ItemMeta meta, List<Component> lore) {
        UUID id = p.getUniqueId();
        Optional<Bolt> loaded = engine.quivers().loaded(id);
        boolean rapid = engine.quivers().rapidFire(id);
        if (rapid && loaded.isEmpty()) loaded = engine.quivers().queue(id).stream().findFirst(); // shot straight from the quiver
        if (meta instanceof CrossbowMeta crossbow) {
            crossbow.setChargedProjectiles(loaded.map(b -> List.of(boltItem(b, false))).orElse(List.of()));
        }
        int speed = engine.quivers().reloadSpeed(id);
        if (speed > 0) meta.addEnchant(Enchantment.QUICK_CHARGE, speed, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP); // our own lines below
        if (rapid) lore.add(plain("Rapid fire: RMB (or hold it) to shoot", NamedTextColor.GOLD));
        lore.add(loaded.isPresent()
                ? plain("Loaded: ", NamedTextColor.GRAY).append(plain(boltName(infusionsOf(loaded.get())), NamedTextColor.GREEN))
                : plain("Not loaded (hold RMB to draw)", NamedTextColor.RED));
        if (speed > 0) lore.add(plain("Reload speed +" + speed, NamedTextColor.AQUA));
    }

    private ItemStack icon(String slot, Ability a) {
        ItemStack item = new ItemStack(iconMaterial(slot, a));
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(MAX_COUNT);
        String key = keybinds.actionFor(slot).map(InputAction::defaultKey).orElse("-");
        meta.displayName(plain("[" + key + "] ", NamedTextColor.YELLOW).append(plain(a.display().name(), NamedTextColor.WHITE)));

        List<Component> lore = new ArrayList<>();
        for (String line : a.display().description()) lore.add(plain(line, NamedTextColor.GRAY));
        if (a.cooldownTicks() > 0) {
            lore.add(plain(a.charges() > 1
                    ? String.format("%d charges, %.1fs each", a.charges(), a.cooldownTicks() / 20.0)
                    : String.format("Cooldown: %.1fs", a.cooldownTicks() / 20.0), NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);
        return tag(item, meta);
    }

    private ItemStack tag(ItemStack item, ItemMeta meta) {
        meta.getPersistentDataContainer().set(hudKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}

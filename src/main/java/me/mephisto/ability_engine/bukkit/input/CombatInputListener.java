package me.mephisto.ability_engine.bukkit.input;

import me.mephisto.ability_engine.bukkit.effect.DamageEffect;
import me.mephisto.ability_engine.bukkit.hud.HotbarHud;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.activation.AbilityActivator;
import me.mephisto.ability_engine.engine.ability.activation.ActivationResult;
import me.mephisto.ability_engine.engine.loadout.Slots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns vanilla inputs into character slots (see Keybinds / config.yml). Default scheme:
 * LMB = primary fire, 1/2/3 = abilities, F (or 7) = ultimate, RMB reserved, Q free.
 *
 * <p>With a character: the held slot is locked to the weapon, vanilla melee is replaced by the
 * primary fire, and Q / F never drop or swap the weapon. Without one: vanilla behaviour
 * (and /ae bind debug items).
 *
 * <p>While AIMING (targeting preview): LMB confirms, RMB cancels, another ability's key switches. The
 * aimed ability's own key does nothing (it doesn't confirm). Recasts stay on the key.
 *
 * <p>Crossbow characters (a CROSSBOW weapon with a quiver) handle it like vanilla: hold RMB to draw
 * (CrossbowListener loads the next bolt when it's drawn), then press RMB again to shoot, which fires the
 * primary slot. LMB doesn't shoot (it still confirms aim previews and uses a melee slot). Their secondary
 * slot is unused. During rapid fire (the quiver's rapid_fire_while) there's no drawing: every RMB, and
 * holding it, shoots.
 *
 * <p>A SPYGLASS weapon (a form, e.g. Arcane Barrage): hold RMB to zoom in (vanilla's spyglass) and charge
 * the primary; letting go fires it (the engine's charge node), and a shot that fired by itself at full
 * charge ends the zoom. LMB does nothing.
 */
public final class CombatInputListener implements Listener {

    /** Holding right click re-sends "use" about every 4 ticks. Closer than this = still held. */
    private static final long RIGHT_CLICK_HOLD_GAP = 5;
    /**
     * Holding a KEY makes the OS repeat it, and Minecraft treats each repeat as a new press. Repeats come
     * every tick or two; a gap this small is a repeat, not a new press. (The very first repeat, after the
     * OS's initial delay, can't be told apart from a quick second tap.)
     */
    private static final long KEY_REPEAT_GAP = 3;
    /**
     * The longest OS delay before a held key starts repeating (1s, the slowest setting). A press within
     * KEY_REPEAT_GAP of one that followed at least this much quiet can't be a repeat yet: it's a double tap.
     */
    private static final long KEY_REPEAT_DELAY_MAX = 20;
    /** Clicks this soon after a click or Q opened a preview are echoes of that press (arm swing etc.). */
    private static final long CLICK_GRACE_TICKS = 3;
    /**
     * The client sends a hotbar change at the start of its NEXT tick, but a click at air right away: press a
     * number key and click in the same client tick and the click arrives first. So a click that did nothing
     * (primary on cooldown etc.) confirms a preview a number key / F opens this many ticks later.
     */
    private static final long CLICK_BUFFER_TICKS = 2;
    /** A scroll-wheel notch always lands next to the weapon; ignore everything briefly after one. */
    private static final long SCROLL_IGNORE_TICKS = 4;
    /**
     * Pressing Q makes the client swing its arm, and Bukkit reports that swing as a left click.
     * Left clicks this soon after a drop are that echo, not a real click.
     */
    private static final long DROP_SWING_ECHO_TICKS = 2;

    private final AbilityEngine engine;
    private final Keybinds keybinds;
    private final HotbarHud hud;
    private final Map<UUID, Map<InputAction, Long>> lastPress = new HashMap<>();
    /** The quiet before each action's last press: tells a quick double tap from a held key's repeats. */
    private final Map<UUID, Map<InputAction, Long>> gapBeforeLast = new HashMap<>();
    private final Map<UUID, Long> scrollIgnoreUntil = new HashMap<>();
    /** When a swingless key last opened a preview (engine ticks): that preview needs no click grace. */
    private final Map<UUID, Long> keyOpenedAt = new HashMap<>();
    /** When a left click last did nothing (server ticks): a preview opened right after takes it as its confirm. */
    private final Map<UUID, Long> idleClickAt = new HashMap<>();
    /** Right clicks before this tick are the client picking a held RMB back up after a slot snap-back. */
    private final Map<UUID, Long> rmbEchoUntil = new HashMap<>();

    /** Players who started a charge with the spyglass: watched every tick for letting go. */
    private final java.util.Set<UUID> scoping = new java.util.HashSet<>();

    public CombatInputListener(AbilityEngine engine, Keybinds keybinds, HotbarHud hud) {
        this.engine = engine;
        this.keybinds = keybinds;
        this.hud = hud;
        engine.scheduler().every(1, 1, this::watchScopes);
    }

    private boolean inCombat(Player p) { return engine.loadouts().has(p.getUniqueId()); }

    private boolean aiming(Player p) { return engine.targeting().isTargeting(p.getUniqueId()); }

    /**
     * Clicks right after a preview opened are echoes of the press that opened it, unless that was a key with
     * no arm swing (number keys, F): then even a click in the very next tick confirms.
     */
    private boolean inGrace(Player p) {
        long age = engine.targeting().ageTicks(p.getUniqueId());
        if (age < 0 || age >= CLICK_GRACE_TICKS) return false;
        Long byKey = keyOpenedAt.get(p.getUniqueId());
        return byKey == null || byKey != engine.clock().now() - age; // this preview wasn't opened by such a key
    }

    /** Keys whose press doesn't swing the arm (Q does: the client swings on a drop). */
    private static boolean swingless(InputAction action) {
        return action == InputAction.SWAP_HANDS || action.name().startsWith("HOTBAR_");
    }

    /** Ticks since the last recorded press of an action, without recording one (MAX if never). */
    private long ticksSince(Player p, InputAction action) {
        Map<InputAction, Long> mine = lastPress.get(p.getUniqueId());
        Long last = mine == null ? null : mine.get(action);
        return last == null ? Long.MAX_VALUE : Bukkit.getCurrentTick() - last;
    }

    /** Records the press; returns ticks since the previous press of the same action (MAX if first). */
    private long sinceLast(Player p, InputAction action) {
        long now = Bukkit.getCurrentTick();
        Long last = lastPress.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(InputAction.class)).put(action, now);
        return last == null ? Long.MAX_VALUE : now - last;
    }

    // ---- mouse ------------------------------------------------------------------------------

    // NOT ignoreCancelled: Bukkit fires *_CLICK_AIR already marked cancelled.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // Stepping on a pressure plate or tripwire (no hand): not an input. Cancelling it would make
        // plates dead under anyone with a character, e.g. a champ-select plate running /ae char none @p.
        if (e.getAction() == Action.PHYSICAL) return;
        if (e.getHand() != EquipmentSlot.HAND) {
            if (inCombat(p)) e.setCancelled(true); // the offhand holds the ultimate's icon: never "use" it
            return;
        }
        Action a = e.getAction();
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) return;
        if (!inCombat(p) && !aiming(p)) return;
        if (right && !aiming(p) && hud.usesCrossbow(p)) {
            crossbowRightClick(p, e);
            return;
        }
        if (right && !aiming(p) && hud.usesScope(p)) {
            scopeRightClick(p, e);
            return;
        }
        if (right && !aiming(p) && hud.usesBow(p)) { // vanilla draws it (its arrows are the HUD's); letting go fires
            e.setUseInteractedBlock(Event.Result.DENY); // no doors or chests
            e.setUseItemInHand(Event.Result.ALLOW);
            return;
        }
        e.setCancelled(true); // no block breaking, doors, chests or item use
        if (left) leftClick(p);
        else rightClick(p);
    }

    /**
     * Nothing loaded (and allowed to reload): let vanilla draw the crossbow, but still no doors or chests.
     * Loaded: a fresh press shoots, through the engine (the primary slot), never vanilla's own shot.
     */
    private void crossbowRightClick(Player p, PlayerInteractEvent e) {
        boolean freshPress = sinceLast(p, InputAction.RIGHT_CLICK) > RIGHT_CLICK_HOLD_GAP && !rmbEcho(p);
        if (engine.quivers().rapidFire(p.getUniqueId())) { // no drawing: every press, and holding, shoots
            e.setCancelled(true);
            if (p.isHandRaised()) p.clearActiveItem(); // the client started a draw: stop it
            p.updateInventory();
            shoot(p);
            return;
        }
        if (engine.quivers().canLoad(p.getUniqueId())) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.ALLOW);
            return;
        }
        e.setCancelled(true);
        p.updateInventory(); // the client may already show vanilla's shot; the HUD redraws the real state
        if (freshPress && engine.quivers().isLoaded(p.getUniqueId())) shoot(p);
    }

    /**
     * Spyglass: start charging the primary and let vanilla zoom in. Already charging: keep zooming.
     * Can't fire (no shots, cooldown): no zoom either.
     */
    private void scopeRightClick(Player p, PlayerInteractEvent e) {
        UUID id = p.getUniqueId();
        e.setUseInteractedBlock(Event.Result.DENY); // no doors or chests
        if (engine.instances().charging(id)) {
            e.setUseItemInHand(Event.Result.ALLOW);
            return;
        }
        ActivationResult result = engine.loadouts().activate(id, Slots.PRIMARY, true);
        if (result.success() && engine.instances().charging(id)) {
            e.setUseItemInHand(Event.Result.ALLOW);
            scoping.add(id);
            hud.refresh(p);
            return;
        }
        e.setUseItemInHand(Event.Result.DENY);
        if (!result.success() && !result.reason().startsWith("on_cooldown")) {
            p.sendActionBar(Component.text(result.reason(), NamedTextColor.RED));
        }
    }

    /** Every tick: a charging player who let go of RMB fires; a shot that fired by itself ends the zoom. */
    private void watchScopes() {
        for (UUID id : List.copyOf(scoping)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                scoping.remove(id);
                continue;
            }
            boolean holding = p.isHandRaised() && p.getActiveItem().getType() == org.bukkit.Material.SPYGLASS;
            boolean charging = engine.instances().charging(id);
            if (charging && !holding) engine.instances().release(id); // let go: fire
            if (!charging && holding) p.clearActiveItem();             // fired at full charge: stop zooming
            if (!charging || !holding) {
                scoping.remove(id);
                hud.refresh(p);
            }
        }
    }

    /**
     * A BOW weapon let go: vanilla's arrow never flies (nor is one used up); the primary slot fires instead, with how far
     * it was drawn (0.1-1) as {@code draw} for the ability (e.g. {@code scale_by: draw} on its damage).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onShootBow(org.bukkit.event.entity.EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !inCombat(p) || !hud.usesBow(p)) return;
        e.setCancelled(true);
        e.setConsumeItem(false);
        ActivationResult result = engine.loadouts().activate(p.getUniqueId(), Slots.PRIMARY, true,
                java.util.Map.of("draw", (double) e.getForce()));
        engine.scheduler().after(1, p::updateInventory); // the client thinks an arrow was used up
        if (result.success()) hud.refresh(p);
        else if (!result.reason().startsWith("on_cooldown")) p.sendActionBar(Component.text(result.reason(), NamedTextColor.RED));
    }

    /** Crossbow characters: fire the primary slot (it takes the loaded bolt). */
    private void shoot(Player p) {
        ActivationResult result = engine.loadouts().activate(p.getUniqueId(), Slots.PRIMARY, true);
        if (result.success()) hud.refresh(p);
        else if (!result.reason().startsWith("on_cooldown")) p.sendActionBar(Component.text(result.reason(), NamedTextColor.RED));
    }

    /** Right-clicking a mob/player does NOT fire PlayerInteractEvent. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND) {
            if (inCombat(p)) e.setCancelled(true);
            return;
        }
        if (!inCombat(p) && !aiming(p)) return;
        // Another plugin's NPC (ae_ignore): a plain interaction for it to handle, not a secondary fire
        if (e.getRightClicked().getScoreboardTags().contains(
                me.mephisto.ability_engine.bukkit.platform.BukkitWorldQuery.IGNORE_TAG)) return;
        e.setCancelled(true);
        // Crossbows: the client follows up with a plain "use item" (onInteract), which draws.
        if (!aiming(p) && (hud.usesCrossbow(p) || hud.usesScope(p))) return;
        rightClick(p);
    }

    /**
     * Left-clicking an entity. Paper fires this for EVERY attack attempt, even on entities in their
     * hit-cooldown frames (the plain damage event is skipped then, which is why melee-range clicks
     * used to do nothing). Cancelled: the held weapon never deals vanilla damage or knockback.
     * Then: confirm a preview, or the melee slot aimed at that entity, or primary fire.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        if (!inCombat(p) && !aiming(p)) return;
        e.setCancelled(true);
        if (ticksSince(p, InputAction.DROP) <= DROP_SWING_ECHO_TICKS) return;
        if (sinceLast(p, InputAction.LEFT_CLICK) == 0) return; // the swing of this same click already counted
        if (aiming(p)) {
            confirm(p);
            return;
        }
        UUID id = p.getUniqueId();
        if (engine.loadouts().abilityIn(id, Slots.MELEE).isPresent()) {
            ActivationResult r = engine.loadouts().activateOn(id, Slots.MELEE, new EntityTarget(e.getAttacked().getUniqueId()));
            if (r.success()) hud.refresh(p);
            else if (!r.reason().startsWith("on_cooldown")) p.sendActionBar(Component.text(r.reason(), NamedTextColor.RED));
        } else if (!hud.usesCrossbow(p) && !hud.usesScope(p) && !hud.usesBow(p)) { // crossbows and bows shoot with RMB, spyglasses charge with it
            // The client says it hit this one: the swing's own (server-side) aim may still miss it - a step out of
            // a hitscan's range, a ray past its hitbox, where it was a moment ago on the server. It counts anyway
            // when it's within reach (ClickAssist).
            fire(p, InputAction.LEFT_CLICK, true, java.util.Map.of(
                    me.mephisto.ability_engine.engine.target.ClickAssist.KEY, new EntityTarget(e.getAttacked().getUniqueId())));
        }
    }

    /** Safety net: no vanilla melee damage from players in combat (sweep attacks etc.). Ability damage passes. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVanillaMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (DamageEffect.isApplying()) return; // our own ability damage uses the player as its source
        if (inCombat(p) || aiming(p)) e.setCancelled(true);
    }

    private void leftClick(Player p) {
        if (ticksSince(p, InputAction.DROP) <= DROP_SWING_ECHO_TICKS) return; // arm swing from pressing Q
        // Swinging at an entity reports both a swing and a hit in the same tick: count it once.
        if (sinceLast(p, InputAction.LEFT_CLICK) == 0) return;
        if (aiming(p)) {
            confirm(p);
            return;
        }
        // Crossbows and bows shoot with RMB, spyglasses charge with it: their LMB does nothing.
        boolean did = !hud.usesCrossbow(p) && !hud.usesScope(p) && !hud.usesBow(p) && fire(p, InputAction.LEFT_CLICK, true);
        if (!did) idleClickAt.put(p.getUniqueId(), (long) Bukkit.getCurrentTick()); // may be meant for a preview (see CLICK_BUFFER_TICKS)
    }

    /** Is this input the key of the ability being aimed right now? */
    private boolean aimedWith(Player p, InputAction action) {
        UUID id = p.getUniqueId();
        var aimed = engine.targeting().current(id);
        if (aimed.isEmpty()) return false;
        return keybinds.slotFor(action).flatMap(slot -> engine.loadouts().abilityIn(id, slot))
                .filter(aimed.get().id()::equals).isPresent();
    }

    private void confirm(Player p) {
        if (inGrace(p)) return; // arm swing from the press that opened the preview
        ActivationResult result = engine.targeting().confirm(p.getUniqueId());
        if (result.success()) hud.refresh(p);
        else p.sendActionBar(Component.text(result.reason(), NamedTextColor.RED));
    }

    private void rightClick(Player p) {
        long since = sinceLast(p, InputAction.RIGHT_CLICK);
        if (since == 0) return; // entity click can also send a use-item packet: same tick = same click
        boolean freshPress = since > RIGHT_CLICK_HOLD_GAP && !rmbEcho(p);
        if (aiming(p)) {
            if (!freshPress || inGrace(p)) return;
            engine.targeting().cancel(p.getUniqueId(), "cancelled");
            return;
        }
        if (inCombat(p)) fire(p, InputAction.RIGHT_CLICK, freshPress);
    }

    // ---- keys ---------------------------------------------------------------------------------

    /** Number keys (and the scroll wheel): the held slot never changes; the key fires its slot. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!inCombat(p)) return;
        e.setCancelled(true); // snap back to the weapon
        settleHands(p);

        long now = Bukkit.getCurrentTick();
        int slot = e.getNewSlot();
        if (Math.abs(slot - HotbarHud.WEAPON_SLOT) == 1) {
            // Scroll notch (always lands next to the weapon). With ping the client can scroll a few
            // notches before our snap-back arrives, so ignore everything for a moment.
            scrollIgnoreUntil.put(p.getUniqueId(), now + SCROLL_IGNORE_TICKS);
            return;
        }
        if (now < scrollIgnoreUntil.getOrDefault(p.getUniqueId(), 0L)) return;
        key(p, InputAction.hotbar(slot));
    }

    /**
     * The client changes slots before our snap-back reaches it (a round trip), and in the meantime it stops
     * drawing the crossbow. Vanilla stops the draw on the server too, but the cancelled event skips that,
     * so the server's draw would carry on while the client's starts over: do it here. And if RMB was held,
     * the client picks it back up once it's on the weapon again: that right click continues the hold, it
     * isn't a fresh press (which would shoot a loaded bolt, cancel an aim preview, or start a new volley).
     */
    private void settleHands(Player p) {
        boolean drawing = p.isHandRaised() && (hud.usesCrossbow(p) || hud.usesBow(p));
        if (drawing) p.clearActiveItem();
        if (drawing || ticksSince(p, InputAction.RIGHT_CLICK) <= RIGHT_CLICK_HOLD_GAP) {
            // Back on the weapon after about the ping, then up to 4 ticks until the client re-sends "use".
            long window = 6 + Math.min(20, (p.getPing() + 49) / 50);
            rmbEchoUntil.put(p.getUniqueId(), Bukkit.getCurrentTick() + window);
        }
    }

    private boolean rmbEcho(Player p) {
        return Bukkit.getCurrentTick() < rmbEchoUntil.getOrDefault(p.getUniqueId(), 0L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!inCombat(e.getPlayer())) return;
        e.setCancelled(true); // F would otherwise move the weapon into the offhand
        key(e.getPlayer(), InputAction.SWAP_HANDS);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!inCombat(p)) {
            if (aiming(p)) sinceLast(p, InputAction.DROP); // still mark it: the swing echo mustn't confirm a preview
            return;
        }
        e.setCancelled(true); // Q would otherwise throw the weapon
        key(p, InputAction.DROP);
    }

    private void key(Player p, InputAction action) {
        long gap = sinceLast(p, action);
        Long before = gapBeforeLast.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(InputAction.class)).put(action, gap);
        // A held key's repeats only start after the OS's delay, so a press this soon after a first press
        // (one that came out of silence) is a second tap, e.g. a quick recast.
        boolean doubleTap = gap > 0 && (before == null || before >= KEY_REPEAT_DELAY_MAX);
        boolean freshPress = gap > KEY_REPEAT_GAP || doubleTap;
        if (aimedWith(p, action)) return; // the aimed ability's own key again: nothing (only LMB confirms)
        fire(p, action, freshPress);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastPress.remove(e.getPlayer().getUniqueId());
        gapBeforeLast.remove(e.getPlayer().getUniqueId());
        scrollIgnoreUntil.remove(e.getPlayer().getUniqueId());
        keyOpenedAt.remove(e.getPlayer().getUniqueId());
        idleClickAt.remove(e.getPlayer().getUniqueId());
        rmbEchoUntil.remove(e.getPlayer().getUniqueId());
    }

    // ---- firing -------------------------------------------------------------------------------

    /**
     * @param freshPress false for held/repeated input: it can re-cast (auto-fire) but never recasts
     *                   or confirms, and doesn't spam failure messages.
     * @return whether the press did anything (cast, opened a preview, or was buffered for a recast)
     */
    private boolean fire(Player p, InputAction action, boolean freshPress) {
        return fire(p, action, freshPress, java.util.Map.of());
    }

    /** @param extra more blackboard values for the cast (e.g. the entity a left click hit: ClickAssist) */
    private boolean fire(Player p, InputAction action, boolean freshPress, java.util.Map<String, Object> extra) {
        return keybinds.slotFor(action).map(slot -> {
            ActivationResult result = engine.loadouts().activate(p.getUniqueId(), slot, freshPress, extra);
            if (result.openedTargeting()) { // the preview's action bar takes over
                if (swingless(action)) {
                    keyOpenedAt.put(p.getUniqueId(), engine.clock().now());
                    Long clickAt = idleClickAt.remove(p.getUniqueId());
                    if (clickAt != null && Bukkit.getCurrentTick() - clickAt <= CLICK_BUFFER_TICKS) confirm(p);
                }
                return true;
            }
            if (result.success()) {
                hud.refresh(p);
                return true;
            } else if (AbilityActivator.BUFFERED.equals(result.reason())) {
                return true; // an early recast press: it lands as the window opens, nothing to report
            } else if (AbilityActivator.PASSIVE.equals(result.reason()) || AbilityActivator.UNAVAILABLE.equals(result.reason())) {
                return false; // in effect anyway (its icon glints), or nothing to use (greyed out): nothing to report
            } else if (freshPress && result.reason().startsWith(AbilityActivator.ULT_CHARGING)) {
                p.sendActionBar(Component.text("Ultimate charging: " + result.reason().substring(AbilityActivator.ULT_CHARGING.length() + 1),
                        NamedTextColor.GOLD));
            } else if (freshPress && !(Slots.PRIMARY.equals(slot) && result.reason().startsWith("on_cooldown"))) {
                // Clicking primary fire faster than its fire rate is normal; don't nag about it.
                p.sendActionBar(Component.text(result.reason(), NamedTextColor.RED));
            }
            return false;
        }).orElse(false);
    }
}

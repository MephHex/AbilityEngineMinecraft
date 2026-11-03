package me.mephisto.ability_engine.bukkit.platform;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.animation.property.IAnimationProperty;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.loadout.CharacterDef;
import me.mephisto.ability_engine.engine.platform.Animator;
import me.mephisto.ability_engine.engine.platform.CueHandle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Model Engine support. The only class that touches Model Engine: only made when its plugin is on the server.
 * <ul>
 *   <li>Characters' models ({@code model:} in a kit). By default a model is only there while one of its animations
 *       plays: the player looks like themselves, and for an animation the model swaps in (with their skin, their body
 *       hidden), plays it and swaps back out when it's over. {@code show: always}: worn the whole time, kept on
 *       (after a respawn, a reload, a model that registers late) and taken off when they stop playing it or leave.</li>
 *   <li>The engine's {@link Animator}: {@code animate} / {@code stop_animation} play an animation on every model of the
 *       entity that has it.</li>
 * </ul>
 */
public final class ModelEngineHook implements Animator, Listener {

    private static final int SYNC_EVERY = 10; // ticks

    private final AbilityEngine engine;
    private final Plugin plugin;
    private final Logger log;
    /** The models we put on: entity -> which. */
    private final Map<UUID, CharacterDef.Model> worn = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private BukkitTask task;
    private BukkitTask timeTask;
    /** The speed each animation we played was asked for, before time scale: model -> animation -> speed. */
    private final Map<ActiveModel, Map<String, Double>> asked = new java.util.WeakHashMap<>();
    /** Entities whose animations follow their time scale (they wear our model, or we played on them) -> its last value. */
    private final Map<UUID, Double> timeOf = new HashMap<>();
    /** Models swapped in for their animations (show: animating): entity -> what's playing on it. */
    private final Map<UUID, Swap> swaps = new HashMap<>();

    /**
     * A model swapped in for its animations: out again once none of them plays any more (looping ones: once they're
     * stopped; HOLD ones, which never end by themselves: once their length has run).
     */
    private static final class Swap {
        final long since = Bukkit.getCurrentTick();
        /** animation -> the tick it's over by at the latest (Long.MAX_VALUE: only when it stops playing). */
        final Map<String, Long> playing = new HashMap<>();
        /** A player's own view of it (null: not a player, they don't want it (/selfview), or it couldn't be made). */
        SelfDouble self;
        /** Its model (for the blend). */
        CharacterDef.Model def;
        /** Its animations are over: it swaps out at this tick, after easing back to its rest pose (-1: not yet). */
        long outAt = -1;
        /** Who was told the wearer holds and wears nothing meanwhile (the model holds it, if it has a hand bone). */
        final List<Player> blanked = new ArrayList<>();
        LivingEntity wearer;

        /** Over: the double goes, everyone sees the wearer's real items again. */
        void close() {
            try {
                if (self != null) self.remove();
            } finally {
                if (wearer != null && wearer.isValid()) {
                    Map<EquipmentSlot, ItemStack> real = equipmentOf(wearer);
                    for (Player viewer : blanked) {
                        if (!viewer.isOnline()) continue;
                        viewer.sendEquipmentChange(wearer, real);
                        if (viewer.equals(wearer)) viewer.updateInventory(); // their own hand, as it really is
                    }
                }
            }
        }
    }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static Map<EquipmentSlot, ItemStack> equipmentOf(LivingEntity entity) {
        Map<EquipmentSlot, ItemStack> items = new java.util.EnumMap<>(EquipmentSlot.class);
        var equipment = entity.getEquipment();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack item = equipment == null ? null : equipment.getItem(slot);
            items.put(slot, item == null ? new ItemStack(org.bukkit.Material.AIR) : item);
        }
        return items;
    }

    /** Ends a swap (if any): its double and blanked items. */
    private void endSwap(UUID id) {
        Swap swap = swaps.remove(id);
        if (swap == null) return;
        try {
            swap.close();
        } catch (RuntimeException | LinkageError ex) {
            if (warned.add("close:" + ex)) log.warning("Model Engine: couldn't end the swap of " + id + ": " + ex);
        }
    }

    public ModelEngineHook(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void start() {
        engine.setAnimator(this);
        engine.loadouts().onAssign(this::sync); // a new character: its model at once
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::syncAll, SYNC_EVERY, SYNC_EVERY);
        timeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            followSwaps();
            followTime();
        }, 1, 1);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Everyone's models off (the plugin stops). */
    public void shutdown() {
        if (task != null) task.cancel();
        if (timeTask != null) timeTask.cancel();
        for (UUID id : List.copyOf(swaps.keySet())) endSwap(id);
        for (UUID id : List.copyOf(worn.keySet())) takeOff(id);
        engine.setAnimator(Animator.NONE);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        endSwap(e.getPlayer().getUniqueId());
        takeOff(e.getPlayer().getUniqueId());
    }

    // ---- models worn ----------------------------------------------------------------------------

    private void syncAll() {
        Set<UUID> ids = new HashSet<>(worn.keySet());
        ids.addAll(engine.loadouts().assignedPlayers());
        ids.forEach(this::sync);
    }

    /** The entity wears its character's model (or none), whatever it wore before. */
    private void sync(UUID id) {
        try {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living && living.isDead()) return; // back on when they respawn
            CharacterDef.Model want = entity == null || !entity.isValid() ? null
                    : engine.loadouts().characterOf(id).map(CharacterDef::model).orElse(null);
            if (want != null && !want.always() && !swaps.containsKey(id)) want = null; // only while an animation plays
            if (want == null) endSwap(id);
            CharacterDef.Model have = worn.get(id);
            if (Objects.equals(want, have) && (want == null || stillOn(id, want.id()))) {
                if (want != null) dressLimbs(entity, want);
                return;
            }
            if (have != null) takeOff(id);
            if (want != null) putOn(entity, want);
        } catch (RuntimeException | LinkageError ex) {
            if (warned.add("sync:" + ex)) log.warning("Model Engine: couldn't update the model of " + id + ": " + ex);
        }
    }

    private static boolean stillOn(UUID id, String modelId) {
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(id);
        return modeled != null && modeled.getModel(modelId).isPresent();
    }

    private void putOn(Entity entity, CharacterDef.Model model) {
        if (ModelEngineAPI.getBlueprint(model.id()) == null) {
            // Not (yet) loaded: Model Engine registers models after the server starts. Tried again soon.
            if (warned.add("model:" + model.id())) {
                log.warning("Model Engine has no model '" + model.id() + "' (yet): check plugins/ModelEngine/blueprints");
            }
            return;
        }
        ModeledEntity modeled = ModelEngineAPI.getOrCreateModeledEntity(entity);
        ActiveModel active = ModelEngineAPI.createActiveModel(model.id());
        if (active == null) return;
        modeled.addModel(active, true);
        modeled.setBaseEntityVisible(model.always() && !model.hidePlayer()); // swapped in: the model is their body
        faceAsWearer(modeled, entity);
        modeled.queuePostInitTask(() -> faceAsWearer(modeled, entity)); // (again once Model Engine has set it up)
        worn.put(entity.getUniqueId(), model);
        dressLimbs(entity, model);
        timeOf.putIfAbsent(entity.getUniqueId(), 1.0);
    }

    /**
     * Player-limb bones (phead_, pbody_, prarm_...) start out empty: Model Engine only draws them once they're given a
     * skin (its own /meg disguise does that). Give the empty ones the wearer's: a player's own, a mannequin's, else a
     * default one. Checked again every sync, so limbs reset by Model Engine (set up late, a respawn) get it back.
     */
    private void dressLimbs(Entity entity, CharacterDef.Model def) {
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
        if (modeled == null) return;
        ActiveModel active = modeled.getModel(def.id()).orElse(null);
        if (active != null) dress(entity, active, def.hold());
    }

    /**
     * The model wears {@code entity}'s skin (its player-limb bones) and holds its items: an item bone (ir_ right hand,
     * il_ left hand, ih_ head) shows what the wearer has there, so a model with a hand bone carries their weapon. A
     * player-limb model without one gets a hand bone made for it on its right arm ({@code hold}), so any such model
     * carries the wearer's main-hand item.
     */
    private void dress(Entity entity, ActiveModel active, CharacterDef.Hold hold) {
        if (hold != null && entity instanceof LivingEntity) giveHand(active, hold);
        java.util.ArrayDeque<com.ticxo.modelengine.api.model.bone.ModelBone> bones =
                new java.util.ArrayDeque<>(active.getBones().values());
        Set<com.ticxo.modelengine.api.model.bone.ModelBone> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        com.destroystokyo.paper.profile.PlayerProfile profile = null;
        while (!bones.isEmpty()) { // (the whole tree: limbs sit under other bones)
            var bone = bones.poll();
            if (!seen.add(bone)) continue;
            bones.addAll(bone.getChildren().values());
            var held = bone.getBoneBehavior(com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes.ITEM);
            if (held.isPresent() && entity instanceof LivingEntity living
                    && !(held.get().getItemProvider() instanceof com.ticxo.modelengine.api.model.bone.type.HeldItem.EquipmentSupplier)) {
                var display = held.get().getDisplay();
                EquipmentSlot slot = display == org.bukkit.entity.ItemDisplay.ItemDisplayTransform.THIRDPERSON_LEFTHAND
                        || display == org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIRSTPERSON_LEFTHAND ? EquipmentSlot.OFF_HAND
                        : display == org.bukkit.entity.ItemDisplay.ItemDisplayTransform.HEAD ? EquipmentSlot.HEAD : EquipmentSlot.HAND;
                held.get().setItemProvider(new com.ticxo.modelengine.api.model.bone.type.HeldItem.EquipmentSupplier(living, slot));
            }
            var limb = bone.getBoneBehavior(com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes.PLAYER_LIMB);
            if (limb.isEmpty()) continue;
            var item = bone.getModel();
            if (item != null && !item.getType().isAir()) continue; // already dressed
            if (profile == null) profile = profileOf(entity);
            limb.get().setTexture(profile);
        }
    }

    private static com.destroystokyo.paper.profile.PlayerProfile profileOf(Entity entity) {
        if (entity instanceof org.bukkit.entity.Player player) return player.getPlayerProfile();
        if (entity instanceof org.bukkit.entity.Mannequin mannequin) {
            var skin = mannequin.getProfile();
            if (skin != null && (skin.uuid() != null || skin.name() != null)) {
                var profile = Bukkit.createProfile(skin.uuid(), skin.name());
                profile.setProperties(skin.properties());
                return profile;
            }
        }
        return Bukkit.createProfile(entity.getUniqueId(), null); // no skin: a default one
    }

    private void takeOff(UUID id) {
        CharacterDef.Model model = worn.remove(id);
        if (model == null) return;
        try {
            ModeledEntity modeled = ModelEngineAPI.getModeledEntity(id);
            if (modeled == null) return;
            modeled.removeModel(model.id()).ifPresent(ActiveModel::destroy);
            if (modeled.getModels().isEmpty()) {
                modeled.setBaseEntityVisible(true);
                ModelEngineAPI.removeModeledEntity(id);
            }
        } catch (RuntimeException | LinkageError ex) {
            if (warned.add("off:" + ex)) log.warning("Model Engine: couldn't take the model off " + id + ": " + ex);
        }
    }

    // ---- animations -----------------------------------------------------------------------------

    @Override
    public CueHandle play(UUID entity, String animation, Settings settings) {
        try {
            Swap swap = swapIn(entity, animation);
            ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity);
            if (modeled == null) return CueHandle.NONE;
            List<ActiveModel> playing = new ArrayList<>();
            Map<ActiveModel, String> names = new HashMap<>();
            double time = engine.timeScale(entity);
            for (ActiveModel model : withDouble(entity, modeled)) {
                String name = animationIn(model, animation);
                if (name == null) continue;
                BlueprintAnimation made = model.getBlueprint().getAnimations().get(name);
                double speed = settings.speed();
                if (settings.fit() > 0 && made.getLength() > 0) speed *= made.getLength() * 20 / settings.fit(); // (length: seconds)
                int blend = swap != null && swap.def != null ? swap.def.blend() : 0; // ease from / to the rest pose
                int inTicks = settings.lerpIn() > 0 ? settings.lerpIn() : blend;
                int outTicks = settings.lerpOut() > 0 ? settings.lerpOut() : blend;
                double lerpIn = inTicks / 20.0, lerpOut = outTicks / 20.0, at = speed * time;
                model.getAnimationHandler().playAnimation(name, lerpIn, lerpOut, at, settings.force());
                ModeledEntity owner = model.getModeledEntity();
                if (owner != null && !owner.isInitialized()) { // just swapped in: once Model Engine has set it up
                    owner.queuePostInitTask(() -> model.getAnimationHandler().playAnimation(name, lerpIn, lerpOut, at, true));
                }
                asked.computeIfAbsent(model, m -> new HashMap<>()).put(name, speed);
                playing.add(model);
                names.put(model, name);
                if (swap != null && (swap.self == null || model != swap.self.model())) swap.playing.put(name, made.getLoopMode() == BlueprintAnimation.LoopMode.LOOP
                        ? Long.MAX_VALUE // until it's stopped
                        : Bukkit.getCurrentTick() + (long) Math.ceil(made.getLength() * 20 / Math.max(0.05, speed * Math.min(1, time)))
                                + outTicks + 5); // (at the latest: a HOLD one never ends by itself)
            }
            if (!playing.isEmpty()) timeOf.putIfAbsent(entity, time);
            if (playing.isEmpty()) {
                if (warned.add("anim:" + animation)) {
                    Map<String, Set<String>> has = new HashMap<>();
                    modeled.getModels().forEach((id, m) -> has.put(id, m.getBlueprint().getAnimations().keySet()));
                    log.warning("Model Engine: no model on " + entity + " has an animation '" + animation
                            + "' (its models and their animations: " + has + ")");
                }
                return CueHandle.NONE;
            }
            return () -> playing.forEach(model -> {
                if (!model.isDestroyed()) model.getAnimationHandler().stopAnimation(names.get(model));
            });
        } catch (RuntimeException | LinkageError ex) {
            if (warned.add("play:" + animation)) log.warning("Model Engine: couldn't play '" + animation + "': " + ex);
            return CueHandle.NONE;
        }
    }

    /**
     * Every tick: animations of entities whose time runs differently (or just changed back) play at their time scale,
     * the ones we started at the speed asked times it, the rest (Model Engine's idle, walk...) at it.
     */
    private void followTime() {
        var it = timeOf.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            UUID id = e.getKey();
            try {
                ModeledEntity modeled = ModelEngineAPI.getModeledEntity(id);
                if (modeled == null) {
                    if (!worn.containsKey(id)) it.remove();
                    continue;
                }
                double time = engine.timeScale(id);
                if (time == 1 && e.getValue() == 1) continue;
                for (ActiveModel model : withDouble(id, modeled)) {
                    Map<String, Double> speeds = asked.getOrDefault(model, Map.of());
                    for (Map.Entry<String, IAnimationProperty> a : model.getAnimationHandler().getAnimations().entrySet()) {
                        a.getValue().setSpeed(speeds.getOrDefault(a.getKey(), 1.0) * time);
                    }
                }
                e.setValue(time);
            } catch (RuntimeException | LinkageError ex) {
                if (warned.add("time:" + ex)) log.warning("Model Engine: couldn't set animation speeds of " + id + ": " + ex);
            }
        }
    }

    /**
     * The entity's character model, if it's only shown for its animations and has this one: swapped in now (if it
     * isn't already). Null: nothing to swap (no such model, it's worn always, or it hasn't this animation).
     */
    private Swap swapIn(UUID id, String animation) {
        CharacterDef.Model model = engine.loadouts().characterOf(id).map(CharacterDef::model).orElse(null);
        if (model == null || model.always()) return null;
        var blueprint = ModelEngineAPI.getBlueprint(model.id());
        if (blueprint == null || blueprint.getAnimations().keySet().stream().noneMatch(a -> a.equalsIgnoreCase(animation))) {
            return null;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity == null || !entity.isValid()) return null;
        Swap swap = swaps.get(id);
        if (swap == null || !worn.containsKey(id)) {
            endSwap(id);
            swap = new Swap();
            swap.def = model;
            swaps.put(id, swap);
            if (!model.equals(worn.get(id))) {
                takeOff(id);
                putOn(entity, model);
            }
            if (entity instanceof LivingEntity living) blank(swap, living);
            if (entity instanceof Player player
                    && me.mephisto.ability_engine.bukkit.command.SelfViewCommand.wants(player, plugin)) {
                try {
                    swap.self = SelfDouble.create(player, model.id());
                    if (swap.self != null) dress(player, swap.self.model(), model.hold());
                } catch (RuntimeException | LinkageError ex) {
                    if (warned.add("double:" + ex)) log.warning("Model Engine: couldn't show " + player.getName()
                            + " their own model: " + ex);
                }
            }
        }
        return swap;
    }

    /**
     * A model added this tick starts facing south (rotation 0) and turns toward the wearer over the next ticks, so
     * its animation starts at an angle: face it the way the wearer faces at once.
     */
    static void faceAsWearer(ModeledEntity modeled, Entity wearer) {
        org.bukkit.Location at = wearer.getLocation();
        float body = wearer instanceof LivingEntity living ? living.getBodyYaw() : at.getYaw();
        modeled.setYBodyRotImmediately(body);
        modeled.setYHeadRotImmediately(at.getYaw());
        modeled.setXHeadRotImmediately(at.getPitch());
    }

    private static final String HAND_BONE = "ae_held_item";
    /** Models we've tried to give a hand bone (made, or found they needn't / couldn't have one). */
    private final Map<ActiveModel, Boolean> handTried = new java.util.WeakHashMap<>();

    /**
     * A hand for the wearer's main-hand item on the model's right player-limb arm: an item bone made at runtime, as
     * Model Engine's importer would make one under that arm in Blockbench ({@code hold}: where on the arm, in pixels and
     * degrees). Not for a model with an item bone of its own, nor one without a right arm limb. Once per model.
     */
    private void giveHand(ActiveModel active, CharacterDef.Hold hold) {
        if (handTried.containsKey(active)) return;
        var arm = (com.ticxo.modelengine.api.model.bone.ModelBone) null;
        java.util.ArrayDeque<com.ticxo.modelengine.api.model.bone.ModelBone> bones = new java.util.ArrayDeque<>(active.getBones().values());
        Set<com.ticxo.modelengine.api.model.bone.ModelBone> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (!bones.isEmpty()) {
            var bone = bones.poll();
            if (!seen.add(bone)) continue;
            bones.addAll(bone.getChildren().values());
            if (bone.getBoneBehavior(com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes.ITEM).isPresent()) {
                handTried.put(active, true); // it has its own item bone
                return;
            }
            var limb = bone.getBoneBehavior(com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes.PLAYER_LIMB);
            if (limb.isPresent() && limb.get().getLimbType() == com.ticxo.modelengine.api.model.bone.type.PlayerLimb.Limb.RIGHT_ARM) {
                arm = bone;
            }
        }
        if (arm == null) {
            if (!active.getBones().isEmpty()) handTried.put(active, true); // (no bones yet: set up later, try again)
            return;
        }
        handTried.put(active, true);
        try {
            var parent = arm.getBlueprintBone();
            var bp = new com.ticxo.modelengine.api.generator.blueprint.BlueprintBone();
            bp.setName(HAND_BONE);
            bp.setUuid(UUID.randomUUID());
            bp.setModelBlueprint(parent.getModelBlueprint());
            // Blockbench pixels -> Model Engine blocks (x and z flipped), degrees -> radians (x and z flipped), as its importer does.
            org.joml.Vector3f global = new org.joml.Vector3f((float) hold.x(), (float) hold.y(), (float) hold.z())
                    .mul(-0.0625f, 0.0625f, -0.0625f).add(parent.getGlobalPosition());
            bp.setGlobalPosition(global);
            org.joml.Vector3f rot = new org.joml.Vector3f((float) hold.pitch(), (float) hold.yaw(), (float) hold.roll())
                    .mul(-0.017453292f, 0.017453292f, -0.017453292f);
            bp.setLocalRotation(rot);
            bp.getLocalQuaternion().rotateZYX(rot.z, rot.y, rot.x);
            org.joml.Vector3f local = global.sub(parent.getGlobalPosition(), new org.joml.Vector3f());
            bp.setLocalPosition(local);
            org.joml.Quaternionf q = new org.joml.Quaternionf(parent.getGlobalQuaternion())
                    .mul(new org.joml.Quaternionf().rotationZYX(rot.z, rot.y, rot.x));
            bp.setGlobalRotation(com.ticxo.modelengine.api.utils.math.TMath.getEulerAnglesZYX(q, new org.joml.Vector3f()));
            bp.setGlobalQuaternion(q);
            bp.setRotatedGlobalPosition(local.rotate(parent.getGlobalQuaternion(), new org.joml.Vector3f())
                    .add(parent.getRotatedGlobalPosition()));
            bp.setParent(parent); // (the shared blueprint itself isn't touched)
            Map<String, Object> data = new HashMap<>();
            data.put("display", org.bukkit.entity.ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND);
            bp.getBehaviors().put(com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes.ITEM.getId(), data);
            com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes.ITEM.assignCachedProvider(bp, data);
            active.forceGenerateBone(arm.getBoneId(), "", bp);
        } catch (RuntimeException | LinkageError ex) {
            if (warned.add("hand:" + ex)) log.warning("Model Engine: couldn't give " + active.getBlueprint().getName()
                    + " a hand for the held item (hold: false turns it off): " + ex);
        }
    }

    /**
     * While the model is in, everyone near (the wearer too) is told they hold and wear nothing: their real items would
     * float where their hidden body is. The model holds them instead, if it has item bones.
     */
    private static void blank(Swap swap, LivingEntity wearer) {
        swap.wearer = wearer;
        Map<EquipmentSlot, ItemStack> none = new java.util.EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) none.put(slot, new ItemStack(org.bukkit.Material.AIR));
        for (Player viewer : wearer.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(wearer.getLocation()) > 96 * 96) continue;
            viewer.sendEquipmentChange(wearer, none);
            swap.blanked.add(viewer);
        }
    }

    /** Every tick: swapped-in models get their skin (Model Engine sets limbs up a tick late) and go once they're done. */
    private void followSwaps() {
        long now = Bukkit.getCurrentTick();
        for (var it = swaps.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            UUID id = e.getKey();
            try {
                Entity entity = Bukkit.getEntity(id);
                CharacterDef.Model model = worn.get(id);
                if (entity == null || !entity.isValid() || model == null) {
                    it.remove();
                    e.getValue().close();
                    takeOff(id);
                    continue;
                }
                dressLimbs(entity, model);
                SelfDouble self = e.getValue().self;
                if (self != null) {
                    self.follow();
                    dress(entity, self.model(), model.hold());
                }
                if (now - e.getValue().since < 2) continue; // (just started)
                ModeledEntity modeled = ModelEngineAPI.getModeledEntity(id);
                ActiveModel active = modeled == null ? null : modeled.getModel(model.id()).orElse(null);
                boolean still = active != null && e.getValue().playing.entrySet().stream().anyMatch(a ->
                        now <= a.getValue() && active.getAnimationHandler().isPlayingAnimation(a.getKey()));
                Swap swap = e.getValue();
                if (still) {
                    swap.outAt = -1; // (another one started meanwhile)
                } else {
                    if (swap.outAt < 0) swap.outAt = now + (swap.def == null ? 0 : swap.def.blend()); // ease out first
                    if (now >= swap.outAt) {
                        it.remove();
                        swap.close();
                        takeOff(id); // themselves again
                    }
                }
            } catch (RuntimeException | LinkageError ex) {
                if (warned.add("swap:" + ex)) log.warning("Model Engine: couldn't swap the model of " + id + ": " + ex);
                it.remove();
                try {
                    e.getValue().close();
                } catch (RuntimeException | LinkageError ignored) {
                    // (already warned)
                }
            }
        }
    }

    /** The entity's models, and the copy of its swapped-in one its player sees (if any). */
    private List<ActiveModel> withDouble(UUID id, ModeledEntity modeled) {
        List<ActiveModel> all = new ArrayList<>(modeled.getModels().values());
        Swap swap = swaps.get(id);
        if (swap != null && swap.self != null) all.add(swap.self.model());
        return all;
    }

    /** The model's own name for {@code animation}: as written, or the same ignoring case (null: it has none). */
    private static String animationIn(ActiveModel model, String animation) {
        Map<String, BlueprintAnimation> all = model.getBlueprint().getAnimations();
        if (all.containsKey(animation)) return animation;
        for (String name : all.keySet()) if (name.equalsIgnoreCase(animation)) return name;
        return null;
    }

    @Override
    public void stop(UUID entity, String animation) {
        try {
            ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity);
            if (modeled == null) return;
            for (ActiveModel model : withDouble(entity, modeled)) {
                String name = animationIn(model, animation);
                if (name != null && model.getAnimationHandler().isPlayingAnimation(name)) {
                    model.getAnimationHandler().stopAnimation(name);
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            if (warned.add("stop:" + animation)) log.warning("Model Engine: couldn't stop '" + animation + "': " + ex);
        }
    }
}

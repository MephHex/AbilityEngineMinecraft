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
 *   <li>Characters' models ({@code model:} in a kit): whoever plays one wears its model, kept on (after a respawn, a
 *       reload, a model that registers late) and taken off when they stop playing it or leave.</li>
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

    public ModelEngineHook(AbilityEngine engine, Plugin plugin) {
        this.engine = engine;
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void start() {
        engine.setAnimator(this);
        engine.loadouts().onAssign(this::sync); // a new character: its model at once
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::syncAll, SYNC_EVERY, SYNC_EVERY);
        timeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::followTime, 1, 1);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Everyone's models off (the plugin stops). */
    public void shutdown() {
        if (task != null) task.cancel();
        if (timeTask != null) timeTask.cancel();
        for (UUID id : List.copyOf(worn.keySet())) takeOff(id);
        engine.setAnimator(Animator.NONE);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { takeOff(e.getPlayer().getUniqueId()); }

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
            CharacterDef.Model have = worn.get(id);
            if (Objects.equals(want, have) && (want == null || stillOn(id, want.id()))) {
                if (want != null) dressLimbs(entity, want.id());
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
        modeled.setBaseEntityVisible(!model.hidePlayer());
        worn.put(entity.getUniqueId(), model);
        dressLimbs(entity, model.id());
        timeOf.putIfAbsent(entity.getUniqueId(), 1.0);
    }

    /**
     * Player-limb bones (phead_, pbody_, prarm_...) start out empty: Model Engine only draws them once they're given a
     * skin (its own /meg disguise does that). Give the empty ones the wearer's: a player's own, a mannequin's, else a
     * default one. Checked again every sync, so limbs reset by Model Engine (set up late, a respawn) get it back.
     */
    private void dressLimbs(Entity entity, String modelId) {
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
        if (modeled == null) return;
        ActiveModel active = modeled.getModel(modelId).orElse(null);
        if (active == null) return;
        com.destroystokyo.paper.profile.PlayerProfile profile = null;
        for (var bone : active.getBones().values()) {
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
            ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity);
            if (modeled == null) return CueHandle.NONE;
            List<ActiveModel> playing = new ArrayList<>();
            Map<ActiveModel, String> names = new HashMap<>();
            double time = engine.timeScale(entity);
            for (ActiveModel model : modeled.getModels().values()) {
                String name = animationIn(model, animation);
                if (name == null) continue;
                BlueprintAnimation made = model.getBlueprint().getAnimations().get(name);
                double speed = settings.speed();
                if (settings.fit() > 0 && made.getLength() > 0) speed *= made.getLength() * 20 / settings.fit(); // (length: seconds)
                model.getAnimationHandler().playAnimation(name, settings.lerpIn() / 20.0, settings.lerpOut() / 20.0,
                        speed * time, settings.force());
                asked.computeIfAbsent(model, m -> new HashMap<>()).put(name, speed);
                playing.add(model);
                names.put(model, name);
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
                for (ActiveModel model : modeled.getModels().values()) {
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
            for (ActiveModel model : modeled.getModels().values()) {
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

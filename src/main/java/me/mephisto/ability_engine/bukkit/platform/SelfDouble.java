package me.mephisto.ability_engine.bukkit.platform;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * What a player sees of their own swapped-in model. Model Engine never shows a player the model on themselves (it only
 * sends it to the players tracking them), so while it's in, a copy stands in their place for their eyes only: a
 * Model Engine dummy only they see, following them every tick and playing the same animations. Their own body is made
 * invisible meanwhile (others don't see it anyway: Model Engine hides it from them). Part of the Model Engine hook:
 * only loaded with Model Engine.
 */
final class SelfDouble {

    private final Player player;
    private final Dummy<Player> dummy;
    private final ModeledEntity modeled;
    private final ActiveModel model;
    private final boolean wasInvisible;

    private SelfDouble(Player player, Dummy<Player> dummy, ModeledEntity modeled, ActiveModel model, boolean wasInvisible) {
        this.player = player;
        this.dummy = dummy;
        this.modeled = modeled;
        this.model = model;
        this.wasInvisible = wasInvisible;
    }

    /** A copy of {@code modelId} only {@code player} sees, where they stand. Null: the model isn't loaded. */
    static SelfDouble create(Player player, String modelId) {
        ActiveModel active = ModelEngineAPI.createActiveModel(modelId);
        if (active == null) return null;
        Dummy<Player> dummy = new Dummy<>(player);
        dummy.setDetectingPlayers(false); // nobody but...
        dummy.setForceViewing(player, true); // ...them
        place(dummy, player);
        ModeledEntity modeled = ModelEngineAPI.createModeledEntity(dummy);
        modeled.addModel(active, true);
        ModelEngineHook.faceAsWearer(modeled, player); // (not turning in from south)
        modeled.queuePostInitTask(() -> ModelEngineHook.faceAsWearer(modeled, player));
        boolean was = player.isInvisible();
        if (!was) player.setInvisible(true); // their own body: the copy is it now
        return new SelfDouble(player, dummy, modeled, active, was);
    }

    private static void place(Dummy<Player> dummy, Player player) {
        Location at = player.getLocation();
        dummy.setLocation(at);
        dummy.setYBodyRot(player.getBodyYaw());
        dummy.setYHeadRot(at.getYaw());
        dummy.setXHeadRot(at.getPitch());
    }

    /** Every tick: where they are, facing where they face. */
    void follow() { place(dummy, player); }

    ActiveModel model() { return model; }

    /** The copy goes, their body comes back. */
    void remove() {
        try {
            ModelEngineAPI.removeModeledEntity(dummy.getUUID());
            modeled.destroy();
            dummy.setRemoved(true);
        } finally {
            if (!wasInvisible && player.isOnline()) player.setInvisible(false);
        }
    }
}

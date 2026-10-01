package me.mephisto.ability_engine.bukkit.effect;

import me.mephisto.ability_engine.bukkit.platform.Convert;
import me.mephisto.ability_engine.engine.data.Params;
import me.mephisto.ability_engine.engine.effect.Effect;
import me.mephisto.ability_engine.engine.effect.EffectContext;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.Optional;

/**
 * Effect id "teleport". Params: {@code to} = blackboard key of the destination, or {@code away_from: <key>} and
 * {@code distance} = that many blocks straight out from there (see TeleportAway);
 * {@code ground} (default false) = only land where there's a solid block under your feet.
 * Moves the target (usually the caster: {@code targets: {type: self}}) to the nearest spot at the
 * destination where its body actually fits: aiming at a wall puts you in front of it, aiming at
 * a ledge edge puts you on top. Keeps facing direction; clears momentum and fall damage.
 * If no safe spot is found near the destination, nothing happens.
 */
public final class TeleportEffect implements Effect {

    private static final double STEP = 0.5;       // back-off step toward the caster
    private static final int MAX_STEPS = 12;      // up to 6 blocks back
    private static final double HALF_WIDTH = 0.3; // player hitbox is 0.6 wide, 1.8 tall
    private static final double HEIGHT = 1.8;

    @Override
    public void apply(EffectContext ctx) {
        if (!(ctx.target() instanceof EntityTarget target) || ctx.execution() == null) return; // needs its cast's keys
        Entity entity = Bukkit.getEntity(target.id());
        if (entity == null) return;

        Optional<PointTarget> dest = me.mephisto.ability_engine.engine.effect.TeleportAway.destination(ctx, target);
        if (dest.isEmpty()) return;
        World world = Bukkit.getWorld(dest.get().world());
        if (world == null) return;

        Location from = entity.getLocation();
        boolean ground = ctx.params().getBool("ground", false);
        Location safe = safeSpot(world, dest.get().position(), Convert.vec(from), ground);
        if (safe == null) {
            ctx.engine().log().debug(() -> "teleport: no safe spot near " + dest.get().position());
            return;
        }
        safe.setYaw(from.getYaw());
        safe.setPitch(from.getPitch());
        entity.teleport(safe, me.mephisto.ability_engine.bukkit.platform.BukkitMovementControl.KEEP_RIDERS);
        entity.setFallDistance(0);
        entity.setVelocity(new Vector(0, 0, 0));
    }

    /** Walk back from the destination toward where we came from until the body fits (also trying one block up). */
    private static Location safeSpot(World w, Vec3 dest, Vec3 from, boolean ground) {
        Vec3 back = from.subtract(dest).normalize();
        for (int i = 0; i <= MAX_STEPS; i++) {
            Vec3 p = dest.add(back.multiply(STEP * i));
            if (fits(w, p.x(), p.y(), p.z()) && (!ground || supported(w, p.x(), p.y(), p.z()))) {
                return new Location(w, p.x(), p.y(), p.z());
            }
            double up = Math.floor(p.y()) + 1; // e.g. aimed at the top edge of a wall: stand on it
            if (fits(w, p.x(), up, p.z()) && (!ground || supported(w, p.x(), up, p.z()))) {
                return new Location(w, p.x(), up, p.z());
            }
        }
        return null;
    }

    /** Something solid right under the feet. */
    private static boolean supported(World w, double x, double y, double z) {
        return !w.getBlockAt((int) Math.floor(x), (int) Math.floor(y - 0.05), (int) Math.floor(z)).isPassable();
    }

    private static boolean fits(World w, double x, double y, double z) {
        for (double dx : new double[]{-HALF_WIDTH, HALF_WIDTH}) {
            for (double dz : new double[]{-HALF_WIDTH, HALF_WIDTH}) {
                for (double dy : new double[]{0.01, HEIGHT / 2, HEIGHT - 0.01}) {
                    if (!w.getBlockAt((int) Math.floor(x + dx), (int) Math.floor(y + dy), (int) Math.floor(z + dz)).isPassable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void validate(Params params) {
        me.mephisto.ability_engine.engine.effect.TeleportAway.validate(params);
    }
}

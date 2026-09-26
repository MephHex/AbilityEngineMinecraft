package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.ProjectileVisual;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;

/**
 * A projectile drawn as a REAL arrow entity ({@code visual: "entity:ARROW"}, also SPECTRAL_ARROW, TRIDENT).
 * The engine still decides where it flies and what it hits; the arrow is only the picture.
 *
 * <p>Arrows point along their velocity and clients fly them smoothly from it, so it keeps the engine's
 * velocity (a frozen, teleported arrow faces the wrong way and stutters). The plugin's projectile tick runs
 * BEFORE entities tick, so the arrow is kept one step behind the engine's position: its own tick then
 * moves it exactly onto that position. In straight or falling flight that needs no teleport at all; only
 * bounces and redirects snap it. Its own hits are cancelled (VisualEntities), it can't be picked up and
 * deals no damage.
 */
final class ArrowVisual implements ProjectileVisual {

    /** Further off than this from where it should be: teleport (a bounce, a redirect). */
    private static final double SNAP_DISTANCE = 0.05;

    private final AbstractArrow arrow;

    private ArrowVisual(AbstractArrow arrow) {
        this.arrow = arrow;
    }

    /** Null if {@code type} isn't an arrow-like entity. */
    static ArrowVisual spawn(Location at, Vec3 velocity, Class<? extends AbstractArrow> type, String tint) {
        World world = at.getWorld();
        if (world == null) return null;
        AbstractArrow arrow = world.spawn(at, type, a -> {
            VisualEntities.mark(a);
            a.setPersistent(false);
            a.setGravity(false);           // the engine's motion (gravity included) arrives as velocity
            a.setSilent(true);
            a.setInvulnerable(true);
            a.setDamage(0);
            a.setCritical(false);
            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            a.setVelocity(Convert.bukkit(velocity));
            if (tint != null && a instanceof Arrow tipped) {
                tipped.setColor(Color.fromRGB(Integer.parseInt(tint.substring(1), 16))); // tipped look + colored trail
            }
        });
        return new ArrowVisual(arrow);
    }

    @Override
    public void moveTo(Vec3 position) { moveTo(position, Vec3.ZERO); }

    @Override
    public void moveTo(Vec3 position, Vec3 velocity) {
        if (!arrow.isValid()) return;
        Vec3 behind = position.subtract(velocity); // this tick's entity movement brings it onto `position`
        Location now = arrow.getLocation();
        if (now.toVector().distanceSquared(Convert.bukkit(behind)) > SNAP_DISTANCE * SNAP_DISTANCE) {
            arrow.teleport(new Location(now.getWorld(), behind.x(), behind.y(), behind.z(), now.getYaw(), now.getPitch()));
        }
        arrow.setVelocity(Convert.bukkit(velocity));
    }

    @Override
    public void remove() {
        if (arrow.isValid()) arrow.remove();
    }
}

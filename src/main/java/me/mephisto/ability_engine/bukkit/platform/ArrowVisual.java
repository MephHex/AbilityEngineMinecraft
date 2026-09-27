package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.FlyingVisual;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;

import java.util.Optional;

/**
 * A projectile that IS a real arrow ({@code visual: "entity:ARROW"}, also SPECTRAL_ARROW, TRIDENT), flown
 * by the game's own physics exactly like a vanilla shot: gravity, drag, smooth on every client. The engine
 * never moves it; it reads it every tick and checks the stretch it's about to fly (see FlyingVisual), so
 * barriers, constructs and allies still count. Its own entity hits are cancelled and it deals no damage
 * (VisualEntities): the engine decides what it hit and removes it there.
 */
final class ArrowVisual implements FlyingVisual {

    private final AbstractArrow arrow;

    private ArrowVisual(AbstractArrow arrow) {
        this.arrow = arrow;
    }

    /** A real arrow at {@code at}, launched with {@code velocity} (blocks per tick). */
    static ArrowVisual spawn(Location at, Vec3 velocity, Class<? extends AbstractArrow> type, String tint) {
        World world = at.getWorld();
        if (world == null) return null;
        AbstractArrow arrow = world.spawn(at, type, a -> {
            VisualEntities.mark(a);
            a.setPersistent(false);
            a.setSilent(true);
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
    public Optional<Vec3> position() {
        return arrow.isValid() ? Optional.of(Convert.vec(arrow.getLocation())) : Optional.empty();
    }

    @Override
    public Vec3 velocity() { return Convert.vec(arrow.getVelocity()); }

    @Override
    public void setVelocity(Vec3 velocity) {
        if (arrow.isValid()) arrow.setVelocity(Convert.bukkit(velocity));
    }

    @Override
    public boolean landed() { return arrow.isInBlock(); }

    @Override
    public void remove() {
        if (arrow.isValid()) arrow.remove();
    }
}

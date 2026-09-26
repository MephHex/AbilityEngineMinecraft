package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.ProjectileRenderer;
import me.mephisto.ability_engine.engine.platform.ProjectileVisual;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Draws projectiles. {@code visual:} in YAML is an item Material or {@code "entity:<EntityType>"}
 * (see VisualSpawner). Arrow-like entities ({@code entity:ARROW}, SPECTRAL_ARROW, TRIDENT) fly as real
 * arrows instead (see ArrowVisual), tinted when the bolt is infused. Purely cosmetic: collision is the
 * engine's ray sweep.
 */
public final class BukkitProjectileRenderer implements ProjectileRenderer {

    private static final String ENTITY_PREFIX = "entity:";

    private final Logger log;

    public BukkitProjectileRenderer(Logger log) {
        this.log = log;
    }

    @Override
    public ProjectileVisual spawn(String world, Vec3 position, Vec3 velocity, ProjectileSpec spec, String tint) {
        Class<? extends AbstractArrow> arrowType = arrowType(spec.visual());
        if (arrowType != null) {
            Location loc = Convert.location(world, position);
            ArrowVisual arrow = loc == null ? null : ArrowVisual.spawn(loc, velocity, arrowType, tint);
            if (arrow != null) return arrow;
        }
        return spawn(world, position, spec);
    }

    /** The entity class for {@code "entity:<arrow type>"}, or null for anything else. */
    private static Class<? extends AbstractArrow> arrowType(String visual) {
        if (visual == null || !visual.regionMatches(true, 0, ENTITY_PREFIX, 0, ENTITY_PREFIX.length())) return null;
        try {
            Class<? extends Entity> cls = EntityType.valueOf(visual.substring(ENTITY_PREFIX.length()).trim()
                    .toUpperCase(Locale.ROOT)).getEntityClass();
            return cls != null && AbstractArrow.class.isAssignableFrom(cls) ? cls.asSubclass(AbstractArrow.class) : null;
        } catch (IllegalArgumentException e) {
            return null; // unknown type: VisualSpawner reports it
        }
    }

    @Override
    public ProjectileVisual spawn(String world, Vec3 position, ProjectileSpec spec) {
        Location loc = Convert.location(world, position);
        if (loc == null) return NONE;
        VisualSpawner.Spawned s = VisualSpawner.spawn(loc, spec.visual(), (float) spec.size(), log);
        Entity entity = s.entity();
        double yOffset = s.yOffset();
        return new ProjectileVisual() {
            @Override
            public void moveTo(Vec3 p) {
                if (entity.isValid()) entity.teleport(new Location(entity.getWorld(), p.x(), p.y() - yOffset, p.z()));
            }

            @Override
            public void remove() {
                if (entity.isValid()) entity.remove();
            }
        };
    }

    private static final ProjectileVisual NONE = new ProjectileVisual() {
        @Override public void moveTo(Vec3 position) {}
        @Override public void remove() {}
    };
}

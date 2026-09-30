package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.ProjectileRenderer;
import me.mephisto.ability_engine.engine.platform.ProjectileVisual;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Draws projectiles. {@code visual:} in YAML is an item Material or {@code "entity:<EntityType>"}
 * (see VisualSpawner). Arrow-like entities ({@code entity:ARROW}, SPECTRAL_ARROW, TRIDENT) are real arrows
 * flown by the game itself (see ArrowVisual), tinted when the bolt is infused. Collision is always the
 * engine's ray sweep.
 */
public final class BukkitProjectileRenderer implements ProjectileRenderer, org.bukkit.event.Listener {

    /** Design HP per Minecraft health point (config damage-scale), for projectile bodies' health. */
    private java.util.function.DoubleSupplier damageScale = () -> 10;
    /** Living projectile bodies (a Chorus Shade): they drop nothing when killed. */
    private final java.util.Set<java.util.UUID> bodies = new java.util.HashSet<>();

    public void setDamageScale(java.util.function.DoubleSupplier damageScale) { this.damageScale = damageScale; }

    /** Who may see a player's projectiles (a duel in a veil: just the two), empty = everyone. */
    private java.util.function.Function<java.util.UUID, java.util.Set<java.util.UUID>> audienceOf = id -> java.util.Set.of();

    public void setAudience(java.util.function.Function<java.util.UUID, java.util.Set<java.util.UUID>> audienceOf) {
        this.audienceOf = audienceOf;
    }

    @org.bukkit.event.EventHandler
    public void onBodyDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if (!bodies.remove(e.getEntity().getUniqueId())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    /**
     * A projectile with {@code health}: its {@code "entity:<type>"} visual is a real target (not an ability
     * visual, not invulnerable) with that health, on the owner's team, so enemies can hit and kill it. The
     * engine still moves it (no AI, no gravity) and ends the projectile when it dies.
     */
    @Override
    public ProjectileVisual spawn(String world, Vec3 position, Vec3 velocity, ProjectileSpec spec, String tint, java.util.UUID owner) {
        java.util.Set<java.util.UUID> audience = owner == null ? java.util.Set.of() : audienceOf.apply(owner);
        ProjectileVisual visual = VisualEntities.withAudience(audience, () -> spawnFor(world, position, velocity, spec, tint, owner));
        if (!audience.isEmpty()) visual.body().map(org.bukkit.Bukkit::getEntity).ifPresent(b -> VisualEntities.restrict(b, audience));
        return visual;
    }

    private ProjectileVisual spawnFor(String world, Vec3 position, Vec3 velocity, ProjectileSpec spec, String tint, java.util.UUID owner) {
        if (spec.health() <= 0 || spec.visual() == null || !spec.visual().regionMatches(true, 0, ENTITY_PREFIX, 0, ENTITY_PREFIX.length())) {
            return spawn(world, position, velocity, spec, tint);
        }
        Location loc = Convert.location(world, position);
        if (loc == null) return NONE;
        Class<? extends Entity> cls;
        try {
            cls = EntityType.valueOf(spec.visual().substring(ENTITY_PREFIX.length()).trim().toUpperCase(Locale.ROOT)).getEntityClass();
        } catch (IllegalArgumentException e) {
            cls = null;
        }
        if (cls == null || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(cls)) return spawn(world, position, velocity, spec, tint);
        double health = Math.max(1, spec.health() / damageScale.getAsDouble());
        Entity owned = org.bukkit.Bukkit.getEntity(owner);
        org.bukkit.scoreboard.Team team = owned == null ? null
                : org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard().getEntityTeam(owned);
        Entity body = loc.getWorld().spawn(loc, cls, e -> {
            e.setPersistent(false);
            e.setGravity(false);
            e.setSilent(true);
            if (e instanceof org.bukkit.entity.LivingEntity living) {
                living.setCollidable(false);
                var max = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (max != null) max.setBaseValue(health);
                living.setHealth(health);
                living.setRemoveWhenFarAway(false);
            }
            if (e instanceof org.bukkit.entity.Mob mob) mob.setAI(false);
            if (team != null) team.addEntity(e);
        });
        bodies.add(body.getUniqueId());
        double yOffset = body.getBoundingBox().getHeight() / 2;
        body.teleport(loc.clone().subtract(0, yOffset, 0));
        return new ProjectileVisual() {
            @Override
            public void moveTo(Vec3 p) {
                if (body.isValid()) body.teleport(new Location(body.getWorld(), p.x(), p.y() - yOffset, p.z(),
                        body.getLocation().getYaw(), body.getLocation().getPitch()));
            }

            @Override
            public void moveTo(Vec3 p, Vec3 v) {
                if (!body.isValid()) return;
                Location at = new Location(body.getWorld(), p.x(), p.y() - yOffset, p.z());
                if (v != null && !v.isZero()) at.setDirection(Convert.bukkit(v)); // faces where it flies
                else at.setDirection(body.getLocation().getDirection());
                body.teleport(at);
            }

            @Override
            public boolean destroyed() { return body.isDead() || !body.isValid(); }

            @Override
            public java.util.Optional<java.util.UUID> body() { return java.util.Optional.of(body.getUniqueId()); }

            @Override
            public void remove() {
                bodies.remove(body.getUniqueId());
                if (body.isValid() && !body.isDead()) body.remove();
            }
        };
    }

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
        float scale = (float) spec.shownSize();
        VisualSpawner.Spawned s = VisualSpawner.spawn(loc, spec.visual(), scale, log);
        Entity entity = s.entity();
        double yOffset = s.yOffset();
        boolean face = spec.faceFlight() && entity instanceof ItemDisplay;
        return new ProjectileVisual() {
            private Vec3 facing;

            @Override
            public void moveTo(Vec3 p) {
                if (entity.isValid()) entity.teleport(new Location(entity.getWorld(), p.x(), p.y() - yOffset, p.z()));
            }

            @Override
            public void moveTo(Vec3 p, Vec3 velocity) {
                moveTo(p);
                if (face && entity.isValid() && velocity != null && !velocity.isZero()) pointAlong((ItemDisplay) entity, velocity);
            }

            /**
             * A tool's sprite points up-right (handle bottom-left, head top-right) in the display's XY plane.
             * Turn it 45 degrees so it points along +X, tilt it to the flight's pitch, then turn it to the
             * flight's heading: head first, blade upright. Only redone when the direction really changed.
             */
            private void pointAlong(ItemDisplay display, Vec3 velocity) {
                Vec3 dir = velocity.normalize();
                if (facing != null && facing.dot(dir) > 0.999) return;
                facing = dir;
                double flat = Math.sqrt(dir.x() * dir.x() + dir.z() * dir.z());
                float heading = (float) Math.atan2(-dir.z(), dir.x());
                float pitch = (float) Math.atan2(dir.y(), flat);
                Quaternionf turn = new Quaternionf().rotateY(heading).rotateZ(pitch - (float) Math.toRadians(45));
                display.setTransformation(new Transformation(new Vector3f(), turn, new Vector3f(scale, scale, scale),
                        new Quaternionf()));
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

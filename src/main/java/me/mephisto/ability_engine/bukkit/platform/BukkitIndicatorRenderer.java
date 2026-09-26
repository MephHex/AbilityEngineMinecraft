package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.platform.IndicatorRenderer;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.targeting.Targeting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Targeting previews drawn with dust particles sent ONLY to the aiming player (player.spawnParticle),
 * so enemies can't see where you're aiming. Redrawn every 2 ticks; the action bar shows the controls.
 *
 * <p>Particles are hidden by walls. When the aim point is OUT OF LINE OF SIGHT (behind a wall, below a
 * ledge), a glowing pillar marks it: the glow outline renders through blocks. The marker entity is
 * hidden from everyone except the aiming player.
 */
public final class BukkitIndicatorRenderer implements IndicatorRenderer {

    private static final Color VALID_COLOR = Color.fromRGB(120, 200, 255);
    private static final Color INVALID_COLOR = Color.fromRGB(230, 60, 60);
    private static final Particle.DustOptions VALID = new Particle.DustOptions(VALID_COLOR, 0.8f);
    private static final Particle.DustOptions INVALID = new Particle.DustOptions(INVALID_COLOR, 0.8f);
    private static final double SPACING = 0.35; // blocks between particles on lines and rings

    private final Plugin plugin;
    private final Set<UUID> hinted = new HashSet<>();
    private final Map<UUID, ItemDisplay> markers = new HashMap<>();
    private final Set<UUID> markerShown = new HashSet<>();

    public BukkitIndicatorRenderer(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void draw(UUID viewer, String abilityName, Targeting t, Aim from, PointTarget at, boolean valid) {
        Player p = Bukkit.getPlayer(viewer);
        if (p == null) return;
        long now = Bukkit.getCurrentTick();

        if (hinted.add(viewer) || now % 20 == 0) {
            p.sendActionBar(Component.text("Aiming " + abilityName + "  ·  LMB confirm  ·  RMB cancel", NamedTextColor.AQUA));
        }
        if (now % 2 != 0) return;

        updateMarker(p, from, at.position(), valid);
        Particle.DustOptions dust = valid ? VALID : INVALID;
        Vec3 target = at.position().add(0, 0.1, 0);
        switch (t.shape()) {
            case CIRCLE -> {
                ring(p, dust, target, t.radius());
                dot(p, dust, target);
            }
            case POINT -> {
                ring(p, dust, target, 0.25);
                dot(p, dust, target);
            }
            case LINE -> {
                Vec3 start = from.eye().add(0, -0.4, 0);
                line(p, dust, start, target);
                ring(p, dust, target, Math.max(0.25, t.width() / 2));
            }
            case CONE -> {
                Vec3 start = from.eye().add(0, -0.4, 0);
                Vec3 forward = target.subtract(start);
                double half = Math.toRadians(t.angle() / 2);
                line(p, dust, start, start.add(rotateY(forward, half)));
                line(p, dust, start, start.add(rotateY(forward, -half)));
                arc(p, dust, start, forward, half);
            }
        }
    }

    @Override
    public void clear(UUID viewer, String reason) {
        hinted.remove(viewer);
        removeMarker(viewer);
        Player p = Bukkit.getPlayer(viewer);
        if (p == null) return;
        switch (reason) {
            case "confirmed", "switched" -> p.sendActionBar(Component.empty());
            case "timeout" -> p.sendActionBar(Component.text("Aim timed out", NamedTextColor.GRAY));
            case "blocked" -> p.sendActionBar(Component.text("Interrupted", NamedTextColor.RED));
            default -> p.sendActionBar(Component.text("Cancelled", NamedTextColor.GRAY));
        }
    }

    // ---- line-of-sight marker -------------------------------------------------------------------

    private void updateMarker(Player p, Aim from, Vec3 spot, boolean valid) {
        Location loc = Convert.location(from.world(), spot);
        if (loc == null) return;
        UUID id = p.getUniqueId();

        ItemDisplay marker = markers.get(id);
        if (marker == null || !marker.isValid() || marker.getWorld() != loc.getWorld()) {
            removeMarker(id);
            marker = spawnMarker(loc);
            markers.put(id, marker);
        } else {
            marker.teleport(loc);
        }
        marker.setGlowColorOverride(valid ? VALID_COLOR : INVALID_COLOR);

        boolean hidden = occluded(loc.getWorld(), from.eye(), spot.add(0, 0.5, 0));
        boolean shown = markerShown.contains(id);
        if (hidden && !shown) {
            p.showEntity(plugin, marker);
            markerShown.add(id);
        } else if (!hidden && shown) {
            p.hideEntity(plugin, marker);
            markerShown.remove(id);
        }
    }

    /** An upright glowing rod standing on the spot. Invisible to everyone until shown to its owner. */
    private static ItemDisplay spawnMarker(Location loc) {
        return loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setVisibleByDefault(false); // nobody sees it unless shown (only ever to the aiming player)
            d.setPersistent(false);
            d.setItemStack(new ItemStack(Material.END_ROD));
            d.setGlowing(true);
            d.setTeleportDuration(2);     // glides with the crosshair instead of jumping
            d.setTransformation(new Transformation(new Vector3f(0, 0.7f, 0), new Quaternionf(),
                    new Vector3f(0.6f, 1.4f, 0.6f), new Quaternionf()));
            VisualEntities.mark(d);
        });
    }

    /** Is there a block between the eye and the point? */
    private static boolean occluded(World w, Vec3 eye, Vec3 target) {
        Vec3 d = target.subtract(eye);
        double dist = d.length();
        if (dist < 0.5) return false;
        Location start = new Location(w, eye.x(), eye.y(), eye.z());
        return w.rayTraceBlocks(start, Convert.bukkit(d.normalize()), dist - 0.3, FluidCollisionMode.NEVER, true) != null;
    }

    private void removeMarker(UUID viewer) {
        markerShown.remove(viewer);
        ItemDisplay m = markers.remove(viewer);
        if (m != null && m.isValid()) m.remove();
    }

    // ---- shapes ------------------------------------------------------------------------------

    private static void dot(Player p, Particle.DustOptions dust, Vec3 v) {
        p.spawnParticle(Particle.DUST, v.x(), v.y(), v.z(), 1, 0, 0, 0, 0, dust);
    }

    private static void ring(Player p, Particle.DustOptions dust, Vec3 center, double radius) {
        int points = Math.max(10, (int) Math.ceil(2 * Math.PI * radius / SPACING));
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            dot(p, dust, center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
    }

    private static void line(Player p, Particle.DustOptions dust, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int points = Math.max(2, (int) Math.ceil(d.length() / SPACING));
        for (int i = 0; i <= points; i++) dot(p, dust, from.add(d.multiply(i / (double) points)));
    }

    private static void arc(Player p, Particle.DustOptions dust, Vec3 origin, Vec3 forward, double half) {
        double len = forward.length();
        int points = Math.max(6, (int) Math.ceil(2 * half * len / SPACING));
        for (int i = 0; i <= points; i++) {
            double a = -half + 2 * half * i / points;
            dot(p, dust, origin.add(rotateY(forward, a)));
        }
    }

    private static Vec3 rotateY(Vec3 v, double rad) {
        double c = Math.cos(rad), s = Math.sin(rad);
        return new Vec3(v.x() * c - v.z() * s, v.y(), v.x() * s + v.z() * c);
    }
}

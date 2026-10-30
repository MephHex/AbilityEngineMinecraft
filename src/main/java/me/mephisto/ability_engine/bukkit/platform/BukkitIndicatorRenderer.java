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
import org.bukkit.Location;
import org.bukkit.Material;
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
 * <p>Particles are hidden by walls, so the same outline is also drawn GLOWING: thin bars along every edge of the
 * shape (a ring of them for a circle), shown only to the aiming player. A glow outline renders through blocks,
 * so the preview stays visible out of line of sight (behind a wall, below a ledge). Blue while valid, red not.
 */
public final class BukkitIndicatorRenderer implements IndicatorRenderer {

    private static final Color VALID_COLOR = Color.fromRGB(120, 200, 255);
    private static final Color INVALID_COLOR = Color.fromRGB(230, 60, 60);
    private static final Particle.DustOptions VALID = new Particle.DustOptions(VALID_COLOR, 0.8f);
    private static final Particle.DustOptions INVALID = new Particle.DustOptions(INVALID_COLOR, 0.8f);
    private static final double SPACING = 0.35; // blocks between particles on lines and rings

    private final Plugin plugin;
    private final Set<UUID> hinted = new HashSet<>();
    /** Each aiming player's glowing bars (reused from draw to draw; spares hidden at zero size). */
    private final Map<UUID, java.util.List<org.bukkit.entity.BlockDisplay>> glows = new HashMap<>();
    /** The edges of the shape being drawn right now (filled by line / ring / arc), for its glowing outline. */
    private final java.util.List<Vec3[]> segments = new java.util.ArrayList<>();
    /** A glowing ring's bars are about this long (blocks). */
    private static final double GLOW_SEGMENT = 0.9;
    /** How thick a glowing bar is. */
    private static final float GLOW_THICK = 0.06f;

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

        segments.clear();
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
            case WALL -> { // a wall across the aim, centred on the spot: its footprint, and its ends standing up
                Vec3 forward = target.subtract(from.eye());
                Vec3 flat = new Vec3(forward.x(), 0, forward.z());
                if (flat.lengthSquared() < 1e-6) flat = new Vec3(1, 0, 0);
                flat = flat.normalize();
                Vec3 right = new Vec3(-flat.z(), 0, flat.x()).multiply(t.width() / 2);
                Vec3 depth = flat.multiply(0.5);
                Vec3 a = target.subtract(right), b = target.add(right);
                line(p, dust, a.subtract(depth), b.subtract(depth));
                line(p, dust, a.add(depth), b.add(depth));
                line(p, dust, a.subtract(depth), a.add(depth));
                line(p, dust, b.subtract(depth), b.add(depth));
                line(p, dust, a, a.add(0, 2, 0));
                line(p, dust, b, b.add(0, 2, 0));
            }
            case RECT -> { // a rectangle on the ground, centred on the spot: its length along the aim, its width across
                Vec3 forward = target.subtract(from.eye());
                Vec3 flat = new Vec3(forward.x(), 0, forward.z());
                if (flat.lengthSquared() < 1e-6) flat = new Vec3(1, 0, 0);
                flat = flat.normalize();
                Vec3 along = flat.multiply(t.length() / 2);
                Vec3 across = new Vec3(-flat.z(), 0, flat.x()).multiply(t.width() / 2);
                Vec3 a = target.subtract(along).subtract(across), b = target.subtract(along).add(across);
                Vec3 c = target.add(along).add(across), d = target.add(along).subtract(across);
                line(p, dust, a, b);
                line(p, dust, b, c);
                line(p, dust, c, d);
                line(p, dust, d, a);
                dot(p, dust, target);
            }
            case SCREEN -> { // a screen standing where it would go, facing the look: its edge and its two diamonds
                var f = me.mephisto.ability_engine.engine.barrier.Screens.frame(from.eye(), from.direction(), at.position(),
                        me.mephisto.ability_engine.engine.barrier.Screens.WALL_GAP, null);
                Vec3 r = f.right().multiply(t.width() / 2), u = f.up().multiply(t.length() / 2);
                Vec3 c = f.center();
                Vec3 a = c.subtract(r).subtract(u), b = c.add(r).subtract(u), cc = c.add(r).add(u), d = c.subtract(r).add(u);
                line(p, dust, a, b);
                line(p, dust, b, cc);
                line(p, dust, cc, d);
                line(p, dust, d, a);
                double half = Math.min(t.width(), t.length()) / 2; // the big diamond touches the nearer edges
                for (double s : new double[] {half, half / 2}) {
                    Vec3 top = c.add(f.up().multiply(s)), right = c.add(f.right().multiply(s));
                    Vec3 bottom = c.subtract(f.up().multiply(s)), left = c.subtract(f.right().multiply(s));
                    line(p, dust, top, right);
                    line(p, dust, right, bottom);
                    line(p, dust, bottom, left);
                    line(p, dust, left, top);
                }
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
        updateGlow(p, at.position(), valid);
    }

    @Override
    public void clear(UUID viewer, String reason) {
        hinted.remove(viewer);
        removeGlow(viewer);
        Player p = Bukkit.getPlayer(viewer);
        if (p == null) return;
        switch (reason) {
            case "confirmed", "switched" -> p.sendActionBar(Component.empty());
            case "timeout" -> p.sendActionBar(Component.text("Aim timed out", NamedTextColor.GRAY));
            case "blocked" -> p.sendActionBar(Component.text("Interrupted", NamedTextColor.RED));
            default -> p.sendActionBar(Component.text("Cancelled", NamedTextColor.GRAY));
        }
    }

    // ---- the glowing outline (seen through walls) -------------------------------------------------

    /**
     * The shape's edges (just drawn as particles) as thin glowing bars, only this player sees them. They all stand on the
     * aim spot and reach their edges by their transformation, so the whole outline moves with one teleport each.
     */
    private void updateGlow(Player p, Vec3 spot, boolean valid) {
        Location anchor = Convert.location(p.getWorld().getName(), spot);
        if (anchor == null) return;
        anchor.setYaw(0);
        anchor.setPitch(0);
        UUID id = p.getUniqueId();
        java.util.List<org.bukkit.entity.BlockDisplay> bars = glows.computeIfAbsent(id, k -> new java.util.ArrayList<>());
        bars.removeIf(b -> !b.isValid() || b.getWorld() != anchor.getWorld());
        while (bars.size() < segments.size()) bars.add(spawnGlow(p, anchor));
        Color color = valid ? VALID_COLOR : INVALID_COLOR;
        for (int i = 0; i < bars.size(); i++) {
            org.bukkit.entity.BlockDisplay bar = bars.get(i);
            bar.teleport(anchor);
            if (!color.equals(bar.getGlowColorOverride())) bar.setGlowColorOverride(color);
            bar.setInterpolationDelay(0);
            if (i >= segments.size()) { // a spare: out of sight
                bar.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf()));
                continue;
            }
            Vec3 a = segments.get(i)[0].subtract(spot), b = segments.get(i)[1].subtract(spot);
            Vec3 d = b.subtract(a);
            float len = (float) d.length();
            if (len < 1e-4) continue;
            Quaternionf rot = new Quaternionf().rotationTo(1, 0, 0, (float) (d.x() / len), (float) (d.y() / len), (float) (d.z() / len));
            // a bar from a to b: its box grows from its corner, so start half its thickness off the line
            Vector3f start = new Vector3f((float) a.x(), (float) a.y(), (float) a.z())
                    .add(rot.transform(new Vector3f(0, -GLOW_THICK / 2, -GLOW_THICK / 2)));
            bar.setTransformation(new Transformation(start, rot, new Vector3f(len, GLOW_THICK, GLOW_THICK), new Quaternionf()));
        }
    }

    /** One glowing bar, invisible to everyone but the aiming player. */
    private org.bukkit.entity.BlockDisplay spawnGlow(Player owner, Location at) {
        org.bukkit.entity.BlockDisplay d = at.getWorld().spawn(at, org.bukkit.entity.BlockDisplay.class, e -> {
            e.setVisibleByDefault(false); // nobody sees it unless shown (only ever to the aiming player)
            e.setPersistent(false);
            e.setBlock(Material.LIGHT_BLUE_CONCRETE.createBlockData());
            e.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            e.setGlowing(true);
            e.setGlowColorOverride(VALID_COLOR);
            e.setTeleportDuration(2);     // glides with the crosshair instead of jumping
            e.setInterpolationDuration(2);
            VisualEntities.mark(e);
        });
        owner.showEntity(plugin, d);
        return d;
    }

    private void removeGlow(UUID viewer) {
        java.util.List<org.bukkit.entity.BlockDisplay> bars = glows.remove(viewer);
        if (bars != null) for (var b : bars) if (b.isValid()) b.remove();
    }

    // ---- shapes ------------------------------------------------------------------------------

    private static void dot(Player p, Particle.DustOptions dust, Vec3 v) {
        p.spawnParticle(Particle.DUST, v.x(), v.y(), v.z(), 1, 0, 0, 0, 0, dust);
    }

    /** A ring of dots, and its glowing outline: a polygon of bars about {@link #GLOW_SEGMENT} long. */
    private void ring(Player p, Particle.DustOptions dust, Vec3 center, double radius) {
        int points = Math.max(10, (int) Math.ceil(2 * Math.PI * radius / SPACING));
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            dot(p, dust, center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
        int sides = Math.max(8, (int) Math.ceil(2 * Math.PI * radius / GLOW_SEGMENT));
        for (int i = 0; i < sides; i++) {
            double a = 2 * Math.PI * i / sides, b = 2 * Math.PI * (i + 1) / sides;
            segments.add(new Vec3[] {center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
                    center.add(Math.cos(b) * radius, 0, Math.sin(b) * radius)});
        }
    }

    private void line(Player p, Particle.DustOptions dust, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int points = Math.max(2, (int) Math.ceil(d.length() / SPACING));
        for (int i = 0; i <= points; i++) dot(p, dust, from.add(d.multiply(i / (double) points)));
        segments.add(new Vec3[] {from, to});
    }

    private void arc(Player p, Particle.DustOptions dust, Vec3 origin, Vec3 forward, double half) {
        double len = forward.length();
        int points = Math.max(6, (int) Math.ceil(2 * half * len / SPACING));
        for (int i = 0; i <= points; i++) {
            double a = -half + 2 * half * i / points;
            dot(p, dust, origin.add(rotateY(forward, a)));
        }
        int sides = Math.max(4, (int) Math.ceil(2 * half * len / GLOW_SEGMENT));
        for (int i = 0; i < sides; i++) {
            double a = -half + 2 * half * i / sides, b = -half + 2 * half * (i + 1) / sides;
            segments.add(new Vec3[] {origin.add(rotateY(forward, a)), origin.add(rotateY(forward, b))});
        }
    }

    private static Vec3 rotateY(Vec3 v, double rad) {
        double c = Math.cos(rad), s = Math.sin(rad);
        return new Vec3(v.x() * c - v.z() * s, v.y(), v.x() * s + v.z() * c);
    }
}

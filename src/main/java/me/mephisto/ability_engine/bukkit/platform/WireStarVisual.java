package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.ProjectileVisual;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A projectile drawn as a 3D star: two wireframe cubes the same size, one inside the other and turned against it, so
 * the inner one's corners poke out through the outer one's faces. Only their edges show (thin glowing bars), and the
 * whole thing tumbles as it flies. Written {@code "wire_star"} or {@code "wire_star:<outer block>:<inner block>"}
 * (default purple and magenta). 24 block displays: for small, short-lived shots (a basic attack).
 */
final class WireStarVisual implements ProjectileVisual {

    static final String PREFIX = "wire_star";

    /** Bar thickness as a share of the cube's side. */
    private static final float BAR = 0.09f;
    /** How fast it tumbles (radians a tick), and about which axis. */
    private static final float SPIN = 0.3f;
    private static final Vector3f AXIS = new Vector3f(0.4f, 1f, 0.25f).normalize();
    /** The inner cube's turn against the outer one. */
    private static final Quaternionf INNER = new Quaternionf().rotateY((float) Math.toRadians(45)).rotateX((float) Math.toRadians(45));

    private record Bar(BlockDisplay display, boolean inner, Vector3f center, Vector3f scale) {}

    private final List<Bar> bars = new ArrayList<>();
    private float angle;

    static boolean matches(String visual) {
        return visual != null && visual.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    static WireStarVisual spawn(Location at, String visual, float size) {
        String[] parts = visual.split(":");
        Material outer = material(parts.length > 1 ? parts[1] : null, Material.PURPLE_CONCRETE);
        Material inner = material(parts.length > 2 ? parts[2] : null, Material.MAGENTA_CONCRETE);
        return new WireStarVisual(at, Math.max(0.1f, size), outer, inner);
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material m = Material.matchMaterial(name.trim());
        return m != null && m.isBlock() ? m : fallback;
    }

    private WireStarVisual(Location at, float side, Material outer, Material inner) {
        Location loc = at.clone();
        loc.setYaw(0);
        loc.setPitch(0);
        float h = side / 2, t = side * BAR;
        for (int cube = 0; cube < 2; cube++) {
            for (int axis = 0; axis < 3; axis++) {
                for (int corner = 0; corner < 4; corner++) { // the 4 edges along this axis
                    float a = (corner & 1) == 0 ? -h : h, b = (corner & 2) == 0 ? -h : h;
                    Vector3f center = switch (axis) {
                        case 0 -> new Vector3f(0, a, b);
                        case 1 -> new Vector3f(a, 0, b);
                        default -> new Vector3f(a, b, 0);
                    };
                    Vector3f scale = switch (axis) { // (a bar's length a hair over the side: the corners close)
                        case 0 -> new Vector3f(side + t, t, t);
                        case 1 -> new Vector3f(t, side + t, t);
                        default -> new Vector3f(t, t, side + t);
                    };
                    Material block = cube == 0 ? outer : inner;
                    BlockDisplay d = loc.getWorld().spawn(loc, BlockDisplay.class, e -> {
                        e.setBlock(block.createBlockData());
                        e.setPersistent(false);
                        e.setBrightness(new Display.Brightness(15, 15));
                        e.setTeleportDuration(1);
                        e.setInterpolationDuration(1);
                        VisualEntities.mark(e);
                    });
                    bars.add(new Bar(d, cube == 1, center, scale));
                }
            }
        }
        pose();
    }

    /** Every bar where it belongs at the current tumble angle. */
    private void pose() {
        Quaternionf spin = new Quaternionf().rotateAxis(angle, AXIS);
        for (Bar bar : bars) {
            if (!bar.display().isValid()) continue;
            Quaternionf rot = bar.inner() ? new Quaternionf(spin).mul(INNER) : new Quaternionf(spin);
            // the bar's centre turned with the cube; the box itself grows from its corner, so start half its size back
            Vector3f corner = new Vector3f(bar.center()).sub(new Vector3f(bar.scale()).mul(0.5f));
            Vector3f translation = rot.transform(corner);
            bar.display().setInterpolationDelay(0);
            bar.display().setTransformation(new Transformation(translation, rot, new Vector3f(bar.scale()), new Quaternionf()));
        }
    }

    @Override
    public void moveTo(Vec3 p) {
        angle += SPIN;
        for (Bar bar : bars) {
            if (!bar.display().isValid()) continue;
            bar.display().teleport(new Location(bar.display().getWorld(), p.x(), p.y(), p.z(), 0, 0));
        }
        pose();
    }

    @Override
    public void remove() {
        for (Bar bar : bars) if (bar.display().isValid()) bar.display().remove();
        bars.clear();
    }
}

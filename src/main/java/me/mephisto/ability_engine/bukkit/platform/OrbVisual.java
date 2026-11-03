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
 * A projectile drawn as a round orb: a see-through shell made of stacked discs (each disc four thin squares turned
 * 22.5 degrees apart, like a floor), slowly turning, around a small solid core that tumbles the other way. Written
 * {@code "orb"} or {@code "orb:<shell block>:<core block>"} (default purple glass around crying obsidian); its
 * {@code visual_size} is the orb's width. Add a {@code trail} cue for the particles around it.
 */
final class OrbVisual implements ProjectileVisual {

    static final String PREFIX = "orb";

    /** The shell's slices (bottom to top), and how fast it and the core turn (radians a tick). */
    private static final int LAYERS = 5;
    private static final float SHELL_SPIN = 0.12f, CORE_SPIN = 0.35f;
    private static final Vector3f CORE_AXIS = new Vector3f(1f, 0.6f, 0.3f).normalize();
    /** The core's width, as a share of the orb's. */
    private static final float CORE = 0.42f;

    private record Part(BlockDisplay display, float y, float half, float thick, float turn, boolean core) {}

    private final List<Part> parts = new ArrayList<>();
    private float angle;

    static boolean matches(String visual) {
        if (visual == null) return false;
        String v = visual.toLowerCase(Locale.ROOT);
        return v.equals(PREFIX) || v.startsWith(PREFIX + ":");
    }

    static OrbVisual spawn(Location at, String visual, float size) {
        String[] bits = visual.split(":");
        Material shell = material(bits.length > 1 ? bits[1] : null, Material.PURPLE_STAINED_GLASS);
        Material core = material(bits.length > 2 ? bits[2] : null, Material.CRYING_OBSIDIAN);
        return new OrbVisual(at, Math.max(0.1f, size), shell, core);
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material m = Material.matchMaterial(name.trim());
        return m != null && m.isBlock() ? m : fallback;
    }

    private OrbVisual(Location at, float width, Material shell, Material core) {
        Location loc = at.clone();
        loc.setYaw(0);
        loc.setPitch(0);
        float r = width / 2, thick = width / LAYERS;
        for (int i = 0; i < LAYERS; i++) {
            float y = -r + thick * (i + 0.5f);                 // the middle of this slice
            float half = (float) Math.sqrt(Math.max(0, r * r - y * y)) / 1.0098f; // a 16-sided disc that wide there
            for (int k = 0; k < 4; k++) {
                parts.add(new Part(display(loc, shell), y - thick / 2, half, thick, (float) (k * Math.PI / 8), false));
            }
        }
        parts.add(new Part(display(loc, core), 0, width * CORE / 2, width * CORE, 0, true));
        pose();
    }

    private static BlockDisplay display(Location loc, Material block) {
        return loc.getWorld().spawn(loc, BlockDisplay.class, e -> {
            e.setBlock(block.createBlockData());
            e.setPersistent(false);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(1);
            e.setInterpolationDuration(1);
            VisualEntities.mark(e);
        });
    }

    private void pose() {
        for (Part p : parts) {
            if (!p.display().isValid()) continue;
            Transformation t;
            if (p.core()) { // a small cube, tumbling about its middle
                Quaternionf rot = new Quaternionf().rotateAxis(angle * CORE_SPIN / SHELL_SPIN, CORE_AXIS);
                Vector3f corner = rot.transform(new Vector3f(-p.half(), -p.half(), -p.half()));
                t = new Transformation(corner, rot, new Vector3f(p.thick()), new Quaternionf());
            } else {           // one square of a slice, turned about the vertical axis
                Quaternionf rot = new Quaternionf().rotateY(p.turn() + angle);
                Vector3f corner = rot.transform(new Vector3f(-p.half(), 0, -p.half())).add(0, p.y(), 0);
                t = new Transformation(corner, rot, new Vector3f(p.half() * 2, p.thick(), p.half() * 2), new Quaternionf());
            }
            p.display().setInterpolationDelay(0);
            p.display().setTransformation(t);
        }
    }

    @Override
    public void moveTo(Vec3 p) {
        angle += SHELL_SPIN;
        for (Part part : parts) {
            if (!part.display().isValid()) continue;
            part.display().teleport(new Location(part.display().getWorld(), p.x(), p.y(), p.z(), 0, 0));
        }
        pose();
    }

    @Override
    public void remove() {
        for (Part p : parts) if (p.display().isValid()) p.display().remove();
        parts.clear();
    }
}

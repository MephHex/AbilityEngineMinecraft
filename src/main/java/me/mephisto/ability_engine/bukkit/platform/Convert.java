package me.mephisto.ability_engine.bukkit.platform;

import me.mephisto.ability_engine.engine.math.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/** Vec3 <-> Bukkit conversions, in one place. */
public final class Convert {

    public static Vec3 vec(Vector v) { return new Vec3(v.getX(), v.getY(), v.getZ()); }
    public static Vec3 vec(Location l) { return new Vec3(l.getX(), l.getY(), l.getZ()); }
    public static Vector bukkit(Vec3 v) { return new Vector(v.x(), v.y(), v.z()); }

    /** Null if the world isn't loaded. */
    public static Location location(String world, Vec3 v) {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, v.x(), v.y(), v.z());
    }

    private Convert() {}
}

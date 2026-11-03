package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.engine.AbilityEngine;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Tag {@code state.aim_highlight}: the enemy under the holder's crosshair, within {@link #RANGE} blocks and in sight,
 * glows - for the holder only (nobody else sees it): what their next attack would reach. The outline is told to them
 * alone (Minecraft's glowing flag, in a packet), again every few ticks (the server's own updates of that entity put its
 * real flags back), and taken off when they look away or lose the tag.
 */
public final class AimHighlight {

    public static final String TAG = "state.aim_highlight";
    public static final double RANGE = 20;
    private static final byte GLOWING = 0x40;

    private final AbilityEngine engine;
    private final Logger log;
    /** Holder -> whom they see glowing right now. */
    private final Map<UUID, UUID> lit = new HashMap<>();
    private boolean broken;
    private int tick;

    public AimHighlight(AbilityEngine engine, Logger log) {
        this.engine = engine;
        this.log = log;
    }

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 2, 2);
    }

    private void update() {
        if (broken) return;
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            UUID before = lit.get(id);
            UUID now = engine.tags().has(id, TAG) ? aimedAt(p) : null;
            if (before != null && !before.equals(now)) {
                show(p, before, false);
                lit.remove(id);
            }
            if (now != null && (!now.equals(before) || tick % 5 == 0)) { // (again now and then: see above)
                show(p, now, true);
                lit.put(id, now);
            }
        }
        lit.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    /** The enemy their crosshair is on, within range and in sight (a block in between stops it). */
    private UUID aimedAt(Player p) {
        Location eye = p.getEyeLocation();
        RayTraceResult hit = p.getWorld().rayTrace(eye, eye.getDirection(), RANGE, FluidCollisionMode.NEVER, true, 0.3,
                e -> e instanceof LivingEntity && !e.equals(p) && e.isValid()
                        && !engine.teams().allies(p.getUniqueId(), e.getUniqueId()));
        Entity e = hit == null ? null : hit.getHitEntity();
        return e == null ? null : e.getUniqueId();
    }

    private void show(Player viewer, UUID target, boolean glowing) {
        Entity e = Bukkit.getEntity(target);
        if (e == null || !viewer.isOnline()) return;
        try {
            Packets.sharedFlags(viewer, e, glowing);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            broken = true;
            log.warning("state.aim_highlight can't show glowing outlines on this server: " + ex);
        }
    }

    /** An entity's shared flags (on fire, sneaking, ..., glowing): its own, the glow added or not, told to one player. */
    private static final class Packets {
        private static Object accessor;
        private static java.lang.reflect.Method entityData, get, createValue;
        private static java.lang.reflect.Constructor<?> packet;
        private static java.lang.reflect.Field connection;
        private static java.lang.reflect.Method send;

        static synchronized void resolve() throws ReflectiveOperationException {
            if (send != null) return;
            Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
            var field = entity.getDeclaredField("DATA_SHARED_FLAGS_ID");
            field.setAccessible(true);
            accessor = field.get(null);
            entityData = entity.getMethod("getEntityData");
            Class<?> accessorType = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            get = Class.forName("net.minecraft.network.syncher.SynchedEntityData").getMethod("get", accessorType);
            createValue = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue")
                    .getMethod("create", accessorType, Object.class);
            packet = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
                    .getConstructor(int.class, List.class);
            connection = Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection");
            send = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl")
                    .getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
        }

        static void sharedFlags(Player viewer, Entity of, boolean glowing) throws ReflectiveOperationException {
            resolve();
            Object handle = of.getClass().getMethod("getHandle").invoke(of);
            byte flags = (Byte) get.invoke(entityData.invoke(handle), accessor);
            byte shown = glowing ? (byte) (flags | GLOWING) : flags;
            Object p = packet.newInstance(of.getEntityId(), List.of(createValue.invoke(null, accessor, shown)));
            send.invoke(connection.get(viewer.getClass().getMethod("getHandle").invoke(viewer)), p);
        }
    }

    /** At startup: can the outlines be shown here? Null: yes; otherwise why not. */
    public static String selfCheck() {
        try {
            Packets.resolve();
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return ex.toString();
        }
    }
}

package me.mephisto.ability_engine.bukkit.platform;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A stand-in for a World that sends particles and sounds only to some players (an audience: the two in a
 * duel). Cues draw with {@code world.spawnParticle(...)} / {@code world.playSound(...)}; handed this world
 * instead, those go to each audience player ({@code player.spawnParticle / playSound}, the same calls).
 * Everything else (spawning entities, blocks...) goes to the real world.
 */
final class AudienceWorld implements InvocationHandler {

    private final World real;
    private final Set<UUID> audience;

    private AudienceWorld(World real, Set<UUID> audience) {
        this.real = real;
        this.audience = audience;
    }

    static World of(World real, Set<UUID> audience) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                new AudienceWorld(real, audience));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] rawArgs) throws Throwable {
        String name = method.getName();
        Object[] args = realLocations(proxy, rawArgs);
        if (name.equals("spawnParticle") || name.equals("playSound")) {
            for (UUID id : audience) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.getWorld().equals(real)) toPlayer(p, method, args);
            }
            return null;
        }
        if (name.equals("equals") && args != null && args.length == 1) return real.equals(args[0]) || proxy == args[0];
        try {
            return method.invoke(real, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** Locations the cue built on this stand-in world point at the real world again before they go on. */
    private Object[] realLocations(Object proxy, Object[] args) {
        if (args == null) return null;
        Object[] out = args.clone();
        for (int i = 0; i < out.length; i++) {
            if (out[i] instanceof org.bukkit.Location l && l.getWorld() == proxy) {
                out[i] = new org.bukkit.Location(real, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
            }
        }
        return out;
    }

    /** The same call on the player; World's overloads with a trailing {@code force} flag drop it. */
    private static void toPlayer(Player p, Method method, Object[] args) throws Throwable {
        Class<?>[] types = method.getParameterTypes();
        Object[] use = args;
        Method target = find(method.getName(), types);
        if (target == null && types.length > 0 && types[types.length - 1] == boolean.class) {
            types = Arrays.copyOf(types, types.length - 1);
            use = Arrays.copyOf(args, args.length - 1);
            target = find(method.getName(), types);
        }
        if (target == null) return;
        try {
            target.invoke(p, use);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static final java.util.Map<String, Method> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method find(String name, Class<?>[] types) {
        List<String> key = new ArrayList<>();
        key.add(name);
        for (Class<?> t : types) key.add(t.getName());
        Method m = CACHE.computeIfAbsent(String.join(",", key), k -> {
            try {
                return Player.class.getMethod(name, types);
            } catch (NoSuchMethodException e) {
                return NONE;
            }
        });
        return m == NONE ? null : m;
    }
}

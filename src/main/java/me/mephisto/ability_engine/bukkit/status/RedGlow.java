package me.mephisto.ability_engine.bukkit.status;

import me.mephisto.ability_engine.bukkit.platform.VisualEntities;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The tag {@code state.glowing_red}: a red glowing outline round them, seen by everyone through walls (e.g. the Dragon
 * Hunter's harpooned target in execution range). A glow's colour is its scoreboard team's, and theirs may be taken (a
 * side), so the outline is an invisible glowing Mannequin standing in them, on a red team of its own, following them.
 */
final class RedGlow {

    static final String TAG = "state.glowing_red";
    private static final String TEAM = "ae_glow_red";

    /** Who glows -> their stand-in, and the task keeping it on them. */
    private static final Map<UUID, Mannequin> GLOWS = new HashMap<>();
    private static BukkitTask follower;

    static void on(LivingEntity e) {
        off(e);
        Mannequin m = e.getWorld().spawn(e.getLocation(), Mannequin.class, s -> {
            s.setInvisible(true);
            s.setGlowing(true);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setGravity(false);
            s.setCollidable(false);
            s.setPersistent(false);
            VisualEntities.mark(s);              // abilities ignore it
        });
        team().addEntity(m);
        GLOWS.put(e.getUniqueId(), m);
        if (follower == null) {
            follower = Bukkit.getScheduler().runTaskTimer(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(RedGlow.class), RedGlow::follow, 1, 1);
        }
    }

    static void off(LivingEntity e) {
        Mannequin m = GLOWS.remove(e.getUniqueId());
        if (m != null && m.isValid()) m.remove();
        if (GLOWS.isEmpty() && follower != null) {
            follower.cancel();
            follower = null;
        }
    }

    /** Every tick: each stand-in where its one is (gone or dead: the glow goes too). */
    private static void follow() {
        GLOWS.entrySet().removeIf(en -> {
            Mannequin m = en.getValue();
            if (!(Bukkit.getEntity(en.getKey()) instanceof LivingEntity who) || !who.isValid() || who.isDead() || !m.isValid()) {
                if (m.isValid()) m.remove();
                return true;
            }
            Location at = who.getLocation();
            if (!at.getWorld().equals(m.getWorld())) {
                m.remove();
                return true;
            }
            m.teleport(at);
            return false;
        });
    }

    private static Team team() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = board.getTeam(TEAM);
        if (t == null) {
            t = board.registerNewTeam(TEAM);
            t.setColor(ChatColor.RED);
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        return t;
    }

    private RedGlow() {}
}

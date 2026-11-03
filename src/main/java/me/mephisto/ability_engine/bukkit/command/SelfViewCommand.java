package me.mephisto.ability_engine.bukkit.command;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * /selfview [on|off]: whether a player sees their own character model while its animations play (Model Engine). Off by
 * default: the server can't tell first person from third, and in first person the model blocks the view. Others always
 * see it. Kept on the player (survives relogs).
 */
public final class SelfViewCommand implements CommandExecutor {

    private final NamespacedKey key;

    public SelfViewCommand(Plugin plugin) {
        this.key = key(plugin);
    }

    public static NamespacedKey key(Plugin plugin) { return new NamespacedKey(plugin, "self_view"); }

    /** Whether {@code player} chose to see their own model. */
    public static boolean wants(Player player, Plugin plugin) {
        return player.getPersistentDataContainer().getOrDefault(key(plugin), PersistentDataType.BOOLEAN, false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can choose what they see.");
            return true;
        }
        boolean now = player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.BOOLEAN, false);
        boolean want = args.length == 0 ? !now : args[0].equalsIgnoreCase("on");
        if (args.length > 0 && !args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off")) return false;
        player.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, want);
        player.sendMessage(want
                ? ChatColor.GREEN + "You'll see your own animations (best in third person, F5)."
                : ChatColor.GRAY + "You won't see your own animations (others still do).");
        return true;
    }
}

package me.mephisto.ability_engine.bukkit.command;

import me.mephisto.ability_engine.bukkit.hud.HotbarHud;
import me.mephisto.ability_engine.bukkit.input.ItemBindings;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.activation.ActivationResult;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.status.ActiveStatus;
import me.mephisto.ability_engine.engine.platform.TaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * /ae char|cast|bind|unbind|cdclear|list|status|setres|reload|debug. Was bukkit/listener/Commands
 * (it isn't a listener). Activation goes through the same AbilityActivator as every input.
 * bind/unbind are the debug path: they only work while you have no character.
 */
public final class AbilityCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("char", "quickcast", "cast", "castas", "bind", "unbind", "cdclear", "list", "status", "apply", "setres", "reload", "debug");

    private final AbilityEngine engine;
    private final ItemBindings bindings;
    private final HotbarHud hud;
    private final Supplier<LoadReport> reloader;

    public AbilityCommand(AbilityEngine engine, ItemBindings bindings, HotbarHud hud, Supplier<LoadReport> reloader) {
        this.engine = engine;
        this.bindings = bindings;
        this.hud = hud;
        this.reloader = reloader;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list" -> {
                sender.sendMessage(ChatColor.GOLD + "Abilities: " + ChatColor.WHITE + String.join(", ", engine.abilities().ids()));
                sender.sendMessage(ChatColor.GOLD + "Characters: " + ChatColor.WHITE + String.join(", ", engine.characters().ids()));
                return true;
            }
            case "reload" -> {
                engine.instances().cancelEverything("reload");
                report(sender, reloader.get());
                return true;
            }
            case "castas" -> {
                castAs(sender, args);
                return true;
            }
            case "debug" -> {
                engine.log().setDebug(!engine.log().isDebug());
                sender.sendMessage(ChatColor.GOLD + "Debug logging " + (engine.log().isDebug() ? "ON" : "OFF"));
                return true;
            }
            default -> {
                // everything else needs a player
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        UUID id = player.getUniqueId();

        switch (sub) {
            case "char" -> {
                if (args.length < 2) return false;
                if (args[1].equalsIgnoreCase("none")) {
                    hud.unequip(player);
                    player.sendMessage(ChatColor.GREEN + "Character removed. Vanilla controls (and /ae bind items) are back.");
                } else if (hud.equip(player, args[1])) {
                    player.sendMessage(ChatColor.GREEN + "Playing " + args[1] + ". LMB fire, 1/2/3 abilities, F ultimate.");
                } else {
                    player.sendMessage(ChatColor.RED + "Unknown character " + args[1]);
                }
            }
            case "cast" -> {
                if (args.length < 2) return false;
                ActivationResult r = engine.activator().activate(id, args[1]);
                if (r.openedTargeting()) player.sendMessage(ChatColor.AQUA + "Aiming " + args[1] + ": left click to confirm (with a character: its key again), right click to cancel.");
                else player.sendMessage(r.success() ? ChatColor.GREEN + "Cast " + args[1] : ChatColor.RED + r.reason());
            }
            case "quickcast" -> {
                boolean on = engine.targeting().toggleQuickCast(id);
                player.sendMessage(ChatColor.GOLD + "Quick cast " + (on ? "ON: abilities fire instantly at your crosshair."
                        : "OFF: aimed abilities show a preview first."));
            }
            case "bind" -> {
                if (args.length < 2) return false;
                if (engine.abilities().find(args[1]).isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Unknown ability " + args[1]);
                    return true;
                }
                if (engine.loadouts().has(id)) {
                    player.sendMessage(ChatColor.RED + "Bound items only work without a character: /ae char none");
                    return true;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                player.sendMessage(bindings.bind(hand, args[1])
                        ? ChatColor.GREEN + "Bound " + args[1] + " to this item. Right-click to cast."
                        : ChatColor.RED + "Hold an item first.");
            }
            case "unbind" -> {
                bindings.unbind(player.getInventory().getItemInMainHand());
                player.sendMessage(ChatColor.GREEN + "Unbound.");
            }
            case "cdclear" -> {
                if (args.length >= 2) engine.cooldowns().clear(id, args[1]);
                else engine.cooldowns().clearAll(id);
                hud.refresh(player);
                player.sendMessage(ChatColor.GREEN + "Cooldowns cleared.");
            }
            case "status" -> {
                player.sendMessage(ChatColor.GOLD + "Character: " + ChatColor.WHITE
                        + engine.loadouts().characterOf(id).map(c -> c.id()).orElse("none"));
                player.sendMessage(ChatColor.GOLD + "Tags: " + ChatColor.WHITE + engine.tags().tagsOf(id));
                for (ActiveStatus s : engine.statuses().on(id)) {
                    long left = engine.statuses().remainingTicks(id, s.def().id());
                    player.sendMessage(ChatColor.GOLD + " " + s.def().id() + ChatColor.WHITE + " x" + s.stacks()
                            + (s.isInfinite() ? " (infinite)" : " " + left + "t"));
                }
                player.sendMessage(ChatColor.GOLD + "Running: " + ChatColor.WHITE + engine.instances().of(id));
                if (engine.quivers().has(id)) {
                    player.sendMessage(ChatColor.GOLD + "Quiver: " + ChatColor.WHITE + "loaded="
                            + engine.quivers().loaded(id).map(b -> b.infusions().toString()).orElse("none")
                            + " queue=" + engine.quivers().queue(id).stream().map(b -> b.infusions().toString()).toList()
                            + " reload_speed=" + engine.quivers().reloadSpeed(id));
                }
            }
            case "apply" -> { // a status on yourself, as if an enemy put it there (a debuff: wards block it)
                if (args.length < 2) return false;
                var def = engine.statusDefs().find(args[1]);
                if (def.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Unknown status: " + args[1]);
                    return true;
                }
                int ticks;
                try {
                    ticks = args.length >= 3 ? Integer.parseInt(args[2]) : def.get().defaultDurationTicks();
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Not a number: " + args[2]);
                    return true;
                }
                engine.statuses().apply(id, def.get(), ticks, null);
                boolean landed = engine.statuses().has(id, args[1]);
                player.sendMessage(landed ? ChatColor.GREEN + "Applied " + args[1] + " (" + ticks + "t, from nobody)"
                        : ChatColor.YELLOW + args[1] + " didn't land (blocked, e.g. debuff immunity)");
            }
            case "setres" -> {
                if (args.length < 3) return false;
                try {
                    engine.resources().set(id, args[1], Integer.parseInt(args[2]));
                    player.sendMessage(ChatColor.GREEN + args[1] + " = " + args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Not a number: " + args[2]);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * /ae castas <selector|uuid> <ability> [recast <ticks>] [hold <ticks>]
     * Make other entities (a mannequin, a mob) cast an ability as themselves, aimed where they're
     * looking. For testing against "enemies" without a second player. The input a player would
     * give is simulated:
     * <ul>
     *   <li>aim previews: skipped, fires instantly at the crosshair (quick cast)</li>
     *   <li>{@code recast N}: presses the ability again after N ticks (e.g. steer a Missile)</li>
     *   <li>{@code hold N}: keeps the button held for N ticks, then releases (e.g. Arcane Focus)</li>
     * </ul>
     * Works from the console and command blocks too.
     */
    private void castAs(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "/ae castas <selector> <ability> [recast <ticks>] [hold <ticks>]");
            sender.sendMessage(ChatColor.GRAY + "e.g. /ae castas @e[type=minecraft:mannequin,limit=1,sort=nearest] arcanist_ab1 recast 10");
            return;
        }
        String ability = args[2];
        int recast = 0;
        int hold = 0;
        for (int i = 3; i + 1 < args.length; i += 2) {
            int ticks;
            try {
                ticks = Integer.parseInt(args[i + 1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Not a number of ticks: " + args[i + 1]);
                return;
            }
            switch (args[i].toLowerCase()) {
                case "recast" -> recast = ticks;
                case "hold" -> hold = ticks;
                default -> {
                    sender.sendMessage(ChatColor.RED + "Unknown option " + args[i] + " (recast, hold)");
                    return;
                }
            }
        }

        List<Entity> casters;
        try {
            casters = Bukkit.selectEntities(sender, args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Bad selector: " + e.getMessage());
            return;
        }
        int ok = 0;
        for (Entity entity : casters) {
            if (!(entity instanceof LivingEntity)) continue;
            UUID id = entity.getUniqueId();
            if (!engine.targeting().isQuickCast(id)) engine.targeting().toggleQuickCast(id);
            giveResourcePools(id);
            ActivationResult r = engine.activator().activate(id, ability);
            if (!r.success()) {
                if (casters.size() == 1) sender.sendMessage(ChatColor.RED + r.reason());
                continue;
            }
            ok++;
            if (hold > 0) holdFor(entity, ability, hold);
            if (recast > 0) {
                engine.scheduler().after(recast, () -> {
                    if (entity.isValid()) engine.activator().activate(id, ability, true); // a fresh second press
                });
            }
        }
        if (casters.size() != 1 || ok == 1) {
            sender.sendMessage(ChatColor.GREEN + "Cast " + ability + " from " + ok + "/" + casters.size() + " entities.");
        }
    }

    /**
     * A dummy has no character, so no Focus/mana/etc. Give it every pool any kit defines (full, with the
     * kit's regen), once, so abilities with costs work. Pools it already has are left alone.
     */
    private void giveResourcePools(UUID id) {
        for (String charId : engine.characters().ids()) {
            engine.characters().find(charId).ifPresent(c -> c.resources().values().forEach(def -> {
                if (engine.resources().definition(id, def.id()).isEmpty()) engine.resources().define(id, def);
            }));
        }
    }

    /** Simulate holding the button: the same "still held" repeat a player's right click sends every 4 ticks. */
    private void holdFor(Entity entity, String ability, int ticks) {
        int[] elapsed = {0};
        TaskHandle[] task = new TaskHandle[1];
        task[0] = engine.scheduler().every(4, 4, () -> {
            elapsed[0] += 4;
            if (elapsed[0] > ticks || !entity.isValid()) {
                task[0].cancel(); // stop repeating: the hold notices the release a few ticks later
                return;
            }
            engine.activator().activate(entity.getUniqueId(), ability, false);
        });
    }

    public static void report(CommandSender to, LoadReport report) {
        to.sendMessage((report.isClean() ? ChatColor.GREEN : ChatColor.YELLOW) + "Loaded " + report);
        if (report.source() != null) to.sendMessage(ChatColor.GRAY + "Read from: " + report.source());
        report.errors().forEach(e -> to.sendMessage(ChatColor.RED + " - " + e));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(SUBS.stream(), args[0]);
        if (args.length == 2 && List.of("cast", "bind", "cdclear").contains(args[0].toLowerCase())) {
            return filter(engine.abilities().ids().stream().sorted(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("castas")) {
            return filter(Stream.of("@e[type=minecraft:mannequin,limit=1,sort=nearest]", "@e[limit=1,sort=nearest,type=!player]"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("castas")) {
            return filter(engine.abilities().ids().stream().sorted(), args[2]);
        }
        if (args.length >= 4 && args.length % 2 == 0 && args[0].equalsIgnoreCase("castas")) {
            return filter(Stream.of("recast", "hold"), args[args.length - 1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("apply")) {
            return filter(engine.statusDefs().ids().stream().sorted(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("char")) {
            return filter(Stream.concat(Stream.of("none"), engine.characters().ids().stream().sorted()), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> options, String prefix) {
        return options.filter(o -> o.startsWith(prefix.toLowerCase())).toList();
    }
}

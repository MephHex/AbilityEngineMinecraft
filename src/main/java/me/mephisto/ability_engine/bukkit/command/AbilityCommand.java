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

    private static final List<String> SUBS = List.of("char", "dummy", "mob", "quickcast", "cast", "castas", "bind", "unbind", "cdclear", "list", "status", "apply", "setres", "reload", "debug");

    /** Design HP per Minecraft health point (config damage-scale), for a dummy's health. */
    private java.util.function.DoubleSupplier damageScale = () -> 10;

    public void setDamageScale(java.util.function.DoubleSupplier damageScale) { this.damageScale = damageScale; }

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
            case "char" -> {
                if (args.length < 2) return false;
                if (args.length >= 3) {
                    charFor(sender, args[1], args[2]);
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("From the console or a command block, name who: /ae char <character|none> <player|selector>");
                    return true;
                }
                equip(player, args[1], sender);
                return true;
            }
            case "dummy" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "/ae dummy <character> [team]");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only a player can place a dummy (it goes in front of you).");
                    return true;
                }
                dummy(player, args[1], args.length >= 3 ? args[2] : null);
                return true;
            }
            case "mob" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "/ae mob <character> [entity type] [team]");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only a player can place a mob (it goes in front of you).");
                    return true;
                }
                String type = args.length >= 3 ? args[2] : "mannequin";
                spawn(player, args[1], type, args.length >= 4 ? args[3] : null, false);
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
            case "cast" -> {
                if (args.length < 2) return false;
                ActivationResult r = engine.activator().activate(id, args[1]);
                if (r.openedTargeting()) player.sendMessage(ChatColor.AQUA + "Aiming " + args[1] + ": left click to confirm, right click to cancel.");
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
                boolean ult = args.length < 2 || engine.stats().isUltimate(id, args[1]);
                if (ult && engine.ultCharge().enabled()) engine.ultCharge().set(id, 100); // the ultimate: fully charged
                hud.refresh(player);
                player.sendMessage(ChatColor.GREEN + "Cooldowns cleared" + (ult && engine.ultCharge().enabled()
                        ? ", ultimate charged." : "."));
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
     * /ae char <character|none> <player|selector>: pick a character for someone else. Works from the
     * console and command blocks (e.g. a champ-select button: /ae char gunner @p).
     */
    private void charFor(CommandSender sender, String character, String who) {
        List<Entity> targets;
        try {
            targets = Bukkit.selectEntities(sender, who);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Bad selector: " + e.getMessage());
            return;
        }
        List<Player> players = targets.stream().filter(e -> e instanceof Player).map(e -> (Player) e).toList();
        // Anything else that lives (a mannequin, a mob): the character without a HUD, e.g. a dummy to test against
        List<LivingEntity> others = targets.stream().filter(e -> e instanceof LivingEntity && !(e instanceof Player))
                .map(e -> (LivingEntity) e).toList();
        if (players.isEmpty() && others.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Nobody matched " + who);
            return;
        }
        if (!character.equalsIgnoreCase("none") && engine.characters().find(character).isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Unknown character " + character);
            return;
        }
        for (LivingEntity e : others) {
            if (character.equalsIgnoreCase("none")) {
                engine.resetEntity(e.getUniqueId(), "character_removed");
                engine.loadouts().clear(e.getUniqueId());
            } else {
                engine.loadouts().assign(e.getUniqueId(), character);
            }
        }
        if (!others.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + (character.equalsIgnoreCase("none") ? "Removed the character from " : "Gave " + character + " to ")
                    + others.size() + " non-player" + (others.size() == 1 ? "" : "s") + ".");
        }
        if (players.isEmpty()) return;
        int ok = 0;
        for (Player p : players) if (equip(p, character, null)) ok++;
        if (ok == 0) sender.sendMessage(ChatColor.RED + "Unknown character " + character);
        else if (!(players.size() == 1 && sender == players.get(0))) {
            sender.sendMessage(ChatColor.GREEN + (character.equalsIgnoreCase("none") ? "Removed the character from " : "Gave " + character + " to ")
                    + ok + " player" + (ok == 1 ? "" : "s") + ".");
        }
    }

    /**
     * /ae dummy <character> [team]: a mannequin 3 blocks in front of the player, facing them, playing that character (its
     * max HP too), on no team (everyone's enemy) or the given scoreboard team. Something to test against: hit it, steal its
     * ultimate, or make it cast with /ae castas. Killed, it's gone.
     */
    private void dummy(Player player, String character, String teamName) {
        spawn(player, character, "mannequin", teamName, true);
    }

    /**
     * Spawn a {@code typeName} (any living type) 3 blocks in front of the player, facing them, playing {@code character}
     * with its max HP and size, on no team (everyone's enemy) or {@code teamName}. A character with a brain drives it
     * (/ae mob: a PvE enemy); without one it stands there ({@code dummy}: also its ultimate charged, to steal).
     */
    private void spawn(Player player, String character, String typeName, String teamName, boolean dummy) {
        if (engine.characters().find(character).isEmpty()) {
            player.sendMessage(ChatColor.RED + "Unknown character " + character);
            return;
        }
        org.bukkit.entity.EntityType type;
        try {
            type = org.bukkit.entity.EntityType.valueOf(typeName.toUpperCase(java.util.Locale.ROOT).replace("MINECRAFT:", ""));
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Unknown entity type " + typeName);
            return;
        }
        if (type.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(type.getEntityClass()) || type == org.bukkit.entity.EntityType.PLAYER) {
            player.sendMessage(ChatColor.RED + typeName + " isn't a living mob");
            return;
        }
        var board = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team team = teamName == null ? null : board.getTeam(teamName);
        if (teamName != null && team == null) {
            player.sendMessage(ChatColor.RED + "No scoreboard team " + teamName);
            return;
        }
        org.bukkit.Location eye = player.getLocation();
        org.bukkit.util.Vector ahead = eye.getDirection().setY(0);
        if (ahead.lengthSquared() < 1e-6) ahead = new org.bukkit.util.Vector(1, 0, 0);
        org.bukkit.Location at = eye.clone().add(ahead.normalize().multiply(3));
        at.setDirection(ahead.clone().multiply(-1)); // facing the player
        var def = engine.characters().find(character).get();
        String name = def.name();
        LivingEntity m = (LivingEntity) player.getWorld().spawn(at, type.getEntityClass(), e -> {
            e.customName(net.kyori.adventure.text.Component.text(name + (dummy ? " (dummy)" : "")));
            e.setCustomNameVisible(true);
            e.setPersistent(false);
        });
        if (m instanceof org.bukkit.entity.Mob mob) mob.setRemoveWhenFarAway(false);
        engine.loadouts().assign(m.getUniqueId(), character);
        var max = m.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double health = Math.max(1, engine.stats().maxHealth(m.getUniqueId()) / damageScale.getAsDouble());
        if (max != null) max.setBaseValue(health);
        m.setHealth(health);
        var size = m.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        if (size != null && def.stats().scale() != 1) size.setBaseValue(def.stats().scale());
        if (team != null) team.addEntity(m);
        if (dummy && engine.ultCharge().enabled()) engine.ultCharge().set(m.getUniqueId(), 100); // its ultimate ready (to steal)
        String who = (dummy ? "A " + name + " dummy" : name) + (team != null ? " on team " + team.getName() : " (everyone's enemy)");
        if (engine.brains().drives(m.getUniqueId())) player.sendMessage(ChatColor.GREEN + who + ", driven by its brain.");
        else player.sendMessage(ChatColor.GREEN + who + ". Make it cast: /ae castas @e[type=minecraft:"
                + type.getKey().getKey() + ",limit=1,sort=nearest] <ability>");
    }

    /** Equip (or with "none", unequip) a character, telling the player. Errors go to {@code errorsTo} when set. */
    private boolean equip(Player player, String character, CommandSender errorsTo) {
        if (character.equalsIgnoreCase("none")) {
            hud.unequip(player);
            player.sendMessage(ChatColor.GREEN + "Character removed. Vanilla controls (and /ae bind items) are back.");
            return true;
        }
        if (hud.equip(player, character)) {
            player.sendMessage(ChatColor.GREEN + "Playing " + character + ". LMB fire, 1/2/3 abilities, F ultimate.");
            return true;
        }
        if (errorsTo != null) errorsTo.sendMessage(ChatColor.RED + "Unknown character " + character);
        return false;
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
        if (args.length == 3 && args[0].equalsIgnoreCase("char")) {
            String prefix = args[2].toLowerCase();
            return Stream.concat(Stream.of("@p", "@a", "@s", "@e[type=minecraft:mannequin,limit=1,sort=nearest]"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .filter(o -> o.toLowerCase().startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("dummy") || args[0].equalsIgnoreCase("mob"))) {
            return filter(engine.characters().ids().stream().sorted(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("mob")) {
            return filter(java.util.Arrays.stream(org.bukkit.entity.EntityType.values())
                    .filter(t -> t.getEntityClass() != null && LivingEntity.class.isAssignableFrom(t.getEntityClass())
                            && t != org.bukkit.entity.EntityType.PLAYER)
                    .map(t -> t.getKey().getKey()).sorted(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("mob")) {
            String prefix = args[3].toLowerCase();
            return Bukkit.getScoreboardManager().getMainScoreboard().getTeams().stream().map(org.bukkit.scoreboard.Team::getName)
                    .filter(o -> o.toLowerCase().startsWith(prefix)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("dummy")) {
            String prefix = args[2].toLowerCase();
            return Bukkit.getScoreboardManager().getMainScoreboard().getTeams().stream().map(org.bukkit.scoreboard.Team::getName)
                    .filter(o -> o.toLowerCase().startsWith(prefix)).toList();
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> options, String prefix) {
        return options.filter(o -> o.startsWith(prefix.toLowerCase())).toList();
    }
}

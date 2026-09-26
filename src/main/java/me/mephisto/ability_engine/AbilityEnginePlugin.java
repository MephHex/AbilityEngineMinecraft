package me.mephisto.ability_engine;

import me.mephisto.ability_engine.bukkit.command.AbilityCommand;
import me.mephisto.ability_engine.bukkit.data.AbilityFiles;
import me.mephisto.ability_engine.bukkit.effect.BukkitEffects;
import me.mephisto.ability_engine.bukkit.effect.DamageEffect;
import me.mephisto.ability_engine.bukkit.effect.OverflowShields;
import me.mephisto.ability_engine.bukkit.hud.CastBarHud;
import me.mephisto.ability_engine.bukkit.hud.HotbarHud;
import me.mephisto.ability_engine.bukkit.input.AbilityInputListener;
import me.mephisto.ability_engine.bukkit.input.CombatInputListener;
import me.mephisto.ability_engine.bukkit.input.InventoryLock;
import me.mephisto.ability_engine.bukkit.input.ItemBindings;
import me.mephisto.ability_engine.bukkit.input.Keybinds;
import me.mephisto.ability_engine.bukkit.input.PlayerLifecycleListener;
import me.mephisto.ability_engine.bukkit.platform.BukkitConstructRenderer;
import me.mephisto.ability_engine.bukkit.platform.BukkitCuePlayer;
import me.mephisto.ability_engine.bukkit.platform.BukkitIndicatorRenderer;
import me.mephisto.ability_engine.bukkit.platform.BukkitMovementControl;
import me.mephisto.ability_engine.bukkit.platform.BukkitProjectileRenderer;
import me.mephisto.ability_engine.bukkit.platform.BukkitWorldQuery;
import me.mephisto.ability_engine.bukkit.platform.PaperClock;
import me.mephisto.ability_engine.bukkit.platform.PaperTaskScheduler;
import me.mephisto.ability_engine.bukkit.platform.VisualEntities;
import me.mephisto.ability_engine.bukkit.status.TagBindings;
import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.data.LoadReport;
import me.mephisto.ability_engine.engine.platform.Platform;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Wiring only. Every rule lives in the engine; every Bukkit call lives in bukkit/*. */
public final class AbilityEnginePlugin extends JavaPlugin {

    private AbilityEngine engine;
    private AbilityFiles files;
    private Keybinds keybinds;
    private HotbarHud hud;
    private CastBarHud castBar;
    private DamageEffect damage;

    @Override
    public void onEnable() {
        BukkitWorldQuery worldQuery = new BukkitWorldQuery();
        BukkitConstructRenderer constructRenderer = new BukkitConstructRenderer(getLogger());
        Platform platform = new Platform(
                new PaperClock(),
                new PaperTaskScheduler(this),
                worldQuery,
                new BukkitMovementControl(),
                new BukkitProjectileRenderer(getLogger()),
                BukkitCuePlayer.withDefaults(this, getLogger()),
                new BukkitIndicatorRenderer(this),
                constructRenderer,
                getLogger());
        engine = new AbilityEngine(platform);
        worldQuery.setLog(engine.log()); // ray traces under /ae debug
        constructRenderer.setEngine(engine); // punches on constructs are reported back to the engine

        OverflowShields shields = new OverflowShields();
        shields.start(this);
        damage = BukkitEffects.registerBuiltins(engine.effects(), shields); // before loading: effects are validated at load time
        TagBindings tagBindings = TagBindings.withDefaults();
        engine.tags().addListener(tagBindings);

        saveDefaultConfig();
        keybinds = new Keybinds();
        hud = new HotbarHud(this, engine, keybinds);
        hud.start();
        files = new AbilityFiles(this, engine, getFile());
        LoadReport report = reloadAll();
        getLogger().info("Loaded " + report);
        report.errors().forEach(e -> getLogger().warning(e));

        ItemBindings bindings = new ItemBindings(this);
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new CombatInputListener(engine, keybinds, hud), this);
        pm.registerEvents(new InventoryLock(engine), this);
        pm.registerEvents(new VisualEntities(), this);
        pm.registerEvents(new me.mephisto.ability_engine.bukkit.status.BarrierGuard(engine), this);
        pm.registerEvents(constructRenderer, this);
        castBar = new CastBarHud(engine);
        pm.registerEvents(castBar, this);
        castBar.start();
        pm.registerEvents(new AbilityInputListener(engine, bindings), this); // debug: /ae bind items
        pm.registerEvents(new PlayerLifecycleListener(engine, tagBindings, hud), this);

        var cmd = getCommand("ae");
        if (cmd != null) {
            AbilityCommand executor = new AbilityCommand(engine, bindings, hud, this::reloadAll);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'ae' missing from plugin.yml");
        }

        // Players already online (plugin reloaded by a plugin manager) may carry stale modifiers/HUD items.
        for (Player p : getServer().getOnlinePlayers()) {
            tagBindings.scrub(p);
            hud.clear(p);
        }
    }

    /** config.yml + abilities.yml, then redraw everyone's HUD (kits may have changed or vanished). */
    private LoadReport reloadAll() {
        reloadConfig();
        keybinds.load(getConfig(), getLogger());
        damage.setScale(getConfig().getDouble("damage-scale", 10));
        LoadReport report = files.reload();
        for (Player p : getServer().getOnlinePlayers()) {
            if (!engine.loadouts().has(p.getUniqueId())) engine.loadouts().clear(p.getUniqueId());
            hud.render(p); // clears the HUD for players without a (valid) character
        }
        return report;
    }

    @Override
    public void onDisable() {
        if (engine == null) return;
        for (Player p : getServer().getOnlinePlayers()) hud.clear(p); // don't save HUD items to disk
        if (castBar != null) castBar.stop();                        // give players their real XP back
        engine.shutdown(); // cancels casts, removes projectiles, undoes stuns
        engine = null;
    }
}

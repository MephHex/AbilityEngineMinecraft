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
import me.mephisto.ability_engine.bukkit.input.CrossbowListener;
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
    private me.mephisto.ability_engine.bukkit.hud.BossBarHud bossBar;
    private DamageEffect damage;
    private InventoryLock inventoryLock;
    private me.mephisto.ability_engine.bukkit.status.HoverFlight hover;

    @Override
    public void onEnable() {
        BukkitWorldQuery worldQuery = new BukkitWorldQuery();
        BukkitConstructRenderer constructRenderer = new BukkitConstructRenderer(getLogger());
        var cloneSpawner = new me.mephisto.ability_engine.bukkit.platform.BukkitCloneSpawner();
        BukkitIndicatorRenderer indicators = new BukkitIndicatorRenderer(this);
        BukkitProjectileRenderer projectileRenderer = new BukkitProjectileRenderer(getLogger());
        Platform platform = new Platform(
                new PaperClock(),
                new PaperTaskScheduler(this),
                worldQuery,
                new BukkitMovementControl(),
                projectileRenderer,
                BukkitCuePlayer.withDefaults(this, getLogger()),
                indicators,
                constructRenderer,
                cloneSpawner,
                getLogger());
        engine = new AbilityEngine(platform);
        worldQuery.setLog(engine.log()); // ray traces under /ae debug
        constructRenderer.setEngine(engine); // punches on constructs are reported back to the engine

        OverflowShields shields = new OverflowShields();
        shields.start(this);
        damage = BukkitEffects.registerBuiltins(engine.effects(), shields); // before loading: effects are validated at load time
        cloneSpawner.setDamageScale(() -> damage.scale()); // a soul's health is in design HP, like damage
        projectileRenderer.setDamageScale(() -> damage.scale()); // a projectile's body too (the Chorus Shade)
        projectileRenderer.setAudience(engine::audienceOf);      // a duel in a veil: only the two see their shots
        worldQuery.setDamageScale(() -> damage.scale());   // mobs' max HP, for % max HP damage
        TagBindings tagBindings = TagBindings.withDefaults();
        engine.tags().addListener(tagBindings);

        saveDefaultConfig();
        keybinds = new Keybinds();
        hud = new HotbarHud(this, engine, keybinds);
        var statsHud = new me.mephisto.ability_engine.bukkit.hud.StatsHud(this, engine, damage);
        hud.setStats(statsHud);
        statsHud.start();
        hud.start();
        inventoryLock = new InventoryLock(engine);
        files = new AbilityFiles(this, engine, getFile());
        LoadReport report = reloadAll();
        getLogger().info("Loaded " + report);
        report.errors().forEach(e -> getLogger().warning(e));

        ItemBindings bindings = new ItemBindings(this);
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new CombatInputListener(engine, keybinds, hud), this);
        pm.registerEvents(new CrossbowListener(engine, hud), this);
        pm.registerEvents(new me.mephisto.ability_engine.bukkit.input.SneakInput(engine, hud), this);
        pm.registerEvents(worldQuery.movementTracker(), this);
        pm.registerEvents(inventoryLock, this);
        pm.registerEvents(new VisualEntities(), this);
        pm.registerEvents(new me.mephisto.ability_engine.bukkit.status.BarrierGuard(engine), this);
        pm.registerEvents(new me.mephisto.ability_engine.bukkit.status.DamageModifierListener(engine), this);
        pm.registerEvents(new me.mephisto.ability_engine.bukkit.status.FrostAndFlight(engine), this);
        pm.registerEvents(new me.mephisto.ability_engine.bukkit.status.BondPotions(engine), this);
        pm.registerEvents(new me.mephisto.ability_engine.bukkit.status.WitherGuard(engine), this);
        pm.registerEvents(projectileRenderer, this); // bodies drop nothing
        new me.mephisto.ability_engine.bukkit.status.HearingGlow(engine, this).start(); // the hearing passive's private glow
        var veilVisibility = new me.mephisto.ability_engine.bukkit.status.VeilVisibility(engine, this); // Into the Veil
        pm.registerEvents(veilVisibility, this);
        veilVisibility.start();
        var traits = new me.mephisto.ability_engine.bukkit.status.Traits(engine);
        pm.registerEvents(traits, this);
        traits.start();
        hover = new me.mephisto.ability_engine.bukkit.status.HoverFlight(engine); // the Fae's flight
        pm.registerEvents(hover, this);
        hover.start();
        var rideGuard = new me.mephisto.ability_engine.bukkit.status.RideGuard(engine);
        pm.registerEvents(rideGuard, this); // perched riders stay on, and see their mount's hearts
        rideGuard.start();
        pm.registerEvents(constructRenderer, this);
        pm.registerEvents(new me.mephisto.ability_engine.bukkit.status.DreamListeners(engine, this), this);
        castBar = new CastBarHud(engine);
        pm.registerEvents(castBar, this);
        castBar.start();
        bossBar = new me.mephisto.ability_engine.bukkit.hud.BossBarHud(engine);
        pm.registerEvents(bossBar, this);
        bossBar.start();
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
        engine.stats().setArmorConstant(getConfig().getDouble("armor-constant", 100));
        inventoryLock.load(getConfig(), getLogger());
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
        if (bossBar != null) bossBar.stop();
        if (castBar != null) castBar.stop();                        // give players their real XP back
        if (hover != null) hover.stop();
        engine.shutdown(); // cancels casts, removes projectiles, undoes stuns
        engine = null;
    }
}

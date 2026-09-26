package me.mephisto.ability_engine.bukkit.data;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Loads ability content. Normally from the plugin folder:
 * <ul>
 *   <li>every {@code .yml} under {@code content/} (subfolders too), in path order</li>
 *   <li>an old single {@code abilities.yml}, if it's still there</li>
 * </ul>
 * The jar's content/ files are copied out on a fresh install (no content/ and no abilities.yml).
 *
 * <p>Development helpers (config.yml, read again on every /ae reload):
 * <ul>
 *   <li>{@code dev.content-folder}: read content from that folder instead (e.g. your project's
 *       src/main/resources/content): edit in your IDE, /ae reload, no rebuild</li>
 *   <li>{@code dev.overwrite-content}: copy the jar's content/ over the plugin folder's on every start
 *       and /ae reload (edits made on the server are LOST)</li>
 * </ul>
 * All files are parsed first; one YAML syntax error cancels the reload and keeps what was loaded.
 */
public final class AbilityFiles {

    public static final String CONTENT_DIR = "content";
    public static final String LEGACY_FILE = "abilities.yml";

    private final Plugin plugin;
    private final AbilityEngine engine;
    private final File jar;

    /** @param jar the plugin's own jar (JavaPlugin#getFile), used to find every content file it ships */
    public AbilityFiles(Plugin plugin, AbilityEngine engine, File jar) {
        this.plugin = plugin;
        this.engine = engine;
        this.jar = jar;
    }

    public LoadReport reload() {
        String devFolder = plugin.getConfig().getString("dev.content-folder", "");
        boolean overwrite = plugin.getConfig().getBoolean("dev.overwrite-content", false);

        List<Path> files = new ArrayList<>();
        Path nameBase;
        if (devFolder != null && !devFolder.isBlank()) {
            Path dir = Path.of(devFolder);
            if (!Files.isDirectory(dir)) return failed("dev.content-folder is not a folder: " + dir.toAbsolutePath());
            plugin.getLogger().info("DEV: reading content straight from " + dir.toAbsolutePath());
            nameBase = dir;
            if (!collect(dir, files)) return failed(dir + ": could not list files");
        } else {
            File legacy = new File(plugin.getDataFolder(), LEGACY_FILE);
            File content = new File(plugin.getDataFolder(), CONTENT_DIR);
            boolean freshInstall = !legacy.exists() && !content.exists();
            if (overwrite || freshInstall) {
                List<String> shipped = shippedContent();
                for (String entry : shipped) plugin.saveResource(entry, overwrite);
                if (overwrite) plugin.getLogger().info("DEV: overwrote " + shipped.size() + " content file(s) from the jar");
            }
            nameBase = plugin.getDataFolder().toPath();
            if (legacy.exists()) files.add(legacy.toPath());
            if (content.isDirectory() && !collect(content.toPath(), files)) return failed(CONTENT_DIR + "/: could not list files");
            plugin.getLogger().info("Loading " + files.size() + " content file(s) from " + plugin.getDataFolder().getAbsolutePath());
        }

        // Parse everything before touching the registries: a typo in one file must not wipe the others.
        List<AbilityLoader.Source> sources = new ArrayList<>();
        for (Path file : files) {
            String name = nameBase.relativize(file).toString().replace('\\', '/');
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, Object> root = new Yaml().load(reader);
                sources.add(new AbilityLoader.Source(name, root));
            } catch (IOException | RuntimeException e) {
                return failed(name + ": " + e.getMessage() + " (kept the previously loaded abilities)");
            }
        }

        engine.abilities().clear();
        engine.statusDefs().clear();
        engine.characters().clear();
        LoadReport report = new AbilityLoader(engine).load(sources);
        report.setSource((devFolder != null && !devFolder.isBlank() ? "DEV folder " : "")
                + nameBase.toAbsolutePath() + " (" + files.size() + " file(s))");
        return report;
    }

    /** Every .yml under a folder, subfolders too, in path order. */
    private static boolean collect(Path dir, List<Path> into) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(f -> f.toString().endsWith(".yml")).sorted().forEach(into::add);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Every file the jar ships under content/ (so new files are picked up without a hardcoded list). */
    private List<String> shippedContent() {
        List<String> out = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            jf.stream().map(JarEntry::getName)
                    .filter(n -> n.startsWith(CONTENT_DIR + "/") && !n.endsWith("/"))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't read the plugin jar to find content files: " + e.getMessage());
        }
        return out;
    }

    private static LoadReport failed(String message) {
        LoadReport report = new LoadReport();
        report.error(message);
        return report;
    }
}

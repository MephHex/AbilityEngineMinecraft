package me.mephisto.ability_engine.engine.testkit;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.data.AbilityLoader;
import me.mephisto.ability_engine.engine.data.LoadReport;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Content for tests.
 * <ul>
 *   <li>{@link #loadClean}: a FROZEN SNAPSHOT (src/test/resources/fixtures). Mechanics tests use it,
 *       so tuning the real content (damage, speeds, radii...) never breaks them.</li>
 *   <li>{@link #realReport}: the real shipped content (src/main/resources/content). Only checked for
 *       loading cleanly, never for specific numbers.</li>
 * </ul>
 * Update a fixture only when a test is about a NEW mechanic in that content.
 */
public final class ShippedContent {

    public static final Path FIXTURES = Path.of("src/test/resources/fixtures");
    public static final Path REAL = Path.of("src/main/resources/content");

    public static List<AbilityLoader.Source> sources(Path dir) throws IOException {
        List<AbilityLoader.Source> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(f -> f.toString().endsWith(".yml")).sorted().toList()) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Map<String, Object> root = new Yaml().load(r);
                    out.add(new AbilityLoader.Source("content/" + dir.relativize(file).toString().replace('\\', '/'), root));
                }
            }
        }
        return out;
    }

    /** The frozen snapshot, failing the test on any load error. */
    public static void loadClean(AbilityEngine engine) throws IOException {
        LoadReport report = new AbilityLoader(engine).load(sources(FIXTURES));
        if (!report.isClean()) throw new AssertionError("fixture errors:\n" + String.join("\n", report.errors()));
    }

    public static LoadReport loadInto(AbilityEngine engine) throws IOException {
        return new AbilityLoader(engine).load(sources(FIXTURES));
    }

    /** The real shipped content. */
    public static LoadReport realReport(AbilityEngine engine) throws IOException {
        return new AbilityLoader(engine).load(sources(REAL));
    }

    private ShippedContent() {}
}

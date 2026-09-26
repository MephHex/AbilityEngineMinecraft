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

/** The shipped content folder, loaded the same way the plugin loads its content/ folder. */
public final class ShippedContent {

    public static final Path DIR = Path.of("src/main/resources/content");

    public static List<AbilityLoader.Source> sources() throws IOException {
        List<AbilityLoader.Source> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(DIR)) {
            for (Path file : walk.filter(f -> f.toString().endsWith(".yml")).sorted().toList()) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Map<String, Object> root = new Yaml().load(r);
                    out.add(new AbilityLoader.Source("content/" + DIR.relativize(file).toString().replace('\\', '/'), root));
                }
            }
        }
        return out;
    }

    public static LoadReport loadInto(AbilityEngine engine) throws IOException {
        return new AbilityLoader(engine).load(sources());
    }

    /** Load and fail the test on any load error. */
    public static void loadClean(AbilityEngine engine) throws IOException {
        LoadReport report = loadInto(engine);
        if (!report.isClean()) throw new AssertionError("content errors:\n" + String.join("\n", report.errors()));
    }

    private ShippedContent() {}
}

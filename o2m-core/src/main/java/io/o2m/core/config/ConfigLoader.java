package io.o2m.core.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigLoader {
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private ConfigLoader() {}

    public static AppConfig loadApp(Path path) throws IOException {
        String raw = Files.readString(path);
        raw = resolveEnv(raw);
        Yaml yaml = new Yaml(new Constructor(AppConfig.class, new LoaderOptions()));
        return yaml.load(raw);
    }

    public static TypeMappingConfig loadTypeMapping(Path path) throws IOException {
        String raw = Files.readString(path);
        Yaml yaml = new Yaml(new Constructor(TypeMappingConfig.class, new LoaderOptions()));
        return yaml.load(raw);
    }

    private static String resolveEnv(String input) {
        Matcher m = ENV_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String val = System.getenv(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : ""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static void resolvePassword(DatabaseConfig db) {
        if (db.getPassword() != null && db.getPassword().contains("${")) {
            db.setPassword(resolveEnv(db.getPassword()));
        }
    }

    public static String resolveEnvInString(String input) {
        return resolveEnv(input);
    }
}

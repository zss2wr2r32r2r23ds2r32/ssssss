package dev.sharded.velocitycore.status;

import org.slf4j.Logger;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class WhitelistPersistence {

    private final Path file;
    private final Logger logger;

    public WhitelistPersistence(Path dataDirectory, Logger logger) {
        this.file = dataDirectory.resolve("whitelist-cache.toml");
        this.logger = logger;
    }

    public Map<String, Boolean> load() {
        Map<String, Boolean> loaded = new HashMap<>();
        if (!Files.exists(file)) {
            return loaded;
        }
        try {
            TomlParseResult parsed = Toml.parse(file);
            for (String key : parsed.keySet()) {
                Boolean value = parsed.getBoolean(key);
                if (value != null) {
                    loaded.put(key.toLowerCase(Locale.ROOT), value);
                }
            }
        } catch (IOException exception) {
            logger.warn("Failed to read whitelist-cache.toml", exception);
        }
        return loaded;
    }

    public void save(Map<String, Boolean> states) {
        try {
            StringBuilder builder = new StringBuilder();
            builder.append("# Cached whitelist states from backend servers\n");
            for (Map.Entry<String, Boolean> entry : states.entrySet()) {
                builder.append(entry.getKey())
                        .append(" = ")
                        .append(entry.getValue())
                        .append('\n');
            }
            Files.writeString(file, builder.toString());
        } catch (IOException exception) {
            logger.warn("Failed to save whitelist-cache.toml", exception);
        }
    }
}

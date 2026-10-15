package dev.sharded.core.message;

import dev.sharded.core.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;
    private final Map<String, String> values = new HashMap<>();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResource("messages.yml", false);
        File file = new File(plugin.getDataFolder(), "messages.yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        values.clear();
        flatten("", yaml);
    }

    public String raw(String key) {
        String n = key.toLowerCase(Locale.ROOT);
        String byDot = values.get(n.replace('/', '.'));
        if (byDot != null) {
            return byDot;
        }
        return values.get(n.replace('.', '/'));
    }

    public Component get(String key, String... replacements) {
        String template = raw(key);
        if (template == null) {
            template = "&#FF0000&lSHARDED &7▷ &f" + key.replace('/', ' ');
        }
        String prefix = raw("prefix");
        if (prefix != null) {
            template = template.replace("{prefix}", prefix);
        }
        template = ColorUtil.apply(template, replacements);
        return ColorUtil.parse(template);
    }

    private void flatten(String path, ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            String next = path.isEmpty() ? key : path + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                flatten(next, child);
            } else if (value != null) {
                values.put(next.toLowerCase(Locale.ROOT), String.valueOf(value));
                values.put(next.toLowerCase(Locale.ROOT).replace('.', '/'), String.valueOf(value));
            }
        }
    }
}

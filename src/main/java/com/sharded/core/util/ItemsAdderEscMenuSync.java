package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;

/** Writes escape-menu lang overrides into ItemsAdder contents (no extra resource pack prompt). */
public final class ItemsAdderEscMenuSync {

    private ItemsAdderEscMenuSync() {
    }

    public static void install(ShardedCore plugin, YamlConfiguration config) {
        if (!config.getBoolean("itemsadder-escape-menu.enabled", true)) return;
        if (!config.getBoolean("itemsadder-escape-menu.auto-install", true)) return;
        if (!ItemsAdderHook.isAvailable()) {
            plugin.getLogger().info("[client] ItemsAdder not found — skip escape menu lang install");
            return;
        }

        String disconnect = ColorUtil.normalize(config.getString(
                "itemsadder-escape-menu.disconnect-text",
                "&x&A&D&4&E&F&FDisconnect from ShardedMC"));
        String namespace = config.getString("itemsadder-escape-menu.namespace", "shardedcore");
        String blockId = config.getString("itemsadder-escape-menu.block-id", "shardedcore_esc_menu");

        String yaml = """
                info:
                  namespace: %s
                
                minecraft_lang_overwrite:
                  %s:
                    entries:
                      "menu.disconnect": "%s"
                    languages:
                      - ALL
                """.formatted(namespace, blockId, escapeYaml(disconnect));

        File target = resolveInstallFile(plugin, config.getString(
                "itemsadder-escape-menu.install-path",
                "plugins/ItemsAdder/contents/shardedcore/configs/esc_menu.yml"));

        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            byte[] next = yaml.getBytes(StandardCharsets.UTF_8);
            if (target.exists()) {
                byte[] current = Files.readAllBytes(target.toPath());
                if (java.util.Arrays.equals(current, next)) {
                    plugin.getLogger().info("[client] ItemsAdder escape menu config already up to date");
                    return;
                }
            }

            Files.write(target.toPath(), next);
            plugin.getLogger().info("[client] Installed ItemsAdder escape menu config: " + target.getPath());

            if (config.getBoolean("itemsadder-escape-menu.reload-on-install", false)) {
                String reload = config.getString("itemsadder-escape-menu.reload-command", "iareload");
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reload), 40L);
                plugin.getLogger().info("[client] Dispatched /" + reload + " for ItemsAdder");
            } else {
                plugin.getLogger().info("[client] Run /iareload (or /iazip) once to apply the escape menu text");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[client] Could not write ItemsAdder config: " + e.getMessage());
        }
    }

    private static File resolveInstallFile(ShardedCore plugin, String path) {
        File raw = new File(path);
        if (raw.isAbsolute()) return raw;
        File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
        return new File(serverRoot, path.replace('/', File.separatorChar));
    }

    private static String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

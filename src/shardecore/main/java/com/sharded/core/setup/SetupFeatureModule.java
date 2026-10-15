package com.sharded.core.setup;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import java.io.File;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Compatibility base for modules ported from SetupCore. */
public abstract class SetupFeatureModule extends Module {
    protected File folder;

    protected SetupFeatureModule(ShardedCore plugin, String id) {
        super(plugin, id);
    }

    protected void loadFiles() {
        this.folder = this.moduleFolder();
        if (this.config == null) {
            this.loadConfigs();
        }
    }

    public String msg(String path) {
        return this.messages == null ? "" : this.messages.getString(path, "");
    }

    public String msg(String path, String def) {
        return this.messages == null ? def : this.messages.getString(path, def);
    }

    public String prefix() {
        if (this.messages != null && this.messages.isString("prefix")) {
            return this.messages.getString("prefix");
        }
        return this.config != null ? this.config.getString("prefix", "") : "";
    }

    protected void saveConfig() {
        try {
            File file = new File(this.moduleFolder(), "config.yml");
            if (this.config != null) {
                this.config.save(file);
            }
        } catch (Exception ignored) {
        }
    }

    protected void migrateConfigLists(String versionKey, String... listKeys) {
        FileConfiguration bundled = this.loadBundledConfig();
        if (bundled == null || this.config == null) {
            return;
        }
        int jarVersion = bundled.getInt(versionKey, 0);
        int liveVersion = this.config.getInt(versionKey, 0);
        if (liveVersion >= jarVersion) {
            return;
        }
        for (String key : listKeys) {
            if (bundled.contains(key)) {
                this.config.set(key, bundled.get(key));
            }
        }
        this.config.set(versionKey, jarVersion);
        this.saveConfig();
        this.plugin.getLogger().info("Migrated " + this.id() + " config lists to " + versionKey + "=" + jarVersion);
    }

    protected boolean hasSetupPerm(CommandSender sender, String setupcoreNode) {
        if (sender == null || setupcoreNode == null || setupcoreNode.isBlank()) {
            return false;
        }
        String sharded = setupcoreNode.startsWith("setupcore.")
            ? "sharded." + setupcoreNode.substring("setupcore.".length())
            : setupcoreNode;
        return sender.hasPermission(setupcoreNode) || sender.hasPermission(sharded);
    }

    protected FileConfiguration loadBundledConfig() {
        try (var stream = this.plugin.getResource(this.jarResourcePath("config.yml"))) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }
}

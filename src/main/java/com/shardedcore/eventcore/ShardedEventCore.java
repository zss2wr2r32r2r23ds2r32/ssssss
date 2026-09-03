package com.shardedcore.eventcore;

import com.shardedcore.eventcore.command.AnnounceCommand;
import com.shardedcore.eventcore.command.CountdownCommand;
import com.shardedcore.eventcore.command.EndCommand;
import com.shardedcore.eventcore.command.KitCommand;
import com.shardedcore.eventcore.command.ModuleCommand;
import com.shardedcore.eventcore.command.RootCommand;
import com.shardedcore.eventcore.command.SetSpawnCommand;
import com.shardedcore.eventcore.command.SettingsCommand;
import com.shardedcore.eventcore.command.SpawnCommand;
import com.shardedcore.eventcore.command.StartCommand;
import com.shardedcore.eventcore.config.ConfigFile;
import com.shardedcore.eventcore.config.Messages;
import com.shardedcore.eventcore.event.EventState;
import com.shardedcore.eventcore.gui.GuiListener;
import com.shardedcore.eventcore.gui.GuiManager;
import com.shardedcore.eventcore.module.ModuleManager;
import com.shardedcore.eventcore.modules.AnnounceModule;
import com.shardedcore.eventcore.modules.BedrockDropModule;
import com.shardedcore.eventcore.modules.ClearBlocksModule;
import com.shardedcore.eventcore.modules.CountdownModule;
import com.shardedcore.eventcore.modules.DeathModule;
import com.shardedcore.eventcore.modules.GameModule;
import com.shardedcore.eventcore.modules.KitModule;
import com.shardedcore.eventcore.modules.PlaceholderModule;
import com.shardedcore.eventcore.modules.ProtectionModule;
import com.shardedcore.eventcore.modules.SettingsModule;
import com.shardedcore.eventcore.modules.SpawnModule;
import com.shardedcore.eventcore.modules.SupplyDropModule;
import com.shardedcore.eventcore.modules.WorldBorderModule;
import com.shardedcore.eventcore.service.ChatPromptService;
import com.shardedcore.eventcore.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShardedEventCore extends JavaPlugin {

    private ConfigFile mainConfig;
    private ConfigFile settingsConfig;
    private ConfigFile dataFile;
    private Messages messages;
    private EventState state;
    private ModuleManager modules;
    private GuiManager guis;
    private ChatPromptService prompts;

    private NamespacedKey deathHeadKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        deathHeadKey = new NamespacedKey(this, "death_head_owner");

        mainConfig = new ConfigFile(this, "config.yml");
        settingsConfig = new ConfigFile(this, "settings.yml");
        dataFile = new ConfigFile(this, "data.yml");
        messages = new Messages(new ConfigFile(this, "messages.yml"));

        state = new EventState(this, dataFile);
        modules = new ModuleManager(this);
        guis = new GuiManager(this);
        prompts = new ChatPromptService(this);

        registerModules();
        modules.startAll();

        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(prompts, this);

        registerCommands();

        getLogger().info("ShardedEventCore enabled (" + modules.all().stream()
                .filter(module -> module.isEnabled()).count() + "/" + modules.all().size() + " modules active).");
    }

    @Override
    public void onDisable() {
        if (modules != null) {
            modules.stopAll();
        }
        if (state != null) {
            state.flush();
        }
        Text.invalidateCache();
    }

    private void registerModules() {
        modules.register(new AnnounceModule(this));
        modules.register(new CountdownModule(this));
        modules.register(new SpawnModule(this));
        modules.register(new KitModule(this));
        modules.register(new ProtectionModule(this));
        modules.register(new WorldBorderModule(this));
        modules.register(new BedrockDropModule(this));
        modules.register(new ClearBlocksModule(this));
        modules.register(new SupplyDropModule(this));
        modules.register(new DeathModule(this));
        modules.register(new GameModule(this));
        modules.register(new SettingsModule(this));
        modules.register(new PlaceholderModule(this));
    }

    private void registerCommands() {
        bind("shardedeventcore", new RootCommand(this));
        bind("module", new ModuleCommand(this));
        bind("announce", new AnnounceCommand(this));
        bind("countdown", new CountdownCommand(this));
        bind("setspawn", new SetSpawnCommand(this));
        bind("spawn", new SpawnCommand(this));
        bind("settings", new SettingsCommand(this));
        bind("kit", new KitCommand(this));
        bind("start", new StartCommand(this));
        bind("end", new EndCommand(this));
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    /** Re-reads every configuration file and rebuilds cached UI. */
    public void reloadEverything() {
        reloadConfig();
        mainConfig.reload();
        settingsConfig.reload();
        dataFile.reload();
        messages.reload();
        Text.invalidateCache();
        state.load();
        modules.reloadAll();
        guis.invalidateAll();
    }

    public ConfigFile mainConfig() {
        return mainConfig;
    }

    public ConfigFile settingsConfig() {
        return settingsConfig;
    }

    public Messages messages() {
        return messages;
    }

    public EventState state() {
        return state;
    }

    public ModuleManager modules() {
        return modules;
    }

    public GuiManager guis() {
        return guis;
    }

    public ChatPromptService prompts() {
        return prompts;
    }

    public NamespacedKey deathHeadKey() {
        return deathHeadKey;
    }
}

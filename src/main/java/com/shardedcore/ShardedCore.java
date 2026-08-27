package com.shardedcore;

import com.shardedcore.command.CoreCommand;
import com.shardedcore.command.DisabledCommand;
import com.shardedcore.data.Toggles;
import com.shardedcore.gui.GuiButtons;
import com.shardedcore.gui.Menus;
import com.shardedcore.hook.CoreExpansion;
import com.shardedcore.module.Module;
import com.shardedcore.module.ModuleManager;
import com.shardedcore.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShardedCore extends JavaPlugin {

    private static ShardedCore instance;
    private ModuleManager modules;
    private Toggles toggles;
    private Menus menus;
    private DisabledCommand disabledCommands;
    private CoreExpansion expansion;
    private final Map<String, String> declaredPermissions = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        GuiButtons.load(this);
        disabledCommands = new DisabledCommand(this);
        snapshotCommandPermissions();
        bindAllDisabled();

        toggles = new Toggles(this);
        toggles.init();
        menus = new Menus(this);
        menus.register();

        PluginCommand core = getCommand("shardedcore");
        CoreCommand executor = new CoreCommand(this);
        if (core != null) {
            core.setExecutor(executor);
            core.setTabCompleter(executor);
        }
        PluginCommand modulesCommand = getCommand("modules");
        if (modulesCommand != null) {
            modulesCommand.setExecutor((sender, command, label, args) -> {
                if (!sender.hasPermission("shardedcore.admin")) {
                    sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                            .deserialize("§cNo permission."));
                    return true;
                }
                if (sender instanceof Player player) {
                    modules.openGui(player, 0);
                } else {
                    sender.sendMessage("Use /shardedcore modules");
                }
                return true;
            });
        }

        modules = new ModuleManager(this);
        modules.loadAll();
        registerExpansion();
        refreshCommands();
        getLogger().info("ShardedCore enabled.");
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        if (modules != null) modules.disableAll();
        if (toggles != null) toggles.close();
        instance = null;
        getLogger().info("ShardedCore disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        GuiButtons.load(this);
        if (modules != null) modules.loadAll();
        refreshCommands();
    }

    /**
     * Capture plugin.yml permissions before any module is marked disabled.
     * If we snapshot later, {@link #bindAllDisabled()} poisons the value and
     * enabled commands stay hidden from Brigadier tab-complete.
     */
    private void snapshotCommandPermissions() {
        declaredPermissions.clear();
        Map<String, Map<String, Object>> commands = getDescription().getCommands();
        if (commands == null) return;
        for (Map.Entry<String, Map<String, Object>> entry : commands.entrySet()) {
            Map<String, Object> data = entry.getValue();
            String permission = null;
            if (data != null && data.get("permission") != null) {
                String value = String.valueOf(data.get("permission")).trim();
                if (!value.isEmpty() && !Module.DISABLED_PERMISSION.equals(value)) {
                    permission = value;
                }
            }
            rememberPermission(entry.getKey(), permission);
            if (data != null && data.get("aliases") != null) {
                for (String alias : aliasesOf(data.get("aliases"))) {
                    rememberPermission(alias, permission);
                }
            }
        }
        getLogger().info("Captured " + declaredPermissions.size() + " command permission entries from plugin.yml.");
    }

    private void rememberPermission(String name, String permission) {
        if (name == null || name.isBlank()) return;
        declaredPermissions.put(name.toLowerCase(Locale.ROOT), permission);
    }

    private static List<String> aliasesOf(Object aliases) {
        List<String> out = new ArrayList<>();
        if (aliases instanceof Iterable<?> iterable) {
            for (Object alias : iterable) {
                if (alias != null) out.add(alias.toString().trim());
            }
        } else if (aliases != null) {
            for (String part : aliases.toString().split(",")) {
                String alias = part.trim();
                if (!alias.isEmpty()) out.add(alias);
            }
        }
        return out;
    }

    public String declaredPermission(String commandName) {
        if (commandName == null) return null;
        return declaredPermissions.get(commandName.toLowerCase(Locale.ROOT));
    }

    public void restoreCommandPermission(PluginCommand command) {
        String permission = declaredPermission(command.getName());
        if (permission == null) {
            permission = declaredPermission(command.getLabel());
        }
        if (Module.DISABLED_PERMISSION.equals(permission)) {
            permission = null;
        }
        command.setPermission(permission);
        command.permissionMessage(null);
    }

    public void hideCommand(PluginCommand command) {
        command.setPermission(Module.DISABLED_PERMISSION);
        String raw = getConfig().getString("disabled-command",
                "&#FF0000&lERROR &8▷ &fThat module is currently &#FF0000disabled&f.");
        command.permissionMessage(ColorUtil.parse(raw.replace("%command%", command.getName())));
        command.setExecutor(disabledCommands);
        command.setTabCompleter(disabledCommands);
    }

    public void refreshCommands() {
        try {
            Bukkit.getServer().getClass().getMethod("syncCommands").invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException ignored) {
            // CraftServer.syncCommands is present on Paper; fall back to client refresh only.
        }
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }

    private void bindAllDisabled() {
        if (getDescription().getCommands() == null) return;
        for (String name : getDescription().getCommands().keySet()) {
            if (name.equals("shardedcore") || name.equals("modules")) continue;
            PluginCommand command = getCommand(name);
            if (command == null) continue;
            hideCommand(command);
        }
    }

    private void registerExpansion() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        expansion = new CoreExpansion(this);
        expansion.register();
    }

    public static ShardedCore get() {
        return instance;
    }

    public ModuleManager modules() {
        return modules;
    }

    public Toggles toggles() {
        return toggles;
    }

    public Menus menus() {
        return menus;
    }

    public DisabledCommand disabledCommands() {
        return disabledCommands;
    }

    public boolean moduleEnabled(String id) {
        return getConfig().getBoolean("modules." + id, true);
    }

    public String prefix() {
        return getConfig().getString("prefix", "&#00A2FF&lCORE &8▷ &r");
    }
}

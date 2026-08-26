package com.shardedcore.modules.commands.guide;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ConfigSync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class GuideModule extends Module implements CommandExecutor, TabCompleter {

    public GuideModule(ShardedCore plugin) {
        super(plugin, "guide");
    }

    @Override
    public void enable() {
        loadMenus();
        Function<Player, Map<String, String>> placeholders = player -> Map.of(
                "discord", config.getString("discord", "discord.gg/shardedmc"),
                "webstore", config.getString("webstore", "store.shardedmc.com"));
        for (String menuId : new String[]{"guide", "guide_rules", "guide_server_rules", "guide_chat_rules"}) {
            plugin.gui().registerMenuExtras(menuId, placeholders);
        }
        registerCommand("guide", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    private void loadMenus() {
        for (String name : new String[]{"guide", "guide_rules", "guide_server_rules", "guide_chat_rules"}) {
            File file = new File(moduleFolder, name + ".yml");
            ConfigSync.sync(plugin, file, "modules/guide/" + name + ".yml");
            plugin.gui().loadMenu(file, name);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.command.guide") && !player.hasPermission("sharded.command.guide")) {
            send(player, "no-permission");
            return true;
        }
        plugin.gui().open(player, "guide");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}

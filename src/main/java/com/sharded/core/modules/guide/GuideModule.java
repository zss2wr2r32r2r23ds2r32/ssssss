package com.sharded.core.modules.guide;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ConfigSync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.function.Function;

/** Server guide and rules menus. */
public final class GuideModule extends Module implements CommandExecutor, org.bukkit.command.TabCompleter {

    public GuideModule(ShardedCore plugin) {
        super(plugin, "guide");
    }

    @Override
    protected void onEnable() {
        loadMenus();
        Function<org.bukkit.entity.Player, Map<String, String>> placeholders = player -> Map.of(
                "discord", config.getString("discord", "discord.gg/shardedmc"),
                "webstore", config.getString("webstore", "store.shardedmc.com"));
        for (String menuId : new String[]{"guide", "guide_rules", "guide_server_rules", "guide_chat_rules"}) {
            plugin.gui().registerMenuExtras(menuId, placeholders);
        }
        registerCommand("guide", this);
        registerCommand("rules", this);
        registerCommand("discord", this);
        registerCommand("store", this);
    }

    private void loadMenus() {
        File folder = moduleFolder();
        for (String name : new String[]{"guide", "guide_rules", "guide_server_rules", "guide_chat_rules"}) {
            File file = new File(folder, name + ".yml");
            ConfigSync.sync(plugin, file, "modules/guide/" + name + ".yml");
            plugin.gui().loadMenu(file, name);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("discord")) {
            send(sender, "discord-link", "%discord%", config.getString("discord", "discord.gg/shardedmc"));
            return true;
        }
        if (cmd.equals("store")) {
            send(sender, "store-link", "%webstore%", config.getString("webstore", "store.shardedmc.com"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (cmd.equals("rules")) {
            plugin.gui().open(player, "guide_rules");
            return true;
        }
        plugin.gui().open(player, "guide");
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return java.util.List.of();
    }
}

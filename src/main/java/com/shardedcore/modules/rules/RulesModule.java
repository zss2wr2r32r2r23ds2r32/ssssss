package com.shardedcore.modules.rules;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class RulesModule extends Module implements CommandExecutor {

    public RulesModule(ShardedCore plugin) {
        super(plugin, "rules");
    }

    @Override
    public void enable() {
        registerCommand("rules", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sendRaw(sender, "&#FF0000&lERROR &7▷ &fOnly a player can open the rules menu.");
            return true;
        }
        Menus.Menu menu = plugin.menus().create(player, cfg("menu.title", "&8Rules | Dashboard"), config.getInt("menu.rows", 3));
        ConfigurationSection categories = config.getConfigurationSection("categories");
        if (categories != null) {
            for (String id : categories.getKeys(false)) {
                ConfigurationSection category = categories.getConfigurationSection(id);
                if (category == null || !category.getBoolean("enabled", true)) continue;
                menu.set(category.getInt("slot", 0), Items.fromSection(category, player));
            }
        }
        if (config.getBoolean("frame.enabled", false)) {
            menu.fill(Items.fromSection(config.getConfigurationSection("frame"), player));
        }
        plugin.menus().open(player, menu);
        Sounds.play(player, config.getConfigurationSection("menu.open-sound"));
        return true;
    }
}

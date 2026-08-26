package com.shardedcore.modules.commands;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.Sounds;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class CommandsModule extends Module implements CommandExecutor {

    public CommandsModule(ShardedCore plugin) {
        super(plugin, "commands");
    }

    @Override
    public void enable() {
        registerCommand("discord", this);
        registerCommand("store", this);
        registerCommand("apply", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String key = switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "store", "webstore" -> "Store";
            case "apply" -> "Apply";
            default -> "Discord";
        };
        ConfigurationSection section = config.getConfigurationSection(key);
        if (section == null) {
            sendRaw(sender, "&#FF0000&lERROR &7▷ &fMissing " + key + " in commands/config.yml");
            return true;
        }
        sendLines(sender, section.getStringList("message"), section.getString("url", ""));
        if (sender instanceof Player player) {
            Sounds.play(player, section.getString("sound", ""), 1f, 1.2f);
        }
        return true;
    }
}

package com.shardedcore.modules.guide;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

public final class GuideModule extends Module implements CommandExecutor {

    public GuideModule(ShardedCore plugin) {
        super(plugin, "guide");
    }

    @Override
    public void enable() {
        registerCommand("guide", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "messages.players-only");
            return true;
        }
        ConfigurationSection entries = config.getConfigurationSection("entries");
        if (entries == null || entries.getKeys(false).isEmpty()) {
            send(player, "messages.empty");
            return true;
        }
        Sounds.play(player, config.getConfigurationSection("sounds.open"));
        Menus.Menu menu = plugin.menus().create(player, cfg("menu.title", "&8Guide"), config.getInt("menu.rows", 4));
        if (config.getBoolean("menu.filler.enabled", false)) {
            menu.fill(Items.named(Sounds.material(cfg("menu.filler.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                    cfg("menu.filler.name", " "), List.of()));
        }
        for (String id : entries.getKeys(false)) {
            ConfigurationSection entry = entries.getConfigurationSection(id);
            if (entry == null || !entry.getBoolean("enabled", true)) continue;
            String permission = entry.getString("permission", "");
            if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) continue;
            int slot = entry.getInt("slot", 0);
            menu.set(slot, Items.fromSection(entry, player), event -> {
                event.setCancelled(true);
                Sounds.play(player, config.getConfigurationSection("sounds.click"));
                String cmd = entry.getString("command", "");
                if (config.getBoolean("close-on-click", true)) player.closeInventory();
                if (cmd != null && !cmd.isBlank()) player.performCommand(cmd);
            });
        }
        plugin.menus().open(player, menu);
        return true;
    }
}

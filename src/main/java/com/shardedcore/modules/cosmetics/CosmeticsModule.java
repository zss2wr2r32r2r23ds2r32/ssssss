package com.shardedcore.modules.cosmetics;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.GuiButtons;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.chatcolor.ChatColorModule;
import com.shardedcore.modules.glows.GlowsModule;
import com.shardedcore.modules.tags.TagsModule;
import com.shardedcore.util.Items;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class CosmeticsModule extends Module implements CommandExecutor {

    public CosmeticsModule(ShardedCore plugin) {
        super(plugin, "cosmetics");
    }

    @Override
    public void enable() {
        registerCommand("cosmetics", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        int rows = Math.max(3, Math.min(6, config.getInt("menu.rows", 3)));
        Menus.Menu menu = plugin.menus().create(player, cfg("menu.title", "Cosmetics"), rows);
        ConfigurationSection entries = config.getConfigurationSection("entries");
        if (entries != null) {
            for (String id : entries.getKeys(false)) {
                ConfigurationSection entry = entries.getConfigurationSection(id);
                if (entry == null || !entry.getBoolean("enabled", true)) continue;
                menu.set(entry.getInt("slot", 0), Items.fromSection(entry, player), event -> {
                    event.setCancelled(true);
                    GuiButtons.play(player, "click");
                    openKind(player, id);
                });
            }
        }
        if (config.getBoolean("menu.filler.enabled", true)) {
            GuiButtons.glass(menu, config.getBoolean("menu.filler.border-only", false));
        }
        plugin.menus().open(player, menu);
        GuiButtons.play(player, "open");
    }

    private void openKind(Player player, String id) {
                        switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "tags", "tag" -> {
                TagsModule tags = plugin.modules().get(TagsModule.class);
                if (tags == null) {
                    send(player, "unavailable");
                    return;
                }
                tags.open(player);
            }
            case "chatcolors", "chatcolor", "colors" -> {
                ChatColorModule colors = plugin.modules().get(ChatColorModule.class);
                if (colors == null) {
                    send(player, "unavailable");
                    return;
                }
                colors.open(player);
            }
            case "glows", "glow" -> {
                GlowsModule glows = plugin.modules().get(GlowsModule.class);
                if (glows == null) {
                    send(player, "unavailable");
                    return;
                }
                glows.open(player);
            }
            default -> send(player, "unavailable");
        }
    }
}

package com.shardedcore.modules.commands;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandsModule extends Module implements CommandExecutor, Listener {

    private volatile Set<String> allowed = Set.of();

    public CommandsModule(ShardedCore plugin) {
        super(plugin, "commands");
    }

    @Override
    public void enable() {
        rebuildWhitelist();
        registerCommand("discord", this);
        registerCommand("store", this);
        registerCommand("apply", this);
        registerCommand("media", this);
        registerListener(this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        rebuildWhitelist();
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }

    private void rebuildWhitelist() {
        Set<String> next = new HashSet<>();
        for (String name : config.getStringList("whitelist.commands")) {
            if (name == null || name.isBlank()) continue;
            String lower = name.toLowerCase(Locale.ROOT).trim();
            if (lower.startsWith("/")) lower = lower.substring(1);
            if (lower.contains(":")) lower = lower.substring(lower.indexOf(':') + 1);
            next.add(lower);
        }
        allowed = next;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("media")) {
            if (!(sender instanceof Player player)) {
                show(sender, "Media");
                return true;
            }
            if (config.getBoolean("media.menu.enabled", true)) {
                openMedia(player);
            } else {
                show(player, "Media");
            }
            return true;
        }
        String key = switch (name) {
            case "store", "webstore" -> "Store";
            case "apply" -> "Apply";
            default -> "Discord";
        };
        show(sender, key);
        return true;
    }

    private void show(CommandSender sender, String key) {
        ConfigurationSection section = section(key);
        if (section == null) {
            sendRaw(sender, "&#FF0000&lERROR &7▷ &fMissing " + key + " in commands/config.yml");
            return;
        }
        sendLines(sender, section.getStringList("message"), section.getString("url", ""));
        if (sender instanceof Player player) {
            Sounds.play(player, section.getString("sound", ""), 1f, 1.2f);
        }
    }

    private ConfigurationSection section(String name) {
        if (config.isConfigurationSection(name)) return config.getConfigurationSection(name);
        for (String key : config.getKeys(false)) {
            if (key.equalsIgnoreCase(name)) return config.getConfigurationSection(key);
        }
        return null;
    }

    private void openMedia(Player player) {
        ConfigurationSection menu = config.getConfigurationSection("media.menu");
        if (menu == null) {
            show(player, "Media");
            return;
        }
        if (!player.hasPermission(menu.getString("open_permission", "shardedcore.command.media"))) {
            sendRaw(player, cfg("whitelist.unknown", "&#00A2FF&lCORE &8▷ &fYou do not have Permission."));
            return;
        }
        Menus.Menu gui = plugin.menus().create(player, menu.getString("menu_title", menu.getString("title", "&8Media")),
                Math.max(1, menu.getInt("size", 27) / 9));
        ConfigurationSection items = menu.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(id);
                if (item == null) continue;
                ItemStack stack = Items.fromSection(item, player);
                if (id.equals("filler") || item.isList("slots")) {
                    for (int slot : item.getIntegerList("slots")) {
                        gui.set(slot, stack);
                    }
                    continue;
                }
                int slot = item.getInt("slot", 0);
                List<String> clicks = item.getStringList("left_click_commands");
                gui.set(slot, stack, event -> {
                    event.setCancelled(true);
                    player.closeInventory();
                    runClicks(player, clicks);
                });
            }
        }
        plugin.menus().open(player, gui);
        Sounds.play(player, menu.getString("open-sound", "block.note_block.pling"), 0.7f, 1.2f);
    }

    private void runClicks(Player player, List<String> clicks) {
        if (clicks == null || clicks.isEmpty()) {
            show(player, "Media");
            return;
        }
        for (String raw : clicks) {
            if (raw == null || raw.isBlank()) continue;
            String line = raw.trim();
            if (showsMedia(line)) {
                show(player, "Media");
                continue;
            }
            if (line.startsWith("[player] ")) {
                player.performCommand(line.substring(9));
            } else if (line.startsWith("[console] ")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.substring(10).replace("%player%", player.getName()));
            } else if (line.startsWith("[message] ")) {
                sendRaw(player, line.substring(10));
            } else {
                show(player, "Media");
            }
        }
    }

    private boolean showsMedia(String line) {
        String value = line;
        if (value.startsWith("[player] ") || value.startsWith("[message] ")) {
            value = value.substring(value.indexOf(' ') + 1).trim();
        }
        return value.equalsIgnoreCase("discord")
                || value.equalsIgnoreCase("media")
                || value.equalsIgnoreCase("show-media")
                || value.equalsIgnoreCase("show_media");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSend(PlayerCommandSendEvent event) {
        if (!config.getBoolean("whitelist.enabled", true)) return;
        if (event.getPlayer().hasPermission(cfg("whitelist.bypass-permission", "shardedcore.command.bypass"))) return;
        Set<String> allow = allowed;
        if (allow.isEmpty()) return;
        event.getCommands().removeIf(command -> {
            String name = command.toLowerCase(Locale.ROOT);
            if (name.contains(":")) return true;
            return !allow.contains(name);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUnknown(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("whitelist.enabled", true)) return;
        Player player = event.getPlayer();
        if (player.hasPermission(cfg("whitelist.bypass-permission", "shardedcore.command.bypass"))) return;
        Set<String> allow = allowed;
        if (allow.isEmpty()) return;
        String raw = event.getMessage();
        if (raw.length() < 2 || raw.charAt(0) != '/') return;
        String name = raw.substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);
        Command command = Bukkit.getCommandMap().getCommand(name);
        if (command != null) return;
        event.setCancelled(true);
        player.sendActionBar(ColorUtil.parse(cfg("whitelist.unknown",
                "&#00A2FF&lCORE &8▷ &fYou do not have Permission.")));
        Sounds.play(player, config.getConfigurationSection("whitelist.sound"));
    }
}

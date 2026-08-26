package com.shardedcore.gui;

import com.shardedcore.ShardedCore;
import com.shardedcore.modules.settings.SettingsModule;
import com.shardedcore.util.MessageUtil;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GuiManager {

    private final ShardedCore plugin;
    private final Map<String, GuiMenu> menus = new HashMap<>();
    private final Map<String, Consumer<Player>> customActions = new HashMap<>();
    private final Map<String, Function<Player, Map<String, String>>> menuExtras = new HashMap<>();
    private String noPermissionMessage = "%prefix%&#FF2727You don't have permission.";

    public GuiManager(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void setNoPermissionMessage(String message) {
        this.noPermissionMessage = message;
    }

    public void registerAction(String id, Consumer<Player> action) {
        customActions.put(id.toLowerCase(Locale.ROOT), action);
    }

    public void registerMenuExtras(String menuId, Function<Player, Map<String, String>> provider) {
        menuExtras.put(menuId.toLowerCase(Locale.ROOT), provider);
    }

    public void unregisterActions() {
        customActions.clear();
        menuExtras.clear();
    }

    public void loadMenu(File file, String id) {
        if (!file.exists()) return;
        menus.put(id, new GuiMenu(id, YamlConfiguration.loadConfiguration(file), plugin.guiNavigation()));
    }

    public void loadFolder(File folder, String prefix) {
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (!yaml.contains("menu_title")) continue;
            String id = prefix.isEmpty() ? file.getName().replace(".yml", "") : prefix + file.getName().replace(".yml", "");
            menus.put(id, new GuiMenu(id, yaml, plugin.guiNavigation()));
        }
    }

    public void clearMenus() {
        menus.clear();
    }

    public GuiMenu menu(String menuId) {
        if (menuId == null || menuId.isBlank()) return null;
        GuiMenu menu = menus.get(menuId);
        if (menu != null) return menu;
        return menus.get(menuId.toLowerCase(Locale.ROOT));
    }

    public void open(Player player, String menuId) {
        open(player, menuId, Map.of());
    }

    public void open(Player player, String menuId, Map<String, String> extra) {
        GuiMenu menu = menu(menuId);
        if (menu == null) {
            player.sendMessage(Text.c(plugin.prefix() + "&#FF2727Unknown menu: &f" + menuId));
            return;
        }
        Map<String, String> merged = new HashMap<>();
        Function<Player, Map<String, String>> provider = menuExtras.get(menuId.toLowerCase(Locale.ROOT));
        if (provider != null) {
            Map<String, String> provided = provider.apply(player);
            if (provided != null) merged.putAll(provided);
        }
        if (extra != null) merged.putAll(extra);
        menu.open(player, this, merged);
    }

    public void handleClick(Player player, String menuId, int slot) {
        GuiMenu menu = menus.get(menuId);
        if (menu == null) return;
        GuiMenu.GuiItem item = menu.itemAt(slot);
        if (item == null) return;
        if (item.permission() != null && !item.permission().isBlank()) {
            String perm = item.permission();
            if (!perm.startsWith("sharded.") && !perm.startsWith("shardedcore.")) {
                perm = "sharded." + perm;
            }
            if (!player.hasPermission(perm)) {
                message(player, noPermissionMessage(), false);
                return;
            }
        }
        List<String> commands = item.clickCommands();
        if (commands.isEmpty()) commands = item.leftClickCommands();
        runCommands(player, menuId, commands, Map.of());
    }

    public void runCommands(Player player, String menuId, List<String> commands, Map<String, String> extra) {
        if (commands == null) return;
        for (String line : commands) {
            if (!runCommand(player, menuId, line, extra)) break;
        }
    }

    public void runCommands(Player player, List<String> commands, Map<String, String> extra) {
        runCommands(player, "", commands, extra);
    }

    private boolean runCommand(Player player, String menuId, String line, Map<String, String> extra) {
        if (line == null || line.isBlank()) return true;
        line = GuiMenu.apply(line, player, extra, this).trim();
        if (!line.startsWith("[")) return true;

        int end = line.indexOf(']');
        if (end <= 1) return true;
        String tag = line.substring(1, end).toLowerCase(Locale.ROOT);
        String payload = line.substring(end + 1).trim();

        return switch (tag) {
            case "message" -> {
                player.sendMessage(Text.c(payload));
                yield true;
            }
            case "actionbar" -> {
                player.sendActionBar(Text.cPlain(payload));
                yield true;
            }
            case "close" -> {
                player.closeInventory();
                yield true;
            }
            case "player" -> {
                player.performCommand(payload);
                yield true;
            }
            case "console" -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), GuiMenu.apply(payload, player, extra, this));
                yield true;
            }
            case "sound" -> {
                try {
                    player.playSound(player.getLocation(), Sound.valueOf(payload.toUpperCase(Locale.ROOT)), 1f, 1f);
                } catch (IllegalArgumentException ignored) {
                }
                yield true;
            }
            case "openguimenu", "opendeluxemenu" -> {
                open(player, payload);
                yield true;
            }
            case "refresh" -> {
                String target = payload.isBlank() ? menuId : payload;
                plugin.getServer().getScheduler().runTask(plugin, () -> open(player, target));
                yield true;
            }
            case "action" -> {
                Consumer<Player> action = customActions.get(payload.toLowerCase(Locale.ROOT));
                if (action != null) action.accept(player);
                yield true;
            }
            default -> {
                Consumer<Player> action = customActions.get(tag);
                if (action != null) action.accept(player);
                yield true;
            }
        };
    }

    public void message(CommandSender sender, String message, boolean actionBar) {
        String formatted = applyPlaceholders(sender instanceof Player p ? p : null, message);
        if (actionBar && sender instanceof Player player) {
            player.sendActionBar(Text.cPlain(formatted));
            return;
        }
        MessageUtil.sendRaw(sender, formatted, sender instanceof Player p ? p : null);
    }

    public String applyPlaceholders(Player player, String input) {
        if (input == null) return "";
        String out = input.replace("%prefix%", plugin.prefix());
        if (player != null) {
            SettingsModule settings = plugin.modules().get(SettingsModule.class);
            if (settings != null) {
                for (Map.Entry<String, String> entry : settings.placeholders(player).entrySet()) {
                    out = out.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
                }
            }
            out = Text.applyPlaceholders(out, player);
        }
        return out;
    }

    public String noPermissionMessage() {
        return noPermissionMessage;
    }
}

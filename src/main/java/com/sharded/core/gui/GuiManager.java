package com.sharded.core.gui;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.Prefix;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Loads and opens DeluxeMenus-style GUI configs. */
public final class GuiManager {

    private static GuiManager instance;

    private final ShardedCore plugin;
    private final Map<String, GuiMenu> menus = new HashMap<>();
    private final Map<String, Consumer<Player>> customActions = new HashMap<>();
    private String noPermissionMessage = "%prefix%&cYou don't have permission.";

    public GuiManager(ShardedCore plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static GuiManager instance() {
        return instance;
    }

    public void setNoPermissionMessage(String message) {
        this.noPermissionMessage = message;
    }

    public String noPermissionMessage() {
        return noPermissionMessage;
    }

    public void registerAction(String id, Consumer<Player> action) {
        customActions.put(id.toLowerCase(), action);
    }

    public void loadMenu(File file, String id) {
        if (!file.exists()) return;
        menus.put(id, new GuiMenu(id, YamlConfiguration.loadConfiguration(file)));
    }

    public void loadFolder(File folder) {
        menus.clear();
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (!yaml.contains("menu_title")) continue;
            String id = file.getName().replace(".yml", "");
            menus.put(id, new GuiMenu(id, yaml));
        }
    }

    public void loadResourceFolder(String resourcePath, File targetFolder) {
        targetFolder.mkdirs();
        String[] defaults = {"mainmenu", "glow", "keys", "cosmetics", "gradients", "rtp", "settings"};
        for (String name : defaults) {
            File out = new File(targetFolder, name + ".yml");
            if (!out.exists()) {
                String res = resourcePath + "/" + name + ".yml";
                if (plugin.getResource(res) != null) plugin.saveResource(res, false);
            }
        }
        loadFolder(targetFolder);
    }

    public GuiMenu menu(String id) {
        return menus.get(id);
    }

    public void open(Player player, String menuId) {
        open(player, menuId, Map.of());
    }

    public void open(Player player, String menuId, Map<String, String> extra) {
        GuiMenu menu = menus.get(menuId);
        if (menu == null) {
            player.sendMessage(Text.c(Prefix.get() + "&cUnknown menu: &f" + menuId));
            return;
        }
        menu.open(player, this, extra);
    }

    public void handleClick(Player player, String menuId, int slot) {
        GuiMenu menu = menus.get(menuId);
        if (menu == null) return;
        GuiMenu.GuiItem item = menu.itemAt(slot);
        if (item == null) return;
        List<String> commands = item.clickCommands();
        if (commands.isEmpty()) commands = item.leftClickCommands();
        runCommands(player, commands, Map.of());
    }

    public void runCommands(Player player, List<String> commands, Map<String, String> extra) {
        if (commands == null) return;
        for (String line : commands) {
            if (!runCommand(player, line, extra)) break;
        }
    }

    /** @return false to stop running further commands in the chain */
    private boolean runCommand(Player player, String line, Map<String, String> extra) {
        if (line == null || line.isBlank()) return true;
        line = GuiMenu.apply(line, player, extra).trim();
        if (!line.startsWith("[")) return true;

        int end = line.indexOf(']');
        if (end <= 1) return true;
        String tag = line.substring(1, end).toLowerCase();
        String payload = line.substring(end + 1).trim();

        return switch (tag) {
            case "message" -> {
                player.sendMessage(Text.c(payload));
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
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), GuiMenu.apply(payload, player, extra));
                yield true;
            }
            case "sound" -> {
                try {
                    player.playSound(player.getLocation(), Sound.valueOf(payload.toUpperCase()), 1f, 1f);
                } catch (IllegalArgumentException ignored) {
                }
                yield true;
            }
            case "openguimenu", "opendeluxemenu" -> {
                open(player, payload);
                yield true;
            }
            case "tokens_take" -> {
                TokenService service = plugin.modules().tokens();
                if (service == null) yield false;
                long amount = parseLong(payload, 0);
                if (amount > 0 && service.take(player.getUniqueId(), amount)) yield true;
                message(player, "%prefix%&cYou don't have enough tokens!");
                yield false;
            }
            default -> {
                Consumer<Player> action = customActions.get(tag);
                if (action != null) action.accept(player);
                yield true;
            }
        };
    }

    public void message(CommandSender sender, String message) {
        sender.sendMessage(Text.c(applyPlaceholders(sender instanceof Player p ? p : null, message)));
    }

    public String applyPlaceholders(Player player, String input) {
        if (input == null) return "";
        String out = input.replace("%prefix%", Prefix.get());
        if (player != null) {
            TokenService tokens = plugin.modules().tokens();
            if (tokens != null) {
                long bal = tokens.getBalance(player.getUniqueId());
                out = out.replace("%tokens%", String.valueOf(bal))
                        .replace("%tokens_formatted%", Numbers.format(bal))
                        .replace("%playerpoints_points%", String.valueOf(bal));
            }
        }
        return out;
    }

    private long parseLong(String raw, long def) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

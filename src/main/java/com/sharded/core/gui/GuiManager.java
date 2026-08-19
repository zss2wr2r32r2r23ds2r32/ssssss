package com.sharded.core.gui;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.Prefix;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Shared GUI engine for all modules (single listener, no cross-menu click bugs). */
public final class GuiManager {

    private final ShardedCore plugin;
    private final Map<String, GuiMenu> menus = new HashMap<>();
    private final Map<String, Consumer<Player>> customActions = new HashMap<>();
    private String noPermissionMessage = "%prefix%&cYou don't have permission.";

    public GuiManager(ShardedCore plugin) {
        this.plugin = plugin;
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

    public void unregisterActions() {
        customActions.clear();
    }

    public void loadMenu(File file, String id) {
        if (!file.exists()) return;
        menus.put(id, new GuiMenu(id, YamlConfiguration.loadConfiguration(file)));
    }

    public void loadFolder(File folder, String prefix) {
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (!yaml.contains("menu_title")) continue;
            String id = prefix.isEmpty() ? file.getName().replace(".yml", "") : prefix + file.getName().replace(".yml", "");
            menus.put(id, new GuiMenu(id, yaml));
        }
    }

    public void clearMenus() {
        menus.clear();
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

    private boolean runCommand(Player player, String line, Map<String, String> extra) {
        if (line == null || line.isBlank()) return true;
        line = GuiMenu.apply(line, player, extra, this).trim();
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
            case "actionbar" -> {
                player.sendActionBar(Text.c(payload));
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
                message(player, tokenPrefix() + "&#FF2727You don't have enough tokens!", false);
                yield false;
            }
            case "deny_if_permission" -> {
                String perm = payload.startsWith("sharded.") ? payload : payload;
                if (player.hasPermission(perm)) {
                    message(player, tokenPrefix() + "&#FF2727You already own this!", false);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    yield false;
                }
                yield true;
            }
            case "temprank_buy" -> {
                String[] parts = payload.split("\\s+");
                if (parts.length < 3) yield false;
                var module = plugin.modules().get(com.sharded.core.modules.tempranks.TempranksModule.class);
                if (module == null) yield false;
                yield module.tryPurchase(player, parts[0], (int) parseLong(parts[1], 0), parseLong(parts[2], 0));
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
            player.sendActionBar(Text.c(formatted));
        } else {
            sender.sendMessage(Text.c(formatted));
        }
    }

    public String tokenPrefix() {
        var tokens = plugin.modules().get(com.sharded.core.modules.tokens.TokensModule.class);
        return tokens == null ? Prefix.get() : tokens.tokenPrefix();
    }

    public String applyPlaceholders(Player player, String input) {
        if (input == null) return "";
        String out = input.replace("%prefix%", Prefix.get())
                .replace("%token_prefix%", tokenPrefix());
        if (player != null) {
            TokenService tokens = plugin.modules().tokens();
            if (tokens != null) {
                long bal = tokens.getBalance(player.getUniqueId());
                out = out.replace("%tokens%", String.valueOf(bal))
                        .replace("%tokens_formatted%", Numbers.format(bal))
                        .replace("%playerpoints_points%", String.valueOf(bal));
            }
        }
        return applyLeaderboardPlaceholders(player, out);
    }

    public String applyLeaderboardPlaceholders(Player player, String input) {
        if (input == null) return "";
        String out = input;
        for (int i = 1; i <= 10; i++) {
            out = out.replace("%tokens_top_" + i + "_name%", topTokenName(i))
                    .replace("%tokens_top_" + i + "_amount%", topTokenAmount(i))
                    .replace("%tokens_top_" + i + "_formatted%", topTokenFormatted(i))
                    .replace("%killstreak_top_" + i + "_name%", topKillstreakName(i))
                    .replace("%killstreak_top_" + i + "_amount%", topKillstreakAmount(i));
        }
        if (player != null) {
            var ks = plugin.modules().get(com.sharded.core.modules.killstreaks.KillstreaksModule.class);
            if (ks != null && ks.database() != null) {
                out = out.replace("%killstreak%", String.valueOf(ks.database().getCurrent(player.getUniqueId())))
                        .replace("%killstreak_best%", String.valueOf(ks.database().getBest(player.getUniqueId())));
            }
        }
        return out;
    }

    private String topTokenName(int rank) {
        var tokens = plugin.modules().get(com.sharded.core.modules.tokens.TokensModule.class);
        if (tokens == null || tokens.database() == null) return "---";
        var top = tokens.database().top(10);
        if (rank > top.size()) return "---";
        return com.sharded.core.util.OfflinePlayers.name(top.get(rank - 1).uuid());
    }

    private String topTokenAmount(int rank) {
        var tokens = plugin.modules().get(com.sharded.core.modules.tokens.TokensModule.class);
        if (tokens == null || tokens.database() == null) return "0";
        var top = tokens.database().top(10);
        if (rank > top.size()) return "0";
        return String.valueOf(top.get(rank - 1).value());
    }

    private String topTokenFormatted(int rank) {
        return Numbers.format(Long.parseLong(topTokenAmount(rank)));
    }

    private String topKillstreakName(int rank) {
        var ks = plugin.modules().get(com.sharded.core.modules.killstreaks.KillstreaksModule.class);
        if (ks == null || ks.database() == null) return "---";
        var top = ks.database().topBest(10);
        if (rank > top.size()) return "---";
        return com.sharded.core.util.OfflinePlayers.name(top.get(rank - 1).uuid());
    }

    private String topKillstreakAmount(int rank) {
        var ks = plugin.modules().get(com.sharded.core.modules.killstreaks.KillstreaksModule.class);
        if (ks == null || ks.database() == null) return "0";
        var top = ks.database().topBest(10);
        if (rank > top.size()) return "0";
        return String.valueOf(top.get(rank - 1).value());
    }

    private long parseLong(String raw, long def) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

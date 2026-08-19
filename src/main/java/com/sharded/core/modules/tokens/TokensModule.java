package com.sharded.core.modules.tokens;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.sharded.core.util.ConfigSync;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TokensModule extends Module implements CommandExecutor, TabCompleter {

    private TokenDatabase database;
    private TokenService service;

    public TokensModule(ShardedCore plugin) {
        super(plugin, "tokens");
    }

    public TokenService service() {
        return service;
    }

    public TokenDatabase database() {
        return database;
    }

    public String tokenPrefix() {
        return messagePrefix();
    }

    @Override
    protected void onEnable() {
        try {
            database = new TokenDatabase(plugin, moduleFolder());
            service = new TokenService(database);
        } catch (Exception e) {
            throw new IllegalStateException("Could not open token database", e);
        }

        plugin.gui().setNoPermissionMessage(raw("no-permission"));
        File menusFolder = new File(moduleFolder(), "menus");
        copyDefaultMenus(menusFolder);
        plugin.gui().loadFolder(menusFolder, "");

        registerCommand("bal", this);
        registerCommand("tokens", this);
        registerCommand("tokenshop", this);
    }

    private void copyDefaultMenus(File folder) {
        folder.mkdirs();
        for (String menu : List.of("mainmenu", "glow", "keys", "cosmetics", "gradients", "chatcolors", "tags", "backpack")) {
            String path = "modules/tokens/menus/" + menu + ".yml";
            ConfigSync.sync(plugin, new File(folder, menu + ".yml"), path);
        }
    }

    @Override
    protected void onDisable() {
        if (database != null) database.close();
        database = null;
        service = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "bal" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "players-only");
                    return true;
                }
                long bal = service.getBalance(player.getUniqueId());
                send(player, "balance", "%amount%", String.valueOf(bal), "%formatted%", Numbers.format(bal));
            }
            case "tokenshop" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "players-only");
                    return true;
                }
                if (!player.hasPermission("sharded.tokenshop.use")) {
                    send(player, "no-permission");
                    return true;
                }
                plugin.gui().open(player, config.getString("main-menu", "mainmenu"));
            }
            case "tokens" -> handleTokensAdmin(sender, args);
        }
        return true;
    }

    private void handleTokensAdmin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "tokens-usage");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (!sender.hasPermission("sharded.tokens.admin")) {
                    send(sender, "no-permission");
                    return;
                }
                if (args.length < 3) {
                    send(sender, "give-usage");
                    return;
                }
                OfflinePlayer target = service.resolve(args[1]);
                long amount = parseAmount(args[2]);
                service.give(target.getUniqueId(), amount);
                send(sender, "given", "%player%", name(target), "%amount%", String.valueOf(amount));
            }
            case "set" -> {
                if (!sender.hasPermission("sharded.tokens.admin")) {
                    send(sender, "no-permission");
                    return;
                }
                if (args.length < 3) {
                    send(sender, "set-usage");
                    return;
                }
                OfflinePlayer target = service.resolve(args[1]);
                long amount = parseAmount(args[2]);
                service.setBalance(target.getUniqueId(), amount);
                send(sender, "set", "%player%", name(target), "%amount%", String.valueOf(amount));
            }
            case "remove", "take" -> {
                if (!sender.hasPermission("sharded.tokens.admin")) {
                    send(sender, "no-permission");
                    return;
                }
                if (args.length < 3) {
                    send(sender, "remove-usage");
                    return;
                }
                OfflinePlayer target = service.resolve(args[1]);
                long amount = parseAmount(args[2]);
                service.take(target.getUniqueId(), amount);
                send(sender, "removed", "%player%", name(target), "%amount%", String.valueOf(amount));
            }
            case "reset" -> {
                if (!sender.hasPermission("sharded.tokens.admin")) {
                    send(sender, "no-permission");
                    return;
                }
                if (args.length < 2) {
                    send(sender, "reset-usage");
                    return;
                }
                OfflinePlayer target = service.resolve(args[1]);
                service.reset(target.getUniqueId());
                send(sender, "reset", "%player%", name(target));
            }
            case "giveall" -> {
                if (!sender.hasPermission("sharded.tokens.admin")) {
                    send(sender, "no-permission");
                    return;
                }
                if (args.length < 2) {
                    send(sender, "giveall-usage");
                    return;
                }
                long amount = parseAmount(args[1]);
                int count = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    service.give(online.getUniqueId(), amount);
                    count++;
                }
                send(sender, "giveall", "%count%", String.valueOf(count), "%amount%", String.valueOf(amount));
            }
            default -> send(sender, "tokens-usage");
        }
    }

    private long parseAmount(String raw) {
        try {
            return Math.max(0, Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String name(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("tokens") || !sender.hasPermission("sharded.tokens.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "give", "set", "remove", "take", "reset", "giveall");
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("giveall")) {
            return TabCompleteHelper.onlinePlayers(args[1]);
        }
        return List.of();
    }
}

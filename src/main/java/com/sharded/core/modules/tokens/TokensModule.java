package com.sharded.core.modules.tokens;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Prefix;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
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
        return ColorUtil.normalize(config.getString("prefix", Prefix.get()));
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

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            registerPlaceholders();
            plugin.getLogger().info("Registered PlaceholderAPI placeholders.");
        }
    }

    private void copyDefaultMenus(File folder) {
        folder.mkdirs();
        for (String menu : List.of("mainmenu", "glow", "keys", "cosmetics", "gradients", "chatcolors", "tags")) {
            File out = new File(folder, menu + ".yml");
            if (!out.exists()) {
                String path = "modules/tokens/menus/" + menu + ".yml";
                if (plugin.getResource(path) != null) plugin.saveResource(path, false);
            }
        }
    }

    private void registerPlaceholders() {
        new PlaceholderExpansion() {
            @Override
            public @NotNull String getIdentifier() {
                return "shardedcore";
            }

            @Override
            public @NotNull String getAuthor() {
                return "Sharded";
            }

            @Override
            public @NotNull String getVersion() {
                return plugin.getDescription().getVersion();
            }

            @Override
            public boolean persist() {
                return true;
            }

            @Override
            public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
                String p = params.toLowerCase(Locale.ROOT);
                if (player != null && service != null) {
                    if (p.equals("tokens") || p.equals("tokens_amount")) {
                        return String.valueOf(service.getBalance(player.getUniqueId()));
                    }
                    if (p.equals("tokens_formatted")) {
                        return Numbers.format(service.getBalance(player.getUniqueId()));
                    }
                }
                if (p.startsWith("tokens_top_")) {
                    return leaderboardValue(p.substring("tokens_top_".length()), true);
                }
                if (p.startsWith("killstreak_top_")) {
                    return leaderboardValue(p.substring("killstreak_top_".length()), false);
                }
                if (player != null) {
                    var ks = plugin.modules().get(com.sharded.core.modules.killstreaks.KillstreaksModule.class);
                    if (ks != null && ks.database() != null) {
                        if (p.equals("killstreak")) return String.valueOf(ks.database().getCurrent(player.getUniqueId()));
                        if (p.equals("killstreak_best")) return String.valueOf(ks.database().getBest(player.getUniqueId()));
                    }
                }
                return null;
            }

            private String leaderboardValue(String spec, boolean tokens) {
                String[] parts = spec.split("_", 2);
                if (parts.length == 0) return "";
                int rank;
                try {
                    rank = Integer.parseInt(parts[0]);
                } catch (NumberFormatException e) {
                    return "";
                }
                if (rank < 1 || rank > 10) return "";
                String field = parts.length > 1 ? parts[1] : "name";
                if (tokens) {
                    List<TokenDatabase.LeaderEntry> top = database.top(10);
                    if (rank > top.size()) return field.equals("amount") || field.equals("value") ? "0" : "---";
                    TokenDatabase.LeaderEntry entry = top.get(rank - 1);
                    return switch (field) {
                        case "amount", "value" -> String.valueOf(entry.value());
                        case "formatted" -> Numbers.format(entry.value());
                        default -> OfflinePlayers.name(entry.uuid());
                    };
                }
                var ks = plugin.modules().get(com.sharded.core.modules.killstreaks.KillstreaksModule.class);
                if (ks == null || ks.database() == null) return "---";
                List<com.sharded.core.modules.killstreaks.KillstreakDatabase.LeaderEntry> top = ks.database().topBest(10);
                if (rank > top.size()) return field.equals("amount") || field.equals("value") ? "0" : "---";
                var entry = top.get(rank - 1);
                return switch (field) {
                    case "amount", "value" -> String.valueOf(entry.value());
                    default -> OfflinePlayers.name(entry.uuid());
                };
            }
        }.register();
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
            return filter(List.of("give", "set", "remove", "reset", "giveall"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("giveall")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))) out.add(o);
        }
        return out;
    }
}

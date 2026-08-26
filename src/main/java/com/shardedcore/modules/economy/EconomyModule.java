package com.shardedcore.modules.economy;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.Numbers;
import com.shardedcore.util.OfflinePlayers;
import com.shardedcore.util.TabCompleteHelper;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class EconomyModule extends Module implements CommandExecutor, TabCompleter {

    private static final String PAY_TOGGLE_KEY = "paytoggle";

    private EconomyDatabase database;
    private EconomyService service;

    public EconomyModule(ShardedCore plugin) {
        super(plugin, "economy");
    }

    public EconomyService service() {
        return service;
    }

    @Override
    public void enable() {
        try {
            database = new EconomyDatabase(plugin, moduleFolder);
            service = new EconomyService(
                    plugin,
                    database,
                    config.getLong("starting-balance", 0L),
                    config.getLong("max-balance", 0L)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Could not open economy database", e);
        }

        registerCommand("ecofreeze", this);
        registerCommand("ecogive", this);
        registerCommand("ecoreset", this);
        registerCommand("ecoset", this);
        registerCommand("ecotake", this);
        registerCommand("bal", this);
        registerCommand("pay", this);
        registerCommand("baltop", this);
    }

    @Override
    public void disable() {
        if (service != null) {
            service.saveNow();
            service.close();
        }
        if (database != null) {
            database.close();
        }
        service = null;
        database = null;
        clearCommands();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "money", "balance", "bal" -> handleBalance(sender, args);
            case "pay" -> handlePay(sender, args);
            case "baltop", "moneytop" -> handleBalTop(sender, args);
            case "ecofreeze" -> handleEcoFreeze(sender, args);
            case "ecogive" -> handleEcoGive(sender, args);
            case "ecoreset" -> handleEcoReset(sender, args);
            case "ecoset" -> handleEcoSet(sender, args);
            case "ecotake" -> handleEcoTake(sender, args);
            default -> true;
        };
    }

    private boolean handleBalance(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            if (!player.hasPermission("shardedcore.economy.balance")) {
                send(sender, "no-permission");
                return true;
            }
            send(player, "balance-self", "formatted", formatBalance(service.getBalance(player.getUniqueId())));
            return true;
        }
        if (!sender.hasPermission("shardedcore.economy.balance")) {
            send(sender, "no-permission");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found", "player", args[0]);
            return true;
        }
        send(sender, "balance-other", "player", name(target), "formatted",
                formatBalance(service.getBalance(target.getUniqueId())));
        return true;
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.economy.pay")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length < 2) {
            send(player, "pay-usage");
            return true;
        }
        long amount = Numbers.parseAmount(args[1]);
        if (amount <= 0) {
            send(player, "invalid-amount");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(player, "player-not-found", "player", args[0]);
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(player, "pay-self");
            return true;
        }
        if (service.isFrozen(player.getUniqueId())) {
            send(player, "account-frozen");
            return true;
        }
        if (service.isFrozen(target.getUniqueId())) {
            send(player, "target-frozen", "player", target.getName());
            return true;
        }
        if (plugin.stateStore().getBool(target.getUniqueId(), PAY_TOGGLE_KEY, false)) {
            send(player, "pay-disabled", "player", target.getName());
            return true;
        }
        if (service.getBalance(player.getUniqueId()) < amount) {
            send(player, "insufficient-funds");
            return true;
        }
        if (!service.canReceive(target.getUniqueId(), amount)) {
            send(player, "target-max-balance", "player", target.getName());
            return true;
        }
        if (!service.take(player.getUniqueId(), amount)) {
            send(player, "insufficient-funds");
            return true;
        }
        service.add(target.getUniqueId(), amount);
        send(player, "pay-sent", "player", target.getName(), "formatted", formatBalance(amount));
        send(target, "pay-received", "player", player.getName(), "formatted", formatBalance(amount));
        return true;
    }

    private boolean handleBalTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.economy.baltop")) {
            send(sender, "no-permission");
            return true;
        }
        int limit = 10;
        if (args.length >= 1) {
            try {
                limit = Math.max(1, Math.min(100, Integer.parseInt(args[0])));
            } catch (NumberFormatException ignored) {
                send(sender, "baltop-usage");
                return true;
            }
        }
        List<EconomyDatabase.LeaderEntry> top = database.top(limit);
        if (top.isEmpty()) {
            send(sender, "baltop-empty");
            return true;
        }
        send(sender, "baltop-header");
        int rank = 1;
        for (EconomyDatabase.LeaderEntry entry : top) {
            send(sender, "baltop-line",
                    "rank", String.valueOf(rank++),
                    "player", OfflinePlayers.name(entry.uuid()),
                    "formatted", formatBalance(entry.balance()));
        }
        return true;
    }

    private boolean handleEcoFreeze(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.economy.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            send(sender, "ecofreeze-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found", "player", args[0]);
            return true;
        }
        UUID uuid = target.getUniqueId();
        boolean frozen = args.length >= 2 ? parseBoolean(args[1]) : !service.isFrozen(uuid);
        service.setFrozen(uuid, frozen);
        send(sender, frozen ? "ecofreeze-enabled" : "ecofreeze-disabled", "player", name(target));
        return true;
    }

    private boolean handleEcoGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.economy.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            send(sender, "ecogive-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found", "player", args[0]);
            return true;
        }
        long amount = Numbers.parseAmount(args[1]);
        if (amount <= 0) {
            send(sender, "invalid-amount");
            return true;
        }
        service.add(target.getUniqueId(), amount);
        send(sender, "ecogive", "player", name(target), "formatted", formatBalance(amount));
        return true;
    }

    private boolean handleEcoReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.economy.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 1) {
            send(sender, "ecoreset-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found", "player", args[0]);
            return true;
        }
        service.reset(target.getUniqueId());
        send(sender, "ecoreset", "player", name(target));
        return true;
    }

    private boolean handleEcoSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.economy.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            send(sender, "ecoset-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found", "player", args[0]);
            return true;
        }
        long amount = Numbers.parseAmount(args[1]);
        if (amount < 0) {
            send(sender, "invalid-amount");
            return true;
        }
        service.setBalance(target.getUniqueId(), amount);
        send(sender, "ecoset", "player", name(target), "formatted", formatBalance(amount));
        return true;
    }

    private boolean handleEcoTake(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.economy.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            send(sender, "ecotake-usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null) {
            send(sender, "player-not-found", "player", args[0]);
            return true;
        }
        long amount = Numbers.parseAmount(args[1]);
        if (amount <= 0) {
            send(sender, "invalid-amount");
            return true;
        }
        if (!service.take(target.getUniqueId(), amount)) {
            send(sender, "ecotake-failed", "player", name(target));
            return true;
        }
        send(sender, "ecotake", "player", name(target), "formatted", formatBalance(amount));
        return true;
    }

    private String formatBalance(long amount) {
        if (config.getBoolean("format-decimals", false)) {
            return String.format(Locale.US, "%,.2f", (double) amount);
        }
        return Numbers.format(amount);
    }

    private boolean parseBoolean(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1", "freeze", "frozen" -> true;
            case "false", "no", "off", "0", "unfreeze", "unfrozen" -> false;
            default -> Boolean.parseBoolean(raw);
        };
    }

    private String name(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("money") || cmd.equals("balance") || cmd.equals("bal")) {
            if (args.length == 1 && sender.hasPermission("shardedcore.economy.balance")) {
                return OfflinePlayers.knownPlayers(args[0]);
            }
            return List.of();
        }
        if (cmd.equals("pay")) {
            if (args.length == 1) {
                return OfflinePlayers.onlinePlayers(args[0]);
            }
            if (args.length == 2) {
                return TabCompleteHelper.filter(List.of("1", "100", "1k", "10k", "100k", "1m", "10m"), args[1]);
            }
            return List.of();
        }
        if (cmd.equals("baltop") || cmd.equals("moneytop")) {
            if (args.length == 1) {
                return TabCompleteHelper.filter(List.of("5", "10", "25", "50", "100"), args[0]);
            }
            return List.of();
        }
        if (!sender.hasPermission("shardedcore.economy.admin")) {
            return List.of();
        }
        return switch (cmd) {
            case "ecofreeze" -> {
                if (args.length == 1) yield OfflinePlayers.knownPlayers(args[0]);
                if (args.length == 2) yield TabCompleteHelper.filter(List.of("true", "false", "on", "off"), args[1]);
                yield List.of();
            }
            case "ecogive", "ecoset", "ecotake" -> {
                if (args.length == 1) yield OfflinePlayers.knownPlayers(args[0]);
                if (args.length == 2) yield TabCompleteHelper.filter(List.of("1", "100", "1k", "10k", "100k", "1m", "10m"), args[1]);
                yield List.of();
            }
            case "ecoreset" -> args.length == 1 ? OfflinePlayers.knownPlayers(args[0]) : List.of();
            default -> List.of();
        };
    }
}

package com.shardedcore.modules.economy;

import com.shardedcore.ShardedCore;
import com.shardedcore.gui.GuiButtons;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.settings.SettingsModule;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.Items;
import com.shardedcore.util.Players;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class EconomyModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private EconomyService service;

    public EconomyModule(ShardedCore plugin) {
        super(plugin, "economy");
    }

    public EconomyService service() {
        return service;
    }

    @Override
    public void enable() {
        service = new EconomyService(plugin, plugin.toggles().sqlite(), config.getDouble("starting-balance", 0));
        registerCommand("bal", this);
        registerCommand("pay", this);
        registerCommand("baltop", this);
        registerCommand("moneymethods", this);
        registerCommand("ecofreeze", this);
        registerCommand("ecogive", this);
        registerCommand("ecoreset", this);
        registerCommand("ecoset", this);
        registerCommand("ecotake", this);
        registerListener(this);
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) service.ensure(player.getUniqueId());
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "bal", "balance", "money" -> balance(sender, args);
            case "pay" -> pay(sender, args);
            case "ecogive" -> admin(sender, args, true, false);
            case "ecotake" -> admin(sender, args, false, false);
            case "ecoset" -> admin(sender, args, true, true);
            case "ecoreset" -> reset(sender, args);
            case "ecofreeze" -> freeze(sender, args);
            case "baltop" -> baltop(sender, args);
            case "moneymethods", "moneymethod", "howtomakemoney" -> moneyMethods(sender);
            default -> true;
        };
    }

    private boolean balance(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendRaw(sender, line("players-only"));
                return true;
            }
            sendRaw(sender, line("self", "amount", service.format(service.get(player.getUniqueId()))));
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sendRaw(sender, line("player-missing"));
            return true;
        }
        sendRaw(sender, line("other", "player", Players.name(target), "amount", service.format(service.get(target.getUniqueId()))));
        return true;
    }

    private boolean pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendRaw(sender, line("players-only"));
            return true;
        }
        if (args.length < 2) {
            sendRaw(player, line("usage-pay"));
            return true;
        }
        Player target = Players.online(args[0]);
        if (target == null) {
            sendRaw(player, line("player-offline"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sendRaw(player, line("self-pay"));
            return true;
        }
        double amount = Amounts.parse(args[1]);
        if (amount <= 0) {
            sendRaw(player, line("invalid-amount"));
            return true;
        }
        if (service.frozen(player.getUniqueId()) || service.frozen(target.getUniqueId())) {
            sendRaw(player, line("frozen-self"));
            return true;
        }
        SettingsModule settings = plugin.modules().get(SettingsModule.class);
        if (settings != null && !settings.pay(target)) {
            sendRaw(player, line("pay-disabled"));
            return true;
        }
        if (!service.take(player.getUniqueId(), amount)) {
            sendRaw(player, line("cannot-afford"));
            return true;
        }
        service.add(target.getUniqueId(), amount);
        sendRaw(player, line("paid", "amount", service.format(amount), "player", target.getName()));
        sendRaw(target, line("received", "amount", service.format(amount), "player", player.getName()));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args, boolean add, boolean set) {
        if (!sender.hasPermission("shardedcore.economy.admin")) {
            sendRaw(sender, line("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sendRaw(sender, line("usage-admin"));
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        double amount = Amounts.parse(args[1]);
        if (amount < 0 || (!set && amount <= 0)) {
            sendRaw(sender, line("invalid-amount"));
            return true;
        }
        UUID uuid = target.getUniqueId();
        if (set) service.set(uuid, amount);
        else if (add) service.add(uuid, amount);
        else service.take(uuid, amount);
        String key = set ? "set" : add ? "give" : "take";
        sendRaw(sender, line(key, "amount", service.format(amount), "player", Players.name(target)));
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.economy.admin")) {
            sendRaw(sender, line("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sendRaw(sender, line("usage-admin"));
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        service.set(target.getUniqueId(), config.getDouble("starting-balance", 0));
        sendRaw(sender, line("reset", "player", Players.name(target)));
        return true;
    }

    private boolean freeze(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.economy.admin")) {
            sendRaw(sender, line("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sendRaw(sender, line("usage-admin"));
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        boolean next = !service.frozen(target.getUniqueId());
        service.freeze(target.getUniqueId(), next);
        sendRaw(sender, line(next ? "freeze-on" : "freeze-off", "player", Players.name(target)));
        return true;
    }

    private boolean baltop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendRaw(sender, line("players-only"));
            return true;
        }
        int page = 0;
        if (args.length > 0) {
            try {
                page = Math.max(0, Integer.parseInt(args[0]) - 1);
            } catch (NumberFormatException ignored) {
            }
        }
        openBaltop(player, page);
        return true;
    }

    private void openBaltop(Player player, int page) {
        ConfigurationSection gui = config.getConfigurationSection("baltop");
        int limit = gui == null ? 100 : Math.max(1, gui.getInt("limit", 100));
        List<Map.Entry<UUID, Double>> top = service.top(limit);
        if (top.isEmpty()) {
            sendRaw(player, line("baltop-empty"));
            return;
        }
        int rows = gui == null ? 6 : Math.max(3, Math.min(6, gui.getInt("rows", 6)));
        int[] slots = GuiButtons.inner(rows);
        int per = Math.max(1, slots.length);
        int pages = Math.max(1, (top.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        String title = Text.apply(gui == null ? "Money Leaderboard" : gui.getString("title", "Money Leaderboard"),
                "page", String.valueOf(current + 1), "pages", String.valueOf(pages));
        Menus.Menu menu = plugin.menus().create(player, title, rows);
        int start = current * per;
        String nameFormat = gui == null ? "%rank%&#94FF00&l%player%" : gui.getString("name", "%rank%&#94FF00&l%player%");
        List<String> loreTemplate = gui == null ? List.of(
                "&8Description",
                "",
                "&#94FF00Information:",
                "&#94FF00| &fPlace: &#94FF00#%place%",
                "&#94FF00| &fBalance: &#94FF00$%amount%",
                "",
                GuiButtons.clickFooter("To View")
        ) : gui.getStringList("lore");
        for (int i = 0; i < per && start + i < top.size(); i++) {
            Map.Entry<UUID, Double> entry = top.get(start + i);
            OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getKey());
            String name = Players.name(target);
            String rank = rankPrefix(target);
            String place = String.valueOf(start + i + 1);
            String amount = service.format(entry.getValue());
            String titleName = Text.apply(nameFormat,
                    "rank", rank, "player", GuiButtons.boldTitle(nameFormat, name),
                    "place", place, "amount", amount);
            List<String> lore = Text.applyList(new ArrayList<>(loreTemplate),
                    "rank", rank, "player", name, "place", place, "amount", amount);
            menu.set(slots[i], Items.head(target, titleName, lore));
        }
        GuiButtons.fill(menu);
        int last = (rows - 1) * 9;
        int prevSlot = gui == null ? last + 3 : gui.getInt("previous.slot", last + 3);
        int headSlot = gui == null ? last + 4 : gui.getInt("self.slot", last + 4);
        int nextSlot = gui == null ? last + 5 : gui.getInt("next.slot", last + 5);
        menu.set(prevSlot, GuiButtons.previous(player, "page", String.valueOf(current)), event -> {
            event.setCancelled(true);
            GuiButtons.play(player, "click");
            if (current > 0) openBaltop(player, current - 1);
        });
        int place = service.rank(player.getUniqueId());
        String selfName = Text.apply(gui == null ? "&#94FF00&lYOUR PLACE" : gui.getString("self.name", "&#94FF00&lYOUR PLACE"),
                "player", player.getName(), "place", place > 0 ? String.valueOf(place) : "Unranked",
                "amount", service.format(service.get(player.getUniqueId())));
        List<String> selfLore = Text.applyList(gui == null ? List.of(
                        "&8Description",
                        "",
                        "&#94FF00Information:",
                        "&#94FF00| &fYou are place &#94FF00#%place%",
                        "&#94FF00| &fBalance: &#94FF00$%amount%",
                        ""
                ) : gui.getStringList("self.lore"),
                "player", player.getName(),
                "place", place > 0 ? String.valueOf(place) : "Unranked",
                "amount", service.format(service.get(player.getUniqueId())));
        menu.set(headSlot, Items.head(player, selfName, selfLore));
        menu.set(nextSlot, GuiButtons.next(player, "page", String.valueOf(current + 2)), event -> {
            event.setCancelled(true);
            GuiButtons.play(player, "click");
            if (current + 1 < pages) openBaltop(player, current + 1);
        });
        plugin.menus().open(player, menu);
        GuiButtons.play(player, "open");
    }

    private boolean moneyMethods(CommandSender sender) {
        List<String> lines = config.getStringList("money-methods");
        if (lines.isEmpty()) {
            sendRaw(sender, line("money-methods-empty"));
            return true;
        }
        for (String line : lines) sendRaw(sender, line);
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.ensure(event.getPlayer().getUniqueId());
    }

    private String rankPrefix(OfflinePlayer player) {
        try {
            LuckPerms api = Bukkit.getServicesManager().load(LuckPerms.class);
            if (api == null) return "";
            var user = player.isOnline() && player.getPlayer() != null
                    ? api.getPlayerAdapter(Player.class).getUser(player.getPlayer())
                    : api.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "";
            String prefix = user.getCachedData().getMetaData().getPrefix();
            return prefix == null ? "" : prefix;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String line(String key, String... pairs) {
        return Text.apply(cfg(key, "").replace("%prefix%", cfg("prefix", "")), pairs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("moneymethods") || command.getName().equalsIgnoreCase("baltop")) {
            return List.of();
        }
        return args.length == 1 ? Tabs.players(args[0]) : List.of();
    }
}

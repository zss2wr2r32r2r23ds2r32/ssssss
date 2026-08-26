package com.shardedcore.modules.orders;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.modules.settings.SettingsModule;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Items;
import com.shardedcore.util.Slots;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class OrdersModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private Sqlite sqlite;
    private FileConfiguration messages;
    private FileConfiguration sounds;
    private FileConfiguration blacklist;
    private final Map<String, FileConfiguration> guis = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, Draft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, String> search = new ConcurrentHashMap<>();
    private final Map<UUID, String> sort = new ConcurrentHashMap<>();
    private final Map<UUID, String> filter = new ConcurrentHashMap<>();
    private List<Material> catalogue = List.of();
    private BukkitTask tick;

    public OrdersModule(ShardedCore plugin) {
        super(plugin, "orders");
    }

    @Override
    protected void extraFiles() {
        extraFile("messages.yml");
        extraFile("sounds.yml");
        extraFile("items/blacklisted.yml");
        for (String name : List.of("order.yml", "new_order.yml", "your_orders.yml", "item_select.yml",
                "deliver.yml", "edit.yml", "cancel_order.yml")) {
            extraFile("gui/" + name);
        }
    }

    @Override
    public void enable() {
        loadExtra();
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        material TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        delivered INTEGER NOT NULL DEFAULT 0,
                        waiting INTEGER NOT NULL DEFAULT 0,
                        price REAL NOT NULL,
                        paid REAL NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS order_stats (
                        uuid TEXT PRIMARY KEY,
                        placed INTEGER NOT NULL DEFAULT 0,
                        deliveries INTEGER NOT NULL DEFAULT 0,
                        earned REAL NOT NULL DEFAULT 0,
                        spent REAL NOT NULL DEFAULT 0
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create order tables", ex);
        }
        catalogue = catalogue();
        registerCommand("order", this);
        registerCommand("orderadmin", this);
        registerListener(this);
        tick = Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20L * 60L, 20L * 60L);
    }

    @Override
    public void disable() {
        if (tick != null) tick.cancel();
        cleanup();
    }

    public void wipe(UUID uuid) {
        if (uuid == null) return;
        try {
            sqlite.execute("DELETE FROM orders WHERE owner = ?", uuid.toString());
            sqlite.execute("DELETE FROM order_stats WHERE uuid = ?", uuid.toString());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to wipe orders", ex);
        }
    }

    @Override
    public void reload() {
        super.reload();
        loadExtra();
        catalogue = catalogue();
    }

    private void loadExtra() {
        messages = Configs.load(new File(folder, "messages.yml"));
        sounds = Configs.load(new File(folder, "sounds.yml"));
        blacklist = Configs.load(new File(folder, "items/blacklisted.yml"));
        guis.clear();
        for (String name : List.of("order", "new_order", "your_orders", "item_select", "deliver", "edit", "cancel_order")) {
            FileConfiguration loaded = Configs.load(new File(folder, "gui/" + name + ".yml"));
            if (name.equals("cancel_order") && loaded.getConfigurationSection("yes") == null) {
                loaded = Configs.load(new File(folder, "gui/cancel_order.yml"));
            }
            guis.put(name, loaded);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("orderadmin")) {
            return admin(sender, args);
        }
        if (!(sender instanceof Player player)) {
            msg(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            openBoard(player, 0);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "mine" -> {
                openMine(player, 0);
                yield true;
            }
            case "new" -> {
                Draft draft = drafts.computeIfAbsent(player.getUniqueId(), ignored -> new Draft());
                if (args.length >= 2) draft.amount = (int) Amounts.parseLong(args[1]);
                if (args.length >= 3) draft.price = Amounts.parse(args[2]);
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand != null && !hand.getType().isAir()) draft.material = hand.getType();
                openNew(player);
                yield true;
            }
            case "stats" -> {
                stats(player);
                yield true;
            }
            case "toggle" -> {
                player.performCommand("ordertoggle");
                yield true;
            }
            case "help" -> {
                sendLines(player, messages.getStringList("help"), "");
                yield true;
            }
            default -> {
                openBoard(player, 0);
                yield true;
            }
        };
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.orders.admin")) {
            msg(sender, "players-only");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendLines(sender, messages.getStringList("admin-help"), "");
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            List<Order> list = orders(null, "all", "newest", "");
            msg(sender, "admin-list", "amount", String.valueOf(list.size()));
            return true;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length < 2) {
                msg(sender, "usage-remove");
                return true;
            }
            try {
                int id = Integer.parseInt(args[1]);
                sqlite.execute("UPDATE orders SET status = 'cancelled' WHERE id = ?", id);
                msg(sender, "removed", "id", String.valueOf(id));
            } catch (Exception ex) {
                msg(sender, "usage-remove");
            }
            return true;
        }
        sendLines(sender, messages.getStringList("admin-help"), "");
        return true;
    }

    private void openBoard(Player player, int page) {
        FileConfiguration gui = guis.get("order");
        String query = search.getOrDefault(player.getUniqueId(), "");
        String sorted = sort.getOrDefault(player.getUniqueId(), "newest");
        String filtered = filter.getOrDefault(player.getUniqueId(), "all");
        List<Order> list = orders(null, filtered, sorted, query);
        int size = Math.max(1, gui.getInt("size", 54) / 9);
        Menus.Menu menu = plugin.menus().create(player, gui.getString("title", "&8Orders"), size);
        int per = 45;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        int start = current * per;
        for (int i = 0; i < per && start + i < list.size(); i++) {
            Order order = list.get(start + i);
            menu.set(i, orderIcon(gui.getConfigurationSection("order"), player, order), event -> {
                event.setCancelled(true);
                if (order.owner.equals(player.getUniqueId())) openEdit(player, order);
                else openDeliver(player, order);
            });
        }
        nav(menu, gui, "previous", player, current > 0, event -> openBoard(player, current - 1));
        nav(menu, gui, "next", player, current + 1 < pages, event -> openBoard(player, current + 1));
        nav(menu, gui, "refresh", player, true, event -> openBoard(player, current));
        nav(menu, gui, "new", player, true, event -> openNew(player));
        nav(menu, gui, "mine-button", player, true, event -> openMine(player, 0));
        nav(menu, gui, "search", player, true, event -> {
            prompts.put(player.getUniqueId(), Prompt.SEARCH);
            player.closeInventory();
            msg(player, "search-prompt");
        });
        nav(menu, gui, "sort", player, true, event -> {
            sort.put(player.getUniqueId(), next(sorted, List.of("newest", "most-paid", "best-per-item", "most-delivered")));
            openBoard(player, 0);
        });
        nav(menu, gui, "filter", player, true, event -> {
            filter.put(player.getUniqueId(), next(filtered, List.of("all", "blocks", "tools", "combat", "food", "potions", "books", "ingredients", "other")));
            openBoard(player, 0);
        });
        plugin.menus().open(player, menu);
        play(player, "click");
    }

    private void openMine(Player player, int page) {
        FileConfiguration gui = guis.get("your_orders");
        List<Order> list = orders(player.getUniqueId(), "all", "newest", "");
        Menus.Menu menu = plugin.menus().create(player, gui.getString("title", "&8Your Orders"), Math.max(1, gui.getInt("size", 27) / 9));
        int per = 18;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        int start = current * per;
        for (int i = 0; i < per && start + i < list.size(); i++) {
            Order order = list.get(start + i);
            String path = switch (order.status) {
                case "ready" -> "ready";
                case "closed" -> "closed";
                default -> "open";
            };
            menu.set(i, orderIcon(gui.getConfigurationSection(path), player, order), event -> {
                event.setCancelled(true);
                openEdit(player, order);
            });
        }
        nav(menu, gui, "new", player, true, event -> openNew(player));
        nav(menu, gui, "back", player, true, event -> openBoard(player, 0));
        nav(menu, gui, "previous", player, current > 0, event -> openMine(player, current - 1));
        nav(menu, gui, "next", player, current + 1 < pages, event -> openMine(player, current + 1));
        menu.fill(Items.fromSection(gui.getConfigurationSection("filler"), player));
        plugin.menus().open(player, menu);
    }

    private void openNew(Player player) {
        Draft draft = drafts.computeIfAbsent(player.getUniqueId(), ignored -> new Draft());
        FileConfiguration gui = guis.get("new_order");
        Menus.Menu menu = plugin.menus().create(player, gui.getString("title", "&8New Order"), Math.max(1, gui.getInt("size", 27) / 9));
        String none = gui.getString("none", "-");
        nav(menu, gui, "back", player, true, event -> openBoard(player, 0));
        nav(menu, gui, "item", player, true, "item_name", draft.material == null ? none : Text.pretty(draft.material.name()), event -> openSelect(player, 0));
        nav(menu, gui, "amount", player, true, "current_amount", draft.amount <= 0 ? none : String.valueOf(draft.amount), event -> {
            prompts.put(player.getUniqueId(), Prompt.AMOUNT);
            player.closeInventory();
            msg(player, "ask-amount");
        });
        nav(menu, gui, "price", player, true, "current_price", draft.price <= 0 ? none : Amounts.format(draft.price), event -> {
            prompts.put(player.getUniqueId(), Prompt.PRICE);
            player.closeInventory();
            msg(player, "ask-price");
        });
        boolean ready = draft.material != null && draft.amount > 0 && draft.price > 0;
        String path = ready ? "confirm" : "incomplete";
        nav(menu, gui, path, player, true, event -> {
            if (ready) create(player, draft);
            else msg(player, "draft-incomplete");
        }, "total_price", Amounts.format(draft.amount * draft.price),
                "item", draft.material == null ? none : Text.pretty(draft.material.name()),
                "amount", draft.amount <= 0 ? none : String.valueOf(draft.amount),
                "price", draft.price <= 0 ? none : Amounts.format(draft.price));
        plugin.menus().open(player, menu);
    }

    private void openSelect(Player player, int page) {
        FileConfiguration gui = guis.get("item_select");
        String query = search.getOrDefault(player.getUniqueId(), "");
        String filtered = filter.getOrDefault(player.getUniqueId(), "all");
        List<Material> list = new ArrayList<>();
        for (Material material : catalogue) {
            if (!matchesFilter(material, filtered)) continue;
            if (!query.isBlank() && !material.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            list.add(material);
        }
        Menus.Menu menu = plugin.menus().create(player, gui.getString("title", "&8Select Item"), 6);
        int per = 45;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        int current = Math.max(0, Math.min(page, pages - 1));
        int start = current * per;
        for (int i = 0; i < per && start + i < list.size(); i++) {
            Material material = list.get(start + i);
            menu.set(i, Items.named(material, Text.apply(gui.getString("item.name", "&f%item%"), "item", Text.pretty(material.name())),
                    gui.getStringList("item.lore")), event -> {
                event.setCancelled(true);
                Draft draft = drafts.computeIfAbsent(player.getUniqueId(), ignored -> new Draft());
                draft.material = material;
                openNew(player);
            });
        }
        nav(menu, gui, "previous", player, current > 0, event -> openSelect(player, current - 1));
        nav(menu, gui, "next", player, current + 1 < pages, event -> openSelect(player, current + 1));
        nav(menu, gui, "back", player, true, event -> openNew(player));
        nav(menu, gui, "search", player, true, event -> {
            prompts.put(player.getUniqueId(), Prompt.SEARCH);
            player.closeInventory();
            msg(player, "search-prompt");
        });
        plugin.menus().open(player, menu);
    }

    private void openDeliver(Player player, Order order) {
        if (order.owner.equals(player.getUniqueId())) {
            msg(player, "own-order");
            return;
        }
        FileConfiguration gui = guis.get("deliver");
        List<Integer> filler = Slots.of(gui, "filler-slots");
        Set<Integer> locked = Set.of(gui.getInt("confirm.slot", 26), gui.getInt("info.slot", 22), gui.getInt("back.slot", 18));
        List<Integer> area = new ArrayList<>();
        int size = gui.getInt("size", 27);
        for (int i = 0; i < size; i++) {
            if (!filler.contains(i) && !locked.contains(i)) area.add(i);
        }
        Menus.Menu menu = plugin.menus().create(player, gui.getString("title", "&8Deliver"), Math.max(1, size / 9))
                .editableSlots(area);
        nav(menu, gui, "confirm", player, true, event -> {
            event.setCancelled(true);
            deliver(player, order, menu, area);
        });
        menu.set(gui.getInt("info.slot", 22), orderIcon(gui.getConfigurationSection("info"), player, order));
        nav(menu, gui, "back", player, true, event -> openBoard(player, 0));
        ItemStack fill = Items.fromSection(gui.getConfigurationSection("filler"), player);
        for (int slot : filler) menu.set(slot, fill);
        plugin.menus().open(player, menu);
    }

    private void openEdit(Player player, Order order) {
        FileConfiguration gui = guis.get("edit");
        Menus.Menu menu = plugin.menus().create(player, gui.getString("title", "&8Edit Order"), Math.max(1, gui.getInt("size", 27) / 9));
        String path = switch (order.status) {
            case "ready" -> "ready";
            case "closed" -> "closed";
            default -> "open";
        };
        menu.set(gui.getInt("order-slot", 10), orderIcon(gui.getConfigurationSection(path), player, order));
        nav(menu, gui, "cancel", player, true, event -> openCancel(player, order));
        nav(menu, gui, "collect", player, true, event -> collect(player, order));
        nav(menu, gui, "back", player, true, event -> openMine(player, 0));
        menu.fill(Items.fromSection(gui.getConfigurationSection("filler"), player));
        plugin.menus().open(player, menu);
    }

    private void openCancel(Player player, Order order) {
        FileConfiguration gui = guis.get("cancel_order");
        Menus.Menu menu = plugin.menus().create(player, gui.getString("title", "&8Cancel Order"), Math.max(1, gui.getInt("size", 27) / 9));
        menu.set(gui.getInt("order-slot", 13), orderIcon(gui.getConfigurationSection("order"), player, order));
        nav(menu, gui, "no", player, true, event -> openEdit(player, order));
        nav(menu, gui, "yes", player, true, event -> cancel(player, order));
        plugin.menus().open(player, menu);
    }

    private void create(Player player, Draft draft) {
        if (draft.material == null || draft.amount <= 0 || draft.price <= 0) {
            msg(player, "draft-incomplete");
            return;
        }
        if (blacklisted(draft.material)) {
            msg(player, "not-orderable");
            return;
        }
        int maxAmount = config.getInt("max-amount", 3456);
        if (draft.amount < 1 || draft.amount > maxAmount) {
            msg(player, "bad-amount", "max", String.valueOf(maxAmount));
            return;
        }
        double min = config.getDouble("min-price", 1);
        double max = config.getDouble("max-price", 10_000_000);
        if (draft.price < min || draft.price > max) {
            msg(player, "bad-price", "min", Amounts.format(min), "max", Amounts.format(max));
            return;
        }
        int open = orders(player.getUniqueId(), "all", "newest", "").stream()
                .filter(order -> order.status.equals("open") || order.status.equals("ready")).toList().size();
        if (open >= config.getInt("max-per-player", 5)) {
            msg(player, "too-many", "limit", String.valueOf(config.getInt("max-per-player", 5)));
            return;
        }
        EconomyModule economy = plugin.modules().get(EconomyModule.class);
        if (economy == null) {
            msg(player, "no-economy");
            return;
        }
        double total = draft.amount * draft.price;
        if (!economy.service().take(player.getUniqueId(), total)) {
            msg(player, "cannot-afford", "price", Amounts.format(total));
            play(player, "error");
            return;
        }
        long now = System.currentTimeMillis();
        long expires = now + config.getLong("expiry-days", 7) * 86_400_000L;
        try {
            sqlite.execute("""
                    INSERT INTO orders (owner, owner_name, material, amount, delivered, waiting, price, paid, created_at, expires_at, status)
                    VALUES (?, ?, ?, ?, 0, 0, ?, 0, ?, ?, 'open')
                    """, player.getUniqueId().toString(), player.getName(), draft.material.name(), draft.amount, draft.price, now, expires);
            bump(player.getUniqueId(), 1, 0, 0, total);
            drafts.remove(player.getUniqueId());
            msg(player, "created", "amount", String.valueOf(draft.amount), "item", Text.pretty(draft.material.name()),
                    "price", Amounts.format(total));
            announce(player, draft);
            openBoard(player, 0);
        } catch (SQLException ex) {
            economy.service().add(player.getUniqueId(), total);
            msg(player, "create-failed");
        }
    }

    private void deliver(Player player, Order latest, Menus.Menu menu, List<Integer> area) {
        Order order = byId(latest.id);
        if (order == null || !order.status.equals("open")) {
            msg(player, "gone");
            return;
        }
        int remaining = order.amount - order.delivered;
        int have = 0;
        ItemStack[] contents = menu.inventory().getContents();
        for (int slot : area) {
            ItemStack item = slot < contents.length ? contents[slot] : null;
            if (item != null && item.getType() == order.material) have += item.getAmount();
        }
        if (have <= 0) {
            msg(player, "nothing-matched");
            play(player, "error");
            return;
        }
        int take = Math.min(have, remaining);
        int left = take;
        for (int slot : area) {
            if (left <= 0) break;
            ItemStack item = slot < contents.length ? contents[slot] : null;
            if (item == null || item.getType() != order.material) continue;
            int used = Math.min(left, item.getAmount());
            left -= used;
            item.setAmount(item.getAmount() - used);
            if (item.getAmount() <= 0) menu.inventory().setItem(slot, null);
            else menu.inventory().setItem(slot, item);
        }
        EconomyModule economy = plugin.modules().get(EconomyModule.class);
        double pay = take * order.price;
        if (economy != null) economy.service().add(player.getUniqueId(), pay);
        int delivered = order.delivered + take;
        String status = delivered >= order.amount ? "ready" : "open";
        try {
            sqlite.execute("UPDATE orders SET delivered = ?, waiting = waiting + ?, paid = paid + ?, status = ? WHERE id = ?",
                    delivered, take, pay, status, order.id);
            bump(player.getUniqueId(), 0, 1, pay, 0);
        } catch (SQLException ex) {
            msg(player, "delivery-failed");
            return;
        }
        for (int slot : area) {
            ItemStack leftover = menu.inventory().getItem(slot);
            give(player, leftover);
            menu.inventory().setItem(slot, null);
        }
        msg(player, "delivered", "amount", String.valueOf(take), "item", Text.pretty(order.material.name()),
                "money", Amounts.format(pay));
        play(player, "delivered");
        Player owner = Bukkit.getPlayer(order.owner);
        if (owner != null) {
            msg(owner, "owner-delivery", "player", player.getName(), "amount", String.valueOf(take),
                    "item", Text.pretty(order.material.name()));
            play(owner, "received");
        }
        player.closeInventory();
    }

    private void collect(Player player, Order latest) {
        Order order = byId(latest.id);
        if (order == null || !order.owner.equals(player.getUniqueId())) {
            msg(player, "not-yours");
            return;
        }
        if (order.waiting <= 0) {
            msg(player, "nothing-to-collect");
            return;
        }
        ItemStack stack = new ItemStack(order.material, order.waiting);
        give(player, stack);
        try {
            sqlite.execute("UPDATE orders SET waiting = 0, status = CASE WHEN delivered >= amount THEN 'closed' ELSE status END WHERE id = ?", order.id);
        } catch (SQLException ex) {
            return;
        }
        msg(player, "collected", "amount", String.valueOf(order.waiting), "item", Text.pretty(order.material.name()));
        player.closeInventory();
    }

    private void cancel(Player player, Order latest) {
        Order order = byId(latest.id);
        if (order == null || !order.owner.equals(player.getUniqueId())) {
            msg(player, "not-yours");
            return;
        }
        double refund = (order.amount - order.delivered) * order.price;
        EconomyModule economy = plugin.modules().get(EconomyModule.class);
        if (refund > 0 && economy != null && !economy.service().add(player.getUniqueId(), refund)) {
            msg(player, "refund-failed", "money", Amounts.format(refund));
            return;
        }
        if (order.waiting > 0) give(player, new ItemStack(order.material, order.waiting));
        try {
            sqlite.execute("UPDATE orders SET status = 'cancelled', waiting = 0 WHERE id = ?", order.id);
        } catch (SQLException ex) {
            msg(player, "cancel-failed");
            return;
        }
        msg(player, "cancelled", "item", Text.pretty(order.material.name()), "money", Amounts.format(refund));
        openMine(player, 0);
    }

    private void expire() {
        long now = System.currentTimeMillis();
        List<Order> open = orders(null, "all", "newest", "");
        for (Order order : open) {
            if (!order.status.equals("open") && !order.status.equals("ready")) continue;
            if (order.expiresAt > now) continue;
            double refund = (order.amount - order.delivered) * order.price;
            try {
                sqlite.execute("UPDATE orders SET status = 'expired' WHERE id = ?", order.id);
            } catch (SQLException ignored) {
                continue;
            }
            Player owner = Bukkit.getPlayer(order.owner);
            EconomyModule economy = plugin.modules().get(EconomyModule.class);
            if (refund > 0 && economy != null) economy.service().add(order.owner, refund);
            if (owner != null) msg(owner, "expired", "item", Text.pretty(order.material.name()), "money", Amounts.format(refund));
        }
        long cut = now - config.getLong("deletion-days", 7) * 86_400_000L;
        try {
            sqlite.execute("DELETE FROM orders WHERE status IN ('closed','cancelled','expired') AND expires_at < ?", cut);
        } catch (SQLException ignored) {
        }
    }

    private void announce(Player player, Draft draft) {
        if (!config.getBoolean("announce", true)) return;
        String line = Text.apply(messages.getString("announce", ""),
                "player", player.getName(),
                "amount", String.valueOf(draft.amount),
                "item", Text.pretty(draft.material.name()),
                "price", Amounts.format(draft.price));
        SettingsModule settings = plugin.modules().get(SettingsModule.class);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (settings != null && !settings.orders(online)) continue;
            online.sendMessage(com.shardedcore.util.ColorUtil.parse(line));
        }
    }

    private void stats(Player player) {
        try {
            sqlite.query("SELECT placed, deliveries, earned, spent FROM order_stats WHERE uuid = ?", rs -> {
                int placed = 0, deliveries = 0;
                double earned = 0, spent = 0;
                try {
                    if (rs.next()) {
                        placed = rs.getInt("placed");
                        deliveries = rs.getInt("deliveries");
                        earned = rs.getDouble("earned");
                        spent = rs.getDouble("spent");
                    }
                } catch (SQLException ignored) {
                }
                int open = (int) orders(player.getUniqueId(), "all", "newest", "").stream()
                        .filter(order -> order.status.equals("open") || order.status.equals("ready")).count();
                List<String> lines = Text.applyList(messages.getStringList("stats"),
                        "placed", String.valueOf(placed),
                        "open", String.valueOf(open),
                        "limit", String.valueOf(config.getInt("max-per-player", 5)),
                        "deliveries", String.valueOf(deliveries),
                        "earned", Amounts.format(earned),
                        "spent", Amounts.format(spent));
                sendLines(player, lines, "");
                return null;
            }, player.getUniqueId().toString());
        } catch (SQLException ex) {
            msg(player, "load-failed");
        }
    }

    private ItemStack orderIcon(ConfigurationSection section, Player player, Order order) {
        if (section == null) return new ItemStack(order.material);
        String none = config.getString("no-time", "-");
        ItemStack stack = Items.fromSection(section, player,
                "player_name", order.ownerName,
                "item_name", Text.pretty(order.material.name()),
                "total_amount", String.valueOf(order.amount),
                "price_per_item", Amounts.format(order.price),
                "total_delivered", String.valueOf(order.delivered),
                "total_paid", Amounts.format(order.paid),
                "total_price", Amounts.format(order.amount * order.price),
                "time_remaining", order.expiresAt > System.currentTimeMillis()
                        ? Amounts.duration(order.expiresAt - System.currentTimeMillis(), "d", "h", "m", "s", 2) : none,
                "time_until_delete", none);
        stack.setType(order.material);
        stack.setAmount(Math.min(64, Math.max(1, order.amount)));
        return stack;
    }

    private void nav(Menus.Menu menu, FileConfiguration gui, String path, Player player, boolean show,
                     java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> click, String... extra) {
        if (!show) return;
        ConfigurationSection section = gui.getConfigurationSection(path);
        if (section == null) return;
        menu.set(section.getInt("slot", 0), Items.fromSection(section, player, extra), event -> {
            event.setCancelled(true);
            click.accept(event);
        });
    }

    private void nav(Menus.Menu menu, FileConfiguration gui, String path, Player player, boolean show,
                     String k1, String v1, java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> click) {
        nav(menu, gui, path, player, show, click, k1, v1);
    }

    private void nav(Menus.Menu menu, FileConfiguration gui, String path, Player player, boolean show,
                     String k1, String v1, String k2, String v2, String k3, String v3,
                     java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> click) {
        nav(menu, gui, path, player, show, click, k1, v1, k2, v2, k3, v3);
    }

    private List<Order> orders(UUID owner, String filtered, String sorted, String query) {
        List<Order> list = new ArrayList<>();
        try {
            sqlite.query("SELECT * FROM orders WHERE status IN ('open','ready','closed') ORDER BY created_at DESC", rs -> {
                try {
                    while (rs.next()) {
                        Order order = new Order(
                                rs.getInt("id"),
                                UUID.fromString(rs.getString("owner")),
                                rs.getString("owner_name"),
                                Material.matchMaterial(rs.getString("material")) == null
                                        ? Material.STONE : Material.matchMaterial(rs.getString("material")),
                                rs.getInt("amount"),
                                rs.getInt("delivered"),
                                rs.getInt("waiting"),
                                rs.getDouble("price"),
                                rs.getDouble("paid"),
                                rs.getLong("created_at"),
                                rs.getLong("expires_at"),
                                rs.getString("status")
                        );
                        if (owner != null && !order.owner.equals(owner)) continue;
                        if (!matchesFilter(order.material, filtered)) continue;
                        if (!query.isBlank() && !order.material.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
                                && !order.ownerName.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
                        list.add(order);
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return list;
            });
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load orders", ex);
        }
        Comparator<Order> comparator = switch (sorted) {
            case "most-paid" -> Comparator.comparingDouble((Order order) -> order.amount * order.price).reversed();
            case "best-per-item" -> Comparator.comparingDouble((Order order) -> order.price).reversed();
            case "most-delivered" -> Comparator.comparingInt((Order order) -> order.delivered).reversed();
            default -> Comparator.comparingLong((Order order) -> order.createdAt).reversed();
        };
        list.sort(comparator);
        return list;
    }

    private Order byId(int id) {
        return orders(null, "all", "newest", "").stream().filter(order -> order.id == id).findFirst().orElse(null);
    }

    private void bump(UUID uuid, int placed, int deliveries, double earned, double spent) {
        try {
            sqlite.execute("""
                    INSERT INTO order_stats (uuid, placed, deliveries, earned, spent) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        placed = placed + excluded.placed,
                        deliveries = deliveries + excluded.deliveries,
                        earned = earned + excluded.earned,
                        spent = spent + excluded.spent
                    """, uuid.toString(), placed, deliveries, earned, spent);
        } catch (SQLException ignored) {
        }
    }

    private List<Material> catalogue() {
        Set<Material> blocked = EnumSet.noneOf(Material.class);
        for (String name : blacklist.getStringList("items")) {
            Material material = Material.matchMaterial(name);
            if (material != null) blocked.add(material);
        }
        List<Material> list = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isItem() || material.isAir() || blocked.contains(material)) continue;
            list.add(material);
        }
        list.sort(Comparator.comparing(material -> Text.pretty(material.name())));
        return List.copyOf(list);
    }

    private boolean blacklisted(Material material) {
        return blacklist.getStringList("items").stream().anyMatch(name -> name.equalsIgnoreCase(material.name()));
    }

    private boolean matchesFilter(Material material, String filter) {
        if (filter == null || filter.equals("all")) return true;
        String name = material.name();
        return switch (filter) {
            case "blocks" -> material.isBlock();
            case "tools" -> name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.equals("SHEARS");
            case "combat" -> name.endsWith("_SWORD") || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                    || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.equals("BOW") || name.equals("CROSSBOW")
                    || name.equals("SHIELD") || name.equals("TRIDENT") || name.equals("MACE");
            case "food" -> material.isEdible();
            case "potions" -> name.contains("POTION") || name.equals("TIPPED_ARROW");
            case "books" -> name.contains("BOOK");
            case "ingredients" -> !material.isBlock() && !material.isEdible() && !name.contains("SWORD");
            default -> true;
        };
    }

    private String next(String current, List<String> options) {
        int index = options.indexOf(current);
        return options.get((index + 1) % options.size());
    }

    private void give(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        player.getInventory().addItem(item.clone()).values()
                .forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private void msg(CommandSender to, String path, String... pairs) {
        String text = Text.apply(messages.getString(path, ""), pairs);
        if (text.isEmpty()) return;
        if (to instanceof Player player && config.getBoolean("actionbar", false)) {
            player.sendActionBar(com.shardedcore.util.ColorUtil.parse(text));
            return;
        }
        to.sendMessage(com.shardedcore.util.ColorUtil.parse(text));
        if (to instanceof Player player && text.contains("&#FF1D54")) play(player, "error");
    }

    private void play(Player player, String path) {
        Sounds.play(player, sounds.getConfigurationSection(path));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) return;
        event.setCancelled(true);
        String typed = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (typed.equalsIgnoreCase("cancel")) {
                if (prompt == Prompt.SEARCH) search.remove(player.getUniqueId());
                openBoard(player, 0);
                return;
            }
            switch (prompt) {
                case SEARCH -> {
                    search.put(player.getUniqueId(), typed);
                    msg(player, "search-set", "search", typed);
                    openBoard(player, 0);
                }
                case AMOUNT -> {
                    drafts.computeIfAbsent(player.getUniqueId(), ignored -> new Draft()).amount = (int) Amounts.parseLong(typed);
                    openNew(player);
                }
                case PRICE -> {
                    drafts.computeIfAbsent(player.getUniqueId(), ignored -> new Draft()).price = Amounts.parse(typed);
                    openNew(player);
                }
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("orderadmin")) {
            if (args.length == 1) return Tabs.filter(List.of("list", "remove", "help"), args[0]);
            return List.of();
        }
        if (args.length == 1) return Tabs.filter(List.of("mine", "new", "stats", "toggle", "help"), args[0]);
        return List.of();
    }

    private enum Prompt { SEARCH, AMOUNT, PRICE }

    private static final class Draft {
        private Material material;
        private int amount;
        private double price;
    }

    private record Order(int id, UUID owner, String ownerName, Material material, int amount, int delivered,
                         int waiting, double price, double paid, long createdAt, long expiresAt, String status) {
    }
}

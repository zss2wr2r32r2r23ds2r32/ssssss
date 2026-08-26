package com.shardedcore.modules.sell;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.modules.economy.EconomyService;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Items;
import com.shardedcore.util.Players;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class SellModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private static final int WORTH_PAGE = 45;
    private static final int HISTORY_PAGE = 45;
    private static final List<String> SORTS = List.of("highest", "lowest", "name");

    private FileConfiguration multiplierFile;
    private FileConfiguration worthFile;
    private FileConfiguration pricesFile;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);
    private final Map<Material, String> categories = new EnumMap<>(Material.class);
    private final Set<Material> blacklist = EnumSet.noneOf(Material.class);
    private final Set<Material> ignoreData = EnumSet.noneOf(Material.class);
    private final Map<Material, Double> shopPrices = new EnumMap<>(Material.class);
    private final List<Tier> tiers = new ArrayList<>();
    private final Map<UUID, Stats> stats = new ConcurrentHashMap<>();
    private final Map<UUID, MenuKind> open = new ConcurrentHashMap<>();
    private final Map<UUID, WorthView> worthViews = new ConcurrentHashMap<>();
    private final Map<UUID, HistoryView> historyViews = new ConcurrentHashMap<>();
    private Sqlite sqlite;

    public SellModule(ShardedCore plugin) {
        super(plugin, "sell");
    }

    @Override
    protected void extraFiles() {
        extraFile("multiplier.yml");
        extraFile("worth.yml");
        extraFile("prices.yml");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS sell_stats (
                        uuid TEXT PRIMARY KEY,
                        sold_base REAL NOT NULL DEFAULT 0,
                        paid REAL NOT NULL DEFAULT 0
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS sell_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        item TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        base REAL NOT NULL,
                        multi REAL NOT NULL,
                        total REAL NOT NULL,
                        at INTEGER NOT NULL
                    )
                    """);
            sqlite.run("CREATE INDEX IF NOT EXISTS sell_history_uuid_at ON sell_history(uuid, at DESC)");
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create sell tables", ex);
        }
        loadData();
        pruneHistory();
        registerCommand("sell", this);
        registerCommand("worth", this);
        registerCommand("sellmulti", this);
        registerCommand("sellhistory", this);
        registerListener(this);
        if (config.getBoolean("rules.check-recipes", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<Recipe> recipes = new ArrayList<>();
                Bukkit.recipeIterator().forEachRemaining(recipes::add);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> checkRecipes(recipes));
            });
        }
    }

    @Override
    public void disable() {
        open.clear();
        worthViews.clear();
        historyViews.clear();
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        loadData();
    }

    private void loadData() {
        multiplierFile = Configs.load(new File(folder, "multiplier.yml"));
        worthFile = Configs.load(new File(folder, "worth.yml"));
        pricesFile = Configs.load(new File(folder, "prices.yml"));
        prices.clear();
        categories.clear();
        blacklist.clear();
        ignoreData.clear();
        shopPrices.clear();
        tiers.clear();
        for (String name : pricesFile.getStringList("blacklist")) {
            Material material = Sounds.material(name, null);
            if (material != null) blacklist.add(material);
        }
        ConfigurationSection categorySection = pricesFile.getConfigurationSection("categories");
        if (categorySection != null) {
            for (String key : categorySection.getKeys(false)) {
                Material material = Sounds.material(key, null);
                if (material != null) categories.put(material, categorySection.getString(key, "").toLowerCase(Locale.ROOT));
            }
        }
        ConfigurationSection priceSection = pricesFile.getConfigurationSection("prices");
        if (priceSection != null) {
            for (String key : priceSection.getKeys(false)) {
                Material material = Sounds.material(key, null);
                if (material != null && priceSection.getDouble(key) > 0) {
                    prices.put(material, priceSection.getDouble(key));
                }
            }
        }
        for (String name : config.getStringList("rules.ignore-data")) {
            Material material = Sounds.material(name, null);
            if (material != null) ignoreData.add(material);
        }
        ConfigurationSection tierSection = multiplierFile.getConfigurationSection("tiers");
        if (tierSection != null) {
            for (String key : tierSection.getKeys(false)) {
                ConfigurationSection tier = tierSection.getConfigurationSection(key);
                if (tier == null) continue;
                tiers.add(new Tier(key, tier.getDouble("multiplier", 1.0), tier.getDouble("goal", 0), tier.getInt("slot", 0)));
            }
            tiers.sort(Comparator.comparingDouble(Tier::goal));
        }
        loadShopPrices();
    }

    private void loadShopPrices() {
        File shopFolder = new File(plugin.getDataFolder(), "modules/shop");
        File[] files = {
                new File(shopFolder, "prices.yml"),
                new File(shopFolder, "config.yml"),
                new File(shopFolder, "shop.yml")
        };
        for (File file : files) {
            if (!file.exists()) continue;
            FileConfiguration yaml = Configs.load(file);
            ingestShop(yaml);
            ingestShop(yaml.getConfigurationSection("prices"));
            ingestShop(yaml.getConfigurationSection("items"));
            ingestShop(yaml.getConfigurationSection("shop"));
        }
    }

    private void ingestShop(ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material material = Sounds.material(key, null);
            if (material == null) continue;
            if (section.isConfigurationSection(key)) {
                ConfigurationSection item = section.getConfigurationSection(key);
                if (item == null) continue;
                if (item.contains("buy")) shopPrices.put(material, item.getDouble("buy"));
                else if (item.contains("price")) shopPrices.put(material, item.getDouble("price"));
            } else if (section.isDouble(key) || section.isInt(key)) {
                shopPrices.put(material, section.getDouble(key));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sell" -> {
                openSell(player);
                yield true;
            }
            case "worth" -> {
                openWorth(player, worthViews.computeIfAbsent(player.getUniqueId(), ignored -> new WorthView()).page);
                yield true;
            }
            case "sellmulti" -> {
                if (!multiplierEnabled()) {
                    send(player, "no-multiplier");
                    yield true;
                }
                openMultiplier(player);
                yield true;
            }
            case "sellhistory" -> history(player, args);
            default -> true;
        };
    }

    private boolean history(Player player, String[] args) {
        UUID target = player.getUniqueId();
        String name = player.getName();
        if (args.length > 0 && player.hasPermission("shardedcore.sell.history.admin")) {
            OfflinePlayer other = Players.offline(args[0]);
            target = other.getUniqueId();
            name = Players.name(other);
        }
        openHistory(player, target, name, 0);
        return true;
    }

    private void openSell(Player player) {
        if (prices.isEmpty()) {
            send(player, "no-prices");
            return;
        }
        int rows = Math.max(2, Math.min(6, config.getInt("menu.rows", 5)));
        int last = (rows - 1) * 9;
        Menus.Menu menu = plugin.menus().create(player, cfg("menu.title", "&8Sell Menu"), rows).unlocked();
        ItemStack filler = Items.named(
                Sounds.material(cfg("filler.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                cfg("filler.name", " "),
                config.getStringList("filler.lore")
        );
        boolean multiOn = multiplierEnabled();
        int multiSlot = config.getInt("multiplier-button.slot", 40);
        for (int slot = last; slot < rows * 9; slot++) {
            if (multiOn && slot == multiSlot) continue;
            menu.set(slot, filler.clone(), event -> event.setCancelled(true));
        }
        if (multiOn) {
            Progress progress = progress(player);
            menu.set(multiSlot, Items.fromSection(config.getConfigurationSection("multiplier-button"), player,
                    "percent", percentText(progress.percent()),
                    "next", decimal(progress.nextMultiplier())
            ), event -> {
                event.setCancelled(true);
                Sounds.play(player, config.getConfigurationSection("sounds.click"));
                openMultiplier(player);
            });
        }
        menu.onAny(event -> {
            if (event.getRawSlot() >= last && event.getRawSlot() < rows * 9) event.setCancelled(true);
        });
        menu.onBottom(event -> {
            if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) return;
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (empty(current)) return;
            Inventory top = menu.inventory();
            ItemStack moving = current.clone();
            for (int slot = 0; slot < last && moving.getAmount() > 0; slot++) {
                ItemStack existing = top.getItem(slot);
                if (empty(existing)) {
                    top.setItem(slot, moving);
                    moving = new ItemStack(Material.AIR);
                    break;
                }
                if (existing.isSimilar(moving) && existing.getAmount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getAmount();
                    int moved = Math.min(space, moving.getAmount());
                    existing.setAmount(existing.getAmount() + moved);
                    moving.setAmount(moving.getAmount() - moved);
                }
            }
            if (empty(moving)) event.setCurrentItem(null);
            else event.setCurrentItem(moving);
        });
        menu.onClose(closed -> {
            open.remove(closed.getUniqueId(), MenuKind.SELL);
            sellFrom(closed, menu.inventory(), last);
        });
        open.put(player.getUniqueId(), MenuKind.SELL);
        Sounds.play(player, config.getConfigurationSection("sounds.open"));
        plugin.menus().open(player, menu);
    }

    private void openMultiplier(Player player) {
        if (!multiplierEnabled()) {
            send(player, "no-multiplier");
            return;
        }
        Progress progress = progress(player);
        int rows = Math.max(1, Math.min(6, multiplierFile.getInt("menu.rows", 6)));
        Menus.Menu menu = plugin.menus().create(player, multiplierFile.getString("menu.title", "&8Sell Menu | Sell Multi"), rows);
        menu.set(multiplierFile.getInt("info.slot", 1), Items.fromSection(multiplierFile.getConfigurationSection("info"), player,
                "multi", decimal(progress.multiplier()),
                "total", Amounts.format(progress.total())
        ), event -> {
            event.setCancelled(true);
            Sounds.play(player, config.getConfigurationSection("sounds.click"));
            openWorth(player, 0);
        });
        menu.set(multiplierFile.getInt("return.slot", 45), Items.fromSection(multiplierFile.getConfigurationSection("return"), player), event -> {
            event.setCancelled(true);
            Sounds.play(player, config.getConfigurationSection("sounds.click"));
            openSell(player);
        });
        Tier current = currentUnlock(progress.total());
        for (Tier tier : tiers) {
            String state = stateOf(tier, progress.total(), current);
            ConfigurationSection section = multiplierFile.getConfigurationSection("states." + state);
            double percent = tier.goal <= 0 ? 100 : Math.min(100, (progress.total() / tier.goal) * 100);
            menu.set(tier.slot, Items.fromSection(section, player,
                    "multi", decimal(tier.multiplier),
                    "goal", Amounts.format(tier.goal),
                    "total", Amounts.format(progress.total()),
                    "percent", percentText(percent)
            ), event -> event.setCancelled(true));
        }
        open.put(player.getUniqueId(), MenuKind.MULTI);
        plugin.menus().open(player, menu);
    }

    private void openWorth(Player player, int page) {
        if (prices.isEmpty()) {
            send(player, "no-prices");
            return;
        }
        WorthView view = worthViews.computeIfAbsent(player.getUniqueId(), ignored -> new WorthView());
        List<String> filters = filterKeys();
        if (!filters.contains(view.filter)) view.filter = filters.getFirst();
        if (!SORTS.contains(view.sort)) view.sort = "highest";
        List<Map.Entry<Material, Double>> entries = listed(view.filter, view.sort);
        int pages = Math.max(1, (entries.size() + WORTH_PAGE - 1) / WORTH_PAGE);
        view.page = Math.max(0, Math.min(page, pages - 1));
        Menus.Menu menu = plugin.menus().create(player, Text.apply(
                worthFile.getString("menu.title", "&8Sell Menu | Item Prices &7[%page%/%pages%]"),
                "page", String.valueOf(view.page + 1),
                "pages", String.valueOf(pages)
        ), 6);
        int start = view.page * WORTH_PAGE;
        ConfigurationSection entry = worthFile.getConfigurationSection("entry");
        for (int i = 0; i < WORTH_PAGE && start + i < entries.size(); i++) {
            Map.Entry<Material, Double> item = entries.get(start + i);
            String pretty = Text.pretty(item.getKey().name());
            ItemStack stack = new Items.ItemBuilder(item.getKey())
                    .name(Text.apply(entry == null ? "&f%item%" : entry.getString("name", "&f%item%"), "item", pretty, "price", Amounts.format(item.getValue())))
                    .lore(Text.applyList(entry == null ? List.of() : entry.getStringList("lore"), "item", pretty, "price", Amounts.format(item.getValue())))
                    .hideAll()
                    .build();
            menu.set(i, stack, event -> event.setCancelled(true));
        }
        ConfigurationSection buttons = worthFile.getConfigurationSection("buttons");
        bindNav(menu, player, buttons, "previous", () -> openWorth(player, view.page - 1));
        bindNav(menu, player, buttons, "next", () -> openWorth(player, view.page + 1));
        bindNav(menu, player, buttons, "refresh", () -> openWorth(player, view.page));
        if (buttons != null) {
            ConfigurationSection sorting = buttons.getConfigurationSection("sorting");
            if (sorting != null) {
                menu.set(sorting.getInt("slot", 48), labeled(sorting, view.sort), event -> {
                    event.setCancelled(true);
                    Sounds.play(player, config.getConfigurationSection("sounds.click"));
                    view.sort = cycle(SORTS, view.sort, !event.isRightClick());
                    openWorth(player, view.page);
                });
            }
            ConfigurationSection filter = buttons.getConfigurationSection("filter");
            if (filter != null) {
                menu.set(filter.getInt("slot", 50), labeled(filter, view.filter), event -> {
                    event.setCancelled(true);
                    Sounds.play(player, config.getConfigurationSection("sounds.click"));
                    view.filter = cycle(filters, view.filter, !event.isRightClick());
                    openWorth(player, 0);
                });
            }
        }
        open.put(player.getUniqueId(), MenuKind.WORTH);
        plugin.menus().open(player, menu);
    }

    private void openHistory(Player player, UUID target, String name, int page) {
        List<Sale> sales = loadHistory(target);
        if (sales.isEmpty()) {
            send(player, "history-empty");
            return;
        }
        int pages = Math.max(1, (sales.size() + HISTORY_PAGE - 1) / HISTORY_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        historyViews.put(player.getUniqueId(), new HistoryView(target, name, current));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(cfg("history.time-format", "dd.MM.yyyy HH:mm"))
                .withZone(ZoneId.systemDefault());
        Menus.Menu menu = plugin.menus().create(player, Text.apply(cfg("history.title", "&8Sell History [%page%]"),
                "page", String.valueOf(current + 1)), 6);
        int start = current * HISTORY_PAGE;
        ConfigurationSection entry = config.getConfigurationSection("history.entry");
        for (int i = 0; i < HISTORY_PAGE && start + i < sales.size(); i++) {
            Sale sale = sales.get(start + i);
            Material material = Sounds.material(sale.item, Material.STONE);
            String pretty = Text.pretty(sale.item);
            String time = formatter.format(Instant.ofEpochMilli(sale.at));
            ItemStack stack = new Items.ItemBuilder(material)
                    .amount(Math.max(1, Math.min(64, sale.amount)))
                    .name(Text.apply(entry == null ? "&#80ee0b&l%amount%x %item%" : entry.getString("name", "%item%"),
                            "amount", String.valueOf(sale.amount), "item", pretty, "player", name,
                            "base", Amounts.format(sale.base), "multi", decimal(sale.multi),
                            "total", Amounts.format(sale.total), "time", time))
                    .lore(Text.applyList(entry == null ? List.of() : entry.getStringList("lore"),
                            "amount", String.valueOf(sale.amount), "item", pretty, "player", name,
                            "base", Amounts.format(sale.base), "multi", decimal(sale.multi),
                            "total", Amounts.format(sale.total), "time", time))
                    .hideAll()
                    .build();
            menu.set(i, stack, event -> event.setCancelled(true));
        }
        bindNav(menu, player, config.getConfigurationSection("history"), "previous", () -> openHistory(player, target, name, current - 1));
        bindNav(menu, player, config.getConfigurationSection("history"), "next", () -> openHistory(player, target, name, current + 1));
        open.put(player.getUniqueId(), MenuKind.HISTORY);
        plugin.menus().open(player, menu);
    }

    private void bindNav(Menus.Menu menu, Player player, ConfigurationSection parent, String key, Runnable action) {
        if (parent == null) return;
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) return;
        menu.set(section.getInt("slot", 0), Items.fromSection(section, player), event -> {
            event.setCancelled(true);
            Sounds.play(player, config.getConfigurationSection("sounds.click"));
            action.run();
        });
    }

    private ItemStack labeled(ConfigurationSection section, String current) {
        List<String> lore = new ArrayList<>(section.getStringList("lore"));
        ConfigurationSection names = section.getConfigurationSection("names");
        if (names != null) {
            String active = section.getString("active", "%name%");
            String inactive = section.getString("inactive", "%name%");
            for (String key : names.getKeys(false)) {
                String line = key.equalsIgnoreCase(current) ? active : inactive;
                lore.add(Text.apply(line, "name", names.getString(key, key)));
            }
        }
        return new Items.ItemBuilder(Sounds.material(section.getString("material", "STONE"), Material.STONE))
                .name(section.getString("name", " "))
                .lore(lore)
                .hideAll()
                .build();
    }

    private void sellFrom(Player player, Inventory inventory, int last) {
        List<ItemStack> leftover = new ArrayList<>();
        List<ItemStack> taken = new ArrayList<>();
        List<Sale> sales = new ArrayList<>();
        double baseTotal = 0;
        double payTotal = 0;
        int items = 0;
        double multi = multiplier(player);
        for (int slot = 0; slot < last; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (empty(stack)) continue;
            inventory.setItem(slot, null);
            if (!sellable(stack)) {
                leftover.add(stack);
                continue;
            }
            double base = price(stack.getType()) * stack.getAmount();
            double paid = payout(stack.getType(), stack.getAmount(), multi);
            baseTotal += base;
            payTotal += paid;
            items += stack.getAmount();
            taken.add(stack);
            sales.add(new Sale(stack.getType().name(), stack.getAmount(), base, multi, paid, System.currentTimeMillis()));
        }
        for (int slot = last; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
        }
        if (sales.isEmpty()) {
            leftover.forEach(item -> give(player, item));
            send(player, "nothing-sold");
            if (!leftover.isEmpty()) {
                send(player, "not-sellable");
                Sounds.play(player, config.getConfigurationSection("sounds.error"));
            }
            return;
        }
        double max = config.getDouble("rules.max-payout", 0);
        if (max > 0 && payTotal > max) {
            taken.forEach(item -> give(player, item));
            leftover.forEach(item -> give(player, item));
            send(player, "too-much", "total", Amounts.format(max));
            Sounds.play(player, config.getConfigurationSection("sounds.error"));
            return;
        }
        EconomyService economy = economy();
        if (economy == null) {
            taken.forEach(item -> give(player, item));
            leftover.forEach(item -> give(player, item));
            send(player, "no-economy");
            Sounds.play(player, config.getConfigurationSection("sounds.error"));
            return;
        }
        if (!economy.add(player.getUniqueId(), payTotal)) {
            taken.forEach(item -> give(player, item));
            leftover.forEach(item -> give(player, item));
            send(player, "sell-failed");
            Sounds.play(player, config.getConfigurationSection("sounds.error"));
            return;
        }
        leftover.forEach(item -> give(player, item));
        addProgress(player.getUniqueId(), baseTotal, payTotal);
        recordHistory(player, sales);
        String key = multi == 1.0 ? "sold" : "sold-multiplied";
        String bar = multi == 1.0 ? "sold-bar" : "sold-multiplied-bar";
        send(player, key, "items", String.valueOf(items), "total", Amounts.format(payTotal), "multi", decimal(multi));
        String barText = Text.apply(cfg("messages." + bar, ""), "items", String.valueOf(items),
                "total", Amounts.format(payTotal), "multi", decimal(multi));
        if (!barText.isBlank()) player.sendActionBar(ColorUtil.parse(barText));
        Sounds.play(player, config.getConfigurationSection("sounds.sell"));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (open.get(player.getUniqueId()) != MenuKind.SELL) return;
        int last = (Math.max(2, Math.min(6, config.getInt("menu.rows", 5))) - 1) * 9;
        for (int slot : event.getRawSlots()) {
            if (slot >= last && slot < last + 9) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean sellable(ItemStack stack) {
        if (empty(stack)) return false;
        Material material = stack.getType();
        if (blacklist.contains(material) || price(material) <= 0) return false;
        if (!config.getBoolean("rules.plain-only", true) || ignoreData.contains(material)) return true;
        if (!stack.getEnchantments().isEmpty()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return true;
        if (meta.hasDisplayName() || meta.hasItemName()) return false;
        if (meta instanceof Damageable damageable && damageable.hasDamage() && damageable.getDamage() > 0) return false;
        if (meta instanceof EnchantmentStorageMeta storage && storage.hasStoredEnchants()) return false;
        if (meta instanceof BundleMeta bundle && !bundle.getItems().isEmpty()) return false;
        if (meta instanceof BlockStateMeta blockMeta && blockMeta.hasBlockState()
                && blockMeta.getBlockState() instanceof InventoryHolder holder) {
            Inventory contents = holder instanceof Container container ? container.getInventory() : holder.getInventory();
            for (ItemStack inside : contents.getContents()) {
                if (!empty(inside)) return false;
            }
        }
        return true;
    }

    private double price(Material material) {
        return prices.getOrDefault(material, 0D);
    }

    private double payout(Material material, int amount, double multi) {
        double listed = price(material) * amount * multi;
        Double shop = shopPrices.get(material);
        double ceiling = config.getDouble("rules.shop-ceiling", 0);
        if (shop == null || ceiling <= 0) return listed;
        return Math.min(listed, shop * ceiling * amount);
    }

    private boolean multiplierEnabled() {
        return multiplierFile.getBoolean("enabled", true);
    }

    private double multiplier(Player player) {
        return progress(player).multiplier();
    }

    private Progress progress(Player player) {
        Stats value = stats(player.getUniqueId());
        double total = "PAID".equalsIgnoreCase(multiplierFile.getString("progress-counts", "BASE"))
                ? value.paid : value.soldBase;
        if (!multiplierEnabled() || tiers.isEmpty()) {
            return new Progress(1.0, 1.0, total, 100);
        }
        double multi = 1.0;
        Tier next = null;
        for (Tier tier : tiers) {
            if (total >= tier.goal) multi = tier.multiplier;
            else if (next == null) next = tier;
        }
        if (next == null) return new Progress(multi, multi, total, 100);
        double percent = next.goal <= 0 ? 100 : Math.min(100, (total / next.goal) * 100);
        return new Progress(multi, next.multiplier, total, percent);
    }

    private Tier currentUnlock(double total) {
        for (Tier tier : tiers) {
            if (total < tier.goal) return tier;
        }
        return null;
    }

    private String stateOf(Tier tier, double total, Tier current) {
        if (total >= tier.goal) return "unlocked";
        if (current != null && current == tier) return "current";
        return "locked";
    }

    private List<Map.Entry<Material, Double>> listed(String filter, String sort) {
        List<Map.Entry<Material, Double>> entries = new ArrayList<>();
        for (Map.Entry<Material, Double> entry : prices.entrySet()) {
            if (!"all".equals(filter) && !categoryOf(entry.getKey()).equals(filter)) continue;
            entries.add(entry);
        }
        Comparator<Map.Entry<Material, Double>> comparator = switch (sort) {
            case "lowest" -> Map.Entry.comparingByValue();
            case "name" -> Comparator.comparing(entry -> entry.getKey().name());
            default -> Map.Entry.<Material, Double>comparingByValue().reversed();
        };
        entries.sort(comparator);
        return entries;
    }

    private List<String> filterKeys() {
        ConfigurationSection names = worthFile.getConfigurationSection("buttons.filter.names");
        if (names == null) return List.of("all");
        return new ArrayList<>(names.getKeys(false));
    }

    private String categoryOf(Material material) {
        String mapped = categories.get(material);
        if (mapped != null && !mapped.isBlank()) return mapped.toLowerCase(Locale.ROOT);
        String name = material.name();
        if (name.contains("POTION") || name.contains("TIPPED_ARROW") || name.equals("GLASS_BOTTLE")) return "potions";
        if (name.contains("BOOK") || name.contains("ENCHANTED_BOOK") || name.contains("WRITABLE") || name.contains("WRITTEN")) {
            return "books";
        }
        if (name.contains("SWORD") || name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS")
                || name.contains("BOOTS") || name.contains("BOW") || name.contains("CROSSBOW") || name.contains("TRIDENT")
                || name.contains("MACE") || name.contains("SHIELD") || name.contains("ARROW") || name.contains("TOTEM")
                || name.contains("HORSE_ARMOR") || name.contains("WOLF_ARMOR") || name.contains("ARMOR_TRIM")) {
            return "combat";
        }
        if (material.isEdible() || name.contains("BEEF") || name.contains("PORK") || name.contains("CHICKEN")
                || name.contains("MUTTON") || name.contains("RABBIT") || name.contains("COD") || name.contains("SALMON")
                || name.contains("STEW") || name.contains("BREAD") || name.contains("COOKIE") || name.contains("CAKE")
                || name.contains("PIE") || name.contains("BERRIES") || name.contains("APPLE") || name.contains("CARROT")
                || name.contains("POTATO") || name.contains("BEETROOT") || name.contains("MELON") || name.contains("HONEY")
                || name.equals("WHEAT") || name.contains("SEEDS") || name.contains("COCOA") || name.equals("SUGAR")
                || name.contains("EGG") || name.contains("MILK") || name.contains("CHORUS")) {
            return "food";
        }
        if (name.contains("PICKAXE") || name.contains("_AXE") || name.contains("SHOVEL") || name.contains("HOE")
                || name.contains("SHEARS") || name.contains("FISHING_ROD") || name.contains("FLINT_AND_STEEL")
                || name.contains("BRUSH") || name.contains("SPYGLASS")) {
            return "tools";
        }
        if (material.isBlock() || name.endsWith("_BLOCK") || name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_PLANKS") || name.endsWith("_LEAVES") || name.endsWith("_ORE")
                || name.contains("CONCRETE") || name.contains("TERRACOTTA") || name.contains("WOOL")
                || name.contains("GLASS") || name.contains("CORAL") || name.contains("DEEPSLATE")
                || name.contains("STONE") || name.contains("DIRT") || name.contains("SAND") || name.contains("GRAVEL")
                || name.contains("BRICK") || name.contains("PRISMARINE") || name.contains("BASALT") || name.contains("TUFF")) {
            return "blocks";
        }
        if (name.contains("INGOT") || name.contains("NUGGET") || name.contains("DUST") || name.contains("REDSTONE")
                || name.contains("DIAMOND") || name.contains("EMERALD") || name.contains("QUARTZ") || name.contains("NETHERITE")
                || name.contains("COAL") || name.contains("COPPER") || name.contains("IRON") || name.contains("GOLD")
                || name.contains("LAPIS") || name.contains("BLAZE") || name.contains("GUNPOWDER") || name.contains("LEATHER")
                || name.contains("STRING") || name.contains("BONE") || name.contains("SLIME") || name.contains("ENDER")
                || name.contains("DYE") || name.contains("NETHER_WART") || name.contains("GLOWSTONE") || name.contains("SHARD")) {
            return "ingredients";
        }
        return "utilities";
    }

    private void checkRecipes(List<Recipe> recipes) {
        int warns = 0;
        for (Recipe recipe : recipes) {
            ItemStack result = recipe.getResult();
            if (empty(result)) continue;
            double output = price(result.getType()) * result.getAmount();
            if (output <= 0) continue;
            double input = inputCost(recipe);
            if (input < 0) continue;
            if (output > input + 0.0001) {
                plugin.getLogger().warning("[sell] Recipe for " + result.getType() + " sells for "
                        + decimal(output) + " but inputs cost " + decimal(input));
                warns++;
            }
        }
        if (warns > 0) {
            plugin.getLogger().warning("[sell] " + warns + " recipes pay more than their inputs.");
        }
    }

    private double inputCost(Recipe recipe) {
        List<RecipeChoice> choices = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            choices.addAll(shaped.getChoiceMap().values());
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            choices.addAll(shapeless.getChoiceList());
        } else if (recipe instanceof CookingRecipe<?> cooking) {
            choices.add(cooking.getInputChoice());
        } else if (recipe instanceof StonecuttingRecipe cutting) {
            choices.add(cutting.getInputChoice());
        } else if (recipe instanceof SmithingRecipe smithing) {
            choices.add(smithing.getBase());
            choices.add(smithing.getAddition());
        } else {
            return -1;
        }
        double total = 0;
        for (RecipeChoice choice : choices) {
            if (choice == null) continue;
            double cost = choiceCost(choice);
            if (cost < 0) return -1;
            total += cost;
        }
        return total;
    }

    private double choiceCost(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice materials) {
            double min = Double.POSITIVE_INFINITY;
            for (Material material : materials.getChoices()) {
                double value = price(material);
                if (value > 0) min = Math.min(min, value);
            }
            return min == Double.POSITIVE_INFINITY ? -1 : min;
        }
        if (choice instanceof RecipeChoice.ExactChoice exact) {
            double min = Double.POSITIVE_INFINITY;
            for (ItemStack stack : exact.getChoices()) {
                if (empty(stack)) continue;
                double value = price(stack.getType()) * Math.max(1, stack.getAmount());
                if (value > 0) min = Math.min(min, value);
            }
            return min == Double.POSITIVE_INFINITY ? -1 : min;
        }
        return -1;
    }

    private Stats stats(UUID uuid) {
        return stats.computeIfAbsent(uuid, id -> {
            try {
                Stats loaded = sqlite.query("SELECT sold_base, paid FROM sell_stats WHERE uuid = ?", rs -> {
                    try {
                        if (!rs.next()) return new Stats();
                        return new Stats(rs.getDouble("sold_base"), rs.getDouble("paid"));
                    } catch (SQLException ex) {
                        return new Stats();
                    }
                }, id.toString());
                return loaded == null ? new Stats() : loaded;
            } catch (SQLException ex) {
                return new Stats();
            }
        });
    }

    private void addProgress(UUID uuid, double base, double paid) {
        Stats value = stats(uuid);
        value.soldBase += base;
        value.paid += paid;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("""
                        INSERT INTO sell_stats (uuid, sold_base, paid) VALUES (?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET sold_base = excluded.sold_base, paid = excluded.paid
                        """, uuid.toString(), value.soldBase, value.paid);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save sell stats", ex);
            }
        });
    }

    private void recordHistory(Player player, List<Sale> sales) {
        List<Sale> copy = List.copyOf(sales);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Sale sale : copy) {
                try {
                    sqlite.execute("""
                            INSERT INTO sell_history (uuid, name, item, amount, base, multi, total, at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """, player.getUniqueId().toString(), player.getName(), sale.item, sale.amount,
                            sale.base, sale.multi, sale.total, sale.at);
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save sell history", ex);
                }
            }
        });
    }

    private List<Sale> loadHistory(UUID uuid) {
        try {
            return sqlite.query("SELECT item, amount, base, multi, total, at FROM sell_history WHERE uuid = ? ORDER BY at DESC", rs -> {
                List<Sale> sales = new ArrayList<>();
                try {
                    while (rs.next()) {
                        sales.add(new Sale(
                                rs.getString("item"),
                                rs.getInt("amount"),
                                rs.getDouble("base"),
                                rs.getDouble("multi"),
                                rs.getDouble("total"),
                                rs.getLong("at")
                        ));
                    }
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to read sell history", ex);
                }
                return sales;
            }, uuid.toString());
        } catch (SQLException ex) {
            return List.of();
        }
    }

    private void pruneHistory() {
        long days = config.getLong("history.keep-days", 14);
        if (days <= 0) return;
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("DELETE FROM sell_history WHERE at < ?", cutoff);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to prune sell history", ex);
            }
        });
    }

    private EconomyService economy() {
        EconomyModule module = plugin.modules().get(EconomyModule.class);
        return module == null ? null : module.service();
    }

    private void give(Player player, ItemStack item) {
        if (empty(item)) return;
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private static boolean empty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    private static String decimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) return String.valueOf((long) Math.rint(value));
        String text = String.format(Locale.US, "%.2f", value);
        if (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private static String percentText(double percent) {
        return String.valueOf((int) Math.round(Math.max(0, Math.min(100, percent))));
    }

    private static String cycle(List<String> options, String current, boolean forward) {
        if (options.isEmpty()) return current;
        int index = options.indexOf(current);
        if (index < 0) index = 0;
        index = (index + (forward ? 1 : -1) + options.size()) % options.size();
        return options.get(index);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("sellhistory") && args.length == 1
                && sender.hasPermission("shardedcore.sell.history.admin")) {
            return Tabs.players(args[0]);
        }
        return List.of();
    }

    private enum MenuKind {SELL, MULTI, WORTH, HISTORY}

    private record Tier(String id, double multiplier, double goal, int slot) {
    }

    private record Progress(double multiplier, double nextMultiplier, double total, double percent) {
    }

    private record Sale(String item, int amount, double base, double multi, double total, long at) {
    }

    private static final class Stats {
        private double soldBase;
        private double paid;

        private Stats() {
        }

        private Stats(double soldBase, double paid) {
            this.soldBase = soldBase;
            this.paid = paid;
        }
    }

    private static final class WorthView {
        private int page;
        private String sort = "highest";
        private String filter = "all";
    }

    private record HistoryView(UUID uuid, String name, int page) {
    }
}

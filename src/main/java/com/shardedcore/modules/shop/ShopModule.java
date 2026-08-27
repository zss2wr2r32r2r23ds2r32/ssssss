package com.shardedcore.modules.shop;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.GuiButtons;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Items;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ShopModule extends Module implements CommandExecutor {

    private FileConfiguration buying;
    private final Map<String, FileConfiguration> sections = new HashMap<>();
    private final Map<UUID, Long> clicks = new ConcurrentHashMap<>();
    private Sqlite sqlite;

    public ShopModule(ShardedCore plugin) {
        super(plugin, "shop");
    }

    @Override
    protected void extraFiles() {
        extraFile("buyingmenu.yml");
        for (String section : List.of("blockshop", "farmshop", "gearshop", "mobdrops", "redstoneshop", "premiumshop", "spawnershop")) {
            Configs.saveDefault(plugin, "modules/shop/" + section + "/config.yml", new File(folder, section + "/config.yml"));
        }
    }

    @Override
    public void enable() {
        buying = Configs.load(new File(folder, "buyingmenu.yml"));
        sections.clear();
        ConfigurationSection listed = config.getConfigurationSection("sections");
        if (listed != null) {
            for (String id : listed.getKeys(false)) {
                File file = new File(folder, id + "/config.yml");
                if (file.exists()) sections.put(id, Configs.load(file));
            }
        }
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS shop_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        player TEXT NOT NULL,
                        section TEXT NOT NULL,
                        item TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        price REAL NOT NULL,
                        at INTEGER NOT NULL
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create shop_history", ex);
        }
        registerCommand("shop", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        extraFiles();
        buying = Configs.load(new File(folder, "buyingmenu.yml"));
        sections.clear();
        ConfigurationSection listed = config.getConfigurationSection("sections");
        if (listed != null) {
            for (String id : listed.getKeys(false)) {
                File file = new File(folder, id + "/config.yml");
                if (file.exists()) sections.put(id, Configs.load(file));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        openMain(player);
        return true;
    }

    private void openMain(Player player) {
        Menus.Menu menu = plugin.menus().create(player, cfg("menu.title", "&8Shop Menu"), config.getInt("menu.rows", 4));
        ConfigurationSection sectionsCfg = config.getConfigurationSection("sections");
        if (sectionsCfg == null) {
            send(player, "no-sections");
            return;
        }
        for (String id : sectionsCfg.getKeys(false)) {
            ConfigurationSection section = sectionsCfg.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            String permission = section.getString("permission", "");
            if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) continue;
            menu.set(section.getInt("slot", 0), Items.fromSection(section.getConfigurationSection("icon"), player), event -> {
                event.setCancelled(true);
                String command = section.getString("command", "");
                if (command != null && !command.isBlank()) {
                    player.closeInventory();
                    player.performCommand(command);
                    return;
                }
                openSection(player, id, 1);
            });
        }
        if (config.getBoolean("frame.enabled", true)) {
            menu.fill(Items.fromSection(config.getConfigurationSection("frame"), player));
        }
        plugin.menus().open(player, menu);
        Sounds.play(player, config.getConfigurationSection("menu.open-sound"));
    }

    private void openSection(Player player, String id, int page) {
        FileConfiguration data = sections.get(id);
        if (data == null) {
            send(player, "no-sections");
            return;
        }
        int pages = Math.max(1, data.getInt("menu.pages", 1));
        ConfigurationSection items = data.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                pages = Math.max(pages, items.getConfigurationSection(key) == null ? 1 : items.getInt(key + ".page", 1));
            }
        }
        int current = Math.max(1, Math.min(page, pages));
        Menus.Menu menu = plugin.menus().create(player, data.getString("menu.title", "&8Shop"), data.getInt("menu.rows", 6));
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null || item.getInt("page", 1) != current) continue;
                menu.set(item.getInt("slot", 0), shopIcon(player, item), event -> {
                    event.setCancelled(true);
                    openBuy(player, id, key, item, 1);
                });
            }
        }
        ConfigurationSection buttons = data.getConfigurationSection("buttons");
        if (buttons != null) {
            if (buttons.isConfigurationSection("return")) {
                menu.set(buttons.getInt("return.slot", GuiButtons.slot("back", 49)),
                        GuiButtons.back(player), event -> {
                    event.setCancelled(true);
                    openMain(player);
                });
            }
            if (current > 1 && buttons.isConfigurationSection("previous")) {
                menu.set(buttons.getInt("previous.slot", GuiButtons.slot("previous", 48)),
                        GuiButtons.previous(player), event -> {
                    event.setCancelled(true);
                    openSection(player, id, current - 1);
                });
            }
            if (current < pages && buttons.isConfigurationSection("next")) {
                menu.set(buttons.getInt("next.slot", GuiButtons.slot("next", 50)),
                        GuiButtons.next(player), event -> {
                    event.setCancelled(true);
                    openSection(player, id, current + 1);
                });
            }
        }
        GuiButtons.fill(menu);
        plugin.menus().open(player, menu);
        Sounds.play(player, config.getConfigurationSection("sounds.click"));
    }

    private void openBuy(Player player, String sectionId, String itemId, ConfigurationSection item, int amount) {
        int max = Math.max(1, buying.getInt("max-amount", 64));
        int current = Math.max(1, Math.min(amount, max));
        double price = item.getDouble("price", 0);
        String label = itemName(item);
        Menus.Menu menu = plugin.menus().create(player, buying.getString("title", "&8Shop Menu | Buying"), buying.getInt("rows", 3));
        menu.set(buying.getInt("item.slot", 13), labeled(player, item, buying.getConfigurationSection("item"), current, price, label));
        ConfigurationSection buttons = buying.getConfigurationSection("buttons");
        if (buttons != null) {
            button(menu, buttons, "set-min", player, current, price, label, event -> openBuy(player, sectionId, itemId, item, 1));
            button(menu, buttons, "remove-10", player, current, price, label, event -> openBuy(player, sectionId, itemId, item, current - 10));
            button(menu, buttons, "remove-1", player, current, price, label, event -> openBuy(player, sectionId, itemId, item, current - 1));
            button(menu, buttons, "add-1", player, current, price, label, event -> openBuy(player, sectionId, itemId, item, current + 1));
            button(menu, buttons, "add-10", player, current, price, label, event -> openBuy(player, sectionId, itemId, item, current + 10));
            button(menu, buttons, "set-max", player, current, price, label, event -> openBuy(player, sectionId, itemId, item, max));
            menu.set(buttons.getInt("cancel.slot", GuiButtons.slot("cancel", 11)), GuiButtons.cancel(player), event -> {
                event.setCancelled(true);
                openSection(player, sectionId, item.getInt("page", 1));
            });
            menu.set(buttons.getInt("confirm.slot", GuiButtons.slot("confirm", 15)), GuiButtons.confirm(player), event -> {
                event.setCancelled(true);
                buy(player, sectionId, item, current);
            });
        }
        GuiButtons.fill(menu);
        plugin.menus().open(player, menu);
    }

    private void button(Menus.Menu menu, ConfigurationSection buttons, String id, Player player, int amount, double price,
                        String label, java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> click) {
        ConfigurationSection section = buttons.getConfigurationSection(id);
        if (section == null) return;
        menu.set(section.getInt("slot", 0), labeled(player, section, section, amount, price, label), event -> {
            event.setCancelled(true);
            click.accept(event);
        });
    }

    private void buy(Player player, String sectionId, ConfigurationSection item, int amount) {
        long wait = player.hasPermission("shardedcore.shop.fastbuy")
                ? config.getLong("fast-buy-millis", 50)
                : 200L;
        Long last = clicks.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < wait) return;
        clicks.put(player.getUniqueId(), System.currentTimeMillis());
        EconomyModule economy = plugin.modules().get(EconomyModule.class);
        if (economy == null) {
            send(player, "no-economy");
            return;
        }
        double total = item.getDouble("price", 0) * amount;
        if (!economy.service().take(player.getUniqueId(), total)) {
            send(player, "cannot-afford", "total", Amounts.commas(total));
            Sounds.play(player, config.getConfigurationSection("sounds.error"));
            return;
        }
        List<String> commands = new ArrayList<>(item.getStringList("commands"));
        if (item.isString("command") && item.getString("command") != null && !item.getString("command").isBlank()) {
            commands.add(item.getString("command"));
        }
        if (!commands.isEmpty()) {
            for (String line : commands) {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(),
                        line.replace("%player%", player.getName()));
            }
        } else {
            ItemStack stack = new ItemStack(Sounds.material(item.getString("material", "STONE"), Material.STONE), amount);
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            }
        }
        send(player, "bought", "amount", String.valueOf(amount), "item", itemName(item), "total", Amounts.commas(total));
        Sounds.play(player, config.getConfigurationSection("sounds.buy"));
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("""
                        INSERT INTO shop_history (uuid, player, section, item, amount, price, at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, player.getUniqueId().toString(), player.getName(), sectionId, itemName(item), amount, total,
                        System.currentTimeMillis());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to save shop history", ex);
            }
        });
        player.closeInventory();
    }

    private ItemStack shopIcon(Player player, ConfigurationSection item) {
        ConfigurationSection template = config.getConfigurationSection("item-template");
        List<String> lore = item.getStringList("lore");
        if (lore.isEmpty() && template != null) lore = template.getStringList("lore");
        String name = item.getString("name", template == null ? "&f%item%" : template.getString("name", "&f%item%"));
        name = Text.apply(name, "item", itemName(item), "price", Amounts.commas(item.getDouble("price", 0)));
        List<String> out = new ArrayList<>();
        for (String line : lore) {
            out.add(Text.apply(line, "item", itemName(item), "price", Amounts.commas(item.getDouble("price", 0))));
        }
        return Items.named(Sounds.material(item.getString("material", "STONE"), Material.STONE), name, out);
    }

    private ItemStack labeled(Player player, ConfigurationSection source, ConfigurationSection display, int amount, double price, String label) {
        if (display == null) display = source;
        double total = price * amount;
        ItemStack stack = Items.fromSection(display, player,
                "item", label,
                "amount", String.valueOf(amount),
                "price", Amounts.commas(price),
                "total", Amounts.commas(total));
        if (source != null && source.contains("material")) {
            stack.setType(Sounds.material(source.getString("material"), stack.getType()));
        }
        int shown = display.getInt("amount", 0);
        if (shown > 0) stack.setAmount(Math.min(64, shown));
        return stack;
    }

    private String itemName(ConfigurationSection item) {
        if (item.isString("name") && item.getString("name") != null && !item.getString("name").contains("%")) {
            return item.getString("name");
        }
        return Text.pretty(item.getString("material", "STONE"));
    }
}

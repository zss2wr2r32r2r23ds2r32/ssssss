package com.shardedcore.modules.crystals;

import com.shardedcore.data.TimedPerks;
import com.shardedcore.ShardedCore;
import com.shardedcore.gui.GuiButtons;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.chatcolor.ChatColorModule;
import com.shardedcore.modules.crates.CratesModule;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.modules.glows.GlowsModule;
import com.shardedcore.modules.shardedtools.ShardedToolsModule;
import com.shardedcore.modules.tags.TagsModule;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.Items;
import com.shardedcore.util.Players;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CrystalsModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private CrystalService service;

    public CrystalsModule(ShardedCore plugin) {
        super(plugin, "crystals");
    }

    public CrystalService service() {
        return service;
    }

    @Override
    public void enable() {
        service = new CrystalService(plugin, plugin.toggles().sqlite(), config.getDouble("starting-balance", 0));
        TimedPerks.ensureTable(plugin.toggles().sqlite());
        registerCommand("crystal", this);
        registerCommand("crystalshop", this);
        registerListener(this);
        for (Player player : Bukkit.getOnlinePlayers()) TimedPerks.apply(player);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        TimedPerks.apply(event.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("crystalshop") || (args.length > 0 && args[0].equalsIgnoreCase("shop"))) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            openMain(player);
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            send(player, "self", "amount", service.format(service.get(player.getUniqueId())));
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> admin(sender, args, true);
            case "remove", "take" -> admin(sender, args, false);
            case "set" -> set(sender, args);
            case "bal", "balance" -> balance(sender, args);
            default -> {
                send(sender, "usage");
                yield true;
            }
        };
    }

    private boolean balance(CommandSender sender, String[] args) {
        OfflinePlayer target = args.length < 2
                ? (sender instanceof Player player ? player : null)
                : Players.offline(args[1]);
        if (target == null) {
            send(sender, "players-only");
            return true;
        }
        send(sender, sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId()) ? "self" : "other",
                "player", Players.name(target),
                "amount", service.format(service.get(target.getUniqueId())));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args, boolean give) {
        if (!sender.hasPermission("shardedcore.crystal.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            send(sender, give ? "usage-give" : "usage-remove");
            return true;
        }
        OfflinePlayer target = Players.offline(args[1]);
        if (target == null || target.getUniqueId() == null) {
            send(sender, "player-missing");
            return true;
        }
        double amount = Amounts.parse(args[2]);
        if (amount <= 0) {
            send(sender, "invalid-amount");
            return true;
        }
        if (give) service.add(target.getUniqueId(), amount);
        else if (!service.take(target.getUniqueId(), amount)) {
            send(sender, "cannot-afford");
            return true;
        }
        send(sender, give ? "gave" : "removed",
                "player", Players.name(target),
                "amount", service.format(amount));
        Player online = target.getPlayer();
        if (online != null) {
            send(online, give ? "received" : "lost", "amount", service.format(amount));
        }
        return true;
    }

    private boolean set(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.crystal.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            send(sender, "usage-set");
            return true;
        }
        OfflinePlayer target = Players.offline(args[1]);
        if (target == null) {
            send(sender, "player-missing");
            return true;
        }
        service.set(target.getUniqueId(), Amounts.parse(args[2]));
        send(sender, "set", "player", Players.name(target), "amount", service.format(service.get(target.getUniqueId())));
        return true;
    }

    private void openMain(Player player) {
        Menus.Menu menu = plugin.menus().create(player, cfg("menu.title", "Crystal Shop"), config.getInt("menu.rows", 4));
        ConfigurationSection sections = config.getConfigurationSection("sections");
        if (sections != null) {
            for (String id : sections.getKeys(false)) {
                ConfigurationSection section = sections.getConfigurationSection(id);
                if (section == null || !section.getBoolean("enabled", true)) continue;
                String permission = section.getString("permission", "");
                if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) continue;
                menu.set(section.getInt("slot", 0), Items.fromSection(section, player,
                        "crystals", service.format(service.get(player.getUniqueId()))), event -> {
                    event.setCancelled(true);
                    openSection(player, id);
                });
            }
        }
        ConfigurationSection info = config.getConfigurationSection("menu.info");
        if (info != null) {
            menu.set(info.getInt("slot", 13), headOrItem(player, info));
        }
        if (config.getBoolean("menu.filler.enabled", true)) {
            GuiButtons.glass(menu, config.getBoolean("menu.filler.border-only", true));
        }
        plugin.menus().open(player, menu);
        Sounds.play(player, config.getConfigurationSection("sounds.open"));
    }

    private ItemStack headOrItem(Player player, ConfigurationSection info) {
        if ("PLAYER_HEAD".equalsIgnoreCase(info.getString("material", ""))) {
            return Items.head(player, Text.apply(info.getString("name", "&#00A2FF&lCRYSTALS"),
                    "crystals", service.format(service.get(player.getUniqueId()))),
                    Text.applyList(new ArrayList<>(info.getStringList("lore")),
                            "crystals", service.format(service.get(player.getUniqueId()))));
        }
        return Items.fromSection(info, player, "crystals", service.format(service.get(player.getUniqueId())));
    }

    private void openSection(Player player, String sectionId) {
        ConfigurationSection section = config.getConfigurationSection("sections." + sectionId);
        if (section == null) return;
        ConfigurationSection items = section.getConfigurationSection("items");
        Menus.Menu menu = plugin.menus().create(player,
                section.getString("title", cfg("menu.title", "&8Crystal Shop")),
                section.getInt("rows", 4));
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(id);
                if (item == null) continue;
                menu.set(item.getInt("slot", 0), Items.fromSection(item, player,
                        "price", service.format(item.getDouble("price", 0)),
                        "crystals", service.format(service.get(player.getUniqueId()))), event -> {
                    event.setCancelled(true);
                    openConfirm(player, sectionId, id, item);
                });
            }
        }
        ConfigurationSection info = section.getConfigurationSection("info");
        if (info != null) {
            menu.set(info.getInt("slot", 22), headOrItem(player, info));
        }
        int backSlot = section.contains("back.slot") ? section.getInt("back.slot")
                : section.getInt("back-slot", Math.max(0, menu.inventory().getSize() - 5));
        GuiButtons.placeBack(menu, player, backSlot, () -> openMain(player));
        GuiButtons.glass(menu, section.getBoolean("border-only",
                idEqualsAny(sectionId, "tags", "chatcolors", "glows", "keys")));
        plugin.menus().open(player, menu);
    }

    private boolean idEqualsAny(String id, String... options) {
        for (String option : options) {
            if (option.equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    private void openConfirm(Player player, String sectionId, String itemId, ConfigurationSection item) {
        ConfigurationSection confirm = config.getConfigurationSection("confirm");
        int rows = confirm == null ? 3 : confirm.getInt("rows", 3);
        Menus.Menu menu = plugin.menus().create(player,
                confirm == null ? "&8Confirm Purchase" : confirm.getString("title", "&8Confirm Purchase"), rows);
        String name = item.getString("name", itemId);
        String price = service.format(item.getDouble("price", 0));
        int itemSlot = confirm == null ? 13 : confirm.getInt("item-slot", 13);
        menu.set(itemSlot, Items.fromSection(item, player, "price", price,
                "crystals", service.format(service.get(player.getUniqueId()))));
        ConfigurationSection yes = confirm == null ? null : confirm.getConfigurationSection("confirm");
        ConfigurationSection no = confirm == null ? null : confirm.getConfigurationSection("cancel");
        menu.set(yes == null ? 11 : yes.getInt("slot", 11),
                yes == null ? GuiButtons.confirm(player, "item", name, "price", price)
                        : Items.fromSection(yes, player, "item", name, "price", price),
                event -> {
                    event.setCancelled(true);
                    buy(player, sectionId, itemId, item);
                });
        menu.set(no == null ? 15 : no.getInt("slot", 15),
                no == null ? GuiButtons.cancel(player, "item", name, "price", price)
                        : Items.fromSection(no, player, "item", name, "price", price),
                event -> {
                    event.setCancelled(true);
                    openSection(player, sectionId);
                });
        if (confirm == null || confirm.getBoolean("filler.enabled", true)) {
            ConfigurationSection filler = confirm == null ? null : confirm.getConfigurationSection("filler");
            menu.fill(filler == null
                    ? Items.named(Material.BLACK_STAINED_GLASS_PANE, " ", List.of())
                    : Items.fromSection(filler, player));
        }
        plugin.menus().open(player, menu);
    }

    private void buy(Player player, String sectionId, String itemId, ConfigurationSection item) {
        double price = item.getDouble("price", 0);
        if (!service.take(player.getUniqueId(), price)) {
            send(player, "cannot-afford", "amount", service.format(price));
            Sounds.play(player, config.getConfigurationSection("sounds.error"));
            return;
        }
        if (!grant(player, item)) {
            service.add(player.getUniqueId(), price);
            send(player, "failed");
            return;
        }
        send(player, "bought", "item", item.getString("name", itemId), "amount", service.format(price));
        Sounds.play(player, config.getConfigurationSection("sounds.buy"));
        openSection(player, sectionId);
    }

    private boolean grant(Player player, ConfigurationSection item) {
        String type = item.getString("type", "item").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "tag" -> {
                TagsModule tags = plugin.modules().get(TagsModule.class);
                if (tags == null) yield false;
                yield tags.unlock(player.getUniqueId(), item.getString("tag", ""));
            }
            case "chatcolor", "color" -> {
                ChatColorModule colors = plugin.modules().get(ChatColorModule.class);
                if (colors == null) yield false;
                String name = item.getString("color", item.getString("chatcolor", ""));
                yield colors.unlock(player.getUniqueId(), name);
            }
            case "tool", "drill", "chopper", "firework", "sellwand" -> {
                ShardedToolsModule tools = plugin.modules().get(ShardedToolsModule.class);
                if (tools == null) yield false;
                yield tools.give(player, item.getString("tool", type), item.getString("expire", null));
            }
            case "key", "crate-key", "cratekey" -> {
                CratesModule crates = plugin.modules().get(CratesModule.class);
                if (crates == null) yield false;
                crates.addKeys(player.getUniqueId(), item.getString("crate", "test"), item.getInt("amount", 1));
                yield true;
            }
            case "money" -> {
                EconomyModule economy = plugin.modules().get(EconomyModule.class);
                if (economy == null) yield false;
                yield economy.service().add(player.getUniqueId(), Amounts.parse(String.valueOf(item.get("money", 0))));
            }
            case "glow", "eglow" -> {
                String glow = item.getString("glow", "");
                if (glow == null || glow.isBlank()) {
                    int seconds = Math.max(1, item.getInt("duration-seconds", 60));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, seconds * 20, 0, false, false, true));
                    yield true;
                }
                GlowsModule glows = plugin.modules().get(GlowsModule.class);
                if (glows == null) yield false;
                yield glows.unlock(player.getUniqueId(), glow);
            }
            case "perk" -> TimedPerks.grant(player, item.getString("perk", ""),
                    item.getString("duration", "7d"), item.getString("permission", ""));
            case "spawner" -> {
                give(player, spawner(item));
                yield true;
            }
            case "command" -> {
                for (String line : item.getStringList("commands")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.replace("%player%", player.getName()));
                }
                yield true;
            }
            default -> {
                Material material = Sounds.material(item.getString("give-material", item.getString("material", "STONE")), Material.STONE);
                ItemStack stack = new ItemStack(material, Math.max(1, item.getInt("amount", 1)));
                give(player, stack);
                yield true;
            }
        };
    }

    private ItemStack spawner(ConfigurationSection item) {
        ItemStack stack = new ItemStack(Material.SPAWNER);
        if (stack.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof CreatureSpawner spawner) {
            try {
                spawner.setSpawnedType(EntityType.valueOf(item.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                spawner.setSpawnedType(EntityType.ZOMBIE);
            }
            meta.setBlockState(spawner);
            meta.displayName(com.shardedcore.util.ColorUtil.parse(item.getString("name", "&fSpawner")));
            stack.setItemMeta(meta);
        }
        NamespacedKey key = new NamespacedKey(plugin, "spawner");
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, item.getString("entity", "ZOMBIE")));
        return stack;
    }

    private void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("crystalshop")) return List.of();
        if (args.length == 1) return Tabs.filter(List.of("shop", "give", "remove", "set", "bal"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("set"))) {
            return Tabs.players(args[1]);
        }
        return List.of();
    }
}

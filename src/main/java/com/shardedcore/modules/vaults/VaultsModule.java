package com.shardedcore.modules.vaults;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.combat.CombatModule;
import com.shardedcore.util.Items;
import com.shardedcore.util.Players;
import com.shardedcore.util.Slots;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class VaultsModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private Sqlite sqlite;
    private final Map<String, UUID> inUse = new ConcurrentHashMap<>();
    private final Set<Material> blocked = new HashSet<>();

    public VaultsModule(ShardedCore plugin) {
        super(plugin, "vaults");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS vault_items (
                        uuid TEXT NOT NULL,
                        number INTEGER NOT NULL,
                        slot INTEGER NOT NULL,
                        item TEXT NOT NULL,
                        PRIMARY KEY (uuid, number, slot)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create vault_items", ex);
        }
        blocked.clear();
        for (String name : config.getStringList("blocked-materials")) {
            Material material = Sounds.material(name, null);
            if (material != null) blocked.add(material);
        }
        registerCommand("vault", this);
        registerCommand("pvadmin", this);
        registerListener(this);
    }

    @Override
    public void disable() {
        inUse.clear();
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        boolean adminCmd = command.getName().equalsIgnoreCase("pvadmin");
        if (adminCmd || (args.length >= 1 && args[0].equalsIgnoreCase("view"))) {
            if (!player.hasPermission("shardedcore.vault.admin")) {
                send(player, "no-permission");
                return true;
            }
            int offset = args.length >= 1 && args[0].equalsIgnoreCase("view") ? 1 : 0;
            if (args.length <= offset) {
                send(player, "usage");
                return true;
            }
            String name = args[offset];
            OfflinePlayer target = Players.offline(name);
            if (target == null || target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                send(player, "unknown-player", "player", name);
                return true;
            }
            int number = args.length > offset + 1 ? parseNumber(args[offset + 1]) : 0;
            if (number > 0) openVault(player, target, number, true);
            else openMenu(player, target, true);
            return true;
        }
        if (args.length == 1) {
            int number = parseNumber(args[0]);
            if (number > 0) {
                openVault(player, player, number, false);
                return true;
            }
        }
        openMenu(player, player, false);
        return true;
    }

    private void openMenu(Player viewer, OfflinePlayer owner, boolean admin) {
        int max = Math.max(1, Math.min(45, config.getInt("vaults", 45)));
        List<Integer> slots = Slots.parse(config.getString("menu.vault-slots", "0-44"));
        String title = admin
                ? Text.apply(cfg("menu.admin-title", "&8Private Vaults | %player%"), "player", Players.name(owner))
                : cfg("menu.title", "&8Private Vaults");
        Menus.Menu menu = plugin.menus().create(viewer, title, config.getInt("menu.rows", 6));
        int unlocked = unlockedCount(owner instanceof Player player ? player : null, owner.getUniqueId(), admin);
        Map<Integer, Integer> used = usedSlots(owner.getUniqueId());
        int vaultRows = config.getInt("vault.rows", 6);
        int capacity = vaultRows * 9;
        for (int i = 0; i < max && i < slots.size(); i++) {
            int number = i + 1;
            int slot = slots.get(i);
            boolean open = admin || number <= unlocked;
            ConfigurationSection icon = config.getConfigurationSection(open ? "menu.unlocked" : "menu.locked");
            ItemStack stack = Items.fromSection(icon, viewer instanceof Player ? viewer : null,
                    "number", String.valueOf(number),
                    "used", String.valueOf(used.getOrDefault(number, 0)),
                    "max", String.valueOf(capacity),
                    "player", Players.name(owner));
            menu.set(slot, stack, event -> {
                event.setCancelled(true);
                if (!open) {
                    send(viewer, "locked", "number", String.valueOf(number));
                    sound(viewer, "sounds.error");
                    return;
                }
                openVault(viewer, owner, number, admin);
            });
        }
        ConfigurationSection close = config.getConfigurationSection("menu.close");
        if (close != null && close.getBoolean("enabled", true)) {
            menu.set(close.getInt("slot", 49), Items.fromSection(close, viewer), event -> {
                event.setCancelled(true);
                viewer.closeInventory();
            });
        }
        if (config.getBoolean("menu.filler.enabled", true)) {
            menu.fill(Items.fromSection(config.getConfigurationSection("menu.filler"), viewer));
        }
        plugin.menus().open(viewer, menu);
        sound(viewer, "sounds.open");
    }

    private void openVault(Player viewer, OfflinePlayer owner, int number, boolean admin) {
        int max = Math.max(1, Math.min(45, config.getInt("vaults", 45)));
        if (number < 1 || number > max) {
            send(viewer, "unknown-vault");
            return;
        }
        if (!admin && number > unlockedCount(viewer, viewer.getUniqueId(), false)) {
            send(viewer, "locked", "number", String.valueOf(number));
            sound(viewer, "sounds.error");
            return;
        }
        if (combatBlocked(viewer)) {
            send(viewer, "in-combat");
            sound(viewer, "sounds.error");
            return;
        }
        String key = owner.getUniqueId() + ":" + number;
        UUID current = inUse.get(key);
        if (current != null && !current.equals(viewer.getUniqueId()) && Bukkit.getPlayer(current) != null) {
            send(viewer, "in-use", "number", String.valueOf(number));
            return;
        }
        Map<Integer, ItemStack> items = load(owner.getUniqueId(), number);
        int rows = config.getInt("vault.rows", 6);
        if (items.keySet().stream().anyMatch(slot -> slot >= rows * 9)) {
            send(viewer, "too-small", "number", String.valueOf(number));
            return;
        }
        String title = admin
                ? Text.apply(cfg("vault.admin-title", "&8Vault #%number% | %player%"),
                "number", String.valueOf(number), "player", Players.name(owner))
                : Text.apply(cfg("vault.title", "&8Vault #%number%"), "number", String.valueOf(number),
                "player", Players.name(owner));
        Menus.Menu menu = plugin.menus().create(viewer, title, rows).unlocked();
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            menu.set(entry.getKey(), entry.getValue());
        }
        inUse.put(key, viewer.getUniqueId());
        menu.onAny(event -> {
            if (blockedItem(event)) {
                event.setCancelled(true);
                send(viewer, "blocked-item");
                sound(viewer, "sounds.error");
            }
        });
        menu.onClose(closed -> {
            inUse.remove(key, closed.getUniqueId());
            save(owner.getUniqueId(), number, menu.inventory().getContents());
            sound(closed, "sounds.close");
        });
        plugin.menus().open(viewer, menu);
        sound(viewer, "sounds.vault");
    }

    private boolean blockedItem(InventoryClickEvent event) {
        if (blocked.isEmpty()) return false;
        ItemStack cursor = event.getCursor();
        if (cursor != null && blocked.contains(cursor.getType())) return true;
        ItemStack current = event.getCurrentItem();
        return event.isShiftClick() && current != null && blocked.contains(current.getType());
    }

    private int unlockedCount(Player player, UUID uuid, boolean admin) {
        if (admin) return Math.max(1, Math.min(45, config.getInt("vaults", 45)));
        int unlocked = config.getInt("default-vaults", 0);
        int max = Math.max(1, Math.min(45, config.getInt("vaults", 45)));
        Player online = player != null ? player : Bukkit.getPlayer(uuid);
        if (online == null) return unlocked;
        for (int i = max; i >= 1; i--) {
            if (online.hasPermission("shardedcore.vault." + i)) {
                unlocked = Math.max(unlocked, i);
                break;
            }
        }
        return unlocked;
    }

    private Map<Integer, Integer> usedSlots(UUID uuid) {
        Map<Integer, Integer> used = new HashMap<>();
        try {
            sqlite.query("SELECT number, COUNT(*) AS n FROM vault_items WHERE uuid = ? GROUP BY number", rs -> {
                try {
                    while (rs.next()) used.put(rs.getInt("number"), rs.getInt("n"));
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return used;
            }, uuid.toString());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to count vault slots", ex);
        }
        return used;
    }

    private Map<Integer, ItemStack> load(UUID uuid, int number) {
        Map<Integer, ItemStack> items = new HashMap<>();
        try {
            sqlite.query("SELECT slot, item FROM vault_items WHERE uuid = ? AND number = ?", rs -> {
                try {
                    while (rs.next()) {
                        ItemStack item = Items.deserialize(rs.getString("item"));
                        if (item != null && !item.getType().isAir()) items.put(rs.getInt("slot"), item);
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return items;
            }, uuid.toString(), number);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load vault " + number, ex);
        }
        return items;
    }

    private void save(UUID uuid, int number, ItemStack[] contents) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("DELETE FROM vault_items WHERE uuid = ? AND number = ?", uuid.toString(), number);
                if (contents == null) return;
                for (int slot = 0; slot < contents.length; slot++) {
                    ItemStack item = contents[slot];
                    if (item == null || item.getType().isAir()) continue;
                    sqlite.execute("INSERT INTO vault_items (uuid, number, slot, item) VALUES (?, ?, ?, ?)",
                            uuid.toString(), number, slot, Items.serialize(item));
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save vault " + number, ex);
            }
        });
    }

    private boolean combatBlocked(Player player) {
        if (!config.getBoolean("block-in-combat", true)) return false;
        if (player.hasPermission("shardedcore.combat.bypass")) return false;
        CombatModule combat = plugin.modules().get(CombatModule.class);
        return combat != null && combat.tagged(player);
    }

    private void closeIfTagged(Player player) {
        if (!combatBlocked(player)) return;
        player.closeInventory();
        send(player, "in-combat");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) closeIfTagged(player);
        if (event.getDamager() instanceof Player player) closeIfTagged(player);
    }

    private static int parseNumber(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("pvadmin") || (args.length >= 1 && args[0].equalsIgnoreCase("view"))) {
            if (!sender.hasPermission("shardedcore.vault.admin")) return List.of();
            if (args.length == 1) {
                List<String> options = new ArrayList<>(List.of("view"));
                options.addAll(Tabs.players(args[0]));
                return Tabs.filter(options, args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("view")) return Tabs.players(args[1]);
        }
        if (args.length == 1 && sender instanceof Player player) {
            List<String> numbers = new ArrayList<>();
            int unlocked = unlockedCount(player, player.getUniqueId(), false);
            for (int i = 1; i <= unlocked; i++) numbers.add(String.valueOf(i));
            return Tabs.filter(numbers, args[0]);
        }
        return List.of();
    }
}

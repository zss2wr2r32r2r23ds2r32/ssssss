package com.shardedcore.modules.crates;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.GuiButtons;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.modules.crates.CrateStorage.BlockLoc;
import com.shardedcore.modules.crates.CrateStorage.Crate;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.Inventories;
import com.shardedcore.util.Items;
import com.shardedcore.util.Players;
import com.shardedcore.util.Slots;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class CratesModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private static final String ADMIN = "shardedcore.crates.admin";

    private CrateStorage storage;
    private Sqlite sqlite;
    private final Map<UUID, Map<String, Integer>> keys = new ConcurrentHashMap<>();
    private final Map<UUID, PendingPunch> pending = new ConcurrentHashMap<>();
    private final Map<UUID, OpenSession> sessions = new ConcurrentHashMap<>();

    public CratesModule(ShardedCore plugin) {
        super(plugin, "crates");
    }

    @Override
    public void enable() {
        storage = new CrateStorage(plugin, new File(folder, "crates"));
        storage.load();
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS crate_keys (
                        uuid TEXT NOT NULL,
                        crate TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        PRIMARY KEY (uuid, crate)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create crate_keys table", ex);
        }
        registerCommand("crate", this);
        registerListener(this);
    }

    @Override
    public void disable() {
        pending.clear();
        sessions.clear();
        keys.clear();
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        if (storage != null) storage.load();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "edit" -> edit(sender, args);
            case "key" -> key(sender, args);
            case "place" -> place(sender, args);
            case "unplace" -> unplace(sender);
            case "delete" -> delete(sender, args);
            case "list" -> list(sender);
            default -> {
                send(sender, "usage");
                yield true;
            }
        };
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 2) {
            send(sender, "usage-create");
            return true;
        }
        String name = args[1];
        if (storage.exists(name)) {
            send(sender, "already-exists", "crate", name);
            return true;
        }
        Crate crate = storage.create(name);
        if (crate == null) {
            send(sender, "invalid-name", "crate", name);
            return true;
        }
        send(sender, "created", "crate", crate.displayName);
        return true;
    }

    private boolean edit(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length < 2) {
            send(sender, "usage-edit");
            return true;
        }
        Crate crate = storage.get(args[1]);
        if (crate == null) {
            send(player, "missing", "crate", args[1]);
            return true;
        }
        openEdit(player, crate);
        return true;
    }

    private boolean key(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 2) {
            send(sender, "usage-key");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> keyGive(sender, args, false);
            case "set" -> keyGive(sender, args, true);
            case "giveall" -> keyGiveAll(sender, args);
            case "inspect" -> keyInspect(sender, args);
            default -> {
                send(sender, "usage-key");
                yield true;
            }
        };
    }

    private boolean keyGive(CommandSender sender, String[] args, boolean set) {
        if (args.length < 5) {
            send(sender, set ? "usage-key-set" : "usage-key-give");
            return true;
        }
        OfflinePlayer target = Players.offline(args[2]);
        if (target == null || target.getUniqueId() == null) {
            send(sender, "player-missing");
            return true;
        }
        Crate crate = storage.get(args[3]);
        if (crate == null) {
            send(sender, "missing", "crate", args[3]);
            return true;
        }
        long parsed = Amounts.parseLong(args[4]);
        if (parsed < 0 || (!set && parsed <= 0) || parsed > Integer.MAX_VALUE) {
            send(sender, "invalid-amount");
            return true;
        }
        int amount = (int) parsed;
        if (set) setKeys(target.getUniqueId(), crate.id, amount);
        else addKeys(target.getUniqueId(), crate.id, amount);
        send(sender, set ? "key-set" : "key-give",
                "player", Players.name(target),
                "amount", Amounts.commas(amount),
                "crate", crate.displayName);
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            send(online, set ? "key-set-target" : "key-give-target",
                    "amount", Amounts.commas(amount),
                    "crate", crate.displayName);
        }
        return true;
    }

    private boolean keyGiveAll(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "usage-key-giveall");
            return true;
        }
        Crate crate = storage.get(args[2]);
        if (crate == null) {
            send(sender, "missing", "crate", args[2]);
            return true;
        }
        long parsed = Amounts.parseLong(args[3]);
        if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
            send(sender, "invalid-amount");
            return true;
        }
        int amount = (int) parsed;
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            addKeys(player.getUniqueId(), crate.id, amount);
            send(player, "key-give-target", "amount", Amounts.commas(amount), "crate", crate.displayName);
            count++;
        }
        send(sender, "key-giveall",
                "amount", Amounts.commas(amount),
                "crate", crate.displayName,
                "count", String.valueOf(count));
        return true;
    }

    private boolean keyInspect(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "usage-key-inspect");
            return true;
        }
        OfflinePlayer target = Players.offline(args[2]);
        if (target == null || target.getUniqueId() == null) {
            send(sender, "player-missing");
            return true;
        }
        Map<String, Integer> owned = new HashMap<>(keyMap(target.getUniqueId()));
        send(sender, "inspect-header", "player", Players.name(target));
        boolean any = false;
        for (Crate crate : storage.all()) {
            int amount = owned.getOrDefault(crate.id, 0);
            if (amount <= 0) continue;
            any = true;
            send(sender, "inspect-line", "crate", crate.displayName, "amount", Amounts.commas(amount));
        }
        for (Map.Entry<String, Integer> entry : owned.entrySet()) {
            if (entry.getValue() <= 0 || storage.get(entry.getKey()) != null) continue;
            any = true;
            send(sender, "inspect-line", "crate", entry.getKey(), "amount", Amounts.commas(entry.getValue()));
        }
        if (!any) send(sender, "inspect-empty", "player", Players.name(target));
        return true;
    }

    private boolean place(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length < 2) {
            send(player, "usage-place");
            return true;
        }
        Crate crate = storage.get(args[1]);
        if (crate == null) {
            send(player, "missing", "crate", args[1]);
            return true;
        }
        pending.put(player.getUniqueId(), new PendingPunch(Punch.PLACE, crate.id, System.currentTimeMillis()));
        send(player, "place-ready", "crate", crate.displayName);
        return true;
    }

    private boolean unplace(CommandSender sender) {
        if (!admin(sender)) return true;
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        pending.put(player.getUniqueId(), new PendingPunch(Punch.UNPLACE, null, System.currentTimeMillis()));
        send(player, "unplace-ready");
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 2) {
            send(sender, "usage-delete");
            return true;
        }
        Crate crate = storage.get(args[1]);
        if (crate == null) {
            send(sender, "missing", "crate", args[1]);
            return true;
        }
        String display = crate.displayName;
        String id = crate.id;
        storage.delete(id);
        clearKeys(id);
        send(sender, "deleted", "crate", display);
        return true;
    }

    private boolean list(CommandSender sender) {
        if (storage.all().isEmpty()) {
            send(sender, "list-empty");
            return true;
        }
        send(sender, "list-header");
        for (Crate crate : storage.all()) {
            send(sender, "list-line",
                    "crate", crate.displayName,
                    "id", crate.id,
                    "rewards", String.valueOf(crate.rewardList().size()),
                    "placed", String.valueOf(crate.locations.size()));
        }
        return true;
    }

    private void openEdit(Player player, Crate crate) {
        List<Integer> area = area(crate.size());
        Menus.Menu menu = plugin.menus().create(player, title("edit-title", crate), crate.rows).editableSlots(area);
        for (Map.Entry<Integer, ItemStack> entry : laidOut(crate).entrySet()) {
            menu.set(entry.getKey(), entry.getValue().clone());
        }
        frame(menu, crate.size(), area);
        menu.onClose(closed -> {
            Crate current = storage.get(crate.id);
            if (current == null) {
                send(closed, "missing", "crate", crate.displayName);
                return;
            }
            current.rewards.clear();
            ItemStack[] contents = menu.inventory().getContents();
            int saved = 0;
            for (int slot : area) {
                if (slot < 0 || slot >= contents.length) continue;
                ItemStack item = contents[slot];
                if (CrateStorage.isAir(item)) continue;
                current.rewards.put(slot, item.clone());
                saved++;
            }
            storage.save(current);
            send(closed, "edited", "crate", current.displayName, "amount", String.valueOf(saved));
            sound(closed, "sounds.add");
        });
        plugin.menus().open(player, menu);
        sound(player, "sounds.edit");
    }

    private void openPreview(Player player, Crate crate) {
        List<Integer> area = area(crate.size());
        Menus.Menu menu = plugin.menus().create(player, title("preview-title", crate), crate.rows);
        for (Map.Entry<Integer, ItemStack> entry : laidOut(crate).entrySet()) {
            menu.set(entry.getKey(), entry.getValue().clone());
        }
        frame(menu, crate.size(), area);
        menu.set(config.getInt("preview.close-slot", crate.size() - 5), GuiButtons.close(player), event -> {
            event.setCancelled(true);
            player.closeInventory();
        });
        menu.onAny(event -> event.setCancelled(true));
        plugin.menus().open(player, menu);
        sound(player, "sounds.preview");
    }

    private void openCrate(Player player, Crate crate) {
        int held = keys(player.getUniqueId(), crate.id);
        if (held <= 0) {
            send(player, "no-keys", "crate", crate.displayName);
            sound(player, "sounds.deny");
            return;
        }
        List<ItemStack> pool = crate.rewardList();
        if (pool.isEmpty()) {
            send(player, "no-rewards", "crate", crate.displayName);
            return;
        }
        if (CrateStorage.isKeyall(crate)) {
            openKeyall(player, crate, pool);
            return;
        }
        int picks = Math.min(held, pool.size());
        OpenSession session = new OpenSession(crate.id, picks, laidOut(crate));
        sessions.put(player.getUniqueId(), session);
        renderOpen(player, crate, session);
        send(player, "open-start", "crate", crate.displayName, "amount", String.valueOf(picks));
        sound(player, "sounds.open");
    }

    private void openKeyall(Player player, Crate crate, List<ItemStack> pool) {
        if (!takeKeys(player.getUniqueId(), crate.id, 1)) {
            send(player, "no-keys", "crate", crate.displayName);
            sound(player, "sounds.deny");
            return;
        }
        ItemStack won = pool.get(ThreadLocalRandom.current().nextInt(pool.size())).clone();
        give(player, won);
        send(player, "keyall-won", "crate", crate.displayName, "item", itemName(won), "amount", String.valueOf(keys(player.getUniqueId(), crate.id)));
        sound(player, "sounds.confirm");
    }

    private void renderOpen(Player player, Crate crate, OpenSession session) {
        List<Integer> area = area(crate.size());
        Menus.Menu menu = plugin.menus().create(player, title("open-title", crate), crate.rows);
        for (int slot : area) {
            int captured = slot;
            if (session.claimed.contains(slot)) {
                menu.set(slot, claimedPane());
                continue;
            }
            ItemStack reward = session.rewards.get(slot);
            if (CrateStorage.isAir(reward)) continue;
            if (session.selected.contains(slot)) {
                menu.set(slot, selectedPane(), event -> {
                    event.setCancelled(true);
                    session.selected.remove(captured);
                    sound(player, "sounds.remove");
                    renderOpen(player, crate, session);
                });
            } else {
                menu.set(slot, reward.clone(), event -> {
                    event.setCancelled(true);
                    if (event.isShiftClick()) return;
                    select(player, crate, session, captured);
                });
            }
        }
        frame(menu, crate.size(), area);
        menu.set(config.getInt("open.confirm-slot", crate.size() - 5), GuiButtons.confirm(player), event -> {
            event.setCancelled(true);
            session.confirmed = true;
            player.closeInventory();
            claimSelected(player, crate, session);
        });
        menu.onAny(event -> event.setCancelled(true));
        menu.onClose(closed -> {
            if (session.redraw) return;
            OpenSession current = sessions.get(closed.getUniqueId());
            if (current != session) return;
            if (!session.confirmed) sessions.remove(closed.getUniqueId());
        });
        session.redraw = true;
        plugin.menus().open(player, menu);
        session.redraw = false;
    }

    private void select(Player player, Crate crate, OpenSession session, int slot) {
        if (session.claimed.contains(slot) || session.selected.contains(slot)) return;
        ItemStack reward = session.rewards.get(slot);
        List<ItemStack> needed = new ArrayList<>();
        for (int selected : session.selected) {
            ItemStack item = session.rewards.get(selected);
            if (!CrateStorage.isAir(item)) needed.add(item);
        }
        if (!CrateStorage.isAir(reward)) needed.add(reward);
        if (!Inventories.hasSpace(player, needed)) {
            send(player, "no-space");
            sound(player, "sounds.deny");
            return;
        }
        if (session.selected.size() >= session.remaining) {
            send(player, "select-limit", "amount", String.valueOf(session.remaining));
            sound(player, "sounds.deny");
            return;
        }
        session.selected.add(slot);
        sound(player, "sounds.select");
        renderOpen(player, crate, session);
    }

    private void claimSelected(Player player, Crate crate, OpenSession session) {
        sessions.remove(player.getUniqueId());
        List<Integer> picks = new ArrayList<>(session.selected);
        if (picks.isEmpty()) return;
        int take = Math.min(picks.size(), keys(player.getUniqueId(), crate.id));
        if (take <= 0) {
            send(player, "no-keys", "crate", crate.displayName);
            sound(player, "sounds.deny");
            return;
        }
        if (!takeKeys(player.getUniqueId(), crate.id, take)) {
            send(player, "no-keys", "crate", crate.displayName);
            return;
        }
        int given = 0;
        for (int i = 0; i < take; i++) {
            ItemStack reward = session.rewards.get(picks.get(i));
            if (CrateStorage.isAir(reward)) continue;
            give(player, reward.clone());
            given++;
        }
        send(player, "confirmed", "item", String.valueOf(given), "amount", String.valueOf(keys(player.getUniqueId(), crate.id)));
        send(player, "open-done", "crate", crate.displayName);
        sound(player, "sounds.confirm");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        PendingPunch punch = pending.get(player.getUniqueId());
        if (punch != null) {
            if (expired(punch)) {
                pending.remove(player.getUniqueId());
                send(player, "punch-expired");
            } else {
                event.setCancelled(true);
                handlePunch(player, block, punch);
                return;
            }
        }
        Crate crate = storage.at(block);
        if (crate == null) return;
        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_BLOCK) {
            openPreview(player, crate);
        } else {
            openCrate(player, crate);
        }
    }

    private void handlePunch(Player player, Block block, PendingPunch punch) {
        pending.remove(player.getUniqueId());
        if (punch.action == Punch.PLACE) {
            Crate crate = storage.get(punch.crateId);
            if (crate == null) {
                send(player, "missing", "crate", punch.crateId);
                return;
            }
            Crate existing = storage.at(block);
            if (existing != null) {
                send(player, "already-placed", "crate", existing.displayName);
                return;
            }
            crate.locations.add(BlockLoc.of(block.getLocation()));
            storage.save(crate);
            send(player, "placed", "crate", crate.displayName);
            sound(player, "sounds.place");
            return;
        }
        Crate crate = storage.at(block);
        if (crate == null) {
            send(player, "not-a-crate");
            return;
        }
        BlockLoc loc = BlockLoc.of(block.getLocation());
        crate.locations.removeIf(loc::equals);
        storage.save(crate);
        send(player, "unplaced", "crate", crate.displayName);
        sound(player, "sounds.unplace");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Crate crate = storage.at(event.getBlock());
        if (crate == null) return;
        event.setCancelled(true);
        send(event.getPlayer(), "cannot-break", "crate", crate.displayName);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        if (storage.at(event.getBlock()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> storage.at(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> storage.at(block) != null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pending.remove(uuid);
        OpenSession session = sessions.remove(uuid);
        if (session != null && !session.selected.isEmpty()) {
            Crate crate = storage.get(session.crateId);
            if (crate != null) claimSelected(event.getPlayer(), crate, session);
        }
        keys.remove(uuid);
    }

    public void wipe(UUID uuid) {
        keys.remove(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("DELETE FROM crate_keys WHERE uuid = ?", uuid.toString());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to wipe crate keys", ex);
            }
        });
    }

    public int keys(UUID uuid, String crateId) {
        return keyMap(uuid).getOrDefault(crateId.toLowerCase(Locale.ROOT), 0);
    }

    public List<String> crateIds() {
        return storage == null ? List.of() : storage.ids();
    }

    public void addKeys(UUID uuid, String crateId, int amount) {
        setKeys(uuid, crateId, keys(uuid, crateId) + amount);
    }

    public void setKeys(UUID uuid, String crateId, int amount) {
        String id = crateId.toLowerCase(Locale.ROOT);
        int value = Math.max(0, amount);
        keyMap(uuid).put(id, value);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (value <= 0) {
                    sqlite.execute("DELETE FROM crate_keys WHERE uuid = ? AND crate = ?", uuid.toString(), id);
                } else {
                    sqlite.execute("""
                            INSERT INTO crate_keys (uuid, crate, amount) VALUES (?, ?, ?)
                            ON CONFLICT(uuid, crate) DO UPDATE SET amount = excluded.amount
                            """, uuid.toString(), id, value);
                }
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save crate keys for " + uuid, ex);
            }
        });
    }

    public boolean takeKeys(UUID uuid, String crateId, int amount) {
        int held = keys(uuid, crateId);
        if (held < amount) return false;
        setKeys(uuid, crateId, held - amount);
        return true;
    }

    private Map<String, Integer> keyMap(UUID uuid) {
        return keys.computeIfAbsent(uuid, this::loadAllKeys);
    }

    private Map<String, Integer> loadAllKeys(UUID uuid) {
        Map<String, Integer> map = new ConcurrentHashMap<>();
        try {
            sqlite.query("SELECT crate, amount FROM crate_keys WHERE uuid = ?", rs -> {
                try {
                    while (rs.next()) {
                        int amount = rs.getInt("amount");
                        if (amount > 0) map.put(rs.getString("crate").toLowerCase(Locale.ROOT), amount);
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return map;
            }, uuid.toString());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load crate keys for " + uuid, ex);
        }
        return map;
    }

    private void clearKeys(String crateId) {
        String id = crateId.toLowerCase(Locale.ROOT);
        for (Map<String, Integer> map : keys.values()) map.remove(id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("DELETE FROM crate_keys WHERE crate = ?", id);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clear keys for crate " + id, ex);
            }
        });
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        send(sender, "no-permission");
        return false;
    }

    private boolean expired(PendingPunch punch) {
        long life = config.getLong("punch-expire-seconds", 15) * 1000L;
        return System.currentTimeMillis() - punch.at > life;
    }

    private String title(String path, Crate crate) {
        return Text.apply(cfg(path, "&8Crate"), "crate", crate.displayName, "id", crate.id);
    }

    private ItemStack selectedPane() {
        return Items.named(
                Sounds.material(cfg("selected.material", "LIME_STAINED_GLASS_PANE"), Material.LIME_STAINED_GLASS_PANE),
                cfg("selected.name", "&#94FF00&lSELECTED"),
                config.getStringList("selected.lore")
        );
    }

    private ItemStack claimedPane() {
        return Items.named(
                Sounds.material(cfg("claimed.material", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE),
                cfg("claimed.name", "&#8B8B8B&lCLAIMED"),
                config.getStringList("claimed.lore")
        );
    }

    private List<Integer> area(int size) {
        List<Integer> slots = new ArrayList<>();
        for (int slot : Slots.of(config, "area")) {
            if (slot >= 0 && slot < size) slots.add(slot);
        }
        if (slots.isEmpty()) {
            for (int slot : Slots.parse("10-16,19-25,28-34")) {
                if (slot >= 0 && slot < size) slots.add(slot);
            }
        }
        return slots;
    }

    private Map<Integer, ItemStack> laidOut(Crate crate) {
        List<Integer> area = area(crate.size());
        Map<Integer, ItemStack> laid = new java.util.LinkedHashMap<>();
        boolean inArea = false;
        for (int slot : crate.rewards.keySet()) {
            if (area.contains(slot)) {
                inArea = true;
                break;
            }
        }
        if (inArea) {
            for (Map.Entry<Integer, ItemStack> entry : crate.rewards.entrySet()) {
                if (area.contains(entry.getKey()) && !CrateStorage.isAir(entry.getValue())) {
                    laid.put(entry.getKey(), entry.getValue().clone());
                }
            }
            return laid;
        }
        int index = 0;
        for (ItemStack item : crate.rewards.values()) {
            if (CrateStorage.isAir(item) || index >= area.size()) continue;
            laid.put(area.get(index++), item.clone());
        }
        return laid;
    }

    private void frame(Menus.Menu menu, int size, List<Integer> area) {
        if (!config.getBoolean("filler.enabled", true)) return;
        GuiButtons.border(menu);
    }

    private Map<Integer, ItemStack> snapshot(Crate crate) {
        return laidOut(crate);
    }

    private void give(Player player, ItemStack item) {
        if (CrateStorage.isAir(item)) return;
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private String itemName(ItemStack item) {
        if (CrateStorage.isAir(item)) return "Unknown";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().displayName() != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
            if (!plain.isBlank()) return plain;
        }
        return Text.pretty(item.getType().name());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("list"));
            if (sender.hasPermission(ADMIN)) {
                options.addAll(List.of("create", "edit", "key", "place", "unplace", "delete"));
            }
            return Tabs.filter(options, args[0]);
        }
        if (!sender.hasPermission(ADMIN)) return List.of();
        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (first) {
                case "edit", "delete", "place" -> Tabs.filter(storage.ids(), args[1]);
                case "key" -> Tabs.filter(List.of("give", "giveall", "set", "inspect"), args[1]);
                default -> List.of();
            };
        }
        if (!first.equals("key")) return List.of();
        String second = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            return switch (second) {
                case "give", "set", "inspect" -> Tabs.players(args[2]);
                case "giveall" -> Tabs.filter(storage.ids(), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && (second.equals("give") || second.equals("set"))) {
            return Tabs.filter(storage.ids(), args[3]);
        }
        if (args.length == 5 && (second.equals("give") || second.equals("set"))) {
            return Tabs.filter(List.of("1", "8", "16", "32", "64"), args[4]);
        }
        if (args.length == 4 && second.equals("giveall")) {
            return Tabs.filter(List.of("1", "8", "16", "32", "64"), args[3]);
        }
        return List.of();
    }

    private enum Punch { PLACE, UNPLACE }

    private record PendingPunch(Punch action, String crateId, long at) {
    }

    private static final class OpenSession {
        private final String crateId;
        private int remaining;
        private final Map<Integer, ItemStack> rewards;
        private final Set<Integer> selected = new HashSet<>();
        private final Set<Integer> claimed = new HashSet<>();
        private boolean redraw;
        private boolean confirmed;

        private OpenSession(String crateId, int remaining, Map<Integer, ItemStack> rewards) {
            this.crateId = crateId;
            this.remaining = remaining;
            this.rewards = rewards;
        }
    }
}

package com.sharded.core.modules.crates;

import com.sharded.core.ShardedCore;
import com.sharded.core.setup.gui.GuiHelper;
import com.sharded.core.setup.gui.GuiHolder;
import com.sharded.core.setup.SetupFeatureModule;
import com.sharded.core.setup.util.ItemBuilder;
import com.sharded.core.setup.util.MaterialUtil;
import com.sharded.core.setup.util.MessageUtil;
import com.sharded.core.setup.util.SoundUtil;
import com.sharded.core.setup.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class CratesModule extends SetupFeatureModule implements CommandExecutor, TabCompleter, Listener {

    private final Map<String, CrateData> crates = new HashMap<>();
    private final Map<String, String> placements = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> keys = new HashMap<>();
    private final Map<UUID, String> editors = new HashMap<>();
    private final Map<UUID, Integer> pendingCommandSlot = new HashMap<>();
    private final Map<UUID, String> pendingCommandCrate = new HashMap<>();
    private final Map<UUID, BundleEdit> bundleEdits = new HashMap<>();
    private final Map<UUID, Set<Integer>> selectable = new HashMap<>();
    private final Map<UUID, String> opening = new HashMap<>();
    private final Map<UUID, String> previewing = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private File cratesDir;
    private File placementsFile;
    private File keysFile;
    private FileConfiguration placementsConfig;
    private FileConfiguration keysConfig;
    private NamespacedKey cleanRewardKey;
    private NamespacedKey crateMenuKey;

    public CratesModule(ShardedCore plugin) {
        super(plugin, "crates");
    }

    @Override
    protected void onEnable() {
        loadFiles();
        migrateConfigLists("config-version", "preview-lore", "select-lore", "edit-force-5-rows");
        ensureMenuDefaults();
        cleanRewardKey = new NamespacedKey(plugin, "crate-reward-clean");
        crateMenuKey = new NamespacedKey(plugin, "crate-menu-id");
        cratesDir = new File(folder, "crates");
        if (!cratesDir.exists()) {
            cratesDir.mkdirs();
        }
        placementsFile = new File(folder, "placements.yml");
        keysFile = new File(folder, "keys.yml");
        placementsConfig = YamlConfiguration.loadConfiguration(placementsFile);
        keysConfig = YamlConfiguration.loadConfiguration(keysFile);
        loadAll();
        for (String cmd : List.of("crates", "crate", "scrates", "keyall")) {
            if (plugin.getCommand(cmd) != null) {
                plugin.getCommand(cmd).setExecutor(this);
                plugin.getCommand(cmd).setTabCompleter(this);
            }
        }
    }

    @Override
    protected void onDisable() {
        saveAll();
        // listeners unregistered by Module.disable()
    }

    @Override
    public void reload() {
        loadFiles();
        migrateConfigLists("config-version", "preview-lore", "select-lore", "edit-force-5-rows");
        ensureMenuDefaults();
        if (cratesDir == null) {
            cratesDir = new File(folder, "crates");
        }
        if (!cratesDir.exists()) {
            cratesDir.mkdirs();
        }
        if (placementsFile == null) {
            placementsFile = new File(folder, "placements.yml");
        }
        if (keysFile == null) {
            keysFile = new File(folder, "keys.yml");
        }
        placementsConfig = YamlConfiguration.loadConfiguration(placementsFile);
        keysConfig = YamlConfiguration.loadConfiguration(keysFile);
        loadAll();
    }

    /** Bottom-row centre slot for close/confirm — always valid for any row count. */
    private int navSlot(int rows) {
        int size = Math.max(9, rows * 9);
        return size - 5;
    }

    private int clampRows(int rows) {
        return Math.max(3, Math.min(6, rows <= 0 ? config.getInt("default-rows", 5) : rows));
    }

    private void loadAll() {
        crates.clear();
        File[] files = cratesDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                List<Reward> rewards = new ArrayList<>();
                List<?> raw = cfg.getList("rewards");
                if (raw != null) {
                    for (Object obj : raw) {
                        if (obj instanceof ItemStack stack) {
                            rewards.add(new Reward(RewardType.ITEM, sanitizeRewardItem(stack.clone()), null));
                        } else if (obj instanceof Map<?, ?> map) {
                            // Empty-slot placeholder written by saveCrate
                            if (Boolean.TRUE.equals(map.get("empty"))) {
                                rewards.add(new Reward(RewardType.ITEM, new ItemStack(Material.AIR), null));
                                continue;
                            }
                            Object typeObj = map.get("type");
                            String type = typeObj == null ? "ITEM" : String.valueOf(typeObj);
                            Object itemObj = map.get("item");
                            ItemStack item = itemObj instanceof ItemStack s ? s : new ItemStack(Material.AIR);
                            Object cmdObj = map.get("command");
                            String command = cmdObj == null ? null : String.valueOf(cmdObj);
                            RewardType rewardType;
                            try {
                                rewardType = RewardType.valueOf(type.toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException ex) {
                                rewardType = RewardType.ITEM;
                            }
                            List<ItemStack> extras = readItemList(map.get("items"));
                            if (item.getType().isAir() && !extras.isEmpty()) {
                                item = extras.remove(0);
                            } else if (!extras.isEmpty() && sameItem(item, extras.get(0))) {
                                extras.remove(0);
                            }
                            rewards.add(new Reward(rewardType, sanitizeRewardItem(item), command, extras));
                        }
                    }
                }
                crates.put(id, new CrateData(
                        id,
                        cfg.getString("display", id),
                        cfg.getString("animation", "selectable"),
                        clampRows(cfg.getInt("rows", config.getInt("default-rows", 5))),
                        rewards));
            }
        }
        placements.clear();
        if (placementsConfig.getConfigurationSection("placements") != null) {
            for (String key : placementsConfig.getConfigurationSection("placements").getKeys(false)) {
                placements.put(key, placementsConfig.getString("placements." + key));
            }
        }
        keys.clear();
        if (keysConfig.getConfigurationSection("players") != null) {
            for (String uuidKey : keysConfig.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidKey);
                Map<String, Integer> map = new HashMap<>();
                for (String crate : keysConfig.getConfigurationSection("players." + uuidKey).getKeys(false)) {
                    map.put(crate, keysConfig.getInt("players." + uuidKey + "." + crate));
                }
                keys.put(uuid, map);
            }
        }
    }

    private void saveAll() {
        for (CrateData crate : crates.values()) {
            saveCrate(crate);
        }
        placementsConfig.set("placements", null);
        for (Map.Entry<String, String> entry : placements.entrySet()) {
            placementsConfig.set("placements." + entry.getKey(), entry.getValue());
        }
        try {
            placementsConfig.save(placementsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving placements.yml", e);
        }
        keysConfig.set("players", null);
        for (Map.Entry<UUID, Map<String, Integer>> entry : keys.entrySet()) {
            for (Map.Entry<String, Integer> crate : entry.getValue().entrySet()) {
                keysConfig.set("players." + entry.getKey() + "." + crate.getKey(), crate.getValue());
            }
        }
        try {
            keysConfig.save(keysFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving keys.yml", e);
        }
    }

    private void saveCrate(CrateData crate) {
        File file = new File(cratesDir, crate.id() + ".yml");
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("display", crate.display());
        cfg.set("animation", crate.animation());
        cfg.set("rows", crate.rows());
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (Reward reward : crate.rewards()) {
            Map<String, Object> map = new HashMap<>();
            if (reward.item() == null || reward.item().getType().isAir()) {
                // Empty/placeholder slot — use a flag instead of serialising AIR ItemStack
                map.put("empty", true);
            } else {
                map.put("type", reward.type().name());
                map.put("item", reward.item());
                List<ItemStack> packed = reward.allItems();
                if (packed.size() > 1) {
                    map.put("items", packed);
                }
                map.put("command", reward.command());
            }
            rewards.add(map);
        }
        cfg.set("rewards", rewards);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving crate " + crate.id(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> readItemList(Object raw) {
        List<ItemStack> items = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return items;
        }
        for (Object entry : list) {
            if (entry instanceof ItemStack stack && !stack.getType().isAir()) {
                items.add(sanitizeRewardItem(stack.clone()));
            }
        }
        return items;
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getType() == b.getType() && a.getAmount() == b.getAmount();
    }

    public int getKeys(UUID uuid, String crate) {
        return keys.getOrDefault(uuid, Map.of()).getOrDefault(crate.toLowerCase(Locale.ROOT), 0);
    }

    public int totalKeys(UUID uuid) {
        return keys.getOrDefault(uuid, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
    }

    public void addKeys(UUID uuid, String crate, int amount) {
        String id = crate.toLowerCase(Locale.ROOT);
        Map<String, Integer> map = keys.computeIfAbsent(uuid, ignored -> new HashMap<>());
        map.put(id, Math.max(0, map.getOrDefault(id, 0) + amount));
        saveAll();
    }

    public void setKeys(UUID uuid, String crate, int amount) {
        String id = crate.toLowerCase(Locale.ROOT);
        keys.computeIfAbsent(uuid, ignored -> new HashMap<>()).put(id, Math.max(0, amount));
        saveAll();
    }

    public void removeAllKeys(UUID uuid) {
        keys.remove(uuid);
        saveAll();
    }

    public void removeAllKeys(UUID uuid, String crate) {
        String id = crate.toLowerCase(Locale.ROOT);
        Map<String, Integer> map = keys.get(uuid);
        if (map != null) {
            map.remove(id);
            if (map.isEmpty()) {
                keys.remove(uuid);
            }
        }
        saveAll();
    }

    private int emptySlots(Player player) {
        int empty = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        if (contents == null) {
            return 0;
        }
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                empty++;
            }
        }
        return empty;
    }

    private String locKey(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("keyall")) {
            return cmdKeyall(sender, args);
        }
        boolean adminCmd = command.getName().equalsIgnoreCase("scrates");
        if (!adminCmd) {
            if (!(sender instanceof Player player)) {
                send(sender, msg("players-only", "&#FFEE00&lCRATES &8▷ &fPlayers only."));
                return true;
            }
            openCratesMenu(player);
            return true;
        }
        if (args.length == 0) {
            send(sender, msg("usage", "&#FFEE00&lCRATES &8▷ &f/scrates <create|delete|place|edit|key|list|...>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> cmdCreate(sender, args);
            case "delete" -> cmdDelete(sender, args);
            case "place" -> cmdPlace(sender, args);
            case "unplace" -> cmdUnplace(sender);
            case "edit" -> cmdEdit(sender, args);
            case "list" -> cmdList(sender);
            case "setanimation" -> cmdAnim(sender, args);
            case "key" -> cmdKey(sender, args);
            default -> {
                send(sender, msg("usage", "&#FFEE00&lCRATES &8▷ &fUnknown subcommand."));
                yield true;
            }
        };
    }

    private boolean cmdKeyall(CommandSender sender, String[] args) {
        if (!this.hasSetupPerm(sender, "setupcore.crates.admin") && !this.hasSetupPerm(sender, "sharded.crates.keyall")) {
            send(sender, msg("no-permission", "&#FFEE00&lCRATES &8▷ &fNo permission."));
            return true;
        }
        if (args.length != 1 || !"start".equalsIgnoreCase(args[0])) {
            send(sender, msg("usage-keyall", "&#FFEE00&lKEYALL &8▷ &f/keyall start"));
            return true;
        }
        int given = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            addKeys(online.getUniqueId(), "keyall", 1);
            send(online, msg("keyall-received", "&#FFEE00&lKEYALL &8▷ &fYou received &#FFEE00x1 Keyall Crate Key&f!"));
            given++;
        }
        send(sender, msg("keyall-given", "&#FFEE00&lKEYALL &8▷ &fGave &#FFEE00x1 Keyall Crate Key &fto &#FFEE00%amount% &fonline player(s).")
                .replace("%amount%", String.valueOf(given)));
        return true;
    }

    private boolean cmdCreate(CommandSender sender, String[] args) {
        if (!this.hasSetupPerm(sender, "setupcore.crates.admin")) {
            send(sender, msg("no-permission", "&#FFEE00&lCRATES &8▷ &fNo permission."));
            return true;
        }
        if (args.length < 2) {
            send(sender, msg("usage-create", "&#FFEE00&lCRATES &8▷ &f/crate create <name>"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (crates.containsKey(id)) {
            send(sender, msg("exists", "&#FFEE00&lCRATES &8▷ &fCrate already exists."));
            return true;
        }
        CrateData crate = new CrateData(id, id,
                config.getString("default-animation", "selectable"),
                config.getInt("default-rows", 5),
                new ArrayList<>());
        crates.put(id, crate);
        saveCrate(crate);
        send(sender, msg("created", "&#FFEE00&lCRATES &8▷ &fCreated crate &#FFEE00%crate%&f.")
                .replace("%crate%", id));
        return true;
    }

    private boolean cmdDelete(CommandSender sender, String[] args) {
        if (!this.hasSetupPerm(sender, "setupcore.crates.admin")) {
            send(sender, msg("no-permission", "&#FFEE00&lCRATES &8▷ &fNo permission."));
            return true;
        }
        if (args.length < 2) {
            send(sender, msg("usage-delete", "&#FFEE00&lCRATES &8▷ &f/crate delete <name>"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (crates.remove(id) == null) {
            send(sender, msg("not-found", "&#FFEE00&lCRATES &8▷ &fCrate not found."));
            return true;
        }
        File file = new File(cratesDir, id + ".yml");
        if (file.exists()) {
            file.delete();
        }
        placements.entrySet().removeIf(e -> id.equalsIgnoreCase(e.getValue()));
        saveAll();
        send(sender, msg("deleted", "&#FFEE00&lCRATES &8▷ &fDeleted crate &#FFEE00%crate%&f.")
                .replace("%crate%", id));
        return true;
    }

    private boolean cmdPlace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, msg("players-only", "&#FFEE00&lCRATES &8▷ &fPlayers only."));
            return true;
        }
        if (!this.hasSetupPerm(player, "setupcore.crates.admin")) {
            send(sender, msg("no-permission", "&#FFEE00&lCRATES &8▷ &fNo permission."));
            return true;
        }
        if (args.length < 2) {
            send(sender, msg("usage-place", "&#FFEE00&lCRATES &8▷ &f/crate place <name>"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!crates.containsKey(id)) {
            send(sender, msg("not-found", "&#FFEE00&lCRATES &8▷ &fCrate not found."));
            return true;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType().isAir()) {
            send(sender, msg("look-block", "&#FFEE00&lCRATES &8▷ &fLook at a block."));
            return true;
        }
        placements.entrySet().removeIf(e -> id.equalsIgnoreCase(e.getValue()));
        placements.put(locKey(target.getLocation()), id);
        saveAll();
        send(sender, msg("placed", "&#FFEE00&lCRATES &8▷ &fPlaced &#FFEE00%crate%&f.")
                .replace("%crate%", crates.get(id) != null ? crates.get(id).display() : id));
        return true;
    }

    private boolean cmdUnplace(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, msg("players-only", "&#FFEE00&lCRATES &8▷ &fPlayers only."));
            return true;
        }
        if (!this.hasSetupPerm(player, "setupcore.crates.admin")) {
            send(sender, msg("no-permission", "&#FFEE00&lCRATES &8▷ &fNo permission."));
            return true;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            send(sender, msg("look-block", "&#FFEE00&lCRATES &8▷ &fLook at a block."));
            return true;
        }
        String removed = placements.remove(locKey(target.getLocation()));
        if (removed == null) {
            send(sender, msg("not-placement", "&#FFEE00&lCRATES &8▷ &fNo crate there."));
            return true;
        }
        saveAll();
        send(sender, msg("unplaced", "&#FFEE00&lCRATES &8▷ &fRemoved placement."));
        return true;
    }

    private boolean cmdEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, msg("players-only", "&#FFEE00&lCRATES &8▷ &fPlayers only."));
            return true;
        }
        if (!this.hasSetupPerm(player, "setupcore.crates.admin")) {
            send(sender, msg("no-permission", "&#FFEE00&lCRATES &8▷ &fNo permission."));
            return true;
        }
        if (args.length < 2) {
            send(sender, msg("usage-edit", "&#FFEE00&lCRATES &8▷ &f/crate edit <name>"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        CrateData crate = crates.get(id);
        if (crate == null) {
            send(sender, msg("not-found", "&#FFEE00&lCRATES &8▷ &fCrate not found."));
            return true;
        }
        openEdit(player, crate);
        return true;
    }

    private boolean cmdList(CommandSender sender) {
        if (!this.hasSetupPerm(sender, "setupcore.crates.admin")) {
            send(sender, msg("no-permission", "&#FFEE00&lCRATES &8▷ &fNo permission."));
            return true;
        }
        send(sender, msg("list-header", "&#FFEE00&lCRATES &8▷ &fCrates: &#FFEE00%list%")
                .replace("%list%", String.join(", ", crates.keySet())));
        return true;
    }

    private boolean cmdAnim(CommandSender sender, String[] args) {
        if (!this.hasSetupPerm(sender, "setupcore.crates.admin")) {
            send(sender, msg("no-permission", "&#FFEE00&lCRATES &8▷ &fNo permission."));
            return true;
        }
        if (args.length < 3) {
            send(sender, msg("usage-anim", "&#FFEE00&lCRATES &8▷ &f/crate setanimation <name> <anim>"));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        CrateData crate = crates.get(id);
        if (crate == null) {
            send(sender, msg("not-found", "&#FFEE00&lCRATES &8▷ &fCrate not found."));
            return true;
        }
        String anim = args[2].toLowerCase(Locale.ROOT);
        if (!List.of("selectable", "rolling1", "rolling2", "rolling3").contains(anim)) {
            send(sender, msg("bad-anim", "&#FFEE00&lCRATES &8▷ &fAnimations: selectable, rolling1-3"));
            return true;
        }
        CrateData updated = new CrateData(crate.id(), crate.display(), anim, crate.rows(), crate.rewards());
        crates.put(id, updated);
        saveCrate(updated);
        send(sender, msg("anim-set", "&#FFEE00&lCRATES &8▷ &fAnimation set to &#FFEE00%anim%&f.")
                .replace("%anim%", anim));
        return true;
    }

    private boolean cmdKey(CommandSender sender, String[] args) {
        if (!this.hasSetupPerm(sender, "setupcore.crates.admin")) {
            send(sender, msg("no-permission", "&#FFEE00&lCRATES &8▷ &fNo permission."));
            return true;
        }
        if (args.length < 2) {
            send(sender, msg("usage-key", "&#FFEE00&lCRATES &8▷ &f/crate key <give|giveall|remove|set|inspect>"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "give" -> {
                if (args.length < 5) {
                    send(sender, msg("usage-key-give", "&#FFEE00&lCRATES &8▷ &f/crate key give <player> <crate> <amount>"));
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    send(sender, msg("player-not-found", "&#FFEE00&lCRATES &8▷ &fPlayer not found."));
                    yield true;
                }
                addKeys(target.getUniqueId(), args[3], Integer.parseInt(args[4]));
                send(sender, msg("key-give", "&#FFEE00&lCRATES &8▷ &fGave keys."));
                yield true;
            }
            case "giveall" -> {
                if (args.length < 4) {
                    send(sender, msg("usage-key-giveall", "&#FFEE00&lCRATES &8▷ &f/crate key giveall <crate> <amount>"));
                    yield true;
                }
                int amount = Integer.parseInt(args[3]);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    addKeys(online.getUniqueId(), args[2], amount);
                }
                send(sender, msg("key-giveall", "&#FFEE00&lCRATES &8▷ &fGave keys to everyone."));
                yield true;
            }
            case "remove" -> {
                if (args.length < 4) {
                    send(sender, msg("usage-key-remove",
                            "&#FFEE00&lCRATES &8▷ &f/crate key remove <player> <crate|all> [amount]"));
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    send(sender, msg("player-not-found", "&#FFEE00&lCRATES &8▷ &fPlayer not found."));
                    yield true;
                }
                String crateArg = args[3].toLowerCase(Locale.ROOT);
                if ("all".equals(crateArg)) {
                    // /crate key remove <player> all  OR  /crate key remove <player> all <amount ignored>
                    removeAllKeys(target.getUniqueId());
                    send(sender, msg("key-remove-all", "&#FFEE00&lCRATES &8▷ &fRemoved all keys from &#FFEE00%player%&f.")
                            .replace("%player%", target.getName()));
                    yield true;
                }
                if (args.length < 5) {
                    // remove all keys of that crate
                    removeAllKeys(target.getUniqueId(), crateArg);
                    send(sender, msg("key-remove", "&#FFEE00&lCRATES &8▷ &fRemoved keys."));
                    yield true;
                }
                if ("all".equalsIgnoreCase(args[4])) {
                    removeAllKeys(target.getUniqueId(), crateArg);
                    send(sender, msg("key-remove", "&#FFEE00&lCRATES &8▷ &fRemoved keys."));
                    yield true;
                }
                addKeys(target.getUniqueId(), crateArg, -Integer.parseInt(args[4]));
                send(sender, msg("key-remove", "&#FFEE00&lCRATES &8▷ &fRemoved keys."));
                yield true;
            }
            case "set" -> {
                if (args.length < 5) {
                    send(sender, msg("usage-key-set", "&#FFEE00&lCRATES &8▷ &f/crate key set <player> <crate> <amount>"));
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    send(sender, msg("player-not-found", "&#FFEE00&lCRATES &8▷ &fPlayer not found."));
                    yield true;
                }
                setKeys(target.getUniqueId(), args[3], Integer.parseInt(args[4]));
                send(sender, msg("key-set", "&#FFEE00&lCRATES &8▷ &fSet keys."));
                yield true;
            }
            case "inspect" -> {
                if (args.length < 3) {
                    send(sender, msg("usage-key-inspect", "&#FFEE00&lCRATES &8▷ &f/crate key inspect <player>"));
                    yield true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    send(sender, msg("player-not-found", "&#FFEE00&lCRATES &8▷ &fPlayer not found."));
                    yield true;
                }
                Map<String, Integer> map = keys.getOrDefault(target.getUniqueId(), Map.of());
                send(sender, msg("key-inspect", "&#FFEE00&lCRATES &8▷ &f%player% keys: &#FFEE00%keys%")
                        .replace("%player%", target.getName())
                        .replace("%keys%", map.isEmpty() ? "none" : map.toString()));
                yield true;
            }
            default -> {
                send(sender, msg("usage-key", "&#FFEE00&lCRATES &8▷ &fUnknown key action."));
                yield true;
            }
        };
    }

    private void openEdit(Player player, CrateData crate) {
        editors.put(player.getUniqueId(), crate.id());
        int rows = clampRows(crate.rows() <= 0 ? config.getInt("default-rows", 5) : crate.rows());
        Inventory inventory = GuiHelper.create("crate-edit",
                config.getString("edit-title", "&8Edit %crate%").replace("%crate%", crate.display()),
                rows);
        GuiHelper.fillDarkBorder(inventory);
        int closeSlot = navSlot(rows);
        List<Integer> contentSlots = editorContentSlots(rows, closeSlot);
        for (int i = 0; i < contentSlots.size(); i++) {
            int invSlot = contentSlots.get(i);
            if (invSlot == closeSlot) {
                continue;
            }
            if (i < crate.rewards().size()) {
                Reward reward = crate.rewards().get(i);
                if (reward != null && reward.item() != null && !reward.item().getType().isAir()) {
                    inventory.setItem(invSlot, decorateEditorReward(reward));
                }
            }
        }
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            inventory.setItem(closeSlot, GuiHelper.closeButton());
        }
        player.openInventory(inventory);
    }

    private List<Integer> editorContentSlots(int rows, int... reserved) {
        java.util.Set<Integer> skip = new java.util.HashSet<>();
        for (int r : reserved) {
            skip.add(r);
        }
        List<Integer> slots = new ArrayList<>();
        // Always use inner content (border is black glass) — min rows is 3
        for (int slot : GuiHelper.innerContentSlots(rows)) {
            if (!skip.contains(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private ItemStack decorateEditorReward(Reward reward) {
        ItemStack clean = reward.item() == null || reward.item().getType().isAir()
                ? new ItemStack(Material.CHEST)
                : reward.item().clone();
        clean = sanitizeRewardItem(clean);
        ItemStack display = clean.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(meta.lore());
            lore.add(TextUtil.itemComponent(""));
            lore.add(TextUtil.itemComponent("&#FFE300Information:"));
            lore.add(TextUtil.itemComponent("&#FFE300| &fLeft click to place / take"));
            lore.add(TextUtil.itemComponent("&#FFE300| &fShift-click to add more items"));
            lore.add(TextUtil.itemComponent("&#FFE300| &fRight click for command reward"));
            List<ItemStack> all = reward.allItems();
            if (all.size() > 1) {
                lore.add(TextUtil.itemComponent("&#FFE300| &fItems in this reward: &#FFE300" + all.size()));
                int shown = 0;
                for (ItemStack extra : all) {
                    if (shown >= 6) {
                        lore.add(TextUtil.itemComponent("&#FFE300| &f+ " + (all.size() - 6) + " more"));
                        break;
                    }
                    lore.add(TextUtil.itemComponent("&#FFE300| &f- &f" + extra.getAmount() + "x " + prettyItemName(extra)));
                    shown++;
                }
            }
            if (reward.type() == RewardType.COMMAND && reward.command() != null && !reward.command().isBlank()) {
                lore.add(TextUtil.itemComponent("&#FFE300| &fCmd: &f" + reward.command()));
            }
            meta.lore(lore);
            display.setItemMeta(meta);
        }
        storeCleanReward(display, clean);
        return display;
    }

    private void storeCleanReward(ItemStack display, ItemStack clean) {
        if (cleanRewardKey == null || display == null || clean == null) {
            return;
        }
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(cleanRewardKey, PersistentDataType.BYTE_ARRAY, clean.serializeAsBytes());
        display.setItemMeta(meta);
    }

    private ItemStack unwrapEditorItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        if (cleanRewardKey != null && item.hasItemMeta()) {
            byte[] raw = item.getItemMeta().getPersistentDataContainer().get(cleanRewardKey, PersistentDataType.BYTE_ARRAY);
            if (raw != null && raw.length > 0) {
                try {
                    return sanitizeRewardItem(ItemStack.deserializeBytes(raw));
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        return sanitizeRewardItem(item.clone());
    }

    /** Strip editor-only lore overlays from reward items (preview/open must stay clean). */
    private ItemStack sanitizeRewardItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        ItemStack clean = item.clone();
        if (cleanRewardKey != null && clean.hasItemMeta()) {
            byte[] raw = clean.getItemMeta().getPersistentDataContainer().get(cleanRewardKey, PersistentDataType.BYTE_ARRAY);
            if (raw != null && raw.length > 0) {
                try {
                    clean = ItemStack.deserializeBytes(raw);
                } catch (Exception ignored) {
                    // continue with strip
                }
            }
        }
        ItemMeta meta = clean.getItemMeta();
        if (meta == null || meta.lore() == null || meta.lore().isEmpty()) {
            return clean;
        }
        List<net.kyori.adventure.text.Component> kept = new ArrayList<>();
        for (net.kyori.adventure.text.Component line : meta.lore()) {
            String plain = TextUtil.plain(line).toLowerCase(Locale.ROOT);
            if (plain.contains("click to toggle")
                    || plain.contains("customise the item")
                    || plain.contains("customize the item")
                    || plain.contains("right-click to enter")
                    || plain.startsWith("type:")
                    || plain.equals("no command")
                    || plain.startsWith("cmd:")
                    || plain.equals("description")
                    || plain.equals("information:")
                    || plain.contains("click here to")) {
                continue;
            }
            // Drop editor bar lines like "| Click here to"
            if (plain.startsWith("|") && (plain.contains("click") || plain.contains("customise") || plain.contains("customize"))) {
                continue;
            }
            kept.add(line);
        }
        meta.lore(kept.isEmpty() ? null : kept);
        if (cleanRewardKey != null) {
            meta.getPersistentDataContainer().remove(cleanRewardKey);
        }
        clean.setItemMeta(meta);
        return clean;
    }

    private static String prettyItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return TextUtil.plain(item.getItemMeta().displayName());
        }
        String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String part : name.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private void ensureMenuDefaults() {
        FileConfiguration bundled = loadBundledConfig();
        int jar = bundled != null ? bundled.getInt("config-version", 8) : 8;
        if (config.getInt("config-version", 0) >= jar && config.isConfigurationSection("menu.items")) {
            return;
        }
        if (bundled != null && bundled.isConfigurationSection("menu")) {
            config.set("menu", bundled.get("menu"));
        }
        config.set("config-version", jar);
        saveConfig();
    }

    private void openCratesMenu(Player player) {
        Inventory inventory = GuiHelper.create("crates-menu",
                config.getString("menu.title", "&8Crates"),
                config.getInt("menu.rows", 5));
        switch (config.getString("menu.filler-style", "glass").toLowerCase(Locale.ROOT)) {
            case "dark" -> GuiHelper.fillDarkGlass(inventory);
            case "bordered-inner", "bordered" -> GuiHelper.fillBorderedInner(inventory);
            case "border" -> GuiHelper.fillBorder(inventory);
            default -> GuiHelper.fillGlass(inventory);
        }
        ConfigurationSection items = config.getConfigurationSection("menu.items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                placeMenuCrate(player, inventory, key, items.getConfigurationSection(key));
            }
        }
        SoundUtil.play(player, config.getString("menu.sound-open", config.getString("sound-open", "block.note_block.pling")));
        player.openInventory(inventory);
    }

    private void placeMenuCrate(Player player, Inventory inventory, String key, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        String id = section.getString("crate-id", key);
        CrateData crate = resolveMenuCrate(id);
        String crateId = crate == null ? id : crate.id();
        String keys = String.valueOf(getKeys(player.getUniqueId(), crateId));
        Material material = MaterialUtil.resolve(section.getString("material"), Material.CHEST);
        ItemStack item = ItemBuilder.of(material)
                .name(applyMenuPlaceholders(section.getString("name", "&f" + key), keys, "0"))
                .lore(applyMenuLore(section.getStringList("lore"), keys, "0"))
                .hideExtras()
                .build();
        ItemMeta meta = item.getItemMeta();
        if (meta != null && crateMenuKey != null) {
            meta.getPersistentDataContainer().set(crateMenuKey, PersistentDataType.STRING, crateId);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    private static String applyMenuPlaceholders(String line, String keys, String opened) {
        if (line == null) {
            return "";
        }
        return line.replace("%key%", keys).replace("%keys%", keys).replace("%opened%", opened);
    }

    private static List<String> applyMenuLore(List<String> lines, String keys, String opened) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(applyMenuPlaceholders(line, keys, opened));
        }
        return out;
    }

    private CrateData resolveMenuCrate(String id) {
        CrateData exact = crates.get(id);
        if (exact != null) {
            return exact;
        }
        for (CrateData crate : crates.values()) {
            String key = crate.id().toLowerCase(Locale.ROOT);
            if (key.equals(id) || key.contains(id) || id.contains(key)) {
                return crate;
            }
            if (id.equals("kill") && (key.contains("kill") || key.equals("killkey"))) {
                return crate;
            }
        }
        return null;
    }

    private void openPreview(Player player, CrateData crate) {
        previewing.put(player.getUniqueId(), crate.id());
        int rows = clampRows(crate.rows() > 0 ? crate.rows() : config.getInt("preview-rows", 5));
        Inventory inventory = GuiHelper.create("crate-preview",
                config.getString("preview-title", "&8Crate Preview").replace("%crate%", crate.display()),
                rows);
        GuiHelper.fillDarkBorder(inventory);
        int closeSlot = navSlot(rows);
        List<Integer> contentSlots = editorContentSlots(rows, closeSlot);
        List<String> previewLore = config.getStringList("preview-lore");
        for (int i = 0; i < crate.rewards().size() && i < contentSlots.size(); i++) {
            Reward prize = crate.rewards().get(i);
            if (prize == null || prize.allItems().isEmpty()) {
                continue;
            }
            ItemStack shown = sanitizeRewardItem(prize.item().clone());
            List<ItemStack> extras = prize.allItems();
            if (extras.size() > 1) {
                ItemMeta extraMeta = shown.getItemMeta();
                if (extraMeta != null) {
                    List<net.kyori.adventure.text.Component> extraLore = extraMeta.lore() == null
                            ? new ArrayList<>()
                            : new ArrayList<>(extraMeta.lore());
                    extraLore.add(TextUtil.itemComponent(""));
                    extraLore.add(TextUtil.itemComponent("&#FFE300| &fAlso includes:"));
                    for (int n = 1; n < extras.size() && n < 8; n++) {
                        extraLore.add(TextUtil.itemComponent("&#FFE300| &f" + extras.get(n).getAmount()
                                + "x " + prettyItemName(extras.get(n))));
                    }
                    extraMeta.lore(extraLore);
                    shown.setItemMeta(extraMeta);
                }
            }
            if (!previewLore.isEmpty()) {
                ItemMeta meta = shown.getItemMeta();
                if (meta != null) {
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    for (String line : previewLore) {
                        lore.add(TextUtil.itemComponent(line));
                    }
                    meta.lore(lore);
                    shown.setItemMeta(meta);
                }
            }
            inventory.setItem(contentSlots.get(i), shown);
        }
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            inventory.setItem(closeSlot, GuiHelper.closeButton());
        }
        player.openInventory(inventory);
    }

    private void openCrate(Player player, CrateData crate) {
        int owned = getKeys(player.getUniqueId(), crate.id());
        if (owned <= 0) {
            MessageUtil.sendChatIfPresent(player, msg("no-keys",
                    "&#FFEE00&lCRATES &8▷ &fYou need a key."));
            return;
        }
        if (emptySlots(player) < 1) {
            MessageUtil.sendChatIfPresent(player, msg("inventory-full",
                    "&#FFEE00&lCRATES &8▷ &fYour inventory is full. Clear space or left-click to preview."));
            return;
        }
        if ("selectable".equalsIgnoreCase(crate.animation())) {
            openSelectable(player, crate, owned);
            return;
        }
        startRolling(player, crate);
    }

    private void openSelectable(Player player, CrateData crate, int owned) {
        opening.put(player.getUniqueId(), crate.id());
        selectable.put(player.getUniqueId(), new HashSet<>());
        int rows = clampRows(crate.rows() > 0 ? crate.rows() : config.getInt("select-rows", 5));
        Inventory inventory = GuiHelper.create("crate-select",
                config.getString("select-title", "&8Select Rewards"), rows);
        GuiHelper.fillDarkBorder(inventory);
        int nav = navSlot(rows);
        List<Integer> contentSlots = editorContentSlots(rows, nav);
        for (int i = 0; i < crate.rewards().size() && i < contentSlots.size(); i++) {
            Reward reward = crate.rewards().get(i);
            if (reward == null || reward.item() == null || reward.item().getType().isAir()) {
                continue;
            }
            inventory.setItem(contentSlots.get(i), sanitizeRewardItem(reward.item().clone()));
        }
        // Preview-style: Close only until something is selected (then swaps to Confirm)
        if (nav >= 0 && nav < inventory.getSize()) {
            inventory.setItem(nav, GuiHelper.closeButton());
        }
        player.openInventory(inventory);
    }

    private void startRolling(Player player, CrateData crate) {
        List<Reward> pool = activeRewards(crate);
        if (pool.isEmpty()) {
            MessageUtil.sendChatIfPresent(player, msg("empty-crate",
                    "&#FFEE00&lCRATES &8▷ &fThis crate has no rewards."));
            return;
        }
        addKeys(player.getUniqueId(), crate.id(), -1);
        opening.put(player.getUniqueId(), crate.id());
        String anim = crate.animation().toLowerCase(Locale.ROOT);
        int rows = switch (anim) {
            case "rolling2" -> 4;
            case "rolling3" -> 5;
            default -> 3; // rolling1
        };
        Inventory inventory = GuiHelper.create("crate-roll",
                config.getString("roll-title", "&8Opening..."), rows);
        GuiHelper.fillDarkBorder(inventory);
        player.openInventory(inventory);
        int center = (rows / 2) * 9 + 4;
        int ticks = switch (anim) {
            case "rolling2" -> 55;
            case "rolling3" -> 80;
            default -> 30;
        };
        int period = switch (anim) {
            case "rolling2" -> 2;
            case "rolling3" -> 1;
            default -> 3;
        };
        final int[] tick = {0};
        final Reward[] last = {null};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Reward reward = pool.get(random.nextInt(pool.size()));
            last[0] = reward;
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder holderGui
                    && "crate-roll".equals(holderGui.getId())) {
                Inventory top = player.getOpenInventory().getTopInventory();
                if ("rolling3".equals(anim)) {
                    for (int offset = -3; offset <= 3; offset++) {
                        int slot = center + offset;
                        if (slot < 0 || slot >= top.getSize()) {
                            continue;
                        }
                        Reward r = offset == 0 ? reward : pool.get(random.nextInt(pool.size()));
                        top.setItem(slot, sanitizeRewardItem(r.item().clone()));
                    }
                } else if ("rolling2".equals(anim)) {
                    if (center - 1 >= 0) {
                        top.setItem(center - 1, sanitizeRewardItem(
                                pool.get(random.nextInt(pool.size())).item().clone()));
                    }
                    top.setItem(center, sanitizeRewardItem(reward.item().clone()));
                    if (center + 1 < top.getSize()) {
                        top.setItem(center + 1, sanitizeRewardItem(
                                pool.get(random.nextInt(pool.size())).item().clone()));
                    }
                } else {
                    top.setItem(center, sanitizeRewardItem(reward.item().clone()));
                }
                SoundUtil.play(player, config.getString("sound-tick", "ui.button.click"));
            }
            tick[0]++;
            if (tick[0] >= ticks) {
                if (holder[0] != null) {
                    holder[0].cancel();
                }
                Reward won = last[0] != null ? last[0] : reward;
                giveReward(player, won);
                MessageUtil.sendChatIfPresent(player, msg("won",
                        "&#FFEE00&lCRATES &8▷ &fYou won a reward!")
                        .replace("%crate%", crate.display()));
                opening.remove(player.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> player.closeInventory(), 30L);
            }
        }, 0L, period);
    }

    private void giveReward(Player player, Reward reward) {
        if (reward == null || (reward.item() == null || reward.item().getType().isAir())
                && (reward.command() == null || reward.command().isBlank())) {
            return; // Skip empty/AIR placeholder rewards
        }
        if (reward.type() == RewardType.COMMAND && reward.command() != null && !reward.command().isBlank()) {
            String cmd = reward.command().replace("%player%", player.getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else {
            for (ItemStack stack : reward.allItems()) {
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(sanitizeRewardItem(stack.clone()));
                leftover.values().clear();
            }
        }
        SoundUtil.play(player, config.getString("sound-win", "entity.player.levelup"));
    }

    private List<Reward> activeRewards(CrateData crate) {
        List<Reward> out = new ArrayList<>();
        if (crate == null || crate.rewards() == null) {
            return out;
        }
        for (Reward reward : crate.rewards()) {
            if (reward != null && !reward.allItems().isEmpty()) {
                out.add(reward);
            }
        }
        return out;
    }

    private void addItemToFirstEmptySlot(InventoryClickEvent event) {
        ItemStack moving = event.getCurrentItem();
        if (moving == null || moving.getType().isAir()) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int rows = top.getSize() / 9;
        int closeSlot = navSlot(rows);
        for (int slot : editorContentSlots(rows, closeSlot)) {
            ItemStack current = top.getItem(slot);
            if (current == null || current.getType().isAir()) {
                top.setItem(slot, moving.clone());
                event.setCurrentItem(null);
                return;
            }
        }
    }

    private void openBundleEditor(Player player, CrateData crate, int rewardIndex) {
        while (crate.rewards().size() <= rewardIndex) {
            crate.rewards().add(new Reward(RewardType.ITEM, new ItemStack(Material.AIR), null));
        }
        Reward current = crate.rewards().get(rewardIndex);
        bundleEdits.put(player.getUniqueId(), new BundleEdit(crate.id(), rewardIndex));
        int rows = Math.max(3, Math.min(6, config.getInt("bundle-rows", 4)));
        Inventory inventory = GuiHelper.create("crate-bundle",
                config.getString("bundle-title", "&8Reward items").replace("%crate%", crate.display()),
                rows);
        GuiHelper.fillDarkBorder(inventory);
        int closeSlot = navSlot(rows);
        List<Integer> contentSlots = editorContentSlots(rows, closeSlot);
        List<ItemStack> items = current.allItems();
        for (int i = 0; i < items.size() && i < contentSlots.size(); i++) {
            inventory.setItem(contentSlots.get(i), items.get(i).clone());
        }
        if (closeSlot >= 0 && closeSlot < inventory.getSize()) {
            inventory.setItem(closeSlot, GuiHelper.closeButton());
        }
        player.openInventory(inventory);
    }

    private void handleBundleClick(InventoryClickEvent event, Player player) {
        if (!bundleEdits.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }
        int rows = event.getInventory().getSize() / 9;
        int closeSlot = navSlot(rows);
        if (event.getRawSlot() == closeSlot) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && clicked.getType().name().endsWith("_STAINED_GLASS_PANE")
                && (event.getCursor() == null || event.getCursor().getType().isAir())) {
            event.setCancelled(true);
            return;
        }
        if (!editorContentSlots(rows, closeSlot).contains(event.getRawSlot())) {
            event.setCancelled(true);
        }
    }

    private void saveBundleAndReturn(Player player, Inventory inventory) {
        BundleEdit edit = bundleEdits.remove(player.getUniqueId());
        if (edit == null) {
            return;
        }
        CrateData crate = crates.get(edit.crateId());
        if (crate == null) {
            return;
        }
        int rows = inventory.getSize() / 9;
        int closeSlot = navSlot(rows);
        List<ItemStack> packed = new ArrayList<>();
        for (int slot : editorContentSlots(rows, closeSlot)) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || item.getType().name().endsWith("_STAINED_GLASS_PANE")) {
                continue;
            }
            packed.add(sanitizeRewardItem(item.clone()));
        }
        while (crate.rewards().size() <= edit.rewardIndex()) {
            crate.rewards().add(new Reward(RewardType.ITEM, new ItemStack(Material.AIR), null));
        }
        Reward prev = crate.rewards().get(edit.rewardIndex());
        ItemStack first = packed.isEmpty() ? new ItemStack(Material.AIR) : packed.remove(0);
        RewardType type = prev != null ? prev.type() : RewardType.ITEM;
        String command = prev != null ? prev.command() : null;
        crate.rewards().set(edit.rewardIndex(), new Reward(type, first, command, packed));
        saveCrate(crate);
        MessageUtil.sendChatIfPresent(player, msg("bundle-saved",
                "&#FFEE00&lCRATES &8▷ &fSaved reward items for &#FFEE00%crate%&f.")
                .replace("%crate%", crate.display()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            CrateData latest = crates.get(edit.crateId());
            if (latest != null && player.isOnline()) {
                openEdit(player, latest);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        String id = holder.getId();
        if ("crate-edit".equals(id)) {
            if (!editors.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            String crateId = editors.get(player.getUniqueId());
            CrateData crate = crates.get(crateId);
            if (crate == null) {
                event.setCancelled(true);
                return;
            }
            // Shift-click from the player's inventory dumps that stack into the next empty reward slot
            if (event.getRawSlot() >= event.getInventory().getSize()) {
                if (event.isShiftClick() && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
                    event.setCancelled(true);
                    addItemToFirstEmptySlot(event);
                }
                return;
            }
            int rows = event.getInventory().getSize() / 9;
            int closeSlot = navSlot(rows);
            if (event.getRawSlot() == closeSlot) {
                event.setCancelled(true);
                player.closeInventory();
                return;
            }
            // Cancel clicks on border glass
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType().name().endsWith("_STAINED_GLASS_PANE")
                    && (event.getCursor() == null || event.getCursor().getType().isAir())) {
                event.setCancelled(true);
                return;
            }
            List<Integer> contentSlots = editorContentSlots(rows, closeSlot);
            int rewardIndex = contentSlots.indexOf(event.getRawSlot());
            if (rewardIndex < 0) {
                event.setCancelled(true);
                return;
            }
            // Shift-click a reward slot to edit every item in that prize (spawners, stacks, etc.)
            if (event.isShiftClick()) {
                event.setCancelled(true);
                openBundleEditor(player, crate, rewardIndex);
                return;
            }
            // Right-click existing reward → COMMAND mode + chat prompt
            if (event.isRightClick() && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()
                    && event.getCursor() != null && event.getCursor().getType().isAir()) {
                event.setCancelled(true);
                // Prefer live unwrapped item in the slot as the reward item
                ItemStack clean = unwrapEditorItem(event.getCurrentItem());
                String existingCmd = null;
                if (rewardIndex < crate.rewards().size()) {
                    existingCmd = crate.rewards().get(rewardIndex).command();
                }
                while (crate.rewards().size() <= rewardIndex) {
                    crate.rewards().add(new Reward(RewardType.ITEM, new ItemStack(Material.STONE), null));
                }
                crate.rewards().set(rewardIndex, new Reward(RewardType.COMMAND, clean,
                        existingCmd == null || existingCmd.isBlank() ? "say %player% won" : existingCmd));
                saveCrate(crate);
                pendingCommandSlot.put(player.getUniqueId(), rewardIndex);
                pendingCommandCrate.put(player.getUniqueId(), crate.id());
                player.closeInventory();
                MessageUtil.sendChatIfPresent(player, msg("command-prompt",
                        "&#FFEE00&lCRATES &8▷ &fType the command in chat (no leading /). Cancel with &#FFEE00cancel&f."));
                return;
            }
            // Left-click with empty cursor on existing: toggle COMMAND -> ITEM only (don't block swaps)
            if (event.isLeftClick() && event.getCursor() != null && event.getCursor().getType().isAir()
                    && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()
                    && rewardIndex < crate.rewards().size()
                    && crate.rewards().get(rewardIndex).type() == RewardType.COMMAND
                    && !event.isShiftClick()) {
                event.setCancelled(true);
                Reward old = crate.rewards().get(rewardIndex);
                crate.rewards().set(rewardIndex, new Reward(RewardType.ITEM, sanitizeRewardItem(old.item()), null));
                saveCrate(crate);
                event.getInventory().setItem(event.getRawSlot(), decorateEditorReward(crate.rewards().get(rewardIndex)));
                return;
            }
            // Otherwise allow free swap / place / take in content slots
            return;
        }
        if ("crate-bundle".equals(id)) {
            handleBundleClick(event, player);
            return;
        }
        if ("crates-menu".equals(id)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || crateMenuKey == null) {
                return;
            }
            String crateId = clicked.getItemMeta().getPersistentDataContainer().get(crateMenuKey, PersistentDataType.STRING);
            if (crateId == null) {
                return;
            }
            CrateData crate = resolveMenuCrate(crateId);
            if (crate == null) {
                send(player, msg("not-found", "&#FFEE00&lCRATES &8▷ &fThat crate is not set up yet."));
                return;
            }
            if (event.isRightClick()) {
                openCrate(player, crate);
            } else {
                openPreview(player, crate);
            }
            return;
        }
        if ("crate-preview".equals(id)) {
            event.setCancelled(true);
            int closeSlot = navSlot(event.getInventory().getSize() / 9);
            if (event.getRawSlot() == closeSlot) {
                player.closeInventory();
            }
            return;
        }
        if ("crate-select".equals(id)) {
            event.setCancelled(true);
            String crateId = opening.get(player.getUniqueId());
            CrateData crate = crates.get(crateId);
            if (crate == null) {
                return;
            }
            int owned = getKeys(player.getUniqueId(), crate.id());
            Set<Integer> selected = selectable.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
            int rows = event.getInventory().getSize() / 9;
            int nav = navSlot(rows);
            List<Integer> contentSlots = editorContentSlots(rows, nav);
            ItemStack navItem = event.getInventory().getItem(nav);
            boolean navIsConfirm = navItem != null && navItem.getType() == Material.LIME_DYE;
            if (event.getRawSlot() == nav) {
                if (navIsConfirm && !selected.isEmpty()) {
                    int consume = selected.size();
                    if (consume > owned) {
                        return;
                    }
                    if (emptySlots(player) < consume) {
                        MessageUtil.sendChatIfPresent(player, msg("inventory-full",
                                "&#FFEE00&lCRATES &8▷ &fYour inventory is full. Clear space or left-click to preview."));
                        return;
                    }
                    addKeys(player.getUniqueId(), crate.id(), -consume);
                    for (int rewardIndex : selected) {
                        if (rewardIndex >= 0 && rewardIndex < crate.rewards().size()) {
                            giveReward(player, crate.rewards().get(rewardIndex));
                        }
                    }
                    MessageUtil.sendChatIfPresent(player, msg("won-multi",
                            "&#FFEE00&lCRATES &8▷ &fClaimed &#FFEE00%amount% &frewards.")
                            .replace("%amount%", String.valueOf(consume)));
                    selectable.remove(player.getUniqueId());
                    opening.remove(player.getUniqueId());
                    player.closeInventory();
                } else if (!navIsConfirm) {
                    selectable.remove(player.getUniqueId());
                    opening.remove(player.getUniqueId());
                    player.closeInventory();
                }
                return;
            }
            int rewardIndex = contentSlots.indexOf(event.getRawSlot());
            if (rewardIndex < 0 || rewardIndex >= crate.rewards().size()) {
                return;
            }
            Reward slotReward = crate.rewards().get(rewardIndex);
            if (slotReward == null || slotReward.item() == null || slotReward.item().getType().isAir()) {
                return;
            }
            if (selected.contains(rewardIndex)) {
                selected.remove(rewardIndex);
                event.getInventory().setItem(event.getRawSlot(),
                        sanitizeRewardItem(crate.rewards().get(rewardIndex).item().clone()));
            } else if (selected.size() < owned) {
                selected.add(rewardIndex);
                event.getInventory().setItem(event.getRawSlot(), ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                        .name("&#80FF00&lSELECTED")
                        .lore(List.of(
                                "&8Description",
                                "",
                                "&#80FF00| &#80FF00Click &fhere to",
                                "&#80FF00| &fUnselect",
                                "",
                                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Unselect"
                        ))
                        .build());
            }
            // Swap Close ↔ Confirm in the same nav slot
            if (selected.isEmpty()) {
                event.getInventory().setItem(nav, GuiHelper.closeButton());
            } else {
                event.getInventory().setItem(nav, GuiHelper.confirmButton(
                        "Claim " + selected.size() + " reward(s)"));
            }
            return;
        }
        if ("crate-roll".equals(id)) {
            event.setCancelled(true);
            // Block number-key / offhand swaps that can ghost-dupe through cancelled clicks
            switch (event.getClick()) {
                case NUMBER_KEY, SWAP_OFFHAND, DOUBLE_CLICK -> event.setCancelled(true);
                default -> {
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        String id = holder.getId();
        if ("crates-menu".equals(id)) {
            event.setCancelled(true);
            return;
        }
        if (!id.startsWith("crate-")) {
            return;
        }
        // Preview / select / roll are fully locked; edit allows content drags only inside top inv
        if ("crate-edit".equals(id) || "crate-bundle".equals(id)) {
            int top = event.getView().getTopInventory().getSize();
            for (int raw : event.getRawSlots()) {
                if (raw < top) {
                    int rows = top / 9;
                    int closeSlot = navSlot(rows);
                    if (raw == closeSlot || (rows >= 4 && isBorderSlot(raw, rows))) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            return;
        }
        event.setCancelled(true);
    }

    private boolean isBorderSlot(int slot, int rows) {
        int col = slot % 9;
        int row = slot / 9;
        return col == 0 || col == 8 || row == 0 || row == rows - 1;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if ("crate-bundle".equals(holder.getId())) {
            saveBundleAndReturn(player, event.getInventory());
            return;
        }
        if ("crate-edit".equals(holder.getId())) {
            // Keep editor session if waiting for command chat input or bundle edit
            if (pendingCommandSlot.containsKey(player.getUniqueId())
                    || bundleEdits.containsKey(player.getUniqueId())) {
                return;
            }
            String crateId = editors.remove(player.getUniqueId());
            if (crateId == null) {
                return;
            }
            CrateData existing = crates.get(crateId);
            if (existing == null) {
                return;
            }
            List<Reward> rewards = new ArrayList<>();
            int rows = event.getInventory().getSize() / 9;
            int closeSlot = navSlot(rows);
            List<Integer> contentSlots = editorContentSlots(rows, closeSlot);
            // Preserve slot layout — empty slots stay empty (AIR placeholders stripped when rolling)
            for (int i = 0; i < contentSlots.size(); i++) {
                int invSlot = contentSlots.get(i);
                ItemStack item = event.getInventory().getItem(invSlot);
                if (item == null || item.getType().isAir()
                        || item.getType().name().endsWith("_STAINED_GLASS_PANE")
                        || item.getType() == Material.RED_DYE
                        || item.getType() == Material.LIME_DYE
                        || item.getType() == Material.FLOWER_BANNER_PATTERN) {
                    rewards.add(new Reward(RewardType.ITEM, new ItemStack(Material.AIR), null));
                    continue;
                }
                RewardType type = RewardType.ITEM;
                String command = null;
                if (i < existing.rewards().size()) {
                    Reward prev = existing.rewards().get(i);
                    if (prev != null && prev.item() != null && !prev.item().getType().isAir()) {
                        type = prev.type();
                        command = prev.command();
                    }
                }
                ItemStack clean = unwrapEditorItem(item);
                List<ItemStack> extras = List.of();
                if (i < existing.rewards().size() && existing.rewards().get(i) != null) {
                    extras = existing.rewards().get(i).extras();
                }
                rewards.add(new Reward(type, clean, command, extras));
            }
            // Trim trailing empty slots only
            while (!rewards.isEmpty()) {
                Reward last = rewards.get(rewards.size() - 1);
                if (last.item() == null || last.item().getType().isAir()) {
                    rewards.remove(rewards.size() - 1);
                } else {
                    break;
                }
            }
            CrateData updated = new CrateData(existing.id(), existing.display(), existing.animation(),
                    clampRows(existing.rows() <= 0 ? config.getInt("default-rows", 5) : existing.rows()), rewards);
            crates.put(crateId, updated);
            saveCrate(updated);
            MessageUtil.sendChatIfPresent(player, msg("saved",
                    "&#FFEE00&lCRATES &8▷ &fSaved crate &#FFEE00%crate%&f.")
                    .replace("%crate%", existing.display() == null ? crateId : existing.display()));
        }
        if ("crate-select".equals(holder.getId())) {
            selectable.remove(player.getUniqueId());
            opening.remove(player.getUniqueId());
        }
        if ("crate-preview".equals(holder.getId())) {
            previewing.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        String crateId = placements.get(locKey(event.getClickedBlock().getLocation()));
        if (crateId == null) {
            return;
        }
        event.setCancelled(true);
        CrateData crate = crates.get(crateId);
        if (crate == null) {
            return;
        }
        Action action = event.getAction();
        // Left-click: preview rewards (no keys required)
        if (action == Action.LEFT_CLICK_BLOCK) {
            openPreview(event.getPlayer(), crate);
            return;
        }
        // Right-click: open/use keys
        if (action == Action.RIGHT_CLICK_BLOCK) {
            openCrate(event.getPlayer(), crate);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Integer slot = pendingCommandSlot.get(player.getUniqueId());
        if (slot == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingCommandSlot.remove(player.getUniqueId());
            String crateId = pendingCommandCrate.remove(player.getUniqueId());
            if (crateId == null) {
                crateId = editors.get(player.getUniqueId());
            }
            CrateData crate = crateId == null ? null : crates.get(crateId);
            if (crate == null || slot < 0 || slot >= crate.rewards().size()) {
                MessageUtil.sendChatIfPresent(player, msg("command-cancel",
                        "&#FFEE00&lCRATES &8▷ &fCommand edit cancelled."));
                return;
            }
            if (text.equalsIgnoreCase("cancel")) {
                editors.put(player.getUniqueId(), crate.id());
                openEdit(player, crate);
                MessageUtil.sendChatIfPresent(player, msg("command-cancel",
                        "&#FFEE00&lCRATES &8▷ &fCommand edit cancelled."));
                return;
            }
            String cmd = text.startsWith("/") ? text.substring(1) : text;
            Reward old = crate.rewards().get(slot);
            crate.rewards().set(slot, new Reward(RewardType.COMMAND, old.item(), cmd));
            editors.put(player.getUniqueId(), crate.id());
            saveCrate(crate);
            openEdit(player, crate);
            MessageUtil.sendChatIfPresent(player, msg("command-set",
                    "&#FFEE00&lCRATES &8▷ &fCommand reward set."));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (placements.containsKey(locKey(event.getBlock().getLocation()))) {
            if (!this.hasSetupPerm(event.getPlayer(), "setupcore.crates.admin")) {
                event.setCancelled(true);
                MessageUtil.sendChatIfPresent(event.getPlayer(), msg("cant-break",
                        "&#FFEE00&lCRATES &8▷ &fYou cannot break crates."));
            }
        }
    }

    private void send(CommandSender sender, String message) {
        if (MessageUtil.isBlank(message)) {
            return;
        }
        if (sender instanceof Player player) {
            MessageUtil.sendChatIfPresent(player, message);
        } else {
            sender.sendMessage(TextUtil.component(message));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("keyall")) {
            if (args.length == 1) {
                return List.of("start").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            }
            return List.of();
        }
        if (args.length == 1) {
            return List.of("create", "delete", "place", "unplace", "edit", "list", "setanimation", "key").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (List.of("delete", "place", "edit", "setanimation").contains(sub)) {
                return crates.keySet().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            if ("key".equals(sub)) {
                return List.of("give", "giveall", "remove", "set", "inspect").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            if ("create".equals(sub)) {
                return List.of();
            }
            if ("unplace".equals(sub) || "list".equals(sub)) {
                return List.of();
            }
        }
        if (args.length == 3 && "setanimation".equals(sub)) {
            return List.of("selectable", "rolling1", "rolling2", "rolling3").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if ("key".equals(sub)) {
            String keyAction = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3) {
                if (List.of("give", "remove", "set", "inspect").contains(keyAction)) {
                    return null; // online players
                }
                if ("giveall".equals(keyAction)) {
                    return crates.keySet().stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
                }
            }
            if (args.length == 4 && List.of("give", "remove", "set").contains(keyAction)) {
                List<String> options = new ArrayList<>(crates.keySet());
                options.add("all");
                return options.stream()
                        .filter(s -> s.startsWith(args[3].toLowerCase(Locale.ROOT))).toList();
            }
            if (args.length == 5 && "remove".equals(keyAction)) {
                return List.of("1", "8", "16", "32", "64", "all").stream()
                        .filter(s -> s.startsWith(args[4].toLowerCase(Locale.ROOT))).toList();
            }
        }
        return List.of();
    }

    private enum RewardType {ITEM, COMMAND}

    private record Reward(RewardType type, ItemStack item, String command, List<ItemStack> extras) {
        Reward(RewardType type, ItemStack item, String command) {
            this(type, item, command, List.of());
        }

        Reward {
            extras = extras == null ? List.of() : List.copyOf(extras);
        }

        List<ItemStack> allItems() {
            List<ItemStack> out = new ArrayList<>();
            if (item != null && !item.getType().isAir()) {
                out.add(item);
            }
            if (extras != null) {
                for (ItemStack extra : extras) {
                    if (extra != null && !extra.getType().isAir()) {
                        out.add(extra);
                    }
                }
            }
            return out;
        }
    }

    private record BundleEdit(String crateId, int rewardIndex) {
    }

    private record CrateData(String id, String display, String animation, int rows, List<Reward> rewards) {
    }
}

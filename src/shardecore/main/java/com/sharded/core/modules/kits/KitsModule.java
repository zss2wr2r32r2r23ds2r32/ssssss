package com.sharded.core.modules.kits;

import com.sharded.core.ShardedCore;
import com.sharded.core.setup.gui.GuiHelper;
import com.sharded.core.setup.gui.GuiHolder;
import com.sharded.core.setup.SetupFeatureModule;
import com.sharded.core.setup.util.InventoryUtil;
import com.sharded.core.setup.util.ItemBuilder;
import com.sharded.core.setup.util.MaterialUtil;
import com.sharded.core.setup.util.MessageUtil;
import com.sharded.core.setup.util.SoundUtil;
import com.sharded.core.setup.util.TextUtil;
import com.sharded.core.util.OfflinePlayers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class KitsModule extends SetupFeatureModule implements CommandExecutor, TabCompleter {

    private final Map<String, KitDefinition> kits = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Set<UUID> openKitsGuis = new HashSet<>();
    private final Set<UUID> openAdminGuis = new HashSet<>();
    private final Map<UUID, String> previewEditors = new HashMap<>();
    private final Map<UUID, String> adminEditors = new HashMap<>();
    private final Map<UUID, Integer> previewSelected = new HashMap<>();
    private final Map<UUID, Integer> adminSelectedSlot = new HashMap<>();
    private final Map<UUID, String> pendingDelete = new HashMap<>();
    private static final int SLOT_HELMET = 36;
    private static final int SLOT_CHEST = 37;
    private static final int SLOT_LEGS = 38;
    private static final int SLOT_BOOTS = 39;
    private static final int SLOT_OFFHAND = 40;
    private static final int SLOT_CANCEL = 45;
    private static final int SLOT_IMPORT = 49;
    private static final int SLOT_RESET = 52;
    private static final int SLOT_SAVE = 53;
    private File kitsFile;
    private File cooldownFile;
    private File layoutsDir;
    private FileConfiguration kitsConfig;
    private FileConfiguration cooldownConfig;
    private BukkitTask refreshTask;
    private NamespacedKey kitKey;

    public KitsModule(ShardedCore plugin) {
        super(plugin, "kits");
    }

    @Override
    protected void onEnable() {
        kitKey = new NamespacedKey(plugin, "kit_id");
        loadFiles();
        migrateKitsMessages();
        migrateConfigLists("config-version", "lore-available", "lore-cooldown", "lore-locked",
                "default-action-available", "default-action-cooldown", "default-action-locked",
                "gui-title", "admin-gui-title", "preview-title", "filler", "star", "star-color");
        kitsFile = new File(folder, "kits.yml");
        cooldownFile = new File(folder, "cooldowns.yml");
        if (!kitsFile.exists()) {
            try {
                kitsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create kits.yml", e);
            }
        }
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
        layoutsDir = new File(folder, "layouts");
        if (!layoutsDir.exists() && !layoutsDir.mkdirs()) {
            plugin.getLogger().warning("Could not create kits/layouts directory");
        }
        loadKits();
        ensureDefaultKits();
        loadCooldowns();
        for (String cmd : List.of("kits", "kit")) {
            if (plugin.getCommand(cmd) != null) {
                plugin.getCommand(cmd).setExecutor(this);
                plugin.getCommand(cmd).setTabCompleter(this);
            }
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenKitsGuis, 20L, 20L);
    }

    @Override
    public void reload() {
        super.reload();
        loadFiles();
        migrateKitsMessages();
        migrateConfigLists("config-version", "lore-available", "lore-cooldown", "lore-locked",
                "default-action-available", "default-action-cooldown", "default-action-locked",
                "gui-title", "admin-gui-title", "preview-title", "filler", "star", "star-color");
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile);
        loadKits();
        ensureDefaultKits();
        loadCooldowns();
    }

    @Override
    protected void onDisable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        openKitsGuis.clear();
        openAdminGuis.clear();
        saveKits();
        saveCooldowns();
    }

    private void migrateKitsMessages() {
        InputStream stream = plugin.getResource("modules/kits/messages.yml");
        if (stream == null || messages == null) {
            return;
        }
        FileConfiguration bundled = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        int jar = bundled.getInt("messages-version", 0);
        int live = messages.getInt("messages-version", 0);
        if (live >= jar) {
            return;
        }
        for (String key : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(key)) {
                continue;
            }
            messages.set(key, bundled.get(key));
        }
        messages.set("messages-version", jar);
        try {
            messages.save(new File(folder, "messages.yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed migrating kits messages", e);
        }
    }

    private void loadKits() {
        kits.clear();
        ConfigurationSection section = kitsConfig.getConfigurationSection("kits");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            String path = "kits." + id + ".";
            kits.put(id.toLowerCase(Locale.ROOT), readKit(id.toLowerCase(Locale.ROOT), kitsConfig, path));
        }
    }

    private KitDefinition readKit(String id, FileConfiguration yaml, String path) {
        List<ItemStack> contents = readItemList(yaml.getList(path + "contents"));
        ItemStack iconItem = yaml.getItemStack(path + "icon-item");
        if (iconItem == null || iconItem.getType().isAir()) {
            Material mat = MaterialUtil.resolve(yaml.getString(path + "icon"), Material.BUNDLE);
            iconItem = new ItemStack(mat == null ? Material.BUNDLE : mat);
        }
        String perm = yaml.getString(path + "permission");
        if (perm != null && perm.equalsIgnoreCase("none")) {
            perm = "";
        } else if (perm != null && perm.equalsIgnoreCase("default")) {
            perm = null;
        }
        return new KitDefinition(
                id,
                yaml.getString(path + "display", id),
                yaml.getString(path + "description", ""),
                iconItem,
                yaml.getBoolean(path + "glow", false),
                yaml.getLong(path + "cooldown-seconds", 3600L),
                yaml.getInt(path + "slot", -1),
                yaml.getInt(path + "rarity", 1),
                perm,
                yaml.getString(path + "action-available"),
                yaml.getString(path + "action-cooldown"),
                yaml.getString(path + "action-locked"),
                contents,
                (ItemStack) yaml.get(path + "helmet"),
                (ItemStack) yaml.get(path + "chestplate"),
                (ItemStack) yaml.get(path + "leggings"),
                (ItemStack) yaml.get(path + "boots"),
                (ItemStack) yaml.get(path + "offhand")
        );
    }

    private static List<ItemStack> readItemList(List<?> raw) {
        List<ItemStack> contents = new ArrayList<>();
        if (raw == null) {
            return contents;
        }
        for (Object obj : raw) {
            if (obj instanceof ItemStack stack) {
                contents.add(stack);
            }
        }
        return contents;
    }

    private void ensureDefaultKits() {
        ConfigurationSection section = config.getConfigurationSection("kits");
        if (section == null && config.getDefaults() != null) {
            section = config.getDefaults().getConfigurationSection("kits");
        }
        if (section == null) {
            return;
        }
        long defaultCooldown = config.getLong("default-cooldown-seconds", 3600L);
        boolean changed = false;
        for (String id : section.getKeys(false)) {
            String key = id.toLowerCase(Locale.ROOT);
            if (kits.containsKey(key)) {
                continue;
            }
            String path = "kits." + id + ".";
            Material icon = MaterialUtil.resolve(
                    config.getString(path + "icon"),
                    config.getString(path + "fallback"));
            if (icon == null) {
                icon = Material.BUNDLE;
            }
            String permRaw = config.getString(path + "permission", "default");
            String storedPerm = permRaw.equalsIgnoreCase("none") ? ""
                    : (permRaw.equalsIgnoreCase("default") ? null : permRaw);
            kits.put(key, new KitDefinition(
                    key,
                    config.getString(path + "display", id),
                    config.getString(path + "description", ""),
                    new ItemStack(icon),
                    config.getBoolean(path + "glow", false),
                    config.getLong(path + "cooldown-seconds", defaultCooldown),
                    config.getInt(path + "slot", -1),
                    config.getInt(path + "rarity", 1),
                    storedPerm,
                    null, null, null,
                    List.of(), null, null, null, null, null));
            changed = true;
        }
        if (changed) {
            saveKits();
        }
    }

    private void saveKits() {
        kitsConfig.set("kits", null);
        for (KitDefinition kit : kits.values()) {
            String path = "kits." + kit.id() + ".";
            kitsConfig.set(path + "display", kit.display());
            kitsConfig.set(path + "description", kit.description());
            kitsConfig.set(path + "icon", kit.icon().getType().name());
            kitsConfig.set(path + "icon-item", kit.icon());
            kitsConfig.set(path + "glow", kit.glow());
            kitsConfig.set(path + "cooldown-seconds", kit.cooldownSeconds());
            kitsConfig.set(path + "slot", kit.slot());
            kitsConfig.set(path + "rarity", kit.rarity());
            if (kit.permission() == null) {
                kitsConfig.set(path + "permission", "default");
            } else if (kit.permission().isEmpty()) {
                kitsConfig.set(path + "permission", "none");
            } else {
                kitsConfig.set(path + "permission", kit.permission());
            }
            kitsConfig.set(path + "action-available", kit.actionAvailable());
            kitsConfig.set(path + "action-cooldown", kit.actionCooldown());
            kitsConfig.set(path + "action-locked", kit.actionLocked());
            kitsConfig.set(path + "contents", kit.contents());
            kitsConfig.set(path + "helmet", kit.helmet());
            kitsConfig.set(path + "chestplate", kit.chestplate());
            kitsConfig.set(path + "leggings", kit.leggings());
            kitsConfig.set(path + "boots", kit.boots());
            kitsConfig.set(path + "offhand", kit.offhand());
        }
        try {
            kitsConfig.save(kitsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving kits.yml", e);
        }
    }

    private void loadCooldowns() {
        cooldowns.clear();
        if (cooldownConfig.getConfigurationSection("players") == null) {
            return;
        }
        for (String uuidKey : cooldownConfig.getConfigurationSection("players").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidKey);
            Map<String, Long> map = new HashMap<>();
            for (String kit : cooldownConfig.getConfigurationSection("players." + uuidKey).getKeys(false)) {
                map.put(kit, cooldownConfig.getLong("players." + uuidKey + "." + kit));
            }
            cooldowns.put(uuid, map);
        }
    }

    private void saveCooldowns() {
        cooldownConfig.set("players", null);
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            for (Map.Entry<String, Long> kit : entry.getValue().entrySet()) {
                cooldownConfig.set("players." + entry.getKey() + "." + kit.getKey(), kit.getValue());
            }
        }
        try {
            cooldownConfig.save(cooldownFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving cooldowns.yml", e);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("kits") || label.equalsIgnoreCase("k")
                || label.equalsIgnoreCase("kits")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(msg("players-only", "Players only."));
                return true;
            }
            openGui(player);
            return true;
        }
        if (args.length < 1) {
            if (sender instanceof Player player) {
                openGui(player);
            } else {
                TextUtil.sendMessage(sender, prefix() + plugin.errorColor()
                        + msg("usage", "Usage: /kit [name|admin|reload|edit]"));
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "admin" -> handleAdmin(sender, args);
            case "reload" -> handleReload(sender);
            case "edit", "editadmin" -> {
                if (args.length >= 2 && isAdmin(sender)) {
                    yield handleEdit(sender, args);
                }
                yield handleEdit(sender, args);
            }
            case "create" -> handleAdminCreate(sender, asAdmin(args));
            case "delete" -> handleAdminDelete(sender, asAdmin(args));
            case "claim" -> handleClaim(sender, args);
            case "cooldown" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
                    yield handleCooldownRemove(sender, args);
                }
                yield handleAdminCooldown(sender, asAdmin(args));
            }
            default -> {
                if (sender instanceof Player player) {
                    claimKit(player, sub, true);
                } else {
                    TextUtil.sendMessage(sender, prefix() + plugin.errorColor()
                            + msg("usage", "Usage: /kit [name|admin|reload|edit]"));
                }
                yield true;
            }
        };
    }

    private static String[] asAdmin(String[] args) {
        String[] out = new String[args.length + 1];
        out[0] = "admin";
        System.arraycopy(args, 0, out, 1, args.length);
        return out;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("kits.reload") && !isAdmin(sender)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("no-permission", "No permission."));
            return true;
        }
        reload();
        TextUtil.sendMessage(sender, prefix() + plugin.successColor() + msg("reloaded", "Reloaded kits."));
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("no-permission", "No permission."));
            return true;
        }
        if (args.length == 1) {
            if (sender instanceof Player player) {
                openAdminGui(player);
            } else {
                sender.sendMessage(msg("players-only", "Players only."));
            }
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleAdminCreate(sender, args);
            case "delete" -> handleAdminDelete(sender, args);
            case "rename" -> handleAdminRename(sender, args);
            case "clone" -> handleAdminClone(sender, args);
            case "cooldown" -> handleAdminCooldown(sender, args);
            case "rarity" -> handleAdminRarity(sender, args);
            case "permission" -> handleAdminPermission(sender, args);
            case "slot" -> handleAdminSlot(sender, args);
            case "icon" -> handleAdminIcon(sender, args);
            case "glow" -> handleAdminGlow(sender, args);
            case "action" -> handleAdminAction(sender, args);
            case "edit" -> handleEdit(sender, new String[]{"edit", args.length > 2 ? args[2] : ""});
            default -> {
                TextUtil.sendMessage(sender, prefix() + plugin.errorColor()
                        + msg("usage", "Usage: /kit admin <create|delete|rename|clone|...>"));
                yield true;
            }
        };
    }

    private boolean handleAdminCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("players-only", "Players only."));
            return true;
        }
        if (!isAdmin(player)) {
            feedback(player, plugin.errorColor() + msg("no-permission", "No permission."));
            return true;
        }
        // /kit admin create <name> <cooldown> <rarity>
        if (args.length < 3) {
            feedback(player, plugin.errorColor() + msg("usage-create",
                    "Usage: /kit admin create <name> <cooldown> <rarity>"));
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (kits.containsKey(id)) {
            feedback(player, plugin.errorColor() + msg("exists", "Kit already exists."));
            return true;
        }
        long cooldown = args.length >= 4
                ? parseCooldownSeconds(args[3], config.getLong("default-cooldown-seconds", 3600L))
                : config.getLong("default-cooldown-seconds", 3600L);
        int rarity = 1;
        if (args.length >= 5) {
            try {
                rarity = Math.max(0, Math.min(5, Integer.parseInt(args[4])));
            } catch (NumberFormatException ignored) {
                rarity = 1;
            }
        }
        KitDefinition captured = captureKit(player, id);
        kits.put(id, captured.withCooldown(cooldown).withRarity(rarity).withSlot(nextOpenSlot()));
        saveKits();
        feedback(player, plugin.successColor() + msg("created", "Created kit %kit%.").replace("%kit%", id));
        return true;
    }

    private int nextOpenSlot() {
        Set<Integer> used = new HashSet<>();
        for (KitDefinition kit : kits.values()) {
            if (kit.slot() >= 0) {
                used.add(kit.slot());
            }
        }
        int size = Math.max(9, config.getInt("rows", 5) * 9);
        for (int i = 0; i < size; i++) {
            if (!used.contains(i)) {
                return i;
            }
        }
        return 0;
    }

    private boolean handleAdminDelete(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) {
            TextUtil.sendMessage(sender, MessageUtil.errorLine(msg("no-permission", "You dont have permission")));
            return true;
        }
        if (args.length < 3) {
            TextUtil.sendMessage(sender, MessageUtil.errorLine(msg("usage-delete",
                    "Usage: /kit admin delete <name> [confirm]")));
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (!kits.containsKey(id)) {
            TextUtil.sendMessage(sender, MessageUtil.errorLine(msg("not-found", "Kit not found.")));
            return true;
        }
        if (args.length >= 4 && args[3].equalsIgnoreCase("confirm")) {
            deleteKit(id);
            TextUtil.sendMessage(sender, prefix() + msg("deleted", "Deleted kit %success%%kit%&f.")
                    .replace("%kit%", id));
            return true;
        }
        if (sender instanceof Player player) {
            openDeleteConfirm(player, id);
        } else {
            TextUtil.sendMessage(sender, prefix() + msg("delete-confirm",
                    "Use /kit admin delete %kit% confirm.").replace("%kit%", id));
        }
        return true;
    }

    private void deleteKit(String id) {
        kits.remove(id);
        invalidateLayoutsForKit(id);
        saveKits();
    }

    private boolean handleAdminRename(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("no-permission", "No permission."));
            return true;
        }
        if (args.length < 4) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("usage-rename",
                    "Usage: /kit admin rename <old> <new>"));
            return true;
        }
        String oldId = args[2].toLowerCase(Locale.ROOT);
        String newId = args[3].toLowerCase(Locale.ROOT);
        KitDefinition old = kits.get(oldId);
        if (old == null) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("not-found", "Kit not found."));
            return true;
        }
        if (kits.containsKey(newId)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("exists", "Kit already exists."));
            return true;
        }
        kits.remove(oldId);
        kits.put(newId, old.withId(newId).withDisplay(newId));
        saveKits();
        TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                + msg("renamed", "Renamed %old% to %kit%.")
                .replace("%old%", oldId).replace("%kit%", newId));
        return true;
    }

    private boolean handleAdminClone(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("no-permission", "No permission."));
            return true;
        }
        if (args.length < 4) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("usage-clone",
                    "Usage: /kit admin clone <source> <new>"));
            return true;
        }
        String source = args[2].toLowerCase(Locale.ROOT);
        String newId = args[3].toLowerCase(Locale.ROOT);
        KitDefinition src = kits.get(source);
        if (src == null) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("not-found", "Kit not found."));
            return true;
        }
        if (kits.containsKey(newId)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("exists", "Kit already exists."));
            return true;
        }
        kits.put(newId, src.withId(newId).withDisplay(newId).withSlot(nextOpenSlot()));
        saveKits();
        TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                + msg("cloned", "Cloned %old% into %kit%.")
                .replace("%old%", source).replace("%kit%", newId));
        return true;
    }

    private boolean handleAdminCooldown(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("no-permission", "No permission."));
            return true;
        }
        if (args.length < 4) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("usage-cooldown",
                    "Usage: /kit admin cooldown <kit> <cooldown>"));
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        KitDefinition kit = kits.get(id);
        if (kit == null) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("not-found", "Kit not found."));
            return true;
        }
        long seconds = parseCooldownSeconds(args[3], kit.cooldownSeconds());
        kits.put(id, kit.withCooldown(seconds));
        saveKits();
        TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                + msg("cooldown-set", "Cooldown for %kit% is now %time%.")
                .replace("%kit%", id)
                .replace("%time%", args[3]));
        return true;
    }

    private boolean handleAdminRarity(CommandSender sender, String[] args) {
        if (!requireAdminKit(sender, args, 4, "usage-rarity")) {
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        KitDefinition kit = kits.get(id);
        int stars;
        try {
            stars = Math.max(0, Math.min(5, Integer.parseInt(args[3])));
        } catch (NumberFormatException e) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("usage-rarity",
                    "Usage: /kit admin rarity <kit> <stars>"));
            return true;
        }
        kits.put(id, kit.withRarity(stars));
        saveKits();
        TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                + msg("rarity-set", "Rarity for %kit% is now %stars% stars.")
                .replace("%kit%", id).replace("%stars%", String.valueOf(stars)));
        return true;
    }

    private boolean handleAdminPermission(CommandSender sender, String[] args) {
        if (!requireAdminKit(sender, args, 4, "usage-permission")) {
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        KitDefinition kit = kits.get(id);
        String value = args[3];
        String stored;
        String shown;
        if (value.equalsIgnoreCase("default")) {
            stored = null;
            shown = "kitsbypriyme.claim." + id;
        } else if (value.equalsIgnoreCase("none")) {
            stored = "";
            shown = "none";
        } else {
            stored = value;
            shown = value;
        }
        kits.put(id, kit.withPermission(stored));
        saveKits();
        TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                + msg("permission-set", "Permission for %kit% is now %permission%.")
                .replace("%kit%", id).replace("%permission%", shown));
        return true;
    }

    private boolean handleAdminSlot(CommandSender sender, String[] args) {
        if (!requireAdminKit(sender, args, 4, "usage-slot")) {
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        KitDefinition kit = kits.get(id);
        int slot;
        try {
            slot = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("usage-slot",
                    "Usage: /kit admin slot <kit> <slot>"));
            return true;
        }
        kits.put(id, kit.withSlot(slot));
        saveKits();
        TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                + msg("slot-set", "Menu slot for %kit% is now %slot%.")
                .replace("%kit%", id).replace("%slot%", String.valueOf(slot)));
        return true;
    }

    private boolean handleAdminIcon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("players-only", "Players only."));
            return true;
        }
        if (!requireAdminKit(sender, args, 3, "usage-icon")) {
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            feedback(player, plugin.errorColor() + msg("empty-hand", "Hold an item to set the kit icon."));
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        kits.put(id, kits.get(id).withIcon(hand.clone()));
        saveKits();
        feedback(player, plugin.successColor() + msg("icon-set", "Set icon for %kit%.").replace("%kit%", id));
        return true;
    }

    private boolean handleAdminGlow(CommandSender sender, String[] args) {
        if (!requireAdminKit(sender, args, 4, "usage-glow")) {
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        boolean on = args[3].equalsIgnoreCase("on") || args[3].equalsIgnoreCase("true");
        kits.put(id, kits.get(id).withGlow(on));
        saveKits();
        TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                + msg("glow-set", "Glow for %kit% is now %value%.")
                .replace("%kit%", id).replace("%value%", on ? "on" : "off"));
        return true;
    }

    private boolean handleAdminAction(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("no-permission", "No permission."));
            return true;
        }
        if (args.length < 5) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("usage-action",
                    "Usage: /kit admin action <kit> <available|cooldown|locked> <text>"));
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        KitDefinition kit = kits.get(id);
        if (kit == null) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("not-found", "Kit not found."));
            return true;
        }
        String type = args[3].toLowerCase(Locale.ROOT);
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
        KitDefinition updated = switch (type) {
            case "available" -> kit.withActions(text, kit.actionCooldown(), kit.actionLocked());
            case "cooldown" -> kit.withActions(kit.actionAvailable(), text, kit.actionLocked());
            case "locked" -> kit.withActions(kit.actionAvailable(), kit.actionCooldown(), text);
            default -> null;
        };
        if (updated == null) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("usage-action",
                    "Usage: /kit admin action <kit> <available|cooldown|locked> <text>"));
            return true;
        }
        kits.put(id, updated);
        saveKits();
        TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                + msg("action-set", "Updated %type% action for %kit%.")
                .replace("%type%", type).replace("%kit%", id));
        return true;
    }

    private boolean requireAdminKit(CommandSender sender, String[] args, int min, String usageKey) {
        if (!isAdmin(sender)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("no-permission", "No permission."));
            return false;
        }
        if (args.length < min) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg(usageKey, "Invalid usage."));
            return false;
        }
        if (kits.get(args[2].toLowerCase(Locale.ROOT)) == null) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("not-found", "Kit not found."));
            return false;
        }
        return true;
    }

    private boolean handleCooldownRemove(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor() + msg("no-permission", "No permission."));
            return true;
        }
        if (args.length < 3) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor()
                    + msg("usage-cooldown", "Usage: /kit cooldown remove <id> [player]"));
            return true;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (!kits.containsKey(id)) {
            TextUtil.sendMessage(sender, prefix() + plugin.errorColor()
                    + msg("not-found", "Kit not found.").replace("%kit%", id));
            return true;
        }
        if (args.length >= 4) {
            var target = OfflinePlayers.resolve(args[3]);
            Map<String, Long> map = cooldowns.get(target.getUniqueId());
            if (map != null) {
                map.remove(id);
            }
            saveCooldowns();
            TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                    + msg("cooldown-removed-player", "Removed cooldown for %kit% from %player%.")
                    .replace("%kit%", id)
                    .replace("%player%", target.getName() == null ? args[3] : target.getName()));
            return true;
        }
        if (sender instanceof Player player) {
            Map<String, Long> map = cooldowns.get(player.getUniqueId());
            if (map != null) {
                map.remove(id);
            }
            saveCooldowns();
            TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                    + msg("cooldown-removed", "Removed your cooldown for %kit%.").replace("%kit%", id));
            return true;
        }
        for (Map<String, Long> map : cooldowns.values()) {
            map.remove(id);
        }
        saveCooldowns();
        TextUtil.sendMessage(sender, prefix() + plugin.successColor()
                + msg("cooldown-removed-all", "Removed cooldown for %kit% from all players.").replace("%kit%", id));
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("players-only", "Players only."));
            return true;
        }
        if (!isAdmin(player)) {
            feedback(player, MessageUtil.errorLine(msg("no-permission", "You dont have permission")));
            return true;
        }
        if (args.length < 2 || args[1].isBlank()) {
            feedback(player, MessageUtil.errorLine(msg("usage-edit", "Usage: /kit edit <name>")));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        KitDefinition kit = kits.get(id);
        if (kit == null) {
            feedback(player, MessageUtil.errorLine(msg("not-found", "Kit not found.")));
            return true;
        }
        openAdminEdit(player, kit);
        return true;
    }

    private boolean handleClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("players-only", "Players only."));
            return true;
        }
        if (args.length < 2) {
            feedback(player, MessageUtil.errorLine(msg("usage-claim", "Usage: /kit <name>")));
            return true;
        }
        claimKit(player, args[1].toLowerCase(Locale.ROOT), true);
        return true;
    }

    private void invalidateLayoutsForKit(String kitId) {
        if (layoutsDir == null || !layoutsDir.isDirectory()) {
            return;
        }
        File[] files = layoutsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (!yaml.contains(kitId)) {
                continue;
            }
            yaml.set(kitId, null);
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed clearing kit layout " + kitId + " in " + file.getName(), e);
            }
        }
    }

    private KitDefinition captureKit(Player player, String id) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        List<ItemStack> contents = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            ItemStack item = storage != null && i < storage.length ? storage[i] : inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                contents.add(item.clone());
            } else {
                contents.add(new ItemStack(Material.AIR));
            }
        }
        ItemStack icon = inv.getItemInMainHand();
        if (icon == null || icon.getType().isAir()) {
            icon = new ItemStack(Material.BUNDLE);
        } else {
            icon = icon.clone();
        }
        return new KitDefinition(id, id, "", icon, false,
                config.getLong("default-cooldown-seconds", 3600L),
                -1, 1, null, null, null, null, contents,
                cloneOrNull(inv.getHelmet()), cloneOrNull(inv.getChestplate()),
                cloneOrNull(inv.getLeggings()), cloneOrNull(inv.getBoots()), cloneOrNull(inv.getItemInOffHand()));
    }

    private ItemStack cloneOrNull(ItemStack stack) {
        if (stack == null || stack.getType().isAir()
                || stack.getType() == Material.GRAY_STAINED_GLASS_PANE
                || stack.getType() == Material.BLACK_STAINED_GLASS_PANE
                || stack.getType() == Material.RED_DYE) {
            return null;
        }
        return stack.clone();
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("kits.admin")
                || this.hasSetupPerm(sender, "setupcore.kit.admin")
                || sender.isOp();
    }

    private boolean canAccessKit(Player player, KitDefinition kit) {
        if (player.hasPermission("kits.bypass") || this.hasSetupPerm(player, "setupcore.kit.bypass")) {
            return true;
        }
        String perm = kit.permission();
        if (perm == null) {
            return player.hasPermission("kitsbypriyme.claim." + kit.id())
                    || this.hasSetupPerm(player, "setupcore.kit." + kit.id());
        }
        if (perm.isEmpty()) {
            return true;
        }
        return player.hasPermission(perm);
    }

    private boolean canBypassCooldown(Player player) {
        if (player.hasPermission("kits.bypass")
                || player.hasPermission("kits.no.cooldown")
                || this.hasSetupPerm(player, "setupcore.kit.bypass")) {
            return true;
        }
        if (!config.getBoolean("admin-bypass-cooldown", false)) {
            return false;
        }
        return isAdmin(player);
    }

    private long remainingMs(Player player, KitDefinition kit) {
        long last = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(kit.id(), 0L);
        return (last + kit.cooldownSeconds() * 1000L) - System.currentTimeMillis();
    }

    private KitDefinition resolveClaimKit(Player player, KitDefinition base) {
        KitDefinition personal = loadPersonalLayout(player.getUniqueId(), base.id());
        if (personal == null) {
            return base;
        }
        boolean personalEmpty = compactContents(personal.contents()).isEmpty();
        boolean baseHasItems = !compactContents(base.contents()).isEmpty();
        if (personalEmpty && baseHasItems) {
            return base.withContents(
                    base.contents(),
                    personal.helmet() != null ? personal.helmet() : base.helmet(),
                    personal.chestplate() != null ? personal.chestplate() : base.chestplate(),
                    personal.leggings() != null ? personal.leggings() : base.leggings(),
                    personal.boots() != null ? personal.boots() : base.boots(),
                    personal.offhand() != null ? personal.offhand() : base.offhand());
        }
        return personal;
    }

    private @Nullable KitDefinition loadPersonalLayout(UUID uuid, String kitId) {
        File file = new File(layoutsDir, uuid + ".yml");
        if (!file.exists()) {
            return null;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = kitId + ".";
        if (!yaml.contains(kitId + ".contents") && !yaml.contains(path + "contents")) {
            return null;
        }
        KitDefinition base = kits.get(kitId);
        if (base == null) {
            return null;
        }
        return base.withContents(
                readItemList(yaml.getList(path + "contents")),
                (ItemStack) yaml.get(path + "helmet"),
                (ItemStack) yaml.get(path + "chestplate"),
                (ItemStack) yaml.get(path + "leggings"),
                (ItemStack) yaml.get(path + "boots"),
                (ItemStack) yaml.get(path + "offhand"));
    }

    private void writePersonalLayout(UUID uuid, String kitId, List<ItemStack> contents,
                                     ItemStack helmet, ItemStack chest, ItemStack legs,
                                     ItemStack boots, ItemStack offhand) {
        File file = new File(layoutsDir, uuid + ".yml");
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = kitId + ".";
        yaml.set(path + "contents", contents);
        yaml.set(path + "helmet", helmet);
        yaml.set(path + "chestplate", chest);
        yaml.set(path + "leggings", legs);
        yaml.set(path + "boots", boots);
        yaml.set(path + "offhand", offhand);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving kit layout for " + uuid, e);
        }
    }

    private void clearPersonalLayout(UUID uuid, String kitId) {
        File file = new File(layoutsDir, uuid + ".yml");
        if (!file.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set(kitId, null);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed clearing kit layout for " + uuid, e);
        }
    }

    private void claimKit(Player player, String id, boolean announce) {
        KitDefinition base = kits.get(id);
        if (base == null) {
            feedback(player, plugin.errorColor() + msg("not-found", "Kit not found."));
            return;
        }
        if (!canAccessKit(player, base)) {
            feedback(player, plugin.errorColor() + msg("locked", "You dont have permission"));
            return;
        }
        KitDefinition kit = resolveClaimKit(player, base);
        long remaining = remainingMs(player, base);
        if (remaining > 0 && !canBypassCooldown(player)) {
            String cooldownMsg = msg("cooldown", "&#9FFF00&lKITS &8▷ &fOn cooldown: &#FF8600%time%")
                    .replace("%time%", formatCooldown(remaining))
                    .replace("%seconds%", String.valueOf(Math.max(1L, remaining / 1000L)));
            MessageUtil.sendActionBar(player, cooldownMsg);
            return;
        }
        List<ItemStack> giveContents = compactContents(kit.contents());
        int needed = giveContents.size();
        int free = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                free++;
            }
        }
        if (free < needed) {
            feedback(player, plugin.errorColor() + msg("no-space", "Not enough inventory space."));
            return;
        }
        PlayerInventory inv = player.getInventory();
        if (kit.contents().size() == 36) {
            for (int i = 0; i < 36; i++) {
                ItemStack item = kit.contents().get(i);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                ItemStack current = inv.getItem(i);
                if (current == null || current.getType().isAir()) {
                    inv.setItem(i, item.clone());
                } else {
                    InventoryUtil.giveItems(player, item, item.getAmount());
                }
            }
        } else {
            for (ItemStack item : giveContents) {
                InventoryUtil.giveItems(player, item, item.getAmount());
            }
        }
        if (kit.helmet() != null) {
            inv.setHelmet(kit.helmet().clone());
        }
        if (kit.chestplate() != null) {
            inv.setChestplate(kit.chestplate().clone());
        }
        if (kit.leggings() != null) {
            inv.setLeggings(kit.leggings().clone());
        }
        if (kit.boots() != null) {
            inv.setBoots(kit.boots().clone());
        }
        if (kit.offhand() != null) {
            inv.setItemInOffHand(kit.offhand().clone());
        }
        cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(id, System.currentTimeMillis());
        saveCooldowns();
        if (announce) {
            feedback(player, plugin.successColor()
                    + msg("claimed", "Claimed kit %kit%.").replace("%kit%", base.display()));
        }
        SoundUtil.play(player, config.getString("sound-success", "entity.experience_orb.pickup"));
    }

    private static List<ItemStack> compactContents(List<ItemStack> contents) {
        List<ItemStack> out = new ArrayList<>();
        if (contents == null) {
            return out;
        }
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                out.add(item);
            }
        }
        return out;
    }

    private void openGui(Player player) {
        Inventory inventory = buildKitsInventory(player, false);
        openKitsGuis.add(player.getUniqueId());
        player.openInventory(inventory);
    }

    private void openAdminGui(Player player) {
        Inventory inventory = buildKitsInventory(player, true);
        openAdminGuis.add(player.getUniqueId());
        player.openInventory(inventory);
    }

    private Inventory buildKitsInventory(Player player, boolean admin) {
        String title = admin
                ? config.getString("admin-gui-title", "&8Kit Admin")
                : config.getString("gui-title", "&8Kits");
        int rows = admin ? config.getInt("admin-rows", config.getInt("rows", 5)) : config.getInt("rows", 5);
        Inventory inventory = GuiHelper.create(admin ? "kits-admin" : "kits", title, rows);
        fillFiller(inventory);
        int auto = 10;
        long now = System.currentTimeMillis();
        for (KitDefinition kit : kits.values()) {
            int slot = kit.slot() >= 0 ? kit.slot() : auto++;
            if (slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, buildKitIcon(player, kit, now, admin));
        }
        return inventory;
    }

    private void fillFiller(Inventory inventory) {
        Material filler = MaterialUtil.resolve(config.getString("filler"), Material.GRAY_STAINED_GLASS_PANE);
        if (filler == null) {
            filler = Material.GRAY_STAINED_GLASS_PANE;
        }
        ItemStack pane = ItemBuilder.of(filler).name(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane.clone());
        }
    }

    private ItemStack buildKitIcon(Player player, KitDefinition kit, long now, boolean admin) {
        boolean locked = !canAccessKit(player, kit);
        long remainingMs = remainingMs(player, kit);
        boolean onCooldown = remainingMs > 0 && !canBypassCooldown(player);
        String color = config.getString("kits." + kit.id() + ".color", "&#9FFF00");
        String displayName = config.getString("kits." + kit.id() + ".name");
        if (displayName == null || displayName.isBlank()) {
            displayName = color + "&l" + kit.display().toUpperCase(Locale.ROOT) + " " + color + "Kit";
        }
        String cooldownText = onCooldown ? formatCooldown(remainingMs) : "Ready";
        String stars = rarityStars(kit.rarity());
        String action;
        List<String> template;
        if (locked) {
            template = loreList("lore-locked");
            action = firstNonBlank(kit.actionLocked(), config.getString("default-action-locked",
                    "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0To Unlock"));
        } else if (onCooldown) {
            template = loreList("lore-cooldown");
            action = firstNonBlank(kit.actionCooldown(), config.getString("default-action-cooldown",
                    "&x&F&F&8&6&0&0▷ On Cooldown"));
        } else {
            template = loreList("lore-available");
            if (template.isEmpty()) {
                template = loreList("lore-owned");
            }
            action = firstNonBlank(kit.actionAvailable(), config.getString("default-action-available",
                    "&x&F&F&B&A&0&0▷ Click To Claim"));
        }
        if (admin) {
            template = new ArrayList<>(template);
            template.add("");
            template.add("&7Left-click to edit contents");
            template.add("&7Drag to another slot to move");
        }
        List<String> lore = new ArrayList<>();
        for (String line : template) {
            lore.add(line
                    .replace("%kit%", kit.display())
                    .replace("%color%", color)
                    .replace("%cooldown%", cooldownText)
                    .replace("%stars%", stars)
                    .replace("%description%", kit.description() == null || kit.description().isBlank()
                            ? "Description" : kit.description())
                    .replace("%action%", action));
        }
        ItemStack icon = kit.icon() == null ? new ItemStack(Material.BUNDLE) : kit.icon().clone();
        ItemBuilder builder = ItemBuilder.of(icon).name(displayName).lore(lore).hideExtras().glow(kit.glow());
        ItemStack built = builder.build();
        ItemMeta meta = built.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, kit.id());
            built.setItemMeta(meta);
        }
        return built;
    }

    private List<String> loreList(String key) {
        List<String> list = config.getStringList(key);
        return list == null ? List.of() : list;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }

    private String rarityStars(int rarity) {
        String star = config.getString("star", "★");
        String color = config.getString("star-color", "&#FFD700");
        int n = Math.max(0, Math.min(5, rarity));
        if (n == 0) {
            return "";
        }
        return color + star.repeat(n);
    }

    private void refreshOpenKitsGuis() {
        refreshTracked(openKitsGuis, "kits", false);
    }

    private void refreshTracked(Set<UUID> set, String id, boolean admin) {
        if (set.isEmpty()) {
            return;
        }
        for (UUID uuid : Set.copyOf(set)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                set.remove(uuid);
                continue;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder holder)
                    || !id.equals(holder.getId())) {
                set.remove(uuid);
                continue;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            fillFiller(top);
            long now = System.currentTimeMillis();
            int auto = 10;
            for (KitDefinition kit : kits.values()) {
                int slot = kit.slot() >= 0 ? kit.slot() : auto++;
                if (slot < top.getSize()) {
                    top.setItem(slot, buildKitIcon(player, kit, now, admin));
                }
            }
        }
    }

    private void openPreview(Player player, KitDefinition kit) {
        KitDefinition view = resolveClaimKit(player, kit);
        Inventory inventory = GuiHelper.create("kits-preview",
                config.getString("preview-title", "&8Kit Preview - %kit%").replace("%kit%", kit.display()), 6);
        fillKitLayoutGui(inventory, view, true);
        previewEditors.put(player.getUniqueId(), kit.id());
        previewSelected.remove(player.getUniqueId());
        player.openInventory(inventory);
    }

    private void openAdminEdit(Player player, KitDefinition kit) {
        Inventory inventory = GuiHelper.create("kits-admin-edit",
                config.getString("edit-title", "&8Edit Kit - %kit%").replace("%kit%", kit.display()), 6);
        fillKitLayoutGui(inventory, kit, false);
        adminEditors.put(player.getUniqueId(), kit.id());
        player.openInventory(inventory);
        feedback(player, plugin.successColor() + msg("edit-opened", "Editing kit %kit%.").replace("%kit%", kit.id()));
    }

    private void openDeleteConfirm(Player player, String kitId) {
        pendingDelete.put(player.getUniqueId(), kitId);
        Inventory inventory = GuiHelper.create("kits-admin-delete",
                config.getString("delete-title", "&8Delete %kit%?").replace("%kit%", kitId), 3);
        fillFiller(inventory);
        inventory.setItem(11, ItemBuilder.of(Material.LIME_CONCRETE)
                .name("&#8AFF00&lCONFIRM")
                .lore("&7Delete kit &f" + kitId).build());
        inventory.setItem(15, ItemBuilder.of(Material.RED_CONCRETE)
                .name("&#FF0000&lCANCEL")
                .lore("&7Keep this kit").build());
        player.openInventory(inventory);
    }

    private void fillKitLayoutGui(Inventory inventory, KitDefinition view, boolean personal) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, null);
        }
        ItemStack dark = ItemBuilder.darkGlassPane();
        ItemStack light = ItemBuilder.glassPane();
        for (int slot = 41; slot <= 44; slot++) {
            inventory.setItem(slot, dark);
        }
        for (int slot = 46; slot <= 51; slot++) {
            inventory.setItem(slot, light);
        }
        List<ItemStack> contents = view.contents() == null ? List.of() : view.contents();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = slot < contents.size() ? contents.get(slot) : null;
            if (item != null && !item.getType().isAir()) {
                inventory.setItem(slot, item.clone());
            }
        }
        if (view.helmet() != null) {
            inventory.setItem(SLOT_HELMET, view.helmet().clone());
        }
        if (view.chestplate() != null) {
            inventory.setItem(SLOT_CHEST, view.chestplate().clone());
        }
        if (view.leggings() != null) {
            inventory.setItem(SLOT_LEGS, view.leggings().clone());
        }
        if (view.boots() != null) {
            inventory.setItem(SLOT_BOOTS, view.boots().clone());
        }
        if (view.offhand() != null) {
            inventory.setItem(SLOT_OFFHAND, view.offhand().clone());
        }
        if (personal) {
            inventory.setItem(SLOT_CANCEL, ItemBuilder.of(Material.RED_CANDLE)
                    .name("&#FF0000&lCANCEL")
                    .lore("&8Description", "", "&#FF0000| &fClose without saving").build());
            inventory.setItem(SLOT_RESET, ItemBuilder.of(Material.CHEST)
                    .name("&#FFBA00&lRESET")
                    .lore("&8Description", "", "&#FFBA00| &fReset personal sorting").build());
            inventory.setItem(SLOT_SAVE, ItemBuilder.of(Material.GREEN_CANDLE)
                    .name("&#8AFF00&lSAVE")
                    .lore("&8Description", "", "&#8AFF00| &fSave your personal sorting").build());
        } else {
            inventory.setItem(SLOT_CANCEL, GuiHelper.backButton("Kits"));
            inventory.setItem(SLOT_IMPORT, ItemBuilder.of(Material.HOPPER)
                    .name("&#9FFF00&lIMPORT INVENTORY")
                    .lore("&7Load your current inventory,", "&7armor, and offhand into this kit.").build());
            inventory.setItem(SLOT_SAVE, ItemBuilder.of(Material.GREEN_CANDLE)
                    .name("&#8AFF00&lSAVE")
                    .lore("&7Save armor, offhand, and items.").build());
        }
    }

    private static boolean isEditablePreviewSlot(int slot) {
        return (slot >= 0 && slot < 36)
                || slot == SLOT_HELMET || slot == SLOT_CHEST || slot == SLOT_LEGS
                || slot == SLOT_BOOTS || slot == SLOT_OFFHAND;
    }

    private static boolean isControlSlot(int slot, boolean personal) {
        if (slot == SLOT_CANCEL || slot == SLOT_SAVE
                || (slot >= 41 && slot <= 44) || (slot >= 46 && slot <= 51 && slot != SLOT_IMPORT)) {
            return true;
        }
        if (personal && slot == SLOT_RESET) {
            return true;
        }
        return !personal && slot == SLOT_IMPORT;
    }

    private static boolean isGlassPane(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return item.getType().name().endsWith("_STAINED_GLASS_PANE");
    }

    private void savePreviewLayout(Player player, Inventory inventory, String kitId) {
        if (kits.get(kitId) == null) {
            return;
        }
        writePersonalLayout(player.getUniqueId(), kitId, readContents(inventory),
                armorOrNull(inventory.getItem(SLOT_HELMET)),
                armorOrNull(inventory.getItem(SLOT_CHEST)),
                armorOrNull(inventory.getItem(SLOT_LEGS)),
                armorOrNull(inventory.getItem(SLOT_BOOTS)),
                armorOrNull(inventory.getItem(SLOT_OFFHAND)));
        MessageUtil.sendActionBar(player, msg("layout-saved-actionbar",
                "&fSuccessfully Saved Your &#8AFF00Arrangement"));
    }

    private void saveAdminKit(Player player, Inventory inventory, String kitId) {
        KitDefinition old = kits.get(kitId);
        if (old == null) {
            return;
        }
        kits.put(kitId, old.withContents(readContents(inventory),
                armorOrNull(inventory.getItem(SLOT_HELMET)),
                armorOrNull(inventory.getItem(SLOT_CHEST)),
                armorOrNull(inventory.getItem(SLOT_LEGS)),
                armorOrNull(inventory.getItem(SLOT_BOOTS)),
                armorOrNull(inventory.getItem(SLOT_OFFHAND))));
        saveKits();
        invalidateLayoutsForKit(kitId);
        feedback(player, msg("edited", "Updated kit %success%%kit%&f.").replace("%kit%", kitId));
    }

    private List<ItemStack> readContents(Inventory inventory) {
        List<ItemStack> contents = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || isGlassPane(item) || isLayoutControl(item)) {
                contents.add(new ItemStack(Material.AIR));
            } else {
                contents.add(item.clone());
            }
        }
        return contents;
    }

    private static boolean isLayoutControl(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.GREEN_CANDLE || type == Material.RED_CANDLE
                || type == Material.CHEST || type == Material.HOPPER || type == Material.SPYGLASS;
    }

    private void revertGuiToBase(Inventory inventory, String kitId) {
        KitDefinition base = kits.get(kitId);
        if (base == null) {
            return;
        }
        fillKitLayoutGui(inventory, base, true);
    }

    private ItemStack armorOrNull(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || isGlassPane(stack) || isLayoutControl(stack)) {
            return null;
        }
        return stack.clone();
    }

    private String formatCooldown(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return totalSeconds + "s";
    }

    static long parseCooldownSeconds(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.endsWith("min")) {
                return Long.parseLong(value.substring(0, value.length() - 3)) * 60L;
            }
            if (value.endsWith("d")) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 86400L;
            }
            if (value.endsWith("h")) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 3600L;
            }
            if (value.endsWith("m")) {
                return Long.parseLong(value.substring(0, value.length() - 1)) * 60L;
            }
            if (value.endsWith("s")) {
                return Long.parseLong(value.substring(0, value.length() - 1));
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void feedback(Player player, String message) {
        if (message != null && message.contains("&#FF0000&lERROR")) {
            MessageUtil.send(player, config, message);
        } else {
            MessageUtil.send(player, config, prefix() + message);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder topHolder)) {
            return;
        }
        String topId = topHolder.getId();
        if ("kits-preview".equals(topId) || "kits-admin-edit".equals(topId)) {
            handleKitLayoutClick(event, player, topId);
            return;
        }
        if ("kits-admin-delete".equals(topId)) {
            event.setCancelled(true);
            if (event.getRawSlot() == 11) {
                String id = pendingDelete.remove(player.getUniqueId());
                if (id != null) {
                    deleteKit(id);
                    feedback(player, msg("deleted", "Deleted kit %success%%kit%&f.").replace("%kit%", id));
                }
                player.closeInventory();
                openAdminGui(player);
            } else if (event.getRawSlot() == 15) {
                pendingDelete.remove(player.getUniqueId());
                player.closeInventory();
                openAdminGui(player);
            }
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if ("kits-admin".equals(holder.getId())) {
            handleAdminMenuClick(event, player);
            return;
        }
        if (!"kits".equals(holder.getId())) {
            return;
        }
        event.setCancelled(true);
        KitDefinition kit = kitAt(event.getCurrentItem());
        if (kit == null) {
            return;
        }
        ClickType click = event.getClick();
        boolean right = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        if (right) {
            openPreview(player, kit);
            return;
        }
        if (!canAccessKit(player, kit)) {
            feedback(player, MessageUtil.errorLine(msg("locked", "You dont have permission")));
            return;
        }
        long remaining = remainingMs(player, kit);
        if (remaining > 0 && !canBypassCooldown(player)) {
            String cooldownMsg = msg("cooldown", "&#9FFF00&lKITS &8▷ &fOn cooldown: &#FF8600%time%")
                    .replace("%time%", formatCooldown(remaining))
                    .replace("%seconds%", String.valueOf(Math.max(1L, remaining / 1000L)));
            MessageUtil.sendActionBar(player, cooldownMsg);
            return;
        }
        player.closeInventory();
        claimKit(player, kit.id(), true);
    }

    private void handleAdminMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int raw = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();
        if (raw < 0 || raw >= top.getSize()) {
            return;
        }
        KitDefinition held = kitAt(player.getItemOnCursor());
        KitDefinition clicked = kitAt(event.getCurrentItem());
        if (held != null) {
            int previous = held.slot();
            kits.put(held.id(), held.withSlot(raw));
            if (clicked != null && !clicked.id().equals(held.id())) {
                kits.put(clicked.id(), clicked.withSlot(previous >= 0 ? previous : nextOpenSlot()));
            }
            saveKits();
            player.setItemOnCursor(null);
            openAdminGui(player);
            return;
        }
        if (clicked == null) {
            return;
        }
        if (event.isRightClick() || event.isShiftClick()) {
            openAdminEdit(player, clicked);
            return;
        }
        player.setItemOnCursor(event.getCurrentItem().clone());
    }

    private @Nullable KitDefinition kitAt(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        return id == null ? null : kits.get(id);
    }

    private void handleKitLayoutClick(InventoryClickEvent event, Player player, String topId) {
        boolean adminEdit = "kits-admin-edit".equals(topId);
        boolean editor = adminEdit
                ? adminEditors.containsKey(player.getUniqueId())
                : previewEditors.containsKey(player.getUniqueId());
        Inventory top = event.getView().getTopInventory();
        int topSize = top.getSize();
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < topSize;
        ClickType click = event.getClick();

        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP
                || click == ClickType.CREATIVE
                || raw < 0
                || event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.OUTSIDE
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ALL_CURSOR
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ONE_CURSOR
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT) {
            event.setCancelled(true);
            return;
        }

        if (inTop && isControlSlot(raw, !adminEdit)) {
            event.setCancelled(true);
            if (raw == SLOT_CANCEL) {
                previewEditors.remove(player.getUniqueId());
                adminEditors.remove(player.getUniqueId());
                previewSelected.remove(player.getUniqueId());
                if (!adminEdit) {
                    MessageUtil.sendActionBar(player, msg("layout-cancelled-actionbar",
                            "&fCancelled without saving"));
                }
                openGui(player);
                return;
            }
            if (raw == SLOT_RESET && !adminEdit) {
                String kitId = previewEditors.get(player.getUniqueId());
                if (kitId != null) {
                    clearPersonalLayout(player.getUniqueId(), kitId);
                    revertGuiToBase(top, kitId);
                    previewSelected.remove(player.getUniqueId());
                    MessageUtil.sendActionBar(player, msg("layout-reverted-actionbar",
                            "&fSuccessfully Reverted Your &#FF0000Arrangement"));
                }
                return;
            }
            if (raw == SLOT_IMPORT && adminEdit) {
                String kitId = adminEditors.get(player.getUniqueId());
                if (kitId != null) {
                    KitDefinition captured = captureKit(player, kitId);
                    KitDefinition old = kits.get(kitId);
                    if (old != null) {
                        captured = old.withContents(captured.contents(), captured.helmet(),
                                captured.chestplate(), captured.leggings(), captured.boots(), captured.offhand());
                    }
                    fillKitLayoutGui(top, captured, false);
                    feedback(player, plugin.successColor()
                            + msg("imported-inventory", "Imported inventory.").replace("%kit%", kitId));
                }
                return;
            }
            if (raw == SLOT_SAVE) {
                if (adminEdit) {
                    String kitId = adminEditors.get(player.getUniqueId());
                    if (kitId != null) {
                        saveAdminKit(player, top, kitId);
                    }
                } else {
                    String kitId = previewEditors.get(player.getUniqueId());
                    if (kitId != null) {
                        savePreviewLayout(player, top, kitId);
                    }
                }
                player.closeInventory();
                return;
            }
            return;
        }

        if (!editor) {
            event.setCancelled(true);
            return;
        }

        if (isGlassPane(event.getCurrentItem()) || isGlassPane(event.getCursor())
                || isLayoutControl(event.getCurrentItem()) || isLayoutControl(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if (!adminEdit) {
            event.setCancelled(true);
            if (!inTop || !isEditablePreviewSlot(raw)) {
                return;
            }
            Integer selected = previewSelected.get(player.getUniqueId());
            if (selected == null) {
                previewSelected.put(player.getUniqueId(), raw);
                return;
            }
            ItemStack a = top.getItem(selected);
            ItemStack b = top.getItem(raw);
            top.setItem(selected, b);
            top.setItem(raw, a);
            previewSelected.remove(player.getUniqueId());
            return;
        }

        boolean shift = click.isShiftClick();
        boolean hotbar = click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DOUBLE_CLICK;
        if (!inTop && shift) {
            event.setCancelled(true);
            ItemStack src = event.getCurrentItem();
            if (src == null || src.getType().isAir()) {
                return;
            }
            ItemStack moving = src.clone();
            event.getClickedInventory().setItem(event.getSlot(), null);
            for (int i = 0; i < 36; i++) {
                ItemStack at = top.getItem(i);
                if (at == null || at.getType().isAir()) {
                    top.setItem(i, moving);
                    moving = null;
                    break;
                }
            }
            if (moving != null) {
                for (int armorSlot : new int[]{SLOT_HELMET, SLOT_CHEST, SLOT_LEGS, SLOT_BOOTS, SLOT_OFFHAND}) {
                    ItemStack at = top.getItem(armorSlot);
                    if (at == null || at.getType().isAir()) {
                        top.setItem(armorSlot, moving);
                        moving = null;
                        break;
                    }
                }
            }
            if (moving != null) {
                player.getInventory().addItem(moving);
            }
            return;
        }
        if (inTop && isEditablePreviewSlot(raw)) {
            if (hotbar || shift) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(false);
            return;
        }
        if (!inTop) {
            if (player.getItemOnCursor() != null && !player.getItemOnCursor().getType().isAir()) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        String id = holder.getId();
        if ("kits-preview".equals(id)) {
            event.setCancelled(true);
            return;
        }
        if ("kits-admin-edit".equals(id)) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int slot : event.getRawSlots()) {
                if (slot >= topSize || !isEditablePreviewSlot(slot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        if ("kits".equals(id) || "kits-admin".equals(id) || "kits-admin-delete".equals(id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        String id = holder.getId();
        if ("kits".equals(id)) {
            openKitsGuis.remove(player.getUniqueId());
            return;
        }
        if ("kits-admin".equals(id)) {
            openAdminGuis.remove(player.getUniqueId());
            adminSelectedSlot.remove(player.getUniqueId());
            return;
        }
        if ("kits-preview".equals(id) || "kits-admin-edit".equals(id)) {
            previewEditors.remove(player.getUniqueId());
            adminEditors.remove(player.getUniqueId());
            previewSelected.remove(player.getUniqueId());
            ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                player.setItemOnCursor(null);
            }
        }
        if ("kits-admin-delete".equals(id)) {
            pendingDelete.remove(player.getUniqueId());
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("kit")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("admin", "reload", "edit", "claim"));
            base.addAll(kits.keySet());
            return base.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && isAdmin(sender)) {
            return List.of("create", "delete", "rename", "clone", "cooldown", "rarity", "permission",
                            "slot", "icon", "glow", "action", "edit")
                    .stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && List.of("edit", "claim", "delete").contains(args[0].toLowerCase(Locale.ROOT))) {
            return kits.keySet().stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (List.of("delete", "rename", "clone", "cooldown", "rarity", "permission", "slot",
                    "icon", "glow", "action", "edit").contains(sub)) {
                return kits.keySet().stream().filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("glow")) {
                return List.of("on", "off");
            }
            if (sub.equals("permission")) {
                return List.of("default", "none");
            }
            if (sub.equals("action")) {
                return List.of("available", "cooldown", "locked");
            }
            if (sub.equals("delete")) {
                return List.of("confirm");
            }
            if (sub.equals("rename") || sub.equals("clone")) {
                return List.of();
            }
        }
        return List.of();
    }

    @Override
    public String prefix() {
        return messages.getString("prefix", "&#9FFF00&lKITS &8▷ &r");
    }

    private record KitDefinition(String id, String display, String description, ItemStack icon, boolean glow,
                                 long cooldownSeconds, int slot, int rarity, String permission,
                                 String actionAvailable, String actionCooldown, String actionLocked,
                                 List<ItemStack> contents, ItemStack helmet, ItemStack chestplate,
                                 ItemStack leggings, ItemStack boots, ItemStack offhand) {
        KitDefinition withId(String newId) {
            return new KitDefinition(newId, display, description, icon, glow, cooldownSeconds, slot, rarity,
                    permission, actionAvailable, actionCooldown, actionLocked, contents, helmet, chestplate,
                    leggings, boots, offhand);
        }

        KitDefinition withDisplay(String newDisplay) {
            return new KitDefinition(id, newDisplay, description, icon, glow, cooldownSeconds, slot, rarity,
                    permission, actionAvailable, actionCooldown, actionLocked, contents, helmet, chestplate,
                    leggings, boots, offhand);
        }

        KitDefinition withCooldown(long seconds) {
            return new KitDefinition(id, display, description, icon, glow, seconds, slot, rarity,
                    permission, actionAvailable, actionCooldown, actionLocked, contents, helmet, chestplate,
                    leggings, boots, offhand);
        }

        KitDefinition withRarity(int stars) {
            return new KitDefinition(id, display, description, icon, glow, cooldownSeconds, slot, stars,
                    permission, actionAvailable, actionCooldown, actionLocked, contents, helmet, chestplate,
                    leggings, boots, offhand);
        }

        KitDefinition withPermission(String perm) {
            return new KitDefinition(id, display, description, icon, glow, cooldownSeconds, slot, rarity,
                    perm, actionAvailable, actionCooldown, actionLocked, contents, helmet, chestplate,
                    leggings, boots, offhand);
        }

        KitDefinition withSlot(int newSlot) {
            return new KitDefinition(id, display, description, icon, glow, cooldownSeconds, newSlot, rarity,
                    permission, actionAvailable, actionCooldown, actionLocked, contents, helmet, chestplate,
                    leggings, boots, offhand);
        }

        KitDefinition withIcon(ItemStack newIcon) {
            return new KitDefinition(id, display, description, newIcon, glow, cooldownSeconds, slot, rarity,
                    permission, actionAvailable, actionCooldown, actionLocked, contents, helmet, chestplate,
                    leggings, boots, offhand);
        }

        KitDefinition withGlow(boolean newGlow) {
            return new KitDefinition(id, display, description, icon, newGlow, cooldownSeconds, slot, rarity,
                    permission, actionAvailable, actionCooldown, actionLocked, contents, helmet, chestplate,
                    leggings, boots, offhand);
        }

        KitDefinition withActions(String available, String cooldown, String locked) {
            return new KitDefinition(id, display, description, icon, glow, cooldownSeconds, slot, rarity,
                    permission, available, cooldown, locked, contents, helmet, chestplate,
                    leggings, boots, offhand);
        }

        KitDefinition withContents(List<ItemStack> newContents, ItemStack h, ItemStack c, ItemStack l,
                                   ItemStack b, ItemStack o) {
            return new KitDefinition(id, display, description, icon, glow, cooldownSeconds, slot, rarity,
                    permission, actionAvailable, actionCooldown, actionLocked, newContents, h, c, l, b, o);
        }
    }
}

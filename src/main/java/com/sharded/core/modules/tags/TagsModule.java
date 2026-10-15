package com.sharded.core.modules.tags;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorConfigUtil;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.EnglishInputUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.GuiFooters;
import com.sharded.core.util.TagDisplayUtil;
import com.sharded.core.util.Text;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.TrackedInventories;
import com.sharded.core.util.WordBlacklist;
import org.bukkit.command.CommandExecutor;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tag equip menu — sharded.tag.* permissions from token shop. */
public final class TagsModule extends Module implements CommandExecutor, TabCompleter {

    private static final Pattern FULL_TAG = Pattern.compile(
            "^&8\\[((?:&x(?:&[0-9A-Fa-f]){6})|(?:&#[0-9A-Fa-f]{3,8})|(?:&[0-9a-fk-or]))(.+?)&8\\]$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT_COLOR_TAG = Pattern.compile(
            "^((?:&x(?:&[0-9A-Fa-f]){6})|(?:&#[0-9A-Fa-f]{3,8})|(?:&[0-9a-fk-or]))(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final int[] TAG_CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int REMOVE_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int LIMITED_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int CUSTOM_SLOT = 53;

    private final List<TagOption> tagPageCache = new ArrayList<>();
    private final List<TagOption> limitedPageCache = new ArrayList<>();
    private final List<String> tabTagIds = new ArrayList<>();
    private final Map<String, TagOption> tags = new LinkedHashMap<>();
    private final Map<String, TagOption> limitedTags = new LinkedHashMap<>();
    private final Map<UUID, Boolean> awaitingCustomTag = new ConcurrentHashMap<>();
    private final Set<String> limitedBlockedText = new HashSet<>();
    private final Set<String> limitedBoldLetters = new HashSet<>();

    private TagDatabase database;

    public TagsModule(ShardedCore plugin) {
        super(plugin, "tags");
    }

    @Override
    protected void onEnable() {
        try {
            database = new TagDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open tags database", e);
        }
        reloadTags();
        registerCommand("tags", this);
        registerCommand("tag", this);
    }

    @Override
    protected void onDisable() {
        awaitingCustomTag.clear();
        if (database != null) database.close();
        database = null;
    }

    private void reloadTags() {
        tags.clear();
        limitedTags.clear();
        limitedBlockedText.clear();
        limitedBoldLetters.clear();
        loadTagSection(config.getConfigurationSection("tags"), tags);
        loadTagSection(config.getConfigurationSection("limited-tags"), limitedTags);
        buildLimitedBlocklist();
        tagPageCache.clear();
        tags.values().stream()
                .filter(t -> !t.customInput())
                .sorted(Comparator.comparingInt(TagOption::slot))
                .forEach(tagPageCache::add);
        limitedPageCache.clear();
        limitedTags.values().stream()
                .sorted(Comparator.comparingInt(TagOption::slot))
                .forEach(limitedPageCache::add);
        tabTagIds.clear();
        tabTagIds.addAll(tags.keySet());
        tabTagIds.addAll(limitedTags.keySet());
    }

    private void buildLimitedBlocklist() {
        for (TagOption tag : limitedTags.values()) {
            indexLimitedVariant(tag.id());
            indexLimitedVariant(tag.displayName());
            indexLimitedVariant(tag.tagDisplay());
            for (String blocked : tag.blockedText()) {
                indexLimitedVariant(blocked);
            }
        }
    }

    private void indexLimitedVariant(String raw) {
        if (raw == null || raw.isBlank()) return;
        String normalized = ColorUtil.normalize(raw);
        limitedBlockedText.add(normalizeCompare(stripFormatCodes(normalized)));
        String inner = extractInnerTagText(normalized);
        if (inner != null) {
            limitedBlockedText.add(normalizeCompare(stripFormatCodes(inner)));
            addLimitedBoldLetters(inner);
        }
        addLimitedBoldLetters(normalized);
    }

    private void addLimitedBoldLetters(String raw) {
        String bold = extractBoldTagText(raw.contains("&8[") ? raw : wrapBracketTag(raw));
        if (bold == null || bold.isBlank()) {
            bold = stripFormatCodes(raw);
        }
        String letters = normalizeCompare(bold);
        if (!letters.isBlank()) limitedBoldLetters.add(letters);
    }

    private String wrapBracketTag(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String norm = ColorUtil.normalize(raw.trim());
        if (norm.contains("&8[")) return norm;
        return "&8[" + norm + "&8]";
    }

    private void loadTagSection(ConfigurationSection section, Map<String, TagOption> target) {
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection tag = section.getConfigurationSection(id);
            if (tag == null) continue;
            target.put(id, new TagOption(
                    id,
                    tag.getInt("slot", 0),
                    ColorConfigUtil.resolvePermission(id, tag, "sharded.tag."),
                    tag.getString("material", "PAPER"),
                    tag.getString("display-name", id),
                    tag.getString("tag-display", tag.getString("display-name", id)),
                    tag.getStringList("lore"),
                    tag.getString("apply-command", ""),
                    tag.getBoolean("custom-input", false),
                    tag.getStringList("blocked-text")
            ));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && handleSubcommand(sender, args)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.tags.use")) {
            send(player, "no-permission");
            return true;
        }
        openMainMenu(player);
        return true;
    }

    private boolean handleSubcommand(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleCreate(sender, args, false);
            case "limited" -> handleCreate(sender, args, true);
            case "delete" -> handleDelete(sender, args);
            case "set" -> handleSet(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "custom" -> handleCustom(sender);
            default -> handleEquipByName(sender, sub);
        };
    }

    private boolean handleCreate(CommandSender sender, String[] args, boolean limited) {
        if (!sender.hasPermission("sharded.tags.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            send(sender, limited ? "tag-limited-usage" : "tag-create-usage");
            return true;
        }
        createTag(sender, args[1].toLowerCase(Locale.ROOT),
                String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)), limited);
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sharded.tags.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) return false;
        deleteTag(sender, args[1].toLowerCase(Locale.ROOT));
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length < 2) return false;
        equipTagById(player, args[1].toLowerCase(Locale.ROOT));
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission("sharded.tags.admin")) {
                send(sender, "no-permission");
                return true;
            }
            deleteTag(sender, args[1].toLowerCase(Locale.ROOT));
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        removeTag(player);
        return true;
    }

    private boolean handleCustom(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.tag.custom")) {
            send(player, "not-owned", "%tag%", "Custom Tag");
            return true;
        }
        awaitingCustomTag.put(player.getUniqueId(), true);
        send(player, "custom-prompt");
        return true;
    }

    private boolean handleEquipByName(CommandSender sender, String id) {
        if (!(sender instanceof Player player)) return false;
        if (!tags.containsKey(id) && !limitedTags.containsKey(id)) return false;
        if (!player.hasPermission("sharded.tags.use")) {
            send(player, "no-permission");
            return true;
        }
        equipTagById(player, id);
        return true;
    }

    private void equipTagById(Player player, String id) {
        TagOption tag = tags.get(id);
        if (tag == null) tag = limitedTags.get(id);
        if (tag == null) {
            send(player, "tag-not-found", "%tag%", id);
            return;
        }
        applyTag(player, tag);
    }

    private void createTag(CommandSender sender, String id, String display, boolean limited) {
        if (!isEnglishTagContent(display)) {
            send(sender, "custom-non-english");
            return;
        }
        String sectionKey = limited ? "limited-tags" : "tags";
        int slot = nextFreeSlot(limited ? limitedTags : tags);
        if (slot < 0) {
            send(sender, "menu-full");
            return;
        }
        String path = sectionKey + "." + id;
        config.set(path + ".slot", slot);
        config.set(path + ".permission", "sharded.tag." + id);
        config.set(path + ".material", "NAME_TAG");
        config.set(path + ".display-name", display);
        config.set(path + ".tag-display", display);
        config.set(path + ".lore", defaultTagLore(id));
        if (limited) {
            List<String> blocked = new ArrayList<>();
            blocked.add(stripColors(display));
            String inner = extractInnerTagText(stripColors(display));
            if (inner != null && !inner.isBlank()) blocked.add(inner);
            config.set(path + ".blocked-text", blocked);
        }
        saveTagsConfig();
        reloadTags();
        send(sender, "tag-created", "%tag%", id);
        send(sender, "tag-permission-hint", "%permission%", "sharded.tag." + id);
    }

    private List<String> defaultTagLore(String id) {
        return List.of(
                "&8Description",
                "",
                "%accent%Information:",
                "%accent%| &fEquip this tag.",
                "%accent%| &fAnd stand out on tab!",
                "",
                "%accent%⚓ &fOwned: %tag_owned_" + id + "%",
                "",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nClick&r &x&F&F&B&A&0&0To Apply");
    }

    private void deleteTag(CommandSender sender, String id) {
        if (config.getConfigurationSection("tags." + id) != null) {
            config.set("tags." + id, null);
        } else if (config.getConfigurationSection("limited-tags." + id) != null) {
            config.set("limited-tags." + id, null);
        } else {
            send(sender, "tag-not-found", "%tag%", id);
            return;
        }
        saveTagsConfig();
        reloadTags();
        send(sender, "tag-deleted", "%tag%", id);
    }

    private int nextFreeSlot(Map<String, TagOption> existing) {
        Set<Integer> used = existing.values().stream().map(TagOption::slot).collect(java.util.stream.Collectors.toSet());
        for (int slot : TAG_CONTENT_SLOTS) {
            if (!used.contains(slot)) return slot;
        }
        return -1;
    }

    private void saveTagsConfig() {
        try {
            if (!config.contains("config-version")) {
                config.set("config-version", 15);
            }
            config.save(new File(moduleFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[tags] Could not save config: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("set", "remove", "custom"));
            if (sender.hasPermission("sharded.tags.admin")) {
                subs.addAll(List.of("create", "limited", "delete"));
            }
            subs.addAll(tabTagIds);
            return TabCompleteHelper.filter(args[0], subs);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("set") || sub.equals("delete")
                    || (sub.equals("remove") && sender.hasPermission("sharded.tags.admin"))) {
                return TabCompleteHelper.filter(args[1], tabTagIds);
            }
            if (sub.equals("create") || sub.equals("limited")) {
                return TabCompleteHelper.filter(args[1], "<id>");
            }
        }
        return List.of();
    }

    public void openMainMenu(Player player) {
        openMenu(player, tagPageCache, config.getString("menu-title", "Tags"), false, 0);
    }

    public void openLimitedMenu(Player player) {
        openMenu(player, limitedPageCache, config.getString("limited-menu-title", "Limited time tags"), true, 0);
    }

    private void openMenu(Player player, List<TagOption> options, String title, boolean limited, int page) {
        int size = config.getInt(limited ? "limited-menu-size" : "menu-size", 54);
        TagMenuHolder holder = new TagMenuHolder(limited, page);
        Map<String, String> placeholders = equipPlaceholders(player);
        holder.setPlaceholders(placeholders);
        Inventory inventory = plugin.getServer().createInventory(holder, size, Text.c(title));
        TrackedInventories.track(inventory, holder);

        ItemStack border = cachedBorderItem();
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, border.clone());
        }

        int perPage = TAG_CONTENT_SLOTS.length;
        int maxPage = Math.max(0, (options.size() + perPage - 1) / perPage - 1);
        page = Math.max(0, Math.min(page, maxPage));
        holder.page = page;
        populateTagSlots(inventory, options, placeholders, page, maxPage);

        if (!limited && config.getBoolean("remove-button.enabled", true)) {
            Material removeMat = Material.matchMaterial(config.getString("remove-button.material", "FLOWER_CHARGE_BANNER_PATTERN"));
            if (removeMat == null) removeMat = Material.FLOWER_BANNER_PATTERN;
            List<String> removeLore = applyPlaceholders(config.getStringList("remove-button.lore"), placeholders);
            inventory.setItem(REMOVE_SLOT, new ItemBuilder(removeMat)
                    .name(config.getString("remove-button.display-name", "&x&F&F&0&0&0&0&lRemove Tag"))
                    .lore(removeLore)
                    .hideAll()
                    .build());
        }

        if (!limited && config.getBoolean("limited-button.enabled", true)) {
            Material mat = Material.matchMaterial(config.getString("limited-button.material", "CLOCK"));
            if (mat == null) mat = Material.CLOCK;
            List<String> limitedLore = applyPlaceholders(config.getStringList("limited-button.lore"), placeholders);
            inventory.setItem(LIMITED_SLOT, new ItemBuilder(mat)
                    .name(config.getString("limited-button.display-name", "&x&F&F&0&0&0&0&lLimited Time"))
                    .lore(limitedLore)
                    .hideAll()
                    .build());
        }

        if (!limited && config.getBoolean("custom-button.enabled", true)) {
            TagOption custom = tags.get("custom");
            if (custom != null) {
                List<String> customLore = applyPlaceholders(buildLore(custom, placeholders), placeholders);
                inventory.setItem(CUSTOM_SLOT, new ItemBuilder(Material.NAME_TAG)
                        .name(config.getString("custom-button.display-name", custom.displayName()))
                        .lore(customLore)
                        .hideAll()
                        .build());
            }
        }

        if (limited) {
            inventory.setItem(CUSTOM_SLOT, plugin.guiNavigation().build("back", config.getConfigurationSection("back-button")));
        }

        player.openInventory(inventory);
    }

    private void populateTagSlots(Inventory inventory, List<TagOption> options,
                                  Map<String, String> placeholders, int page, int maxPage) {
        ItemStack border = cachedBorderItem();
        int start = page * TAG_CONTENT_SLOTS.length;
        for (int i = 0; i < TAG_CONTENT_SLOTS.length; i++) {
            int slot = TAG_CONTENT_SLOTS[i];
            if (start + i < options.size()) {
                TagOption tag = options.get(start + i);
                List<String> lore = applyPlaceholders(buildLore(tag, placeholders), placeholders);
                Material material = Material.matchMaterial(tag.material());
                if (material == null) material = Material.PAPER;
                String name = applyPlaceholders(tag.displayName(), placeholders);
                inventory.setItem(slot, new ItemBuilder(material).name(name).lore(lore).hideAll().build());
            } else {
                inventory.setItem(slot, border.clone());
            }
        }

        inventory.setItem(PREV_SLOT, page > 0
                ? navItem(Material.RED_DYE,
                config.getString("previous-page.name", "&cPrevious Page"),
                config.getStringList("previous-page.lore"))
                : border.clone());
        inventory.setItem(NEXT_SLOT, page < maxPage
                ? navItem(Material.LIME_DYE,
                config.getString("next-page.name", "&aNext Page"),
                config.getStringList("next-page.lore"))
                : border.clone());
    }

    private void flipPage(Player player, TagMenuHolder holder, int delta) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        List<TagOption> options = holder.limited() ? limitedPageCache : tagPageCache;
        int perPage = TAG_CONTENT_SLOTS.length;
        int maxPage = Math.max(0, (options.size() + perPage - 1) / perPage - 1);
        int page = Math.max(0, Math.min(holder.page() + delta, maxPage));
        holder.page = page;
        Map<String, String> placeholders = holder.placeholders();
        if (placeholders == null) {
            placeholders = equipPlaceholders(player);
            holder.setPlaceholders(placeholders);
        }
        populateTagSlots(inventory, options, placeholders, page, maxPage);
    }

    private ItemStack cachedBorderItem() {
        Material borderMat = Material.matchMaterial(config.getString("border-material", "BLACK_STAINED_GLASS_PANE"));
        if (borderMat == null) borderMat = Material.BLACK_STAINED_GLASS_PANE;
        return new ItemBuilder(borderMat).name(" ").hideAll().build();
    }

    private ItemStack navItem(Material material, String name, List<String> lore) {
        ItemBuilder builder = new ItemBuilder(material).name(name).hideAll();
        if (lore != null && !lore.isEmpty()) builder.lore(lore);
        return builder.build();
    }

    private List<String> buildLore(TagOption tag, Map<String, String> placeholders) {
        String accent = loreAccent(tag);
        if (!tag.lore().isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String line : tag.lore()) {
                out.add(applyPlaceholders(line.replace("%accent%", accent), placeholders));
            }
            return out;
        }
        String owned = placeholders.getOrDefault("tag_owned_" + tag.id(),
                config.getString("placeholders.owned-no", "&#FF2727&nNo"));
        return List.of(
                "&8Description",
                "",
                TagDisplayUtil.loreLine(accent, "Information:"),
                TagDisplayUtil.loreLine(accent, "| &fEquip this tag."),
                TagDisplayUtil.loreLine(accent, "| &fAnd stand out on tab!"),
                "",
                TagDisplayUtil.loreLine(accent, "⚓ &fOwned: ") + owned,
                "",
                GuiFooters.apply());
    }

    private String loreAccent(TagOption tag) {
        return TagDisplayUtil.accentColor(tag.displayName());
    }

    private String applyPlaceholders(String line, Map<String, String> placeholders) {
        String out = line;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return out;
    }

    private List<String> applyPlaceholders(List<String> lines, Map<String, String> placeholders) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(applyPlaceholders(line, placeholders));
        return out;
    }

    /** Placeholders for equip GUI lore in modules/tags/config.yml — not used in token shop. */
    public Map<String, String> equipPlaceholders(Player player) {
        Map<String, String> map = new LinkedHashMap<>();
        String ownedYes = config.getString("placeholders.owned-yes", "&#9FFF00Yes");
        String ownedNo = config.getString("placeholders.owned-no", "&#FF2727No");
        String none = config.getString("placeholders.none", "&7None");
        String lastCustom = database == null ? null : database.getLastCustomTag(player.getUniqueId());
        map.put("last_custom_tag", lastCustom == null || lastCustom.isBlank() ? none : lastCustom);
        long created = database == null ? 0L : database.getLastCustomCreatedAt(player.getUniqueId());
        long cooldownMs = config.getLong("custom-cooldown-hours", 24L) * 3_600_000L;
        if (created <= 0 || System.currentTimeMillis() - created >= cooldownMs) {
            map.put("custom_cooldown", config.getString("placeholders.cooldown-ready", "&#9FFF00Ready"));
        } else {
            long remaining = (cooldownMs - (System.currentTimeMillis() - created)) / 1000L;
            map.put("custom_cooldown", com.sharded.core.util.Text.time(remaining));
        }

        for (TagOption tag : tags.values()) {
            map.put("tag_owned_" + tag.id(), player.hasPermission(tag.permission()) ? ownedYes : ownedNo);
        }
        for (TagOption tag : limitedTags.values()) {
            map.put("tag_owned_" + tag.id(), player.hasPermission(tag.permission()) ? ownedYes : ownedNo);
        }
        return map;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        TagMenuHolder holder = TrackedInventories.lookup(
                event.getView().getTopInventory(), TagMenuHolder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (!holder.limited() && slot == REMOVE_SLOT && config.getBoolean("remove-button.enabled", true)) {
            player.closeInventory();
            removeTag(player);
            return;
        }

        if (slot == CUSTOM_SLOT) {
            player.closeInventory();
            if (holder.limited()) {
                openMainMenu(player);
                return;
            }
            TagOption custom = tags.get("custom");
            if (custom != null) applyTag(player, custom);
            return;
        }

        if (slot == PREV_SLOT && holder.page() > 0) {
            flipPage(player, holder, -1);
            return;
        }

        if (slot == NEXT_SLOT) {
            List<TagOption> options = holder.limited() ? limitedPageCache : tagPageCache;
            int maxPage = Math.max(0, (options.size() + TAG_CONTENT_SLOTS.length - 1) / TAG_CONTENT_SLOTS.length - 1);
            if (holder.page() < maxPage) {
                flipPage(player, holder, 1);
            }
            return;
        }

        if (!holder.limited() && slot == LIMITED_SLOT && config.getBoolean("limited-button.enabled", true)) {
            player.closeInventory();
            openLimitedMenu(player);
            return;
        }

        List<TagOption> options = holder.limited() ? limitedPageCache : tagPageCache;
        int perPage = TAG_CONTENT_SLOTS.length;
        int index = holder.page() * perPage;
        for (int i = 0; i < perPage && index + i < options.size(); i++) {
            if (TAG_CONTENT_SLOTS[i] != slot) continue;
            player.closeInventory();
            applyTag(player, options.get(index + i));
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (TrackedInventories.lookup(event.getView().getTopInventory(), TagMenuHolder.class) != null) {
            event.setCancelled(true);
        }
    }

    private void applyTag(Player player, TagOption tag) {
        if (!player.hasPermission(tag.permission())) {
            send(player, "not-owned", "%tag%", tag.displayName());
            return;
        }
        if (tag.customInput()) {
            String last = database == null ? null : database.getLastCustomTag(player.getUniqueId());
            if (last != null && !last.isBlank()) {
                applyCustomTag(player, last, false);
                return;
            }
            awaitingCustomTag.put(player.getUniqueId(), true);
            send(player, "custom-prompt");
            return;
        }
        clearEquippedTag(player);
        if (plugin.cosmetics() != null) {
            plugin.cosmetics().setTag(player, tag.id(), tag.effectiveDisplay());
        }
        if (tag.applyCommand() != null && !tag.applyCommand().isBlank()) {
            runApplyCommand(player, tag, null);
        }
        send(player, "equipped", "%tag%", tag.displayName());
    }

    private void runApplyCommand(Player player, TagOption tag, String customValue) {
        String cmd = tag.applyCommand()
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%tag_id%", tag.id())
                .replace("%tag%", tag.id())
                .replace("%custom%", customValue == null ? "" : customValue);
        dispatchCommand(player, cmd);
    }

    private void dispatchCommand(Player player, String cmd) {
        if (cmd.startsWith("[console]")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring("[console]".length()).trim());
        } else if (cmd.startsWith("[player]")) {
            player.performCommand(cmd.substring("[player]".length()).trim());
        } else if (cmd.startsWith("/")) {
            player.performCommand(cmd.substring(1));
        } else {
            player.performCommand(cmd);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCustomChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!Boolean.TRUE.equals(awaitingCustomTag.remove(player.getUniqueId()))) return;
        event.setCancelled(true);

        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleCustomInput(player, input));
    }

    private void handleCustomInput(Player player, String rawInput) {
        if (!player.hasPermission("sharded.tag.custom")) {
            send(player, "not-owned", "%tag%", "Custom Tag");
            return;
        }
        applyCustomTag(player, rawInput.trim(), true);
    }

    private void applyCustomTag(Player player, String input, boolean fromChat) {
        String formatted = formatCustomTag(ColorUtil.normalize(input == null ? "" : input.trim()));
        if (formatted == null) {
            send(player, "custom-invalid-format");
            return;
        }
        input = formatted;

        String boldText = extractBoldTagText(input);
        if (boldText == null || boldText.isBlank()) {
            send(player, "custom-bold-required");
            return;
        }
        if (!EnglishInputUtil.isEnglishLettersOnly(boldText)) {
            send(player, "custom-non-english");
            return;
        }

        int maxLetters = config.getInt("custom-max-letters", 5);
        if (EnglishInputUtil.countEnglishLetters(boldText) > maxLetters) {
            send(player, "custom-too-long");
            return;
        }

        if (containsBlockedEmoji(input)) {
            send(player, "custom-emoji-blocked");
            return;
        }
        if (WordBlacklist.contains(config, "custom-tag-blacklist", input)) {
            send(player, "custom-blacklisted");
            return;
        }
        if (matchesLimitedTag(input)) {
            send(player, "custom-limited-blocked");
            return;
        }

        if (fromChat) {
            long cooldownMs = config.getLong("custom-cooldown-hours", 24L) * 3_600_000L;
            long lastCreated = database == null ? 0L : database.getLastCustomCreatedAt(player.getUniqueId());
            if (lastCreated > 0 && System.currentTimeMillis() - lastCreated < cooldownMs) {
                long remaining = (cooldownMs - (System.currentTimeMillis() - lastCreated)) / 1000L;
                send(player, "custom-cooldown", "%time%", com.sharded.core.util.Text.time(remaining));
                return;
            }
        }

        clearEquippedTag(player);

        String finalInput = input;
        boolean chatCreation = fromChat;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.cosmetics() != null) {
                plugin.cosmetics().setTag(player, "custom", finalInput);
            }
            if (database != null) database.saveLastCustomTag(player.getUniqueId(), finalInput, chatCreation);
            send(player, chatCreation ? "custom-set" : "custom-reapplied", "%tag%", finalInput);
        });
    }

    private void removeTag(Player player) {
        clearEquippedTag(player);
        if (plugin.cosmetics() != null) plugin.cosmetics().clearTag(player);
        send(player, "removed");
    }

    private void clearEquippedTag(Player player) {
        for (String line : config.getStringList("clear-equipped-commands")) {
            if (line == null || line.isBlank()) continue;
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("luckperms") || lower.contains(" lp ") || lower.contains("eternaltags")) continue;
            String cmd = line.replace("%player%", player.getName()).replace("%player_name%", player.getName());
            dispatchCommand(player, cmd);
        }
    }

    private boolean matchesLimitedTag(String input) {
        if (!config.getBoolean("block-limited-tag-copy", true)) return false;

        String boldText = extractBoldTagText(input);
        if (boldText != null) {
            String boldKey = normalizeCompare(boldText);
            if (!boldKey.isBlank()) {
                if (limitedBoldLetters.contains(boldKey)) return true;
                for (String blocked : limitedBoldLetters) {
                    if (!blocked.isBlank() && (boldKey.contains(blocked) || blocked.contains(boldKey))) {
                        return true;
                    }
                }
            }
        }

        String normalizedInput = normalizeCompare(stripColors(input));
        if (limitedBlockedText.contains(normalizedInput)) return true;

        String inner = extractInnerTagText(input);
        if (inner != null && limitedBlockedText.contains(normalizeCompare(stripFormatCodes(inner)))) return true;

        for (String blocked : limitedBlockedText) {
            if (!blocked.isBlank() && (normalizedInput.contains(blocked) || blocked.contains(normalizedInput))) {
                return true;
            }
        }
        return false;
    }

    /** Accepts full &8[color&lTEXT&8] or short color+&l+text; returns normalized bracket form without spaces. */
    private String formatCustomTag(String input) {
        if (input == null || input.isBlank()) return null;
        String raw = input.trim().replace(" ", "");
        String normalized = ColorUtil.normalize(raw);

        Matcher full = FULL_TAG.matcher(normalized);
        if (full.matches()) {
            String color = full.group(1);
            String inner = full.group(2);
            if (!containsBoldMarker(inner)) return null;
            return "&8[" + color + normalizeBoldInner(inner) + "&8]";
        }

        Matcher shortTag = SHORT_COLOR_TAG.matcher(normalized);
        if (shortTag.matches()) {
            String color = shortTag.group(1);
            String text = shortTag.group(2);
            if (text.isBlank() || !containsBoldMarker(text)) return null;
            String inner = normalizeBoldInner(text);
            if (inner.isBlank() || !containsBoldMarker(inner)) return null;
            return "&8[" + color + inner + "&8]";
        }
        return null;
    }

    private boolean containsBoldMarker(String inner) {
        return inner != null && inner.toLowerCase(Locale.ROOT).contains("&l");
    }

    /** Keeps color codes and a single &l prefix before the visible letters. */
    private String normalizeBoldInner(String inner) {
        String stripped = stripFormatCodesExceptBold(inner);
        int boldIdx = stripped.toLowerCase(Locale.ROOT).indexOf("&l");
        if (boldIdx < 0) return inner;
        String beforeBold = stripped.substring(0, boldIdx);
        String afterBold = stripped.substring(boldIdx + 2).replaceAll("(?i)&l", "");
        afterBold = stripFormatCodes(afterBold);
        if (afterBold.isBlank()) return beforeBold + "&l";
        return beforeBold + "&l" + afterBold;
    }

    private String stripFormatCodesExceptBold(String input) {
        if (input == null) return "";
        return ColorUtil.normalize(input)
                .replaceAll("(?i)&#[0-9a-f]{6}", "§HEX")
                .replaceAll("(?i)&x(&[0-9a-f]){6}", "§HEX")
                .replaceAll("(?i)&[0-9a-f]", "§C")
                .replaceAll("(?i)&[k-o]", "")
                .replaceAll("(?i)&r", "");
    }

    /** Letters shown after the bold marker inside a formatted custom tag. */
    private String extractBoldTagText(String formattedTag) {
        String inner = extractInnerTagText(formattedTag);
        if (inner == null) return null;
        String normalized = ColorUtil.normalize(inner);
        int boldIdx = normalized.toLowerCase(Locale.ROOT).lastIndexOf("&l");
        if (boldIdx < 0) return null;
        return stripFormatCodes(normalized.substring(boldIdx + 2));
    }

    private boolean isEnglishTagContent(String display) {
        if (display == null) return true;
        String inner = extractInnerTagText(display);
        String text = inner != null ? stripFormatCodes(inner) : stripFormatCodes(display);
        return EnglishInputUtil.isEnglishLettersOnly(text);
    }

    private String stripFormatCodes(String input) {
        if (input == null) return "";
        return ColorUtil.normalize(input)
                .replaceAll("(?i)&#[0-9a-f]{6}", "")
                .replaceAll("(?i)&x(&[0-9a-f]){6}", "")
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replace("§", "");
    }

    private boolean containsBlockedEmoji(String input) {
        if (input == null || input.isBlank()) return false;
        for (String blocked : config.getStringList("custom-emoji-blacklist")) {
            if (blocked != null && !blocked.isBlank() && input.contains(blocked)) return true;
        }
        return false;
    }

    private String extractInnerTagText(String input) {
        if (input == null) return null;
        Matcher m = Pattern.compile(
                "&8\\[((?:&x(?:&[0-9A-Fa-f]){6})|(?:&#[0-9A-Fa-f]{3,8})|(?:&[0-9a-fk-or]))(.+?)&8\\]",
                Pattern.CASE_INSENSITIVE).matcher(input);
        if (m.find()) return m.group(2).trim();
        return null;
    }

    private String stripColors(String input) {
        return stripFormatCodes(input);
    }

    private String normalizeCompare(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingCustomTag.remove(event.getPlayer().getUniqueId());
    }

    private static final class TagMenuHolder implements InventoryHolder {
        private final boolean limited;
        private int page;
        private Map<String, String> placeholders;

        TagMenuHolder(boolean limited, int page) {
            this.limited = limited;
            this.page = page;
        }

        boolean limited() {
            return limited;
        }

        int page() {
            return page;
        }

        Map<String, String> placeholders() {
            return placeholders;
        }

        void setPlaceholders(Map<String, String> placeholders) {
            this.placeholders = placeholders;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record TagOption(String id, int slot, String permission, String material,
                             String displayName, String tagDisplay, List<String> lore,
                             String applyCommand, boolean customInput, List<String> blockedText) {
        String effectiveDisplay() {
            if (tagDisplay != null && !tagDisplay.isBlank()) {
                return TagDisplayUtil.tabTag(tagDisplay);
            }
            return TagDisplayUtil.tabTag(displayName == null || displayName.isBlank() ? id : displayName);
        }
    }
}

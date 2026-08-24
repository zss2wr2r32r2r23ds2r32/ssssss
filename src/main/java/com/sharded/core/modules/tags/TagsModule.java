package com.sharded.core.modules.tags;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TabCompleteHelper;
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

    private final Map<String, TagOption> tags = new LinkedHashMap<>();
    private final Map<String, TagOption> limitedTags = new LinkedHashMap<>();
    private final Map<UUID, Boolean> awaitingCustomTag = new ConcurrentHashMap<>();
    private final Set<String> limitedBlockedText = new HashSet<>();

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
        loadTagSection(config.getConfigurationSection("tags"), tags);
        loadTagSection(config.getConfigurationSection("limited-tags"), limitedTags);
        buildLimitedBlocklist();
    }

    private void buildLimitedBlocklist() {
        for (TagOption tag : limitedTags.values()) {
            limitedBlockedText.add(normalizeCompare(tag.id()));
            limitedBlockedText.add(normalizeCompare(stripColors(tag.displayName())));
            for (String blocked : tag.blockedText()) {
                if (!blocked.isBlank()) limitedBlockedText.add(normalizeCompare(blocked));
            }
            String inner = extractInnerTagText(stripColors(tag.displayName()));
            if (inner != null && !inner.isBlank()) limitedBlockedText.add(normalizeCompare(inner));
        }
    }

    private void loadTagSection(ConfigurationSection section, Map<String, TagOption> target) {
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection tag = section.getConfigurationSection(id);
            if (tag == null) continue;
            target.put(id, new TagOption(
                    id,
                    tag.getInt("slot", 0),
                    tag.getString("permission", "sharded.tag." + id),
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
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("tags")) {
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

        if (args.length == 0) {
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

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("create") && args.length >= 3) {
            if (!sender.hasPermission("sharded.tags.admin")) {
                send(sender, "no-permission");
                return true;
            }
            String id = args[1].toLowerCase(Locale.ROOT);
            String display = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            createTag(sender, id, display, false);
            return true;
        }
        if (sub.equals("limited") && args.length >= 3) {
            if (!sender.hasPermission("sharded.tags.admin")) {
                send(sender, "no-permission");
                return true;
            }
            String id = args[1].toLowerCase(Locale.ROOT);
            String display = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            createTag(sender, id, display, true);
            return true;
        }
        if (sub.equals("delete") && args.length >= 2) {
            if (!sender.hasPermission("sharded.tags.admin")) {
                send(sender, "no-permission");
                return true;
            }
            deleteTag(sender, args[1].toLowerCase(Locale.ROOT));
            return true;
        }
        if (sub.equals("set") && args.length >= 2) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            equipTagById(player, args[1].toLowerCase(Locale.ROOT));
            return true;
        }
        if (sub.equals("remove")) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            removeTag(player);
            return true;
        }
        if (sub.equals("custom")) {
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

        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.tags.use")) {
            send(player, "no-permission");
            return true;
        }
        equipTagById(player, sub);
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
        String sectionKey = limited ? "limited-tags" : "tags";
        int size = config.getInt(limited ? "limited-menu-size" : "menu-size", 54);
        int slot = nextFreeSlot(limited ? limitedTags : tags, size);
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
        config.set(path + ".lore", List.of(
                "&8Descriptions", "", "&#FF3399Information:",
                "&#FF3399| &fEquip this tag.", "", "%click%to apply"));
        saveTagsConfig();
        reloadTags();
        send(sender, "tag-created", "%tag%", id);
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

    private int nextFreeSlot(Map<String, TagOption> existing, int size) {
        Set<Integer> used = existing.values().stream().map(TagOption::slot).collect(java.util.stream.Collectors.toSet());
        for (int i = 0; i < size; i++) {
            if (!used.contains(i)) return i;
        }
        return -1;
    }

    private void saveTagsConfig() {
        try {
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
            subs.addAll(tags.keySet());
            subs.addAll(limitedTags.keySet());
            return TabCompleteHelper.filter(args[0], subs);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("delete"))) {
            List<String> ids = new ArrayList<>(tags.keySet());
            ids.addAll(limitedTags.keySet());
            return TabCompleteHelper.filter(args[1], ids);
        }
        return List.of();
    }

    public void openMainMenu(Player player) {
        openMenu(player, tags, config.getString("menu-title", "Tags"), false);
    }

    public void openLimitedMenu(Player player) {
        openMenu(player, limitedTags, config.getString("limited-menu-title", "Limited Time Tags"), true);
    }

    private void openMenu(Player player, Map<String, TagOption> options, String title, boolean limited) {
        int size = config.getInt(limited ? "limited-menu-size" : "menu-size", 54);
        TagMenuHolder holder = new TagMenuHolder(limited);
        Inventory inventory = plugin.getServer().createInventory(holder, size, Text.c(title));

        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").hideAll().build();
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        Map<String, String> placeholders = equipPlaceholders(player);
        for (TagOption tag : options.values()) {
            List<String> lore = applyPlaceholders(buildLore(tag, placeholders), placeholders);
            Material material = Material.matchMaterial(tag.material());
            if (material == null) material = Material.PAPER;
            String name = applyPlaceholders(tag.displayName(), placeholders);
            inventory.setItem(tag.slot(), new ItemBuilder(material).name(name).lore(lore).hideAll().build());
        }

        if (!limited && config.getBoolean("limited-button.enabled", true)) {
            List<String> limitedLore = applyPlaceholders(config.getStringList("limited-button.lore"), placeholders);
            Material mat = Material.matchMaterial(config.getString("limited-button.material", "CLOCK"));
            if (mat == null) mat = Material.CLOCK;
            inventory.setItem(config.getInt("limited-button.slot", 22),
                    new ItemBuilder(mat)
                            .name(config.getString("limited-button.display-name", "&x&F&F&0&0&0&0&lLIMITED TIME"))
                            .lore(limitedLore)
                            .hideAll()
                            .build());
        }

        if (!limited && config.getBoolean("remove-button.enabled", true)) {
            List<String> removeLore = applyPlaceholders(config.getStringList("remove-button.lore"), placeholders);
            Material mat = Material.matchMaterial(config.getString("remove-button.material", "REDSTONE"));
            if (mat == null) mat = Material.REDSTONE;
            inventory.setItem(config.getInt("remove-button.slot", 15),
                    new ItemBuilder(mat)
                            .name(config.getString("remove-button.display-name", "&x&F&F&0&0&0&0&lREMOVE TAG"))
                            .lore(removeLore)
                            .hideAll()
                            .build());
        }

        if (limited || config.getBoolean("close-button.enabled", true)) {
            int closeSlot = limited ? config.getInt("limited-close-slot", 26) : config.getInt("close-button.slot", 26);
            var navOverride = config.getConfigurationSection("close-button");
            inventory.setItem(closeSlot, plugin.guiNavigation().build("close", navOverride));
        }

        player.openInventory(inventory);
    }

    private List<String> buildLore(TagOption tag, Map<String, String> placeholders) {
        return new ArrayList<>(tag.lore());
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
        if (!(event.getView().getTopInventory().getHolder() instanceof TagMenuHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (!holder.limited() && slot == config.getInt("remove-button.slot", 15)
                && config.getBoolean("remove-button.enabled", true)) {
            player.closeInventory();
            removeTag(player);
            return;
        }

        if (slot == config.getInt("close-button.slot", 26)
                || (holder.limited() && slot == config.getInt("limited-close-slot", 26))) {
            player.closeInventory();
            return;
        }

        if (!holder.limited() && slot == config.getInt("limited-button.slot", 22)
                && config.getBoolean("limited-button.enabled", true)) {
            player.closeInventory();
            openLimitedMenu(player);
            return;
        }

        Map<String, TagOption> options = holder.limited() ? limitedTags : tags;
        for (TagOption tag : options.values()) {
            if (tag.slot() != slot) continue;
            player.closeInventory();
            applyTag(player, tag);
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TagMenuHolder) {
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
        TagOption chosen = tag;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.cosmetics() != null) {
                plugin.cosmetics().setTag(player, chosen.id(), chosen.effectiveDisplay());
            }
            if (chosen.applyCommand() != null && !chosen.applyCommand().isBlank()) {
                runApplyCommand(player, chosen, null);
            }
            send(player, "equipped", "%tag%", chosen.displayName());
        }, 2L);
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

        int maxLetters = config.getInt("custom-max-letters", 5);
        if (countLetters(input) > maxLetters) {
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
        long delay = config.getLong("custom-apply-delay-ticks", 5L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.cosmetics() != null) {
                plugin.cosmetics().setTag(player, "custom", finalInput);
            }
            dispatchCommand(player, buildLpCommand(player, config.getString("custom-apply-command",
                    "[console] lp user %player_name% meta setsuffix %priority% \"%custom%\""), finalInput));

            if (database != null) database.saveLastCustomTag(player.getUniqueId(), finalInput, chatCreation);
            send(player, chatCreation ? "custom-set" : "custom-reapplied", "%tag%", finalInput);
        }, delay);
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
            if (lower.contains("clearsuffix") || lower.contains("removesuffix") || lower.contains("setsuffix")) {
                continue;
            }
            String cmd = line.replace("%player%", player.getName()).replace("%player_name%", player.getName());
            dispatchCommand(player, cmd);
        }
        removeCustomSuffix(player);
    }

    /** LuckPerms removesuffix uses priority (e.g. 1), not the suffix text. */
    private void removeCustomSuffix(Player player) {
        if (!plugin.luckPerms().isAvailable()) return;
        dispatchCommand(player, buildLpCommand(player, config.getString("custom-remove-command",
                "[console] lp user %player_name% meta removesuffix %priority%"), null));
    }

    private String buildLpCommand(Player player, String template, String custom) {
        if (template == null || template.isBlank()) return "";
        return template
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%custom%", custom == null ? "" : custom)
                .replace("%priority%", String.valueOf(suffixPriority()));
    }

    private int suffixPriority() {
        return config.getInt("custom-suffix-priority", 1);
    }

    private boolean matchesLimitedTag(String input) {
        if (!config.getBoolean("block-limited-tag-copy", true)) return false;
        String normalizedInput = normalizeCompare(stripColors(input));
        if (limitedBlockedText.contains(normalizedInput)) return true;

        String inner = extractInnerTagText(stripColors(input));
        if (inner != null && limitedBlockedText.contains(normalizeCompare(inner))) return true;

        for (String blocked : limitedBlockedText) {
            if (!blocked.isBlank() && (normalizedInput.contains(blocked) || blocked.contains(normalizedInput))) {
                return true;
            }
        }
        return false;
    }

    /** Accepts full &8[colorTEXT&8] or short color+text; returns normalized bracket form without spaces. */
    private String formatCustomTag(String input) {
        if (input == null || input.isBlank()) return null;
        String raw = input.trim().replace(" ", "");
        String normalized = ColorUtil.normalize(raw);

        Matcher full = FULL_TAG.matcher(normalized);
        if (full.matches()) {
            return "&8[" + full.group(1) + full.group(2) + "&8]";
        }

        Matcher shortTag = SHORT_COLOR_TAG.matcher(normalized);
        if (shortTag.matches()) {
            String color = shortTag.group(1);
            String text = shortTag.group(2);
            if (text.isBlank()) return null;
            return "&8[" + color + text + "&8]";
        }
        return null;
    }

    private int countLetters(String formattedTag) {
        String inner = extractInnerTagText(formattedTag);
        String text = inner != null ? stripColors(inner) : stripColors(formattedTag);
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) count++;
        }
        return count;
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
        if (input == null) return "";
        return ColorUtil.normalize(input)
                .replaceAll("(?i)&#[0-9a-f]{6}", "")
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replace("§", "");
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

        TagMenuHolder(boolean limited) {
            this.limited = limited;
        }

        boolean limited() {
            return limited;
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
            return tagDisplay == null || tagDisplay.isBlank() ? displayName : tagDisplay;
        }
    }
}

package com.sharded.core.modules.tags;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.WordBlacklist;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Tag equip menu — requires eternaltags.tag.* permissions from token shop. */
public final class TagsModule extends Module implements CommandExecutor {

    private static final Pattern EXTENDED_HEX_TAG = Pattern.compile(
            "^&8\\[&x(?:&[0-9A-Fa-f]){6}&l[A-Za-z0-9 ]+&8\\]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HASH_HEX_TAG = Pattern.compile(
            "^&8\\[(&#[0-9A-Fa-f]{3,8})&l[A-Za-z0-9 ]+&8\\]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NORMALIZED_HEX_TAG = Pattern.compile(
            "^&8\\[(&#[0-9A-Fa-f]{6})&l[A-Za-z0-9 ]+&8\\]$", Pattern.CASE_INSENSITIVE);

    private final Map<String, TagOption> tags = new LinkedHashMap<>();
    private final Map<String, TagOption> limitedTags = new LinkedHashMap<>();
    private final Map<UUID, Boolean> awaitingCustomTag = new ConcurrentHashMap<>();

    public TagsModule(ShardedCore plugin) {
        super(plugin, "tags");
    }

    @Override
    protected void onEnable() {
        reloadTags();
        registerCommand("tags", this);
        registerCommand("tag", this);
        plugin.gui().registerMenuExtras("tags", this::shopPlaceholders);
    }

    @Override
    protected void onDisable() {
        awaitingCustomTag.clear();
    }

    private void reloadTags() {
        tags.clear();
        limitedTags.clear();
        loadTagSection(config.getConfigurationSection("tags"), tags);
        loadTagSection(config.getConfigurationSection("limited-tags"), limitedTags);
    }

    private void loadTagSection(ConfigurationSection section, Map<String, TagOption> target) {
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection tag = section.getConfigurationSection(id);
            if (tag == null) continue;
            target.put(id, new TagOption(
                    id,
                    tag.getInt("slot", 0),
                    tag.getString("permission", "eternaltags.tag." + id),
                    tag.getString("material", "PAPER"),
                    tag.getString("display-name", id),
                    tag.getStringList("lore"),
                    tag.getString("apply-command", "eternaltags set %tag_id%"),
                    tag.getBoolean("custom-input", false)
            ));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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

    public void openMainMenu(Player player) {
        openMenu(player, tags, config.getString("menu-title", "Tags"), false);
    }

    public void openLimitedMenu(Player player) {
        openMenu(player, limitedTags, config.getString("limited-menu-title", "Limited Time Tags"), true);
    }

    private void openMenu(Player player, Map<String, TagOption> options, String title, boolean limited) {
        int size = config.getInt(limited ? "limited-menu-size" : "menu-size", 27);
        TagMenuHolder holder = new TagMenuHolder(limited);
        Inventory inventory = plugin.getServer().createInventory(holder, size, Text.c(title));

        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").hideAll().build();
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler.clone());
        }

        String ownedYes = config.getString("owned.yes", "&#9FFF00Yes");
        String ownedNo = config.getString("owned.no", "&#FF2727No");
        String ownedLineTemplate = config.getString("owned.lore-line", "%color%⚓ &fOwned: %owned%");

        for (TagOption tag : options.values()) {
            boolean owned = player.hasPermission(tag.permission());
            List<String> lore = buildLore(tag, owned, ownedYes, ownedNo, ownedLineTemplate);
            Material material = Material.matchMaterial(tag.material());
            if (material == null) material = Material.PAPER;
            inventory.setItem(tag.slot(), new ItemBuilder(material).name(tag.displayName()).lore(lore).hideAll().build());
        }

        if (!limited && config.getBoolean("limited-button.enabled", true)) {
            List<String> limitedLore = config.getStringList("limited-button.lore");
            Material mat = Material.matchMaterial(config.getString("limited-button.material", "CLOCK"));
            if (mat == null) mat = Material.CLOCK;
            inventory.setItem(config.getInt("limited-button.slot", 22),
                    new ItemBuilder(mat)
                            .name(config.getString("limited-button.display-name", "&x&F&F&0&0&0&0&lLIMITED TIME"))
                            .lore(limitedLore)
                            .hideAll()
                            .build());
        }

        if (limited || config.getBoolean("close-button.enabled", true)) {
            int closeSlot = limited ? config.getInt("limited-close-slot", 26) : config.getInt("close-button.slot", 26);
            inventory.setItem(closeSlot, new ItemBuilder(Material.BARRIER)
                    .name(config.getString("close-button.display-name", "&x&F&F&0&0&0&0&lCLOSE"))
                    .hideAll()
                    .build());
        }

        player.openInventory(inventory);
    }

    private List<String> buildLore(TagOption tag, boolean owned, String ownedYes, String ownedNo, String ownedLineTemplate) {
        List<String> lore = new ArrayList<>();
        for (String line : tag.lore()) {
            lore.add(line.replace("%owned%", owned ? ownedYes : ownedNo));
        }
        if (config.getBoolean("owned.show-in-lore", true)) {
            String ownedLine = ownedLineTemplate.replace("%owned%", owned ? ownedYes : ownedNo);
            if (!lore.contains(ownedLine) && !lore.contains(ownedLine.replace("%color%", ""))) {
                lore.add("");
                lore.add(ownedLine);
            }
        }
        return lore;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof TagMenuHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
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
            awaitingCustomTag.put(player.getUniqueId(), true);
            send(player, "custom-prompt");
            return;
        }
        runApplyCommand(player, tag, null);
        send(player, "equipped", "%tag%", tag.displayName());
    }

    private void runApplyCommand(Player player, TagOption tag, String customValue) {
        String cmd = tag.applyCommand()
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%tag_id%", tag.id())
                .replace("%tag%", tag.id())
                .replace("%custom%", customValue == null ? "" : customValue);
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
        if (!awaitingCustomTag.remove(player.getUniqueId())) return;
        event.setCancelled(true);

        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        String normalized = ColorUtil.normalize(input);

        Bukkit.getScheduler().runTask(plugin, () -> handleCustomInput(player, normalized));
    }

    private void handleCustomInput(Player player, String input) {
        if (!player.hasPermission("eternaltags.tag.custom")) {
            send(player, "not-owned", "%tag%", "Custom Tag");
            return;
        }
        if (WordBlacklist.contains(config, "custom-tag-blacklist", input)) {
            send(player, "custom-blacklisted");
            return;
        }
        if (!isValidCustomTag(input)) {
            send(player, "custom-invalid-format");
            return;
        }
        TagOption custom = tags.values().stream().filter(TagOption::customInput).findFirst().orElse(null);
        if (custom == null) {
            String cmd = config.getString("custom-apply-command", "eternaltags set %custom%")
                    .replace("%player%", player.getName())
                    .replace("%player_name%", player.getName())
                    .replace("%custom%", input);
            if (cmd.startsWith("[console]")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring("[console]".length()).trim());
            } else {
                player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
        } else {
            runApplyCommand(player, custom, input);
        }
        send(player, "custom-set", "%tag%", input);
    }

    private boolean isValidCustomTag(String input) {
        if (input == null || input.isBlank()) return false;
        String raw = input.trim();
        if (EXTENDED_HEX_TAG.matcher(raw).matches() || HASH_HEX_TAG.matcher(raw).matches()) return true;
        String normalized = ColorUtil.normalize(raw);
        return NORMALIZED_HEX_TAG.matcher(normalized).matches() || HASH_HEX_TAG.matcher(normalized).matches();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingCustomTag.remove(event.getPlayer().getUniqueId());
    }

    public Map<String, String> shopPlaceholders(Player player) {
        Map<String, String> map = new HashMap<>();
        String ownedYes = config.getString("owned.yes", "&#9FFF00Yes");
        String ownedNo = config.getString("owned.no", "&#FF2727No");
        for (TagOption tag : tags.values()) {
            map.put("tag_owned_" + tag.id(), player.hasPermission(tag.permission()) ? ownedYes : ownedNo);
            map.put("owned_" + tag.id(), player.hasPermission(tag.permission()) ? ownedYes : ownedNo);
        }
        return map;
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
                             String displayName, List<String> lore, String applyCommand,
                             boolean customInput) {
    }
}

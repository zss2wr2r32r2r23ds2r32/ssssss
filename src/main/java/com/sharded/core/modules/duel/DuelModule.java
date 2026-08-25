package com.sharded.core.modules.duel;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /duel <player> — configurable duel request GUI. */
public final class DuelModule extends Module implements CommandExecutor, TabCompleter {

    static final class Holder implements InventoryHolder {
        final UUID targetId;
        final String targetName;
        Inventory inventory;

        Holder(UUID targetId, String targetName) {
            this.targetId = targetId;
            this.targetName = targetName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Map<UUID, UUID> pendingRequests = new HashMap<>();

    public DuelModule(ShardedCore plugin) {
        super(plugin, "duel");
    }

    @Override
    protected void onEnable() {
        registerCommand("duel", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.duel.use")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(player, "usage");
            return true;
        }
        OfflinePlayer target = OfflinePlayers.resolve(args[0]);
        if (target == null || !target.hasPlayedBefore() && !target.isOnline()) {
            send(player, "player-not-found");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(player, "self-duel");
            return true;
        }
        openRequestGui(player, target.getUniqueId(), target.getName() == null ? args[0] : target.getName());
        return true;
    }

    void openRequestGui(Player requester, UUID targetId, String targetName) {
        String title = Text.apply(config.getString("gui.title", "&8Duel Request"), "%player%", targetName);
        Holder holder = new Holder(targetId, targetName);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.c(title));
        holder.inventory = inv;
        TrackedInventories.track(inv, holder);

        ItemStack filler = buildItem("gui.filler", Material.BLACK_STAINED_GLASS_PANE, Map.of("%player%", targetName));
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(config.getInt("gui.slots.send", 11),
                buildItem("gui.send", Material.LIME_STAINED_GLASS_PANE, Map.of("%player%", targetName)));
        inv.setItem(config.getInt("gui.slots.details", 13),
                buildItem("gui.details", Material.OAK_SIGN, Map.of("%player%", targetName)));
        inv.setItem(config.getInt("gui.slots.cancel", 15),
                buildItem("gui.cancel", Material.RED_STAINED_GLASS_PANE, Map.of("%player%", targetName)));

        requester.openInventory(inv);
    }

    private ItemStack buildItem(String path, Material fallback, Map<String, String> placeholders) {
        ConfigurationSection section = config.getConfigurationSection(path);
        Material mat = fallback;
        if (section != null) {
            Material parsed = Material.matchMaterial(section.getString("material", fallback.name()));
            if (parsed != null) mat = parsed;
        }
        String name = section == null ? " " : section.getString("name", " ");
        List<String> lore = section == null ? List.of() : section.getStringList("lore");
        name = Text.apply(name, "%player%", placeholders.getOrDefault("%player%", ""));
        List<String> appliedLore = new ArrayList<>();
        for (String line : lore) {
            appliedLore.add(Text.apply(line, "%player%", placeholders.getOrDefault("%player%", "")));
        }
        return new ItemBuilder(mat).name(name).lore(appliedLore).build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Holder holder = TrackedInventories.lookup(event.getView().getTopInventory(), Holder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (slot == config.getInt("gui.slots.send", 11)) {
            player.closeInventory();
            if (isDisallowed(player)) {
                send(player, "disallowed-items");
                return;
            }
            pendingRequests.put(holder.targetId, player.getUniqueId());
            List<String> commands = config.getStringList("on-send-commands");
            for (String line : commands) {
                String cmd = Text.apply(line, "%player%", holder.targetName, "%sender%", player.getName())
                        .replace("%target%", holder.targetName);
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            send(player, "request-sent", "%player%", holder.targetName);
            Player target = Bukkit.getPlayer(holder.targetId);
            if (target != null) {
                send(target, "request-received", "%player%", player.getName());
            }
        } else if (slot == config.getInt("gui.slots.cancel", 15)) {
            player.closeInventory();
            send(player, "request-cancelled", "%player%", holder.targetName);
        }
    }

    private boolean isDisallowed(Player player) {
        List<String> blocked = config.getStringList("disallowed-items");
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            String name = item.getType().name();
            for (String rule : blocked) {
                if (rule.equalsIgnoreCase(name)) return true;
                if (rule.endsWith("*") && name.startsWith(rule.substring(0, rule.length() - 1).toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.duel.use")) return List.of();
        if (args.length == 1) return TabCompleteHelper.onlinePlayers(args[0]);
        return List.of();
    }
}

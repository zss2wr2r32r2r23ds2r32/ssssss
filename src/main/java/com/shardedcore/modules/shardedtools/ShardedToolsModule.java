package com.shardedcore.modules.shardedtools;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.Amounts;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Players;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Container;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public final class ShardedToolsModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private NamespacedKey idKey;
    private NamespacedKey expireKey;
    private BukkitTask task;
    private final Set<UUID> busy = ConcurrentSet();

    public ShardedToolsModule(ShardedCore plugin) {
        super(plugin, "shardedtools");
    }

    private static Set<UUID> ConcurrentSet() {
        return java.util.concurrent.ConcurrentHashMap.newKeySet();
    }

    @Override
    public void enable() {
        idKey = new NamespacedKey(plugin, "sharded_tool");
        expireKey = new NamespacedKey(plugin, "sharded_tool_expire");
        registerCommand("shardedtool", this);
        registerListener(this);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @Override
    public void disable() {
        if (task != null) task.cancel();
        busy.clear();
        cleanup();
    }

    public boolean give(Player player, String tool, String expireOverride) {
        ConfigurationSection section = toolSection(tool);
        if (section == null || player == null) return false;
        ItemStack item = build(section, expireOverride);
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> giveCommand(sender, args);
            case "remove" -> removeCommand(sender, args);
            default -> {
                send(sender, "usage");
                yield true;
            }
        };
    }

    private boolean giveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.shardedtool.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            send(sender, "usage-give");
            return true;
        }
        Player target = args.length >= 3 ? Players.online(args[2]) : (sender instanceof Player player ? player : null);
        if (target == null) {
            send(sender, "player-missing");
            return true;
        }
        String expire = args.length >= 4 ? args[3] : null;
        if (!give(target, args[1], expire)) {
            send(sender, "unknown-tool", "tool", args[1]);
            return true;
        }
        send(sender, "gave", "tool", args[1], "player", target.getName());
        send(target, "received", "tool", args[1]);
        return true;
    }

    private boolean removeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.shardedtool.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            send(sender, "usage-remove");
            return true;
        }
        Player target = Players.online(args[2]);
        if (target == null) {
            send(sender, "player-missing");
            return true;
        }
        String tool = sanitize(args[1]);
        int removed = 0;
        removed += strip(target.getInventory().getContents(), tool);
        removed += strip(target.getInventory().getArmorContents(), tool);
        ItemStack off = target.getInventory().getItemInOffHand();
        if (isTool(off, tool)) {
            target.getInventory().setItemInOffHand(null);
            removed++;
        }
        send(sender, removed > 0 ? "removed" : "none", "tool", tool, "player", target.getName());
        return true;
    }

    private int strip(ItemStack[] contents, String tool) {
        int count = 0;
        for (int i = 0; i < contents.length; i++) {
            if (isTool(contents[i], tool)) {
                contents[i] = null;
                count++;
            }
        }
        return count;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        String id = toolId(item);
        if (id == null) return;
        if (expired(item)) {
            event.setCancelled(true);
            expire(player, item, EquipmentSlot.HAND);
            return;
        }
        if (id.equals("drill")) {
            drill(player, event.getBlock(), item);
        } else if (id.equals("chopper")) {
            chop(player, event.getBlock(), item);
        }
    }

    @EventHandler
    public void onBoost(PlayerElytraBoostEvent event) {
        ItemStack item = event.getItemStack();
        if (!isTool(item, "firework")) return;
        if (expired(item)) {
            event.setCancelled(true);
            expire(event.getPlayer(), item, null);
            return;
        }
        event.setShouldConsume(false);
        Player player = event.getPlayer();
        ItemStack clone = item.clone();
        clone.setAmount(Math.max(1, config.getInt("tools.firework.amount", 1)));
        Bukkit.getScheduler().runTask(plugin, () -> restoreFirework(player, clone));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isTool(item, "sellwand")) return;
        if (expired(item)) {
            expire(event.getPlayer(), item, EquipmentSlot.HAND);
            event.setCancelled(true);
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof InventoryHolder holder)) return;
        event.setCancelled(true);
        org.bukkit.inventory.Inventory inventory = holder instanceof Container container
                ? container.getInventory() : holder.getInventory();
        com.shardedcore.modules.sell.SellModule sell = plugin.modules().get(com.shardedcore.modules.sell.SellModule.class);
        if (sell != null) sell.sellInventory(event.getPlayer(), inventory);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
        ItemStack item = event.getItem();
        if (!isTool(item, "firework")) return;
        if (expired(item)) {
            expire(event.getPlayer(), item, event.getHand());
            event.setCancelled(true);
            return;
        }
        ItemStack clone = item.clone();
        clone.setAmount(Math.max(1, config.getInt("tools.firework.amount", 1)));
        Bukkit.getScheduler().runTask(plugin, () -> restoreFirework(event.getPlayer(), clone));
    }

    private void restoreFirework(Player player, ItemStack clone) {
        boolean found = false;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack held = contents[i];
            if (isTool(held, "firework")) {
                held.setAmount(Math.max(1, config.getInt("tools.firework.amount", 1)));
                found = true;
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isTool(off, "firework")) {
            off.setAmount(Math.max(1, config.getInt("tools.firework.amount", 1)));
            found = true;
        }
        if (!found && clone != null) {
            player.getInventory().addItem(clone).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private void refillFirework(Player player, EquipmentSlot slot) {
        restoreFirework(player, null);
    }

    private void drill(Player player, Block origin, ItemStack item) {
        if (!busy.add(player.getUniqueId())) return;
        try {
            BlockFace face = player.getTargetBlockFace(6);
            if (face == null) {
                float pitch = player.getLocation().getPitch();
                if (pitch > 45) face = BlockFace.DOWN;
                else if (pitch < -45) face = BlockFace.UP;
                else face = player.getFacing();
            }
            int radius = config.getInt("tools.drill.radius", 1);
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    if (a == 0 && b == 0) continue;
                    Block extra = switch (face) {
                        case UP, DOWN -> origin.getRelative(a, 0, b);
                        case NORTH, SOUTH -> origin.getRelative(a, b, 0);
                        default -> origin.getRelative(0, a, b);
                    };
                    breakExtra(player, extra, item);
                }
            }
        } finally {
            busy.remove(player.getUniqueId());
        }
    }

    private void chop(Player player, Block origin, ItemStack item) {
        if (!Tag.LOGS.isTagged(origin.getType()) && !Tag.LEAVES.isTagged(origin.getType())) return;
        if (!busy.add(player.getUniqueId())) return;
        try {
            int max = Math.max(1, config.getInt("tools.chopper.max-blocks", 512));
            boolean leaves = config.getBoolean("tools.chopper.break-leaves", true);
            Set<Block> seen = new HashSet<>();
            Queue<Block> queue = new ArrayDeque<>();
            queue.add(origin);
            seen.add(origin);
            int broken = 0;
            while (!queue.isEmpty() && broken < max) {
                Block current = queue.poll();
                if (!current.equals(origin)) {
                    if (!breakExtra(player, current, item)) continue;
                    broken++;
                }
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            Block next = current.getRelative(x, y, z);
                            if (!seen.add(next)) continue;
                            if (Tag.LOGS.isTagged(next.getType()) || (leaves && Tag.LEAVES.isTagged(next.getType()))) {
                                queue.add(next);
                            }
                        }
                    }
                }
            }
        } finally {
            busy.remove(player.getUniqueId());
        }
    }

    private boolean breakExtra(Player player, Block block, ItemStack item) {
        if (block.getType().isAir() || block.getType() == Material.BEDROCK || block.getType() == Material.BARRIER) {
            return false;
        }
        BlockBreakEvent extra = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(extra);
        if (extra.isCancelled()) return false;
        block.breakNaturally(item);
        return true;
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scan(player, player.getInventory().getContents());
            scan(player, player.getInventory().getArmorContents());
            ItemStack off = player.getInventory().getItemInOffHand();
            if (refresh(player, off)) player.getInventory().setItemInOffHand(off.getAmount() <= 0 ? null : off);
        }
    }

    private void scan(Player player, ItemStack[] contents) {
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (refresh(player, item) && (item == null || item.getAmount() <= 0)) contents[i] = null;
        }
    }

    private boolean refresh(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = toolId(item);
        if (id == null) return false;
        if (expired(item)) {
            expire(player, item, null);
            item.setAmount(0);
            return true;
        }
        updateLore(item);
        return false;
    }

    private void expire(Player player, ItemStack item, EquipmentSlot slot) {
        send(player, "expired", "tool", toolId(item) == null ? "tool" : toolId(item));
        item.setAmount(0);
        if (slot == EquipmentSlot.HAND) player.getInventory().setItemInMainHand(null);
        if (slot == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
    }

    private ItemStack build(ConfigurationSection section, String expireOverride) {
        Material material = Sounds.material(section.getString("material", "NETHERITE_PICKAXE"), Material.NETHERITE_PICKAXE);
        ItemStack item = new ItemStack(material, Math.max(1, section.getInt("amount", 1)));
        long expireAt = expireAt(section.getString("expire", "7d"), expireOverride);
        item.editMeta(meta -> {
            meta.displayName(ColorUtil.parse(section.getString("name", "&#A370EE&lSHARDED TOOL")));
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, sanitize(section.getName()));
            if (expireAt > 0) meta.getPersistentDataContainer().set(expireKey, PersistentDataType.LONG, expireAt);
            meta.setUnbreakable(section.getBoolean("unbreakable", false));
            if (!section.getBoolean("hide-enchants", false)) {
                meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            ConfigurationSection enchants = section.getConfigurationSection("enchants");
            if (enchants != null) {
                for (String key : enchants.getKeys(false)) {
                    Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                    if (enchantment != null) meta.addEnchant(enchantment, enchants.getInt(key), true);
                }
            }
        });
        updateLore(item);
        return item;
    }

    private void updateLore(ItemStack item) {
        ConfigurationSection section = toolSection(toolId(item));
        if (section == null) return;
        long left = remaining(item);
        String expire = left <= 0
                ? cfg("expired-text", "Expired")
                : Amounts.duration(left,
                cfg("time.days", "d"),
                cfg("time.hours", "h"),
                cfg("time.minutes", "m"),
                cfg("time.seconds", "s"),
                config.getInt("time.units", 2));
        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(Text.apply(line, "expire", expire));
        }
        if (left != Long.MAX_VALUE) {
            String expireLine = section.getString("expire-line", cfg("expire-line", "&#A370EE⚓ &fExpires in: %expire%"));
            lore.add(Text.apply(expireLine, "expire", expire));
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> components = new ArrayList<>();
        for (String line : lore) components.add(ColorUtil.parse(line));
        meta.lore(components);
        item.setItemMeta(meta);
    }

    private long expireAt(String configured, String override) {
        String raw = override == null || override.isBlank() ? configured : override;
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("infinite")
                || raw.equalsIgnoreCase("never") || raw.equals("0")) {
            return 0L;
        }
        long duration = Amounts.durationMillis(raw);
        return duration <= 0 ? 0L : System.currentTimeMillis() + duration;
    }

    private long remaining(ItemStack item) {
        Long at = item.getItemMeta().getPersistentDataContainer().get(expireKey, PersistentDataType.LONG);
        if (at == null || at <= 0) return Long.MAX_VALUE;
        return at - System.currentTimeMillis();
    }

    private boolean expired(ItemStack item) {
        long left = remaining(item);
        return left != Long.MAX_VALUE && left <= 0;
    }

    private String toolId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    private boolean isTool(ItemStack item, String id) {
        String found = toolId(item);
        return found != null && found.equalsIgnoreCase(id);
    }

    private ConfigurationSection toolSection(String id) {
        if (id == null) return null;
        ConfigurationSection section = config.getConfigurationSection("tools." + sanitize(id));
        if (section != null) return section;
        ConfigurationSection tools = config.getConfigurationSection("tools");
        if (tools == null) return null;
        for (String key : tools.getKeys(false)) {
            if (key.equalsIgnoreCase(id)) return tools.getConfigurationSection(key);
        }
        return null;
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        busy.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Tabs.filter(List.of("give", "remove"), args[0]);
        ConfigurationSection tools = config.getConfigurationSection("tools");
        List<String> names = tools == null ? List.of() : new ArrayList<>(tools.getKeys(false));
        if (args.length == 2) return Tabs.filter(names, args[1]);
        if (args.length == 3) return Tabs.players(args[2]);
        return List.of();
    }
}

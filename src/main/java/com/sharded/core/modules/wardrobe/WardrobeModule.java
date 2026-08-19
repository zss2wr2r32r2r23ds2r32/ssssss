package com.sharded.core.modules.wardrobe;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.ItemsAdderHook;
import com.sharded.core.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cosmetic hat wardrobe — equip ItemsAdder hats as helmets with enchants. */
public final class WardrobeModule extends Module implements CommandExecutor {

    private static final String MENU_TITLE = "Wardrobe";
    private final Map<String, HatOption> hats = new LinkedHashMap<>();
    private WardrobeDatabase database;

    private static final class MenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public WardrobeModule(ShardedCore plugin) {
        super(plugin, "wardrobe");
    }

    @Override
    protected void onEnable() {
        try {
            database = new WardrobeDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open wardrobe database", e);
        }
        loadHats();
        registerCommand("wardrobe", this);
    }

    @Override
    protected void onDisable() {
        if (database != null) database.close();
        database = null;
        // Never remove equipped hats on reload.
    }

    private void loadHats() {
        hats.clear();
        var section = config.getConfigurationSection("hats");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var hat = section.getConfigurationSection(id);
            if (hat == null) continue;
            hats.put(id, new HatOption(
                    id,
                    hat.getInt("slot", 0),
                    hat.getString("permission", "sharded.wardrobe." + id),
                    hat.getString("itemsadder-id", "hats:" + id),
                    hat.getString("material", "PAPER"),
                    hat.getString("display-name", id),
                    hat.getStringList("lore")
            ));
        }
    }

    /** Called from token shop via [wardrobe_unlock] hat_id */
    public boolean unlock(Player player, String hatId) {
        HatOption hat = hats.get(hatId.toLowerCase());
        if (hat == null) return false;
        if (database != null) database.unlock(player.getUniqueId(), hat.id());
        send(player, "unlocked", "%hat%", hat.displayName());
        return true;
    }

    public boolean owns(Player player, HatOption hat) {
        if (player.hasPermission(hat.permission())) return true;
        return database != null && database.isUnlocked(player.getUniqueId(), hat.id());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.wardrobe.use")) {
            send(player, "no-permission");
            return true;
        }
        openMenu(player);
        return true;
    }

    public void openMenu(Player player) {
        int rows = config.getInt("menu-rows", 4);
        Inventory inventory = plugin.getServer().createInventory(new MenuHolder(), rows * 9, Text.c(MENU_TITLE));
        Material fillerMat = Material.matchMaterial(config.getString("filler-material", "BLACK_STAINED_GLASS_PANE"));
        if (fillerMat == null) fillerMat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = new ItemBuilder(fillerMat).name(" ").hideAll().build();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler.clone());

        Map<String, String> ph = equipPlaceholders(player);
        for (HatOption hat : hats.values()) {
            if (!owns(player, hat)) continue;
            ItemStack stack = ItemsAdderHook.parseItem(hat.itemsadderId());
            if (stack == null) {
                Material mat = Material.matchMaterial(hat.material());
                if (mat == null) mat = Material.PAPER;
                stack = new ItemStack(mat);
            }
            inventory.setItem(hat.slot(), new ItemBuilder(stack)
                    .name(apply(hat.displayName(), ph))
                    .lore(apply(hat.lore(), ph))
                    .hideAll()
                    .build());
        }

        int removeSlot = config.getInt("remove.slot", 4);
        inventory.setItem(removeSlot, new ItemBuilder(Material.BARRIER)
                .name(config.getString("remove.display-name", "&c&lREMOVE HAT"))
                .lore(config.getStringList("remove.lore"))
                .hideAll()
                .build());

        player.openInventory(inventory);
    }

    public Map<String, String> equipPlaceholders(Player player) {
        Map<String, String> map = new LinkedHashMap<>();
        String yes = config.getString("placeholders.owned-yes", "&#9FFF00&nYes");
        String no = config.getString("placeholders.owned-no", "&#FF2727&nNo");
        for (HatOption hat : hats.values()) {
            map.put("wardrobe_owned_" + hat.id(), owns(player, hat) ? yes : no);
        }
        String equipped = database == null ? "" : database.getEquipped(player.getUniqueId());
        map.put("equipped_hat", equipped == null || equipped.isBlank()
                ? config.getString("placeholders.none", "&7None") : equipped);
        return map;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (event.getSlot() == config.getInt("remove.slot", 4)) {
            player.closeInventory();
            unequip(player);
            send(player, "removed");
            return;
        }

        for (HatOption hat : hats.values()) {
            if (hat.slot() != event.getSlot()) continue;
            player.closeInventory();
            if (!owns(player, hat)) {
                send(player, "not-owned", "%hat%", hat.displayName());
                return;
            }
            equip(player, hat);
            send(player, "equipped", "%hat%", hat.displayName());
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (database == null) return;
        Player player = event.getPlayer();
        String equipped = database.getEquipped(player.getUniqueId());
        if (equipped == null || equipped.isBlank()) return;
        HatOption hat = hats.get(equipped);
        if (hat == null || !owns(player, hat)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> equipSilent(player, hat), 5L);
    }

    public void equip(Player player, HatOption hat) {
        equipSilent(player, hat);
        if (database != null) database.setEquipped(player.getUniqueId(), hat.id());
    }

    private void equipSilent(Player player, HatOption hat) {
        ItemStack hatItem = buildHatItem(hat);
        if (hatItem == null) {
            send(player, "item-missing", "%hat%", hat.displayName());
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack current = inv.getHelmet();
        if (current != null && !current.getType().isAir()) {
            Map<Integer, ItemStack> leftover = inv.addItem(current.clone());
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        inv.setHelmet(hatItem);
    }

    private void unequip(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && !helmet.getType().isAir()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(helmet.clone());
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        player.getInventory().setHelmet(null);
        if (database != null) database.setEquipped(player.getUniqueId(), "");
    }

    private ItemStack buildHatItem(HatOption hat) {
        ItemStack stack = ItemsAdderHook.getItem(hat.itemsadderId());
        if (stack == null) stack = ItemsAdderHook.parseItem("itemsadder-" + hat.itemsadderId());
        if (stack == null) return null;
        stack = stack.clone();
        int prot = config.getInt("enchantments.protection", 4);
        int unb = config.getInt("enchantments.unbreaking", 3);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.PROTECTION, prot, true);
            meta.addEnchant(Enchantment.UNBREAKING, unb, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.setUnbreakable(false);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<String> apply(List<String> lines, Map<String, String> ph) {
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(apply(line, ph));
        return out;
    }

    private String apply(String line, Map<String, String> ph) {
        String out = line;
        for (Map.Entry<String, String> e : ph.entrySet()) out = out.replace("%" + e.getKey() + "%", e.getValue());
        return out;
    }

    private record HatOption(String id, int slot, String permission, String itemsadderId,
                             String material, String displayName, List<String> lore) {
    }
}

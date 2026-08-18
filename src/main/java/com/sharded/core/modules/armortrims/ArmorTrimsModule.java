package com.sharded.core.modules.armortrims;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ArmorTrimsModule extends Module implements CommandExecutor {

    private static final int PATTERN_SLOT = 10;
    private static final int ARMOR_SLOT = 13;
    private static final int MATERIAL_SLOT = 16;
    private static final int CONFIRM_SLOT = 22;

    private static final Map<String, Material> MATERIAL_ICONS = Map.ofEntries(
            Map.entry("quartz", Material.QUARTZ),
            Map.entry("iron", Material.IRON_INGOT),
            Map.entry("gold", Material.GOLD_INGOT),
            Map.entry("copper", Material.COPPER_INGOT),
            Map.entry("emerald", Material.EMERALD),
            Map.entry("diamond", Material.DIAMOND),
            Map.entry("redstone", Material.REDSTONE),
            Map.entry("lapis", Material.LAPIS_LAZULI),
            Map.entry("amethyst", Material.AMETHYST_SHARD));

    private final class TrimHolder implements InventoryHolder {
        private Inventory inventory;
        private int patternIndex;
        private int materialIndex;
        private final List<TrimPattern> patterns = new ArrayList<>();
        private final List<TrimMaterial> materials = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public ArmorTrimsModule(ShardedCore plugin) {
        super(plugin, "armortrims");
    }

    @Override
    protected void onEnable() {
        registerCommand("armortrims", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.armortrims.use")) {
            send(player, "no-permission");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        TrimHolder holder = new TrimHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.c(config.getString("title", "&8Armor Trim Station")));
        holder.inventory = inv;
        loadOptions(holder);
        render(holder);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.6f, 1.2f);
        send(player, "opened");
    }

    private void loadOptions(TrimHolder holder) {
        List<String> excludedPatterns = config.getStringList("excluded-patterns");
        List<String> excludedMaterials = config.getStringList("excluded-materials");
        Registry<TrimPattern> pr = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
        for (TrimPattern p : pr) {
            NamespacedKey key = pr.getKey(p);
            if (key == null) continue;
            String name = key.getKey().toLowerCase(Locale.ROOT);
            if (name.contains("netherite") || excludedPatterns.contains(name)) continue;
            holder.patterns.add(p);
        }
        Registry<TrimMaterial> mr = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
        for (TrimMaterial m : mr) {
            NamespacedKey key = mr.getKey(m);
            if (key == null) continue;
            String name = key.getKey().toLowerCase(Locale.ROOT);
            if (name.contains("netherite") || excludedMaterials.contains(name)) continue;
            holder.materials.add(m);
        }
    }

    private void render(TrimHolder holder) {
        Inventory inv = holder.inventory;
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&r").build();
        for (int i = 0; i < 27; i++) {
            if (i == ARMOR_SLOT) continue;
            inv.setItem(i, filler);
        }
        ItemStack armor = inv.getItem(ARMOR_SLOT);
        inv.setItem(ARMOR_SLOT, armor);

        Registry<TrimPattern> pr = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
        if (!holder.patterns.isEmpty()) {
            TrimPattern pattern = holder.patterns.get(holder.patternIndex);
            NamespacedKey key = pr.getKey(pattern);
            inv.setItem(PATTERN_SLOT, new ItemBuilder(patternIcon(key))
                    .edit(meta -> meta.displayName(Component.empty().append(Text.c("&e◀ ")).append(pattern.description())))
                    .lore(raw("lore-cycle-pattern")).glow(true).hideAll().build());
        }
        Registry<TrimMaterial> mr = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
        if (!holder.materials.isEmpty()) {
            TrimMaterial material = holder.materials.get(holder.materialIndex);
            NamespacedKey key = mr.getKey(material);
            inv.setItem(MATERIAL_SLOT, new ItemBuilder(materialIcon(key))
                    .edit(meta -> meta.displayName(Component.empty().append(material.description()).append(Text.c(" &b▶"))))
                    .lore(raw("lore-cycle-material")).glow(true).hideAll().build());
        }
        inv.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_CONCRETE).name(raw("confirm-name")).lore(raw("confirm-lore")).build());
    }

    private Material patternIcon(NamespacedKey key) {
        if (key != null) {
            Material t = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT) + "_ARMOR_TRIM_SMITHING_TEMPLATE");
            if (t != null) return t;
        }
        return Material.PAPER;
    }

    private Material materialIcon(NamespacedKey key) {
        if (key == null) return Material.EMERALD;
        Material icon = MATERIAL_ICONS.get(key.getKey().toLowerCase(Locale.ROOT));
        if (icon != null) return icon;
        icon = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT));
        if (icon != null) return icon;
        icon = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT) + "_INGOT");
        return icon != null ? icon : Material.EMERALD;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TrimHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() == holder.inventory) {
            int slot = event.getSlot();
            if (slot == PATTERN_SLOT && !holder.patterns.isEmpty()) {
                holder.patternIndex = (holder.patternIndex + 1) % holder.patterns.size();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
                applyTrim(holder);
            } else if (slot == MATERIAL_SLOT && !holder.materials.isEmpty()) {
                holder.materialIndex = (holder.materialIndex + 1) % holder.materials.size();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.7f);
                applyTrim(holder);
            } else if (slot == CONFIRM_SLOT) {
                applyTrim(holder);
                send(player, "trim-applied");
                player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
            } else if (slot == ARMOR_SLOT) {
                ItemStack cursor = event.getCursor();
                ItemStack current = event.getCurrentItem();
                if (cursor != null && !cursor.getType().isAir()) {
                    if (!isArmor(cursor)) {
                        send(player, "not-armor");
                        return;
                    }
                    holder.inventory.setItem(ARMOR_SLOT, cursor.clone());
                    player.setItemOnCursor(current != null && !current.getType().isAir() ? current.clone() : null);
                    applyTrim(holder);
                } else if (current != null && !current.getType().isAir()) {
                    player.setItemOnCursor(current.clone());
                    holder.inventory.setItem(ARMOR_SLOT, null);
                    render(holder);
                }
            }
            return;
        }

        if (event.isShiftClick()) {
            ItemStack cur = event.getCurrentItem();
            ItemStack armor = holder.inventory.getItem(ARMOR_SLOT);
            if (isArmor(cur) && (armor == null || armor.getType().isAir())) {
                holder.inventory.setItem(ARMOR_SLOT, cur.clone());
                event.getClickedInventory().setItem(event.getSlot(), null);
                applyTrim(holder);
            }
        }
    }

    private void applyTrim(TrimHolder holder) {
        ItemStack armor = holder.inventory.getItem(ARMOR_SLOT);
        if (!isArmor(armor) || holder.patterns.isEmpty() || holder.materials.isEmpty()) {
            render(holder);
            return;
        }
        ArmorMeta meta = (ArmorMeta) armor.getItemMeta();
        meta.setTrim(new ArmorTrim(holder.materials.get(holder.materialIndex), holder.patterns.get(holder.patternIndex)));
        armor.setItemMeta(meta);
        holder.inventory.setItem(ARMOR_SLOT, armor);
        render(holder);
    }

    private boolean isArmor(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getItemMeta() instanceof ArmorMeta;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TrimHolder)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TrimHolder)) return;
        ItemStack armor = event.getInventory().getItem(ARMOR_SLOT);
        if (armor != null && !armor.getType().isAir() && event.getPlayer() instanceof Player player) {
            player.getInventory().addItem(armor).values()
                    .forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }
    }
}

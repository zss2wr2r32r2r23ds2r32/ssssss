package com.sharded.core.modules.armortrims;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.BundleUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryAction;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ArmorTrimsModule extends Module implements CommandExecutor {

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

    private int patternSlot = 10;
    private int armorSlot = 13;
    private int materialSlot = 16;
    private int confirmSlot = 22;
    private int guiSize = 27;
    private Material fillerMaterial = Material.BLACK_STAINED_GLASS_PANE;
    private String fillerName = "&r";
    private Material confirmMaterial = Material.LIME_DYE;
    private Material patternDisplayMaterial = Material.PAPER;
    private boolean useTemplateIcon = true;
    private String patternDisplayName = "&#0083FF&l%pattern%";
    private String materialDisplayName = "&#0083FF&l%material%";
    private boolean patternGlow = true;
    private boolean materialGlow = true;
    private Material patternFallback = Material.PAPER;
    private Material materialFallback = Material.EMERALD;
    private Map<String, Material> materialIcons = Map.of();
    private Map<String, Material> patternIcons = Map.of();

    public ArmorTrimsModule(ShardedCore plugin) {
        super(plugin, "armortrims");
    }

    @Override
    protected void onEnable() {
        reloadGuiSettings();
        registerCommand("armortrims", this);
    }

    private void reloadGuiSettings() {
        ConfigurationSection gui = config.getConfigurationSection("gui");
        guiSize = Math.max(9, Math.min(54, config.getInt("gui.size", 27)));
        if (gui != null) {
            ConfigurationSection slots = gui.getConfigurationSection("slots");
            if (slots != null) {
                patternSlot = slots.getInt("pattern", 10);
                armorSlot = slots.getInt("armor", 13);
                materialSlot = slots.getInt("material", 16);
                confirmSlot = slots.getInt("confirm", 22);
            }
            ConfigurationSection filler = gui.getConfigurationSection("filler");
            if (filler != null) {
                fillerMaterial = parseMaterial(filler.getString("material"), Material.BLACK_STAINED_GLASS_PANE);
                fillerName = filler.getString("name", "&r");
            }
            ConfigurationSection patternItem = gui.getConfigurationSection("pattern-item");
            if (patternItem != null) {
                patternDisplayMaterial = parseMaterial(patternItem.getString("material"), Material.PAPER);
                useTemplateIcon = patternItem.getBoolean("use-template-icon", true);
                patternDisplayName = patternItem.getString("display-name", patternDisplayName);
                patternGlow = patternItem.getBoolean("glow", true);
            }
            ConfigurationSection materialItem = gui.getConfigurationSection("material-item");
            if (materialItem != null) {
                materialDisplayName = materialItem.getString("display-name", materialDisplayName);
                materialGlow = materialItem.getBoolean("glow", true);
            }
            ConfigurationSection confirm = gui.getConfigurationSection("confirm");
            if (confirm != null) {
                confirmMaterial = parseMaterial(confirm.getString("material"), Material.LIME_CONCRETE);
            }
        }
        patternFallback = parseMaterial(config.getString("pattern-icon-fallback"), Material.PAPER);
        materialFallback = parseMaterial(config.getString("material-icon-fallback"), Material.EMERALD);
        materialIcons = loadIconMap("material-icons");
        patternIcons = loadIconMap("pattern-icons");
    }

    private Map<String, Material> loadIconMap(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return Map.of();
        Map<String, Material> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            Material mat = parseMaterial(section.getString(key), null);
            if (mat != null) map.put(key.toLowerCase(Locale.ROOT), mat);
        }
        return Map.copyOf(map);
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material mat = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return mat != null ? mat : fallback;
    }

    private Sound parseSound(String path, Sound fallback) {
        String name = config.getString(path);
        if (name == null || name.isBlank()) return fallback;
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private float soundFloat(String path, float fallback) {
        return (float) config.getDouble(path, fallback);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        String permission = config.getString("permission", "sharded.armortrims.use");
        if (!player.hasPermission(permission)) {
            send(player, "no-permission");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        reloadGuiSettings();
        TrimHolder holder = new TrimHolder();
        Inventory inv = Bukkit.createInventory(holder, guiSize, Text.c(config.getString("title", "&8Armor Trim Station")));
        holder.inventory = inv;
        TrackedInventories.track(inv, holder);
        loadOptions(holder);
        render(holder);
        player.openInventory(inv);
        player.playSound(player.getLocation(),
                parseSound("sounds.open", Sound.BLOCK_SMITHING_TABLE_USE),
                soundFloat("sounds.open-volume", 0.6f),
                soundFloat("sounds.open-pitch", 1.2f));
        send(player, "opened");
    }

    private void loadOptions(TrimHolder holder) {
        holder.patterns.clear();
        holder.materials.clear();
        ConfigurationSection filters = config.getConfigurationSection("filters");
        boolean excludeNetherite = filters == null || filters.getBoolean("exclude-netherite", true);
        List<String> excludedPatterns = filters != null
                ? filters.getStringList("excluded-patterns")
                : config.getStringList("excluded-patterns");
        List<String> excludedMaterials = filters != null
                ? filters.getStringList("excluded-materials")
                : config.getStringList("excluded-materials");

        Registry<TrimPattern> pr = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
        for (TrimPattern p : pr) {
            NamespacedKey key = pr.getKey(p);
            if (key == null) continue;
            String name = key.getKey().toLowerCase(Locale.ROOT);
            if (excludeNetherite && name.contains("netherite")) continue;
            if (name.equals("silence")) continue;
            if (excludedPatterns.contains(name)) continue;
            holder.patterns.add(p);
        }
        Registry<TrimMaterial> mr = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
        for (TrimMaterial m : mr) {
            NamespacedKey key = mr.getKey(m);
            if (key == null) continue;
            String name = key.getKey().toLowerCase(Locale.ROOT);
            if (excludeNetherite && name.contains("netherite")) continue;
            if (excludedMaterials.contains(name)) continue;
            holder.materials.add(m);
        }
    }

    private void render(TrimHolder holder) {
        Inventory inv = holder.inventory;
        ItemStack filler = new ItemBuilder(fillerMaterial).name(fillerName).build();
        ItemStack armor = inv.getItem(armorSlot);
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == armorSlot) continue;
            inv.setItem(i, filler);
        }
        inv.setItem(armorSlot, armor);

        Registry<TrimPattern> pr = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
        if (!holder.patterns.isEmpty()) {
            TrimPattern pattern = holder.patterns.get(holder.patternIndex);
            NamespacedKey key = pr.getKey(pattern);
            String label = registryLabel(key);
            String name = Text.apply(messages.getString("pattern-name", patternDisplayName), "%pattern%", label);
            ItemStack patternItem = new ItemBuilder(patternDisplayMaterial(key))
                    .name(name)
                    .lore(rawList("pattern-lore"))
                    .glow(patternGlow)
                    .hideAll()
                    .build();
            BundleUtil.forceCustomTooltip(patternItem);
            inv.setItem(patternSlot, patternItem);
        }
        Registry<TrimMaterial> mr = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
        if (!holder.materials.isEmpty()) {
            TrimMaterial material = holder.materials.get(holder.materialIndex);
            NamespacedKey key = mr.getKey(material);
            String label = registryLabel(key);
            String name = Text.apply(messages.getString("material-name", materialDisplayName), "%material%", label);
            ItemStack materialItem = new ItemBuilder(materialIcon(key))
                    .name(name)
                    .lore(rawList("material-lore"))
                    .glow(materialGlow)
                    .hideAll()
                    .build();
            BundleUtil.forceCustomTooltip(materialItem);
            inv.setItem(materialSlot, materialItem);
        }
        ItemStack confirm = new ItemBuilder(confirmMaterial)
                .name(raw("confirm-name"))
                .lore(rawList("confirm-lore"))
                .hideAll()
                .build();
        inv.setItem(confirmSlot, confirm);
    }

    private Material patternDisplayMaterial(NamespacedKey key) {
        if (useTemplateIcon && key != null) {
            Material template = patternIcon(key);
            if (template != Material.PAPER) return template;
        }
        Material override = key != null ? patternIcons.get(key.getKey().toLowerCase(Locale.ROOT)) : null;
        if (override != null) return override;
        return patternDisplayMaterial;
    }

    private String registryLabel(NamespacedKey key) {
        if (key == null) return "Unknown";
        return key.getKey().replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private Material patternIcon(NamespacedKey key) {
        if (key != null) {
            Material override = patternIcons.get(key.getKey().toLowerCase(Locale.ROOT));
            if (override != null) return override;
            Material t = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT) + "_ARMOR_TRIM_SMITHING_TEMPLATE");
            if (t != null) return t;
        }
        return patternFallback;
    }

    private Material materialIcon(NamespacedKey key) {
        if (key == null) return materialFallback;
        Material icon = materialIcons.get(key.getKey().toLowerCase(Locale.ROOT));
        if (icon != null) return icon;
        icon = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT));
        if (icon != null) return icon;
        icon = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT) + "_INGOT");
        return icon != null ? icon : materialFallback;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        TrimHolder holder = TrackedInventories.lookup(
                event.getView().getTopInventory(), TrimHolder.class);
        if (holder == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        if (rawSlot < topSize) {
            if (rawSlot == armorSlot) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && !cursor.getType().isAir() && !isArmor(cursor)) {
                    event.setCancelled(true);
                    send(player, "not-armor");
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> applyTrim(holder));
                return;
            }
            event.setCancelled(true);
            if (rawSlot == patternSlot && !holder.patterns.isEmpty()) {
                holder.patternIndex = (holder.patternIndex + 1) % holder.patterns.size();
                player.playSound(player.getLocation(),
                        parseSound("sounds.cycle", Sound.UI_BUTTON_CLICK),
                        soundFloat("sounds.cycle-volume", 0.5f),
                        soundFloat("sounds.pattern-pitch", 1.4f));
                applyTrim(holder);
            } else if (rawSlot == materialSlot && !holder.materials.isEmpty()) {
                holder.materialIndex = (holder.materialIndex + 1) % holder.materials.size();
                player.playSound(player.getLocation(),
                        parseSound("sounds.cycle", Sound.UI_BUTTON_CLICK),
                        soundFloat("sounds.cycle-volume", 0.5f),
                        soundFloat("sounds.material-pitch", 1.7f));
                applyTrim(holder);
            } else if (rawSlot == confirmSlot) {
                applyTrim(holder);
                send(player, "trim-applied");
                player.playSound(player.getLocation(),
                        parseSound("sounds.confirm", Sound.BLOCK_SMITHING_TABLE_USE),
                        soundFloat("sounds.confirm-volume", 1f),
                        soundFloat("sounds.confirm-pitch", 1f));
            }
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            ItemStack existing = holder.inventory.getItem(armorSlot);
            if (isArmor(current) && (existing == null || existing.getType().isAir())) {
                holder.inventory.setItem(armorSlot, current.clone());
                event.setCurrentItem(null);
                applyTrim(holder);
            }
        }
    }

    private void applyTrim(TrimHolder holder) {
        ItemStack armor = holder.inventory.getItem(armorSlot);
        if (!isArmor(armor) || holder.patterns.isEmpty() || holder.materials.isEmpty()) {
            render(holder);
            return;
        }
        ArmorMeta meta = (ArmorMeta) armor.getItemMeta();
        meta.setTrim(new ArmorTrim(holder.materials.get(holder.materialIndex), holder.patterns.get(holder.patternIndex)));
        armor.setItemMeta(meta);
        holder.inventory.setItem(armorSlot, armor);
        render(holder);
    }

    private boolean isArmor(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getItemMeta() instanceof ArmorMeta;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (TrackedInventories.lookup(event.getView().getTopInventory(), TrimHolder.class) == null) return;
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize() && slot != armorSlot) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        TrimHolder holder = TrackedInventories.untrack(event.getInventory(), TrimHolder.class);
        if (holder == null) return;
        ItemStack armor = event.getInventory().getItem(armorSlot);
        if (armor != null && !armor.getType().isAir() && event.getPlayer() instanceof Player player) {
            player.getInventory().addItem(armor).values()
                    .forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }
    }
}

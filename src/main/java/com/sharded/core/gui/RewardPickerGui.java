package com.sharded.core.gui;

import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Config-driven reward picker GUI with rarity colors. */
public final class RewardPickerGui {

    public static final class Holder implements InventoryHolder {
        public final String pickerId;
        Inventory inventory;

        Holder(String pickerId) {
            this.pickerId = pickerId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public record RewardOption(String id, String display, String rarityColor, String rarityLabel,
                               Material material, int slot, List<String> commands) {}

    private final String id;
    private final String title;
    private final int size;
    private final Map<Integer, RewardOption> bySlot = new HashMap<>();

    public RewardPickerGui(String id, YamlConfiguration config) {
        this.id = id;
        this.title = config.getString("picker-title", config.getString("gui.picker-title", "&8Rewards"));
        this.size = Math.max(9, Math.min(54, config.getInt("picker-size", 27)));
        loadRewards(config);
    }

    private void loadRewards(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("rewards");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(key);
            if (r == null) continue;
            Material mat = Material.matchMaterial(r.getString("material", "CHEST").toUpperCase());
            if (mat == null) mat = Material.CHEST;
            bySlot.put(r.getInt("slot", 13), new RewardOption(
                    key,
                    r.getString("display", key),
                    r.getString("rarity-color", "&7"),
                    r.getString("rarity-label", r.getString("rarity", "Common")),
                    mat,
                    r.getInt("slot", 13),
                    r.getStringList("commands")));
        }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(id), size, Text.c(title));
        ((Holder) inv.getHolder()).inventory = inv;
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
        for (RewardOption option : bySlot.values()) {
            inv.setItem(option.slot(), buildItem(option));
        }
        player.openInventory(inv);
    }

    private ItemStack buildItem(RewardOption option) {
        String click = "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Claim";
        return new ItemBuilder(option.material())
                .name(option.rarityColor() + "&l" + option.display())
                .lore(List.of(
                        "&8Reward",
                        "",
                        option.rarityColor() + "Information:",
                        option.rarityColor() + "| &fRarity: " + option.rarityColor() + option.rarityLabel(),
                        option.rarityColor() + "| &fClick to claim this reward.",
                        "",
                        click))
                .build();
    }

    public RewardOption optionAt(int slot) {
        return bySlot.get(slot);
    }

    public static void grant(Player player, RewardOption option, BiConsumer<Player, RewardOption> afterGrant) {
        for (String cmd : option.commands()) {
            String parsed = cmd.replace("%player%", player.getName())
                    .replace("%player_name%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
        if (afterGrant != null) afterGrant.accept(player, option);
    }
}

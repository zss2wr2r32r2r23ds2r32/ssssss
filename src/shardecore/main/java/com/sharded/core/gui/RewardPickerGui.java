package com.sharded.core.gui;

import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class RewardPickerGui {
   private final String id;
   private final String title;
   private final int size;
   private final Map<Integer, RewardPickerGui.RewardOption> bySlot = new HashMap<>();

   public RewardPickerGui(String id, YamlConfiguration config) {
      this.id = id;
      this.title = config.getString("picker-title", config.getString("gui.picker-title", "&8Rewards"));
      this.size = Math.max(9, Math.min(54, config.getInt("picker-size", 27)));
      this.loadRewards(config);
   }

   private void loadRewards(YamlConfiguration config) {
      ConfigurationSection configurationsection = config.getConfigurationSection("rewards");
      if (configurationsection != null) {
         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null) {
               Material material = Material.matchMaterial(configurationsection1.getString("material", "CHEST").toUpperCase());
               if (material == null) {
                  material = Material.CHEST;
               }

               this.bySlot
                  .put(
                     configurationsection1.getInt("slot", 13),
                     new RewardPickerGui.RewardOption(
                        s,
                        configurationsection1.getString("display", s),
                        configurationsection1.getString("rarity-color", "&7"),
                        configurationsection1.getString("rarity-label", configurationsection1.getString("rarity", "Common")),
                        material,
                        configurationsection1.getInt("slot", 13),
                        configurationsection1.getStringList("commands")
                     )
                  );
            }
         }
      }
   }

   public void open(Player player) {
      RewardPickerGui.Holder rewardpickergui$holder = new RewardPickerGui.Holder(this.id);
      Inventory inventory = Bukkit.createInventory(rewardpickergui$holder, this.size, Text.c(this.title));
      rewardpickergui$holder.inventory = inventory;
      TrackedInventories.track(inventory, rewardpickergui$holder);
      ItemStack itemstack = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();

      for (int i = 0; i < this.size; i++) {
         inventory.setItem(i, itemstack);
      }

      for (RewardPickerGui.RewardOption rewardpickergui$rewardoption : this.bySlot.values()) {
         inventory.setItem(rewardpickergui$rewardoption.slot(), this.buildItem(rewardpickergui$rewardoption));
      }

      player.openInventory(inventory);
   }

   private ItemStack buildItem(RewardPickerGui.RewardOption option) {
      String s = "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Claim";
      return new ItemBuilder(option.material())
         .name(option.rarityColor() + "&l" + option.display())
         .lore(
            List.of(
               "&8Reward",
               "",
               option.rarityColor() + "Information:",
               option.rarityColor() + "| &fRarity: " + option.rarityColor() + option.rarityLabel(),
               option.rarityColor() + "| &fClick to claim this reward.",
               "",
               s
            )
         )
         .build();
   }

   public RewardPickerGui.RewardOption optionAt(int slot) {
      return this.bySlot.get(slot);
   }

   public static void grant(Player player, RewardPickerGui.RewardOption option, BiConsumer<Player, RewardPickerGui.RewardOption> afterGrant) {
      for (String s : option.commands()) {
         String s1 = s.replace("%player%", player.getName()).replace("%player_name%", player.getName()).replace("%uuid%", player.getUniqueId().toString());
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s1);
      }

      if (afterGrant != null) {
         afterGrant.accept(player, option);
      }
   }

   public static final class Holder implements InventoryHolder {
      public final String pickerId;
      Inventory inventory;

      Holder(String pickerId) {
         this.pickerId = pickerId;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }

   public static record RewardOption(String id, String display, String rarityColor, String rarityLabel, Material material, int slot, List<String> commands) {
   }
}

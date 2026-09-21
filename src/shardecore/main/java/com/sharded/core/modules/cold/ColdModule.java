package com.sharded.core.modules.cold;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.setup.gui.GuiHelper;
import com.sharded.core.setup.gui.GuiHolder;
import com.sharded.core.setup.util.ItemBuilder;
import com.sharded.core.setup.util.SoundUtil;
import com.sharded.core.setup.util.TextUtil;
import com.sharded.core.util.ItemsAdderHook;
import java.util.HashMap;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ColdModule extends Module implements CommandExecutor, TabCompleter {
   private static final String GUI_ID = "cold-wardrobe";
   private static final String HAT_ID = "somehats:pharaon_hat";
   private static final int HAT_SLOT = 10;
   private final NamespacedKey mark;

   public ColdModule(ShardedCore plugin) {
      super(plugin, "cold");
      this.mark = new NamespacedKey(plugin, "cold-hat");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("cold", this);
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         return true;
      }
      if (!player.isOp()) {
         return true;
      }
      this.open(player);
      return true;
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return List.of();
   }

   private void open(Player player) {
      var inventory = GuiHelper.create(GUI_ID, "Wardrobe", 6);
      GuiHelper.fillDarkGlass(inventory);
      inventory.setItem(HAT_SLOT, this.hatItem());
      player.openInventory(inventory);
      SoundUtil.play(player, "block.note_block.pling");
   }

   private ItemStack hatItem() {
      ItemStack stack = ItemsAdderHook.resolveCustom(HAT_ID);
      if (stack == null) {
         stack = ItemsAdderHook.resolveCustom("somehats:pharaoh_hat");
      }
      if (stack == null) {
         stack = ItemBuilder.of(Material.GOLDEN_HELMET)
               .name("&#FCFF00&lPharaoh Hat")
               .lore(List.of("&8Hat", "", "&#FCFF00| &fClick to equip"))
               .hideExtras()
               .build();
      }
      this.enchant(stack);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         if (meta.displayName() == null) {
            meta.displayName(TextUtil.itemComponent("&#FCFF00&lPharaoh Hat"));
         }
         meta.lore(List.of(
               TextUtil.itemComponent("&8Hat"),
               TextUtil.itemComponent(""),
               TextUtil.itemComponent("&#FCFF00| &fProtection IV"),
               TextUtil.itemComponent("&#FCFF00| &fUnbreaking III"),
               TextUtil.itemComponent("&#FCFF00| &fMending"),
               TextUtil.itemComponent(""),
               TextUtil.itemComponent("&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Equip")
         ));
         meta.getPersistentDataContainer().set(this.mark, PersistentDataType.BYTE, (byte) 1);
         stack.setItemMeta(meta);
      }
      return stack;
   }

   private void enchant(ItemStack stack) {
      stack.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
      stack.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
      stack.addUnsafeEnchantment(Enchantment.MENDING, 1);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
         stack.setItemMeta(meta);
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }
      if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
         return;
      }
      if (!GUI_ID.equals(holder.getId())) {
         return;
      }
      event.setCancelled(true);
      if (event.getRawSlot() != HAT_SLOT) {
         return;
      }
      ItemStack hat = this.hatItem();
      PlayerInventory inv = player.getInventory();
      var wardrobe = this.plugin.modules().get(com.sharded.core.modules.wardrobe.WardrobeModule.class);
      if (wardrobe != null && wardrobe.isWardrobeHat(inv.getHelmet())) {
         wardrobe.clearCosmeticHat(player);
      }
      ItemStack current = inv.getHelmet();
      inv.setHelmet(hat);
      if (current != null && !current.getType().isAir()) {
         HashMap<Integer, ItemStack> leftover = inv.addItem(current);
         leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
      }
      player.closeInventory();
      SoundUtil.play(player, "entity.player.levelup");
   }
}
